package fr.signaturedss.core.token; // paquet jetons

import fr.signaturedss.core.PortablePaths; // chemins portables
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.io.IOException; // erreurs d'E/S
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.util.List; // liste
import java.util.Optional; // valeur facultative

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class Pkcs11DriversTest { // tests de detection du pilote

    @Test // pilote embarque sur la cle (Windows/pilotes)
    void bundledWindowsDriverFound(@TempDir Path usb) throws IOException { // pilote OpenSC embarque
        Path win = Files.createDirectories(usb.resolve("SignatureDSS/Windows/pilotes")); // dossier des pilotes
        Path dll = Files.writeString(win.resolve("opensc-pkcs11.dll"), "x"); // pilote simule
        PortablePaths p = PortablePaths.fromAppLocation(usb.resolve("SignatureDSS/Windows/app")); // application sur la cle
        assertEquals(Optional.of(dll), Pkcs11Drivers.find(p, "windows", List.of())); // pilote embarque trouve
    }

    @Test // sinon, installation existante sur l'ordinateur
    void systemDriverUsedWhenNoBundled(@TempDir Path dir) throws IOException { // OpenSC installe
        Path sys = Files.writeString(dir.resolve("opensc-pkcs11.so"), "x"); // pilote systeme simule
        PortablePaths p = PortablePaths.fromAppLocation(Files.createDirectories(dir.resolve("app"))); // application
        assertEquals(Optional.of(sys), Pkcs11Drivers.find(p, "linux", List.of(dir.resolve("absent.so"), sys))); // premier existant
    }

    @Test // rien de trouve
    void noneFound(@TempDir Path dir) throws IOException { // aucun pilote
        PortablePaths p = PortablePaths.fromAppLocation(Files.createDirectories(dir.resolve("app"))); // application
        assertTrue(Pkcs11Drivers.find(p, "macos", List.of(dir.resolve("absent.so"))).isEmpty()); // vide
    }

    @Test // emplacements standards connus par systeme
    void systemCandidatesPerOs() { // chemins verifies dans OpenSC 0.27.1 et Debian
        assertTrue(Pkcs11Drivers.systemCandidates("linux").contains(Path.of("/usr/lib/x86_64-linux-gnu/opensc-pkcs11.so"))); // Debian
        assertTrue(Pkcs11Drivers.systemCandidates("macos").contains(Path.of("/Library/OpenSC/lib/opensc-pkcs11.so"))); // macOS
        assertTrue(Pkcs11Drivers.systemCandidates("windows").contains(Path.of("C:\\Program Files\\OpenSC Project\\OpenSC\\pkcs11\\opensc-pkcs11.dll"))); // Windows
    }
}
