package fr.signaturedss.core.extend; // paquet prolongation

import eu.europa.esig.dss.enumerations.SignatureLevel; // niveau DSS
import eu.europa.esig.dss.token.Pkcs12SignatureToken; // jeton
import fr.signaturedss.core.OutputMode; // forme
import fr.signaturedss.core.SigningLevel; // niveau
import fr.signaturedss.core.sign.Samples; // exemples
import fr.signaturedss.core.sign.SignRequest; // requete
import fr.signaturedss.core.sign.SigningService; // signature
import fr.signaturedss.core.trust.TrustContext; // confiance
import fr.signaturedss.core.validate.MissingOriginalException; // original manquant
import fr.signaturedss.core.validate.ValidationOutcome; // resultat
import fr.signaturedss.core.validate.ValidationService; // validation
import fr.signaturedss.testpki.TestPki; // PKI de test
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.nio.file.Path; // chemins
import java.util.List; // liste

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertNotEquals; // difference
import static org.junit.jupiter.api.Assertions.assertThrows; // exception attendue

class ExtensionServiceTest { // tests de prolongation

    private static final TestPki PKI = TestPki.create(); // PKI partagee
    private static final TrustContext TRUST = TrustContext.of(PKI.verifier()); // confiance de test

    private static Path sign(Path in, SigningLevel l, OutputMode m) { // signature
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton
            return new SigningService(TRUST, PKI.tsp()).sign(new SignRequest(List.of(in), l, m, false, t.getKeys().get(0)), t).get(0).output(); // sortie
        }
    }

    private static ValidationOutcome validate(Path p, List<Path> originals) { return new ValidationService(TRUST).validate(p, originals); } // validation

    @Test // B vers LT (CAdES detache)
    void extendsBtoLt(@TempDir Path dir) throws Exception { // prolongation
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        Path out = new ExtensionService(TRUST, PKI.tsp()).extend(sign(in, SigningLevel.B, OutputMode.CADES_DETACHED), List.of(in), SigningLevel.LT); // prolongation
        assertEquals(SignatureLevel.CAdES_BASELINE_LT, validate(out, List.of(in)).level()); // LT
        assertEquals(dir.resolve("plan.dxf-prolonge.p7s"), out); // nom de sortie
    }

    @Test // B vers LTA (PDF)
    void extendsPdfToLta(@TempDir Path dir) throws Exception { // PDF
        Path in = Samples.pdf(dir, "doc.pdf"); // PDF
        Path out = new ExtensionService(TRUST, PKI.tsp()).extend(sign(in, SigningLevel.B, OutputMode.CADES_DETACHED), List.of(), SigningLevel.LTA); // prolongation
        assertEquals(SignatureLevel.PAdES_BASELINE_LTA, validate(out, List.of()).level()); // LTA
        assertEquals(dir.resolve("doc-signe-prolonge.pdf"), out); // nom
    }

    @Test // renouvellement LTA : un horodatage de plus
    void ltaRenewalAddsArchiveTimestamp(@TempDir Path dir) throws Exception { // ASiC-E
        Path in = Samples.wav(dir, "son.wav"); // audio
        Path lta = sign(in, SigningLevel.LTA, OutputMode.ASICE); // LTA
        int before = validate(lta, List.of()).reports().getDiagnosticData().getTimestampList().size(); // horodatages avant
        Path renewed = new ExtensionService(TRUST, PKI.tsp()).extend(lta, List.of(), SigningLevel.LTA); // renouvellement
        int after = validate(renewed, List.of()).reports().getDiagnosticData().getTimestampList().size(); // horodatages apres
        assertEquals(before + 1, after); // un de plus
    }

    @Test // l'entree n'est pas modifiee
    void inputUntouched(@TempDir Path dir) throws Exception { // original intact
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        Path sig = sign(in, SigningLevel.B, OutputMode.CADES_DETACHED); // signature
        String h = Samples.sha256(sig); // empreinte avant
        Path out = new ExtensionService(TRUST, PKI.tsp()).extend(sig, List.of(in), SigningLevel.T); // prolongation
        assertEquals(h, Samples.sha256(sig)); // entree intacte
        assertNotEquals(sig, out); // nouveau fichier
    }

    @Test // detachee sans original
    void detachedWithoutOriginalAsks(@TempDir Path dir) throws Exception { // original requis
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        Path sig = sign(in, SigningLevel.B, OutputMode.CADES_DETACHED); // signature
        assertThrows(MissingOriginalException.class, () -> new ExtensionService(TRUST, PKI.tsp()).extend(sig, List.of(), SigningLevel.T)); // erreur claire
    }
}
