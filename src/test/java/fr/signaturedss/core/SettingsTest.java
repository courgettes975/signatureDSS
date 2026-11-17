package fr.signaturedss.core; // paquet du noyau

import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.io.IOException; // erreurs d'E/S
import java.nio.charset.StandardCharsets; // UTF-8
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.util.List; // liste
import java.util.Locale; // langue
import java.util.Optional; // valeur facultative

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertFalse; // condition fausse
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class SettingsTest { // tests des parametres

    @Test // valeurs par defaut
    void defaultsWhenFileMissing(@TempDir Path dir) { // fichier absent
        Settings s = Settings.load(dir.resolve("config.properties"), dir); // chargement
        assertEquals(SigningLevel.LT, s.defaultLevel()); // LT par defaut
        assertEquals(OutputMode.CADES_DETACHED, s.defaultOutputMode()); // CAdES detache
        assertFalse(s.pdfVisibleSignature()); // signature visible desactivee
        assertEquals(Locale.FRENCH, s.locale()); // francais
        assertTrue(s.tsas().isEmpty()); // aucune TSA
    }

    @Test // ordre des TSA conserve
    void roundTripKeepsTsaOrder(@TempDir Path dir) { // aller-retour
        Path f = dir.resolve("config.properties"); // fichier
        Settings s = Settings.load(f, dir); // chargement initial
        s.setTsas(List.of(new TsaConfig("https://b.example/tsa", null), new TsaConfig("https://a.example/tsa", "user"))); // deux TSA
        s.setDefaultLevel(SigningLevel.LTA); // niveau LTA
        s.save(); // sauvegarde
        Settings r = Settings.load(f, dir); // relecture
        assertEquals("https://b.example/tsa", r.tsas().get(0).url()); // premiere TSA
        assertEquals("user", r.tsas().get(1).username()); // identifiant conserve
        assertEquals(SigningLevel.LTA, r.defaultLevel()); // niveau conserve
    }

    @Test // aucun mot de passe enregistre
    void neverWritesPasswordKeys(@TempDir Path dir) throws IOException { // controle de securite
        Path f = dir.resolve("config.properties"); // fichier
        Settings s = Settings.load(f, dir); // chargement
        s.setTsas(List.of(new TsaConfig("https://a.example/tsa", "user"))); // TSA avec identifiant
        s.setLastP12(Optional.of(dir.resolve("moi.p12"))); // dernier P12
        s.save(); // sauvegarde
        for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) { // chaque ligne
            if (line.startsWith("#") || !line.contains("=")) { continue; } // commentaires ignores
            String key = line.substring(0, line.indexOf('=')); // cle
            assertFalse(key.matches("(?i).*(pass|pin|secret).*"), key); // pas de cle sensible
        }
    }

    @Test // changement de lettre de lecteur (Review Focus 1)
    void relativePathSurvivesDriveLetterChange(@TempDir Path dir) throws IOException { // cle deplacee
        Path rootE = Files.createDirectories(dir.resolve("E/SignatureDSS")); // premiere racine
        Path rootF = Files.createDirectories(dir.resolve("F/SignatureDSS")); // seconde racine
        Settings s = Settings.load(rootE.resolve("config.properties"), rootE); // chargement sous E
        s.setLastP12(Optional.of(rootE.resolve("certificats/moi.p12"))); // P12 sur la cle
        s.save(); // sauvegarde
        Files.copy(rootE.resolve("config.properties"), rootF.resolve("config.properties")); // cle remontee en F
        Settings r = Settings.load(rootF.resolve("config.properties"), rootF); // chargement sous F
        assertEquals(rootF.resolve("certificats/moi.p12"), r.lastP12().orElseThrow()); // resolu sous F
    }

    @Test // bibliotheque PKCS#11 et P12 externe propres a chaque systeme
    void externalPathsArePerOs(@TempDir Path dir) throws IOException { // configuration partagee par trois systemes
        Path root = Files.createDirectories(dir.resolve("cle")); // racine
        Path f = root.resolve("config.properties"); // fichier commun
        Path winLib = dir.resolve("win/eps2003csp11.dll").toAbsolutePath(); // bibliotheque Windows
        Settings w = Settings.load(f, root, "windows"); // session Windows
        w.setPkcs11Library(Optional.of(winLib)); // choix Windows
        w.save(); // sauvegarde
        Settings m = Settings.load(f, root, "macos"); // session macOS
        assertTrue(m.pkcs11Library().isEmpty()); // rien d'herite de Windows
        m.setPkcs11Library(Optional.of(dir.resolve("mac/libcastle.dylib").toAbsolutePath())); // choix macOS
        m.save(); // sauvegarde
        assertEquals(winLib, Settings.load(f, root, "windows").pkcs11Library().orElseThrow()); // Windows intact
    }

    @Test // chemin hors de la cle conserve en absolu
    void outsidePathKeptAbsolute(@TempDir Path dir) throws IOException { // chemin externe
        Path root = Files.createDirectories(dir.resolve("cle")); // racine
        Path outside = dir.resolve("ailleurs/lib.so").toAbsolutePath(); // bibliotheque hors cle
        Settings s = Settings.load(root.resolve("config.properties"), root); // chargement
        s.setPkcs11Library(Optional.of(outside)); // bibliotheque PKCS#11
        s.save(); // sauvegarde
        assertEquals(outside, Settings.load(root.resolve("config.properties"), root).pkcs11Library().orElseThrow()); // identique
    }
}
