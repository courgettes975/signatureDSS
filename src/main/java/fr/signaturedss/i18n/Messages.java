package fr.signaturedss.i18n; // paquet des traductions

import java.io.BufferedReader; // lecture ligne a ligne
import java.io.IOException; // erreurs d'E/S
import java.io.InputStream; // flux de ressource
import java.io.InputStreamReader; // lecteur de caracteres
import java.nio.charset.StandardCharsets; // UTF-8
import java.util.ArrayList; // liste modifiable
import java.util.List; // liste
import java.util.Locale; // langue
import java.util.MissingResourceException; // cle absente
import java.util.ResourceBundle; // fichiers de traduction

/**
 * Acces aux textes traduits. Ajouter une langue : creer messages_xx.properties
 * et ajouter "xx" dans i18n/languages.txt.
 */
public final class Messages { // utilitaire statique

    private static final String BASE = "i18n.messages"; // nom de base des fichiers
    private static volatile Locale locale = Locale.FRENCH; // langue courante, francais par defaut
    private static volatile ResourceBundle bundle = load(Locale.FRENCH); // textes de la langue courante

    private Messages() { } // pas d'instance

    private static ResourceBundle load(Locale l) { // charge le fichier d'une langue
        return ResourceBundle.getBundle(BASE, l, ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)); // sans repli sur la langue systeme
    }

    /** Change la langue de l'interface. */
    public static void setLocale(Locale l) { // changement de langue
        bundle = load(l); // chargement des textes
        locale = l; // memorisation
    }

    /** Langue courante. */
    public static Locale currentLocale() { return locale; } // accesseur

    /** Texte traduit ; {0}, {1}... sont remplaces par les arguments. */
    public static String get(String key, Object... args) { // lecture d'un texte
        String text; // texte brut
        try { // lecture protegee
            text = bundle.getString(key); // texte de la langue
        } catch (MissingResourceException e) { // cle absente
            return "!" + key + "!"; // marqueur visible plutot qu'une erreur
        }
        for (int i = 0; i < args.length; i++) { // remplacement simple, sans MessageFormat (apostrophes francaises)
            text = text.replace("{" + i + "}", String.valueOf(args[i])); // insertion de l'argument
        }
        return text; // texte final
    }

    /** Langues declarees dans i18n/languages.txt. */
    public static List<Locale> available() { // liste des langues
        List<Locale> out = new ArrayList<>(); // resultat
        try (InputStream in = Messages.class.getResourceAsStream("/i18n/languages.txt")) { // fichier des langues
            if (in == null) { return List.of(Locale.FRENCH); } // repli si fichier absent
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)); // lecteur UTF-8
            String line; // ligne courante
            while ((line = r.readLine()) != null) { // lecture de chaque ligne
                if (!line.isBlank()) { out.add(Locale.forLanguageTag(line.trim())); } // code de langue
            }
        } catch (IOException e) { // lecture impossible
            return List.of(Locale.FRENCH); // repli
        }
        return out; // langues disponibles
    }
}
