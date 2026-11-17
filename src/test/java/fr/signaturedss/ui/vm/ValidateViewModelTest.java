package fr.signaturedss.ui.vm; // paquet des modeles de vue

import eu.europa.esig.dss.enumerations.Indication; // verdict
import eu.europa.esig.dss.token.Pkcs12SignatureToken; // jeton
import fr.signaturedss.core.OutputMode; // forme
import fr.signaturedss.core.SigningLevel; // niveau
import fr.signaturedss.core.sign.Samples; // exemples
import fr.signaturedss.core.sign.SignRequest; // requete
import fr.signaturedss.core.sign.SigningService; // signature
import fr.signaturedss.core.trust.TrustContext; // confiance
import fr.signaturedss.testpki.TestPki; // PKI de test
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.nio.file.Path; // chemins
import java.util.List; // liste

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertFalse; // condition fausse
import static org.junit.jupiter.api.Assertions.assertNotNull; // non nul
import static org.junit.jupiter.api.Assertions.assertNull; // nul
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class ValidateViewModelTest { // tests du modele de validation

    private static final TestPki PKI = TestPki.create(); // PKI partagee
    private static final TrustContext TRUST = TrustContext.of(PKI.verifier()); // confiance

    private static Path signDetached(Path in) { // signature detachee
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton
            return new SigningService(TRUST, PKI.tsp()).sign(new SignRequest(List.of(in), SigningLevel.B, OutputMode.CADES_DETACHED, false, t.getKeys().get(0)), t).get(0).output(); // sortie
        }
    }

    @Test // original manquant : indicateur leve
    void missingOriginalSetsFlag(@TempDir Path dir) throws Exception { // detachee seule
        ValidateViewModel m = new ValidateViewModel(() -> TRUST); // modele
        m.signedFile().set(signDetached(Samples.dxf(dir, "plan.dxf"))); // fichier signe seul
        m.validate(); // validation
        assertTrue(m.needsOriginal().get()); // original demande
        assertNull(m.outcome().get()); // pas de resultat
    }

    @Test // avec l'original : resultat
    void withOriginalGivesOutcome(@TempDir Path dir) throws Exception { // cas nominal
        Path in = Samples.dxf(dir, "plan.dxf"); // original
        ValidateViewModel m = new ValidateViewModel(() -> TRUST); // modele
        m.signedFile().set(signDetached(in)); // signature
        m.originals().add(in); // original
        m.validate(); // validation
        assertFalse(m.needsOriginal().get()); // rien a demander
        assertNotNull(m.outcome().get()); // resultat
        assertEquals(Indication.TOTAL_PASSED, m.outcome().get().verdict()); // valide
    }

    @Test // fichier non signe : message d'erreur
    void unsignedFileSetsError(@TempDir Path dir) throws Exception { // pas de signature
        ValidateViewModel m = new ValidateViewModel(() -> TRUST); // modele
        m.signedFile().set(Samples.dxf(dir, "plan.dxf")); // fichier non signe
        m.validate(); // validation
        assertFalse(m.error().get().isEmpty()); // erreur affichee
    }
}
