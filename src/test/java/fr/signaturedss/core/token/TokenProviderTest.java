package fr.signaturedss.core.token; // paquet jetons

import eu.europa.esig.dss.token.SignatureTokenConnection; // jeton ouvert
import fr.signaturedss.testpki.TestPki; // PKI de test
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.io.ByteArrayOutputStream; // tampon
import java.io.PrintStream; // flux d'impression
import java.nio.charset.StandardCharsets; // UTF-8
import java.nio.file.Path; // chemins
import java.util.concurrent.atomic.AtomicInteger; // compteur

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertFalse; // condition fausse
import static org.junit.jupiter.api.Assertions.assertThrows; // exception attendue
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class TokenProviderTest { // tests d'acces aux certificats

    private static final TestPki PKI = TestPki.create(); // PKI partagee

    @Test // P12 ouvert et cle listee
    void opensP12AndListsKey(@TempDir Path dir) throws Exception { // cas nominal
        Path p12 = PKI.signerP12(dir); // fichier P12
        try (SignatureTokenConnection c = new TokenProvider().open(new TokenSource.P12(p12), TestPki.P12_PASSWORD.clone())) { // ouverture
            assertEquals(1, c.getKeys().size()); // une cle
        }
    }

    @Test // mauvais mot de passe : erreur claire
    void wrongP12PasswordGivesClearError(@TempDir Path dir) throws Exception { // mot de passe faux
        Path p12 = PKI.signerP12(dir); // fichier P12
        TokenException e = assertThrows(TokenException.class, () -> new TokenProvider().open(new TokenSource.P12(p12), "faux".toCharArray())); // echec
        assertEquals("erreur.token.motdepasse", e.key()); // cle de message
    }

    @Test // une seule tentative (Review Focus 3)
    void singleAttemptOnWrongPin(@TempDir Path dir) throws Exception { // pas de nouvel essai
        Path p12 = PKI.signerP12(dir); // fichier P12
        AtomicInteger attempts = new AtomicInteger(); // compteur
        TokenProvider p = new TokenProvider((src, pw) -> { attempts.incrementAndGet(); return TokenProvider.DEFAULT_OPENER.open(src, pw); }); // ouverture comptee
        assertThrows(TokenException.class, () -> p.open(new TokenSource.P12(p12), "faux".toCharArray())); // echec
        assertEquals(1, attempts.get()); // une seule tentative
    }

    @Test // secret efface apres ouverture
    void secretWipedAfterOpen(@TempDir Path dir) throws Exception { // effacement
        Path p12 = PKI.signerP12(dir); // fichier P12
        char[] secret = TestPki.P12_PASSWORD.clone(); // secret
        new TokenProvider().open(new TokenSource.P12(p12), secret).close(); // ouverture puis fermeture
        for (char ch : secret) { assertEquals('\0', ch); } // efface
        char[] wrong = "faux".toCharArray(); // secret faux
        assertThrows(TokenException.class, () -> new TokenProvider().open(new TokenSource.P12(p12), wrong)); // echec
        for (char ch : wrong) { assertEquals('\0', ch); } // efface aussi en cas d'echec
    }

    @Test // bibliotheque PKCS#11 absente
    void missingPkcs11LibraryGivesClearError(@TempDir Path dir) { // bibliotheque introuvable
        TokenException e = assertThrows(TokenException.class, () -> new TokenProvider().open(new TokenSource.Pkcs11(dir.resolve("absente.dll")), "1234".toCharArray())); // echec
        assertEquals("erreur.token.bibliotheque", e.key()); // cle de message
    }

    @Test // bibliotheque chargee mais aucun jeton branche
    void noTokenPluggedGivesClearError(@TempDir Path dir) throws Exception { // jeton absent
        Path lib = java.nio.file.Files.writeString(dir.resolve("lib.dll"), "x"); // bibliotheque presente
        TokenProvider p = new TokenProvider((src, pw) -> { throw new eu.europa.esig.dss.model.DSSException("Can't initialize Sun PKCS#11 security provider. More: PKCS11 not found", new java.security.KeyStoreException("PKCS11 not found")); }); // erreur reelle observee sans jeton
        TokenException e = assertThrows(TokenException.class, () -> p.open(new TokenSource.Pkcs11(lib), "1234".toCharArray())); // echec
        assertEquals("erreur.token.absent", e.key()); // branchez la cle
    }

    @Test // chemin Windows passe a SunPKCS11 avec des barres obliques (sinon "\n" de "...\nouveau" devient un saut de ligne)
    void pkcs11PathUsesForwardSlashes() { // normalisation du chemin
        assertEquals("C:/Users/moi/nouveau/opensc-pkcs11.dll", TokenProvider.pkcs11Path("C:\\Users\\moi\\nouveau\\opensc-pkcs11.dll")); // aucune barre inverse
    }

    @Test // pilote OpenSC reel charge sans aucune identification (jamais de PIN envoye a un jeton)
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS) // DLL Windows
    void bundledOpenScLoadsWithoutLogin() throws Exception { // chargement seul
        Path dir = java.nio.file.Files.createTempDirectory(java.nio.file.Files.createDirectories(Path.of("target/pkcs11-test")), "t"); // dans target/ : la DLL chargee reste verrouillee par Windows
        Path lib = java.nio.file.Files.createDirectories(dir.resolve("nouveau")).resolve("opensc-pkcs11.dll"); // chemin ...\nouveau\...
        java.nio.file.Files.copy(Path.of("packaging/opensc/windows-x64/opensc-pkcs11.dll"), lib); // copie du pilote embarque
        java.security.Provider p = java.security.Security.getProvider("SunPKCS11").configure("--name=TestOpenSC\nlibrary=" + TokenProvider.pkcs11Path(lib.toAbsolutePath().toString())); // C_Initialize seulement, aucune connexion
        assertTrue(p.getName().contains("TestOpenSC")); // fournisseur cree : bibliotheque chargee
    }

    @Test // fichier P12 absent
    void missingP12GivesClearError(@TempDir Path dir) { // fichier introuvable
        TokenException e = assertThrows(TokenException.class, () -> new TokenProvider().open(new TokenSource.P12(dir.resolve("absent.p12")), "x".toCharArray())); // echec
        assertEquals("erreur.token.fichier", e.key()); // cle de message
    }

    @Test // le secret n'apparait ni dans le journal ni dans le message
    void secretNeverLogged(@TempDir Path dir) throws Exception { // confidentialite
        Path p12 = PKI.signerP12(dir); // fichier P12
        PrintStream old = System.err; // sortie d'erreur d'origine
        ByteArrayOutputStream buf = new ByteArrayOutputStream(); // capture
        System.setErr(new PrintStream(buf, true, StandardCharsets.UTF_8)); // redirection
        TokenException e; // exception capturee
        try { // ouverture avec un secret reconnaissable
            e = assertThrows(TokenException.class, () -> new TokenProvider().open(new TokenSource.P12(p12), "S3cr3t!".toCharArray())); // echec
        } finally { // restauration
            System.setErr(old); // sortie d'origine
        }
        assertFalse(buf.toString(StandardCharsets.UTF_8).contains("S3cr3t!")); // absent du journal
        assertFalse(String.valueOf(e.getMessage()).contains("S3cr3t!")); // absent du message
    }

    @Test // magasins systeme proposes selon l'OS
    void availableSourcesMatchOs() { // liste par systeme
        String os = System.getProperty("os.name").toLowerCase(); // systeme
        assertTrue(TokenProvider.available().contains(TokenSource.P12.class)); // P12 partout
        assertTrue(TokenProvider.available().contains(TokenSource.Pkcs11.class)); // PKCS#11 partout
        assertEquals(os.contains("win"), TokenProvider.available().contains(TokenSource.WindowsStore.class)); // Windows seulement
        assertEquals(os.contains("mac"), TokenProvider.available().contains(TokenSource.MacKeychain.class)); // macOS seulement
    }
}
