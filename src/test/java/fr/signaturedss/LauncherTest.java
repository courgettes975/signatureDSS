package fr.signaturedss; // paquet racine

import fr.signaturedss.core.PortablePaths; // chemins portables
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.nio.file.Path; // chemins

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class LauncherTest { // tests du demarrage

    @Test // le cache JavaFX et le journal vont dans donnees/
    void configureKeepsEverythingOnTheStick(@TempDir Path dir) { // portabilite
        PortablePaths p = PortablePaths.fromAppLocation(dir); // chemins
        Launcher.configure(p); // configuration
        assertTrue(Path.of(System.getProperty("javafx.cachedir")).startsWith(p.dataDir())); // cache JavaFX sur la cle
        assertTrue(Path.of(System.getProperty("org.slf4j.simpleLogger.logFile")).startsWith(p.logsDir())); // journal sur la cle
    }

    @Test // donnees/ impossible a creer : message clair et code de sortie
    void startupFailureIsReported() { // cle en lecture seule
        java.util.List<String> shown = new java.util.ArrayList<>(); // messages affiches
        int code = Launcher.start(new String[0], () -> { throw new java.io.UncheckedIOException(new java.io.IOException("lecture seule")); }, shown::add); // demarrage
        assertEquals(5, code); // code d'erreur
        assertEquals(1, shown.size()); // un message
        assertTrue(shown.get(0).contains("lecture seule"), shown.get(0)); // cause citee
    }

    @Test // auto-controle sans affichage
    void selfCheckReturnsZero(@TempDir Path dir) { // verification d'un paquet
        assertEquals(0, Launcher.selfCheck(PortablePaths.fromAppLocation(dir))); // succes
    }
}
