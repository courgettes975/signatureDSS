package fr.signaturedss.i18n; // paquet des traductions

import org.junit.jupiter.api.AfterEach; // remise a zero apres chaque test
import org.junit.jupiter.api.Test; // annotation de test

import java.io.IOException; // erreurs d'E/S
import java.io.InputStream; // flux de ressource
import java.io.InputStreamReader; // lecteur de caracteres
import java.nio.charset.StandardCharsets; // UTF-8
import java.util.Locale; // langue
import java.util.Properties; // fichier de proprietes
import java.util.Set; // ensemble de cles

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class MessagesTest { // tests des traductions

    @AfterEach // retour au francais
    void reset() { Messages.setLocale(Locale.FRENCH); } // langue par defaut

    private Set<String> keysOf(String lang) throws IOException { // lit les cles d'une langue
        try (InputStream in = Messages.class.getResourceAsStream("/i18n/messages_" + lang + ".properties")) { // ressource
            Properties p = new Properties(); // conteneur
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8)); // lecture UTF-8
            return p.stringPropertyNames(); // noms des cles
        }
    }

    @Test // memes cles en francais et en anglais
    void frAndEnHaveSameKeys() throws IOException { assertEquals(keysOf("fr"), keysOf("en")); } // parite

    @Test // francais par defaut
    void defaultIsFrench() { assertEquals(Locale.FRENCH, Messages.currentLocale()); } // langue initiale

    @Test // accents lus correctement
    void frenchAccentsDecoded() { assertTrue(Messages.get("onglet.parametres").contains("è")); } // "Paramètres"

    @Test // arguments formates
    void argumentsFormatted() { assertTrue(Messages.get("erreur.tsa.indisponible", "X").contains("X")); } // argument insere

    @Test // bascule en anglais
    void switchToEnglish() { // test de langue
        Messages.setLocale(Locale.ENGLISH); // anglais
        assertEquals("Settings", Messages.get("onglet.parametres")); // texte anglais
    }

    @Test // langues disponibles
    void availableLocales() { assertTrue(Messages.available().contains(Locale.ENGLISH)); } // anglais present

    @Test // chaque cle litterale du code existe
    void allLiteralKeysUsedInCodeExist() throws IOException { // couverture des textes
        Set<String> keys = keysOf("fr"); // cles definies
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?:Messages\\.get|Ui\\.label|label|section|tab|ask\\([^,]+,|SecretDialog\\.ask\\(window\\(\\),)\\s*\\(?\\s*\"([a-z][a-zA-Z0-9_.]*\\.[a-zA-Z0-9_.]+)\""); // appels avec cle
        java.util.List<String> missing = new java.util.ArrayList<>(); // cles absentes
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java"))) { // sources
            for (java.nio.file.Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) { // chaque fichier
                java.util.regex.Matcher m = p.matcher(java.nio.file.Files.readString(f)); // recherche
                while (m.find()) { if (!keys.contains(m.group(1))) { missing.add(f.getFileName() + ":" + m.group(1)); } } // cle absente
            }
        }
        assertTrue(missing.isEmpty(), "Cles absentes : " + missing); // aucune absente
    }

    @Test // cles construites a partir des enumerations
    void enumKeysExist() throws IOException { // niveaux, formes, sources, verdicts
        Set<String> keys = keysOf("fr"); // cles definies
        for (fr.signaturedss.core.SigningLevel l : fr.signaturedss.core.SigningLevel.values()) { assertTrue(keys.contains("niveau." + l.name())); } // niveaux
        for (fr.signaturedss.core.OutputMode m : fr.signaturedss.core.OutputMode.values()) { assertTrue(keys.contains("mode." + m.name())); } // formes
        for (String s : java.util.List.of("P12", "Pkcs11", "WindowsStore", "MacKeychain")) { assertTrue(keys.contains("source." + s)); } // sources
        for (eu.europa.esig.dss.enumerations.Indication i : eu.europa.esig.dss.enumerations.Indication.values()) { assertTrue(keys.contains("verdict." + i.name()), i.name()); } // verdicts
    }
}
