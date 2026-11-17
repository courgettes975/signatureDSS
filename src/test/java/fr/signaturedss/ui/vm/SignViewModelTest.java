package fr.signaturedss.ui.vm; // paquet des modeles de vue

import eu.europa.esig.dss.token.Pkcs12SignatureToken; // jeton
import fr.signaturedss.core.SigningLevel; // niveau
import fr.signaturedss.core.sign.Samples; // exemples
import fr.signaturedss.core.token.TokenSource; // source du certificat
import fr.signaturedss.core.trust.TrustContext; // confiance
import fr.signaturedss.testpki.TestPki; // PKI de test
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.util.List; // liste
import java.util.Optional; // valeur facultative
import java.util.concurrent.atomic.AtomicBoolean; // indicateur

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertFalse; // condition fausse
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class SignViewModelTest { // tests du modele de signature

    private static final TestPki PKI = TestPki.create(); // PKI partagee

    private static SignViewModel vm(TestPki pki, boolean trustRoot) { // modele de test
        return new SignViewModel(() -> TrustContext.of(pki.verifier(trustRoot)), pki::tsp); // confiance et TSA injectees
    }

    @Test // revocation manquante : proposition de passer en T
    void preflightFailureAsksDowngrade(@TempDir Path dir) throws Exception { // cas degrade
        TestPki hidden = PKI.withoutRevocationFor(PKI.signer()); // revocation muette
        SignViewModel m = vm(hidden, true); // modele
        m.files().add(Samples.dxf(dir, "plan.dxf")); // fichier
        m.level().set(SigningLevel.LT); // niveau LT
        AtomicBoolean asked = new AtomicBoolean(); // question posee
        SignViewModel.SignOutcome o = m.run(new TokenSource.P12(PKI.signerP12(dir)), TestPki.P12_PASSWORD.clone(), keys -> keys.get(0), missing -> { asked.set(true); return Optional.of(SigningLevel.T); }); // execution
        assertTrue(asked.get()); // question posee
        assertEquals(SigningLevel.T, o.usedLevel()); // signe en T
        assertEquals(1, o.results().size()); // un fichier signe
    }

    @Test // annulation apres la question
    void downgradeRefusedCancels(@TempDir Path dir) throws Exception { // annulation
        SignViewModel m = vm(PKI.withoutRevocationFor(PKI.signer()), true); // modele
        Path f = Samples.dxf(dir, "plan.dxf"); // fichier
        m.files().add(f); // ajout
        m.level().set(SigningLevel.LTA); // niveau LTA
        SignViewModel.SignOutcome o = m.run(new TokenSource.P12(PKI.signerP12(dir)), TestPki.P12_PASSWORD.clone(), keys -> keys.get(0), missing -> Optional.empty()); // refus
        assertTrue(o.cancelled()); // annule
        assertFalse(Files.exists(dir.resolve("plan.dxf.p7s"))); // rien produit
    }

    @Test // avertissement si la racine n'est pas reconnue
    void postCheckWarningSurfaced(@TempDir Path dir) throws Exception { // avertissement
        SignViewModel m = vm(PKI, false); // sans confiance
        m.files().add(Samples.dxf(dir, "plan.dxf")); // fichier
        m.level().set(SigningLevel.B); // niveau B
        SignViewModel.SignOutcome o = m.run(new TokenSource.P12(PKI.signerP12(dir)), TestPki.P12_PASSWORD.clone(), keys -> keys.get(0), missing -> Optional.empty()); // execution
        assertFalse(o.warnings().isEmpty()); // avertissement remonte
        assertTrue(o.errors().isEmpty()); // pas d'erreur
    }

    @Test // signature nominale sans avertissement
    void nominalLtHasNoWarning(@TempDir Path dir) throws Exception { // cas nominal
        SignViewModel m = vm(PKI, true); // modele
        m.files().add(Samples.pdf(dir, "doc.pdf")); // PDF
        SignViewModel.SignOutcome o = m.run(new TokenSource.P12(PKI.signerP12(dir)), TestPki.P12_PASSWORD.clone(), keys -> keys.get(0), missing -> Optional.empty()); // LT par defaut
        assertEquals(SigningLevel.LT, o.usedLevel()); // LT
        assertTrue(o.warnings().isEmpty()); // aucun avertissement
        assertTrue(o.errors().isEmpty()); // aucune erreur
    }

    @Test // repli sur une autre TSA : avertissement
    void tsaFallbackWarns(@TempDir Path dir) throws Exception { // TSA principale en panne
        eu.europa.esig.dss.spi.x509.tsp.TSPSource down = (a, d) -> { throw new eu.europa.esig.dss.model.DSSException("401"); }; // TSA refusee
        SignViewModel m = new SignViewModel(() -> TrustContext.of(PKI.verifier()), () -> new fr.signaturedss.core.tsa.FailoverTspSource(List.of(new fr.signaturedss.core.tsa.NamedTsp("tsa-actalis", down), new fr.signaturedss.core.tsa.NamedTsp("tsa-libre", PKI.tsp())))); // modele
        m.files().add(Samples.dxf(dir, "plan.dxf")); // fichier
        m.level().set(SigningLevel.T); // niveau T
        SignViewModel.SignOutcome o = m.run(new TokenSource.P12(PKI.signerP12(dir)), TestPki.P12_PASSWORD.clone(), keys -> keys.get(0), missing -> Optional.empty()); // execution
        assertTrue(o.warnings().stream().anyMatch(w -> w.contains("tsa-actalis")), o.warnings().toString()); // repli signale
        assertEquals(List.of("tsa-actalis"), o.failedTsas()); // TSA en echec rapportee
    }

    @Test // lot interrompu : fichiers deja signes rapportes et controles
    void batchFailureReportsSignedFiles(@TempDir Path dir) throws Exception { // echec en cours de lot
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger(); // compteur
        eu.europa.esig.dss.spi.x509.tsp.TSPSource real = PKI.tsp(); // TSA de test
        SignViewModel m = new SignViewModel(() -> TrustContext.of(PKI.verifier()), () -> (a, d) -> { if (calls.incrementAndGet() > 1) { throw new eu.europa.esig.dss.model.DSSException("coupure"); } return real.getTimeStampResponse(a, d); }); // TSA qui repond une fois
        m.files().add(Samples.dxf(dir, "a.dxf")); // premier
        m.files().add(Samples.dxf(dir, "b.dxf")); // second
        m.level().set(SigningLevel.T); // niveau T
        SignViewModel.SignOutcome o = m.run(new TokenSource.P12(PKI.signerP12(dir)), TestPki.P12_PASSWORD.clone(), keys -> keys.get(0), missing -> Optional.empty()); // execution
        assertEquals(1, o.results().size()); // premier fichier rapporte
        assertTrue(o.errors().stream().anyMatch(e -> e.contains("b.dxf")), o.errors().toString()); // echec du second cite
    }

    @Test // secret efface apres execution
    void secretArrayWipedAfterRun(@TempDir Path dir) throws Exception { // effacement
        SignViewModel m = vm(PKI, true); // modele
        m.files().add(Samples.dxf(dir, "plan.dxf")); // fichier
        m.level().set(SigningLevel.B); // niveau B
        char[] secret = TestPki.P12_PASSWORD.clone(); // secret
        m.run(new TokenSource.P12(PKI.signerP12(dir)), secret, keys -> keys.get(0), missing -> Optional.empty()); // execution
        for (char c : secret) { assertEquals('\0', c); } // efface
    }

    @Test // une seule cle : pas de question
    void singleKeySkipsChooser(@TempDir Path dir) throws Exception { // choix de cle
        SignViewModel m = vm(PKI, true); // modele
        m.files().add(Samples.dxf(dir, "plan.dxf")); // fichier
        m.level().set(SigningLevel.B); // niveau B
        AtomicBoolean called = new AtomicBoolean(); // appel
        try (Pkcs12SignatureToken ignored = PKI.signerToken()) { // garantit la PKI chargee
            m.run(new TokenSource.P12(PKI.signerP12(dir)), TestPki.P12_PASSWORD.clone(), keys -> { called.set(true); return keys.get(0); }, missing -> Optional.empty()); // execution
        }
        assertFalse(called.get()); // aucun choix demande
    }
}
