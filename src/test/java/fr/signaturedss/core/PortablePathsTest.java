package fr.signaturedss.core; // paquet du noyau

import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire de test

import java.io.IOException; // erreurs d'E/S
import java.nio.file.Files; // operations fichiers
import java.nio.file.Path; // chemins

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class PortablePathsTest { // tests des chemins portables

    @Test // donnees/ est a cote du dossier de plateforme
    void dataDirIsSiblingOfAppFolder(@TempDir Path usb) throws IOException {
        Path app = Files.createDirectories(usb.resolve("SignatureDSS/Windows")); // dossier Windows simule
        PortablePaths p = PortablePaths.fromAppLocation(app); // resolution
        assertEquals(usb.resolve("SignatureDSS/donnees"), p.dataDir()); // donnees/ attendu
        assertTrue(Files.isDirectory(p.tslCache())); // cache LOTL cree
        assertTrue(Files.isDirectory(p.anchorsDir())); // dossier des ancres cree
        assertTrue(Files.isDirectory(p.logsDir())); // dossier des journaux cree
    }

    @Test // jamais d'ecriture dans le bundle macOS
    void macBundleNeverWritesInsideApp(@TempDir Path usb) throws IOException {
        Path app = Files.createDirectories(usb.resolve("SignatureDSS/macOS/SignatureDSS.app/Contents/app")); // bundle simule
        assertEquals(usb.resolve("SignatureDSS/donnees"), PortablePaths.fromAppLocation(app).dataDir()); // hors du .app
    }

    @Test // accents et espaces conserves (Review Focus 1)
    void accentsAndSpacesSurvive(@TempDir Path usb) throws IOException {
        Path app = Files.createDirectories(usb.resolve("Clé USB/Signature DSS/Windows")); // chemin accentue
        assertTrue(PortablePaths.fromAppLocation(app).dataDir().toString().contains("Clé USB")); // accent intact
    }

    @Test // dossier quelconque : donnees/ a cote
    void unknownFolderGetsDataInside(@TempDir Path dir) throws IOException {
        Path app = Files.createDirectories(dir.resolve("dev")); // dossier de developpement
        assertEquals(app.resolve("donnees"), PortablePaths.fromAppLocation(app).dataDir()); // donnees/ dedans
    }

    @Test // la propriete systeme a priorite
    void systemPropertyOverrides(@TempDir Path dir) {
        System.setProperty("signaturedss.home", dir.toString()); // forcage
        try { // bloc protege
            assertEquals(dir.resolve("donnees"), PortablePaths.detect().dataDir()); // racine forcee
        } finally { // nettoyage
            System.clearProperty("signaturedss.home"); // retrait de la propriete
        }
    }
}
