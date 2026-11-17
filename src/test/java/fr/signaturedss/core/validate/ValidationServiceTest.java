package fr.signaturedss.core.validate; // paquet validation

import eu.europa.esig.dss.enumerations.DigestAlgorithm; // empreinte
import eu.europa.esig.dss.enumerations.Indication; // verdict
import eu.europa.esig.dss.enumerations.SignatureLevel; // niveau DSS
import eu.europa.esig.dss.enumerations.SignaturePackaging; // enveloppe
import eu.europa.esig.dss.model.DSSDocument; // document
import eu.europa.esig.dss.model.FileDocument; // document fichier
import eu.europa.esig.dss.model.SignatureValue; // valeur de signature
import eu.europa.esig.dss.model.ToBeSigned; // donnees a signer
import eu.europa.esig.dss.token.DSSPrivateKeyEntry; // cle privee
import eu.europa.esig.dss.token.Pkcs12SignatureToken; // jeton
import eu.europa.esig.dss.xades.XAdESSignatureParameters; // parametres XAdES
import eu.europa.esig.dss.xades.signature.XAdESService; // service XAdES
import fr.signaturedss.core.OutputMode; // forme
import fr.signaturedss.core.SigningLevel; // niveau
import fr.signaturedss.core.sign.Samples; // exemples
import fr.signaturedss.core.sign.SignRequest; // requete
import fr.signaturedss.core.sign.SigningService; // signature
import fr.signaturedss.core.trust.TrustContext; // confiance
import fr.signaturedss.testpki.TestPki; // PKI de test
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.nio.charset.StandardCharsets; // UTF-8
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.util.List; // liste

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertNotNull; // non nul
import static org.junit.jupiter.api.Assertions.assertThrows; // exception attendue
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class ValidationServiceTest { // tests de validation

    private static final TestPki PKI = TestPki.create(); // PKI partagee

    private static Path sign(Path in, SigningLevel l, OutputMode m) { // signe avec la PKI de test
        SigningService s = new SigningService(TrustContext.of(PKI.verifier()), PKI.tsp()); // service
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton
            DSSPrivateKeyEntry k = t.getKeys().get(0); // cle
            return s.sign(new SignRequest(List.of(in), l, m, false, k), t).get(0).output(); // sortie
        }
    }

    private static ValidationService service() { return new ValidationService(TrustContext.of(PKI.verifier())); } // validateur de test

    @Test // signature LT propre : valide et LT
    void validatesOwnLtSignature(@TempDir Path dir) throws Exception { // LT detache
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        ValidationOutcome o = service().validate(sign(in, SigningLevel.LT, OutputMode.CADES_DETACHED), List.of(in)); // validation
        assertEquals(Indication.TOTAL_PASSED, o.verdict()); // valide
        assertEquals(SignatureLevel.CAdES_BASELINE_LT, o.level()); // niveau LT
    }

    @Test // chaque niveau PAdES est bien atteint
    void padesLevelsReached(@TempDir Path dir) throws Exception { // PDF
        for (SigningLevel l : SigningLevel.values()) { // chaque niveau
            Path in = Samples.pdf(dir, "d-" + l + ".pdf"); // PDF
            ValidationOutcome o = service().validate(sign(in, l, OutputMode.CADES_DETACHED), List.of()); // validation
            assertEquals(eu.europa.esig.dss.enumerations.SignatureLevel.valueOf("PAdES_BASELINE_" + l), o.level(), l.name()); // niveau atteint
        }
    }

    @Test // ASiC-E valide
    void validatesAsice(@TempDir Path dir) throws Exception { // conteneur
        Path in = Samples.mp4(dir, "v.mp4"); // video
        ValidationOutcome o = service().validate(sign(in, SigningLevel.LTA, OutputMode.ASICE), List.of()); // validation
        assertEquals(Indication.TOTAL_PASSED, o.verdict()); // valide
        assertEquals(SignatureLevel.CAdES_BASELINE_LTA, o.level()); // LTA
    }

    @Test // signature XAdES d'un tiers
    void validatesThirdPartyXades(@TempDir Path dir) throws Exception { // XAdES
        Path xml = Files.writeString(dir.resolve("a.xml"), "<a>texte</a>", StandardCharsets.UTF_8); // document XML
        XAdESService s = new XAdESService(PKI.verifier()); // service tiers
        XAdESSignatureParameters p = new XAdESSignatureParameters(); // parametres
        Path out = dir.resolve("a-xades.xml"); // sortie
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton
            DSSPrivateKeyEntry k = t.getKeys().get(0); // cle
            p.setSignatureLevel(SignatureLevel.XAdES_BASELINE_B); // niveau B
            p.setSignaturePackaging(SignaturePackaging.ENVELOPED); // enveloppee
            p.setDigestAlgorithm(DigestAlgorithm.SHA256); // SHA-256
            p.setSigningCertificate(k.getCertificate()); // certificat
            p.setCertificateChain(k.getCertificateChain()); // chaine
            DSSDocument doc = new FileDocument(xml.toFile()); // document
            ToBeSigned tbs = s.getDataToSign(doc, p); // a signer
            SignatureValue sv = t.sign(tbs, DigestAlgorithm.SHA256, k); // signature
            s.signDocument(doc, p, sv).save(out.toString()); // ecriture
        }
        ValidationOutcome o = service().validate(out, List.of()); // validation
        assertNotNull(o.verdict()); // verdict present
        assertEquals(SignatureLevel.XAdES_BASELINE_B, o.level()); // niveau XAdES B
    }

    @Test // original modifie : echec
    void tamperedOriginalFails(@TempDir Path dir) throws Exception { // falsification
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        Path sig = sign(in, SigningLevel.B, OutputMode.CADES_DETACHED); // signature
        Files.writeString(in, "0\nSECTION\nmodifie\n"); // modification
        assertEquals(Indication.TOTAL_FAILED, service().validate(sig, List.of(in)).verdict()); // echec
    }

    @Test // detachee sans original : demande l'original (Review Focus 5)
    void detachedWithoutOriginalAsksForIt(@TempDir Path dir) throws Exception { // original manquant
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        Path sig = sign(in, SigningLevel.B, OutputMode.CADES_DETACHED); // signature
        assertThrows(MissingOriginalException.class, () -> service().validate(sig, List.of())); // exception claire
    }

    @Test // export PDF du rapport
    void exportsPdfReport(@TempDir Path dir) throws Exception { // rapport PDF
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        ValidationOutcome o = service().validate(sign(in, SigningLevel.T, OutputMode.CADES_DETACHED), List.of(in)); // validation
        Path pdf = dir.resolve("rapport.pdf"); // sortie
        ReportExporter.exportPdf(o.reports(), pdf); // export
        assertTrue(new String(Files.readAllBytes(pdf), 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF")); // en-tete PDF
    }

    @Test // export XML des trois rapports
    void exportsXmlReports(@TempDir Path dir) throws Exception { // rapports XML
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        ValidationOutcome o = service().validate(sign(in, SigningLevel.B, OutputMode.CADES_DETACHED), List.of(in)); // validation
        List<Path> files = ReportExporter.exportXml(o.reports(), dir, "plan"); // export
        assertEquals(3, files.size()); // trois fichiers
        for (Path f : files) { assertTrue(Files.size(f) > 0); } // non vides
    }

    @Test // expiration du dernier horodatage
    void timestampExpiryReported(@TempDir Path dir) throws Exception { // date d'expiration
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        ValidationOutcome o = service().validate(sign(in, SigningLevel.T, OutputMode.CADES_DETACHED), List.of(in)); // validation
        assertEquals(PKI.tsaCert().getNotAfter(), o.lastTimestampExpiry().orElseThrow()); // certificat TSA
    }

    @Test // niveau B : pas d'horodatage
    void noTimestampAtLevelB(@TempDir Path dir) throws Exception { // aucune date
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        assertTrue(service().validate(sign(in, SigningLevel.B, OutputMode.CADES_DETACHED), List.of(in)).lastTimestampExpiry().isEmpty()); // vide
    }
}
