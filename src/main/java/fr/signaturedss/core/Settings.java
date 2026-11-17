package fr.signaturedss.core; // paquet du noyau

import java.io.IOException; // erreurs d'E/S
import java.io.Reader; // lecteur
import java.io.UncheckedIOException; // erreur non verifiee
import java.io.Writer; // ecrivain
import java.nio.charset.StandardCharsets; // UTF-8
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.util.ArrayList; // liste modifiable
import java.util.List; // liste
import java.util.Locale; // langue
import java.util.Optional; // valeur facultative
import java.util.Properties; // proprietes

/**
 * Parametres de l'application, stockes dans donnees/config.properties.
 * Les chemins situes sur la cle sont enregistres en relatif pour survivre
 * a un changement de lettre de lecteur. Aucun secret n'est jamais enregistre.
 */
public final class Settings { // parametres modifiables

    private final Path file; // fichier de configuration
    private final Path root; // racine portable
    private final String os; // systeme courant (windows, macos, linux) : suffixe des chemins hors de la cle
    private final Properties otherSystems = new Properties(); // chemins des autres systemes, conserves tels quels
    private List<TsaConfig> tsas = new ArrayList<>(); // TSA dans l'ordre d'essai
    private SigningLevel defaultLevel = SigningLevel.LT; // niveau par defaut
    private OutputMode defaultOutputMode = OutputMode.CADES_DETACHED; // forme par defaut
    private boolean pdfVisibleSignature = false; // signature visible desactivee par defaut
    private Optional<Path> pkcs11Library = Optional.empty(); // bibliotheque PKCS#11
    private Optional<Path> lastP12 = Optional.empty(); // dernier fichier P12
    private Locale locale = Locale.FRENCH; // langue
    private boolean darkTheme = false; // theme sombre choisi manuellement

    private Settings(Path file, Path root, String os) { // constructeur prive
        this.os = os; // systeme
        this.file = file; // fichier
        this.root = root.toAbsolutePath().normalize(); // racine normalisee
    }

    /** Charge les parametres ; valeurs par defaut si le fichier est absent ou illisible. */
    public static Settings load(Path configFile, Path root) { return load(configFile, root, currentOs()); } // systeme courant

    /** Systeme courant : windows, macos ou linux. */
    public static String currentOs() { // detection
        String n = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT); // nom du systeme
        return n.contains("win") ? "windows" : n.contains("mac") ? "macos" : "linux"; // cle courte
    }

    /** Chargement pour un systeme donne (la configuration est partagee par Windows, macOS et Linux). */
    static Settings load(Path configFile, Path root, String os) { // lecture
        Settings s = new Settings(configFile, root, os); // valeurs par defaut
        if (!Files.isRegularFile(configFile)) { return s; } // pas de fichier
        Properties p = new Properties(); // conteneur
        try (Reader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) { // lecture UTF-8
            p.load(r); // analyse
        } catch (IOException e) { // fichier illisible
            return s; // repli sur les valeurs par defaut
        }
        for (int i = 0; p.containsKey("tsa." + i + ".url"); i++) { // TSA numerotees
            s.tsas.add(new TsaConfig(p.getProperty("tsa." + i + ".url"), emptyToNull(p.getProperty("tsa." + i + ".utilisateur")))); // ajout
        }
        s.defaultLevel = parseEnum(SigningLevel.class, p.getProperty("niveau.defaut"), SigningLevel.LT); // niveau
        s.defaultOutputMode = parseEnum(OutputMode.class, p.getProperty("mode.defaut"), OutputMode.CADES_DETACHED); // forme
        s.pdfVisibleSignature = Boolean.parseBoolean(p.getProperty("pdf.signature.visible", "false")); // signature visible
        s.pkcs11Library = s.fromStored(p.getProperty("pkcs11.bibliotheque." + os)); // bibliotheque PKCS#11 de ce systeme
        s.lastP12 = s.fromStored(p.getProperty("p12.dernier." + os)); // dernier P12 de ce systeme
        for (String k : p.stringPropertyNames()) { // chemins des autres systemes
            boolean perOs = k.startsWith("pkcs11.bibliotheque.") || k.startsWith("p12.dernier."); // cle par systeme
            if (perOs && !k.endsWith("." + os)) { s.otherSystems.setProperty(k, p.getProperty(k)); } // conservation
        }
        s.locale = Locale.forLanguageTag(p.getProperty("langue", "fr")); // langue
        s.darkTheme = Boolean.parseBoolean(p.getProperty("theme.sombre", "false")); // theme
        return s; // parametres charges
    }

    /** Chargement pour un systeme donne (stub). */
    /** Enregistre les parametres. */
    public void save() { // ecriture
        try { // operation protegee
            Files.createDirectories(file.toAbsolutePath().getParent()); // dossier parent
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { // ecriture UTF-8
                w.write("# Parametres SignatureDSS (aucun mot de passe n'est enregistre)\n"); // en-tete
                for (int i = 0; i < tsas.size(); i++) { // chaque TSA
                    line(w, "tsa." + i + ".url", tsas.get(i).url()); // adresse
                    if (tsas.get(i).needsAuth()) { line(w, "tsa." + i + ".utilisateur", tsas.get(i).username()); } // identifiant
                }
                line(w, "niveau.defaut", defaultLevel.name()); // niveau
                line(w, "mode.defaut", defaultOutputMode.name()); // forme
                line(w, "pdf.signature.visible", Boolean.toString(pdfVisibleSignature)); // signature visible
                if (pkcs11Library.isPresent()) { line(w, "pkcs11.bibliotheque." + os, toStored(pkcs11Library.get())); } // bibliotheque de ce systeme
                if (lastP12.isPresent()) { line(w, "p12.dernier." + os, toStored(lastP12.get())); } // dernier P12 de ce systeme
                for (String k : new java.util.TreeSet<>(otherSystems.stringPropertyNames())) { line(w, k, otherSystems.getProperty(k)); } // autres systemes inchanges
                line(w, "langue", locale.toLanguageTag()); // langue
                line(w, "theme.sombre", Boolean.toString(darkTheme)); // theme
            }
        } catch (IOException e) { // ecriture impossible
            throw new UncheckedIOException("Impossible d'enregistrer " + file, e); // erreur explicite
        }
    }

    private static void line(Writer w, String key, String value) throws IOException { // ecrit une ligne cle=valeur
        Properties one = new Properties(); // proprietes d'une ligne
        one.setProperty(key, value); // valeur
        java.io.StringWriter sw = new java.io.StringWriter(); // tampon
        one.store(sw, null); // echappement standard
        for (String l : sw.toString().split("\\R")) { // lignes produites
            if (!l.startsWith("#") && !l.isEmpty()) { w.write(l + "\n"); } // sans commentaire de date
        }
    }

    private String toStored(Path p) { // convertit un chemin pour l'enregistrement
        Path abs = p.toAbsolutePath().normalize(); // chemin absolu
        return abs.startsWith(root) ? "@racine/" + root.relativize(abs).toString().replace('\\', '/') : abs.toString(); // relatif si sur la cle
    }

    private Optional<Path> fromStored(String v) { // reconvertit un chemin enregistre
        if (v == null || v.isBlank()) { return Optional.empty(); } // absent
        if (v.startsWith("@racine/")) { return Optional.of(root.resolve(v.substring("@racine/".length())).normalize()); } // relatif a la racine
        return Optional.of(Path.of(v)); // absolu
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String v, E def) { // lit une valeur d'enumeration
        if (v == null) { return def; } // absent
        try { return Enum.valueOf(type, v.trim()); } catch (IllegalArgumentException e) { return def; } // valeur inconnue
    }

    private static String emptyToNull(String v) { return v == null || v.isBlank() ? null : v; } // chaine vide vers null

    public List<TsaConfig> tsas() { return List.copyOf(tsas); } // TSA (copie)
    public void setTsas(List<TsaConfig> v) { tsas = new ArrayList<>(v); } // modification
    public SigningLevel defaultLevel() { return defaultLevel; } // niveau
    public void setDefaultLevel(SigningLevel v) { defaultLevel = v; } // modification
    public OutputMode defaultOutputMode() { return defaultOutputMode; } // forme
    public void setDefaultOutputMode(OutputMode v) { defaultOutputMode = v; } // modification
    public boolean pdfVisibleSignature() { return pdfVisibleSignature; } // signature visible
    public void setPdfVisibleSignature(boolean v) { pdfVisibleSignature = v; } // modification
    public Optional<Path> pkcs11Library() { return pkcs11Library; } // bibliotheque PKCS#11
    public void setPkcs11Library(Optional<Path> v) { pkcs11Library = v; } // modification
    public Optional<Path> lastP12() { return lastP12; } // dernier P12
    public void setLastP12(Optional<Path> v) { lastP12 = v; } // modification
    public Locale locale() { return locale; } // langue
    public void setLocale(Locale v) { locale = v; } // modification
    public boolean darkTheme() { return darkTheme; } // theme sombre
    public void setDarkTheme(boolean v) { darkTheme = v; } // modification
}
