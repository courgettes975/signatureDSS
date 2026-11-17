package fr.signaturedss.core.sign; // paquet signature

import eu.europa.esig.dss.enumerations.Indication; // verdict
import eu.europa.esig.dss.enumerations.SignatureLevel; // niveau DSS
import eu.europa.esig.dss.model.x509.CertificateToken; // certificat
import eu.europa.esig.dss.token.Pkcs12SignatureToken; // jeton
import fr.signaturedss.core.OutputMode; // forme
import fr.signaturedss.core.SigningLevel; // niveau
import fr.signaturedss.core.trust.TrustContext; // confiance
import fr.signaturedss.core.validate.ValidationService; // validation
import fr.signaturedss.testpki.TestPki; // PKI de test
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.nio.file.Path; // chemins
import java.util.Arrays; // tableaux
import java.util.List; // liste

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertFalse; // condition fausse
import static org.junit.jupiter.api.Assertions.assertNotEquals; // difference
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class PreflightAndPostCheckTest { // tests des controles

    private static final TestPki PKI = TestPki.create(); // PKI partagee

    private static List<CertificateToken> chain() { // chaine du signataire
        try (Pkcs12SignatureToken t = PKI.signerToken()) { return Arrays.asList(t.getKeys().get(0).getCertificateChain()); } // lecture
    }

    private static Path sign(Path in, SigningLevel l) { // signature de test
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton
            return new SigningService(TrustContext.of(PKI.verifier()), PKI.tsp()).sign(new SignRequest(List.of(in), l, OutputMode.CADES_DETACHED, false, t.getKeys().get(0)), t).get(0).output(); // sortie
        }
    }

    @Test // PKI complete : revocation disponible
    void preflightOkWithFullPki() { // cas nominal
        RevocationPreflight.PreflightResult r = RevocationPreflight.check(chain(), PKI.tsp(), PKI.verifier()); // controle
        assertTrue(r.revocationAvailable()); // disponible
        assertTrue(r.missingSubjects().isEmpty()); // rien ne manque
    }

    @Test // revocation absente pour le signataire
    void preflightReportsMissingRevocation() { // cas degrade
        TestPki hidden = PKI.withoutRevocationFor(PKI.signer()); // OCSP et CRL muets pour le signataire
        RevocationPreflight.PreflightResult r = RevocationPreflight.check(chain(), hidden.tsp(), hidden.verifier()); // controle
        assertFalse(r.revocationAvailable()); // indisponible
        assertTrue(r.missingSubjects().stream().anyMatch(s -> s.contains("Signataire Test"))); // signataire cite
    }

    @Test // revocation absente pour la TSA
    void preflightChecksTsaChain() { // chaine TSA
        TestPki hidden = PKI.withoutRevocationFor(PKI.tsaCert()); // muets pour la TSA
        RevocationPreflight.PreflightResult r = RevocationPreflight.check(chain(), hidden.tsp(), hidden.verifier()); // controle
        assertFalse(r.revocationAvailable()); // indisponible
        assertTrue(r.missingSubjects().stream().anyMatch(s -> s.contains("TSA Test"))); // TSA citee
    }

    @Test // niveau LT detecte apres signature
    void postCheckDetectsLevel(@TempDir Path dir) throws Exception { // controle apres signature
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        PostSignCheck c = new PostSignCheck(new ValidationService(TrustContext.of(PKI.verifier()))); // controleur
        PostSignCheck.PostSignOutcome o = c.check(sign(in, SigningLevel.LT), List.of(in), SignatureLevel.CAdES_BASELINE_LT); // controle
        assertTrue(o.levelReached()); // niveau atteint
        assertEquals(Indication.TOTAL_PASSED, o.verdict()); // valide
        assertTrue(o.warnings().isEmpty()); // aucun avertissement
    }

    @Test // niveau inferieur detecte
    void postCheckDetectsLowerLevel(@TempDir Path dir) throws Exception { // niveau manque
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        PostSignCheck c = new PostSignCheck(new ValidationService(TrustContext.of(PKI.verifier()))); // controleur
        assertFalse(c.check(sign(in, SigningLevel.T), List.of(in), SignatureLevel.CAdES_BASELINE_LTA).levelReached()); // T au lieu de LTA
    }

    @Test // racine non reconnue : avertissement
    void postCheckFlagsUntrusted(@TempDir Path dir) throws Exception { // confiance absente
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        PostSignCheck c = new PostSignCheck(new ValidationService(TrustContext.of(PKI.verifier(false)))); // sans confiance
        PostSignCheck.PostSignOutcome o = c.check(sign(in, SigningLevel.B), List.of(in), SignatureLevel.CAdES_BASELINE_B); // controle
        assertNotEquals(Indication.TOTAL_PASSED, o.verdict()); // non valide
        assertFalse(o.warnings().isEmpty()); // avertissement
        assertTrue(o.levelReached()); // la structure est bien la
    }
}
