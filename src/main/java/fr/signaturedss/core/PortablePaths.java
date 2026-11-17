package fr.signaturedss.core; // paquet du noyau

import java.io.IOException; // erreurs d'E/S
import java.io.UncheckedIOException; // erreur d'E/S non verifiee
import java.net.URISyntaxException; // URI invalide
import java.nio.file.Files; // operations fichiers
import java.nio.file.Path; // chemins
import java.util.Locale; // casse independante de la langue
import java.util.Set; // ensemble de noms

/**
 * Resout l'emplacement des donnees ecrites par l'application.
 * Toutes les donnees vont dans "donnees/", a cote de l'application, jamais dans le profil utilisateur.
 */
public final class PortablePaths { // classe immuable

    private static final String DATA = "donnees"; // nom du dossier de donnees
    private static final Set<String> PLATFORM_DIRS = Set.of("windows", "linux", "macos"); // dossiers de plateforme sur la cle
    private static final int MAX_DEPTH = 6; // profondeur maximale de recherche vers le haut

    private final Path root; // racine de l'installation portable
    private final Path dataDir; // dossier donnees/

    private PortablePaths(Path root) { // constructeur prive
        this.root = root.toAbsolutePath().normalize(); // racine absolue normalisee
        this.dataDir = this.root.resolve(DATA); // donnees/ sous la racine
        createDirs(); // creation des sous-dossiers
    }

    /** Construit les chemins a partir du dossier ou se trouve l'application. */
    public static PortablePaths fromAppLocation(Path appLocation) { // fabrique principale
        Path start = appLocation.toAbsolutePath().normalize(); // point de depart absolu
        Path current = start; // curseur de remontee
        for (int i = 0; i < MAX_DEPTH && current != null; i++) { // remontee bornee
            String name = current.getFileName() == null ? "" : current.getFileName().toString().toLowerCase(Locale.ROOT); // nom du dossier
            if (name.endsWith(".app") && current.getParent() != null && current.getParent().getParent() != null) { // bundle macOS
                return new PortablePaths(current.getParent().getParent()); // parent du dossier macOS
            }
            if (PLATFORM_DIRS.contains(name) && current.getParent() != null) { // dossier Windows, Linux ou macOS
                return new PortablePaths(current.getParent()); // racine de la cle
            }
            current = current.getParent(); // niveau superieur
        }
        return new PortablePaths(start); // cas par defaut : donnees/ dans le dossier courant
    }

    /** Detecte l'emplacement : propriete "signaturedss.home" ou emplacement du code. */
    public static PortablePaths detect() { // detection automatique
        String forced = System.getProperty("signaturedss.home"); // propriete de forcage
        if (forced != null && !forced.isBlank()) { // propriete renseignee
            return new PortablePaths(Path.of(forced)); // racine imposee
        }
        try { // lecture de l'emplacement du code
            Path code = Path.of(PortablePaths.class.getProtectionDomain().getCodeSource().getLocation().toURI()); // jar ou classes
            Path start = Files.isRegularFile(code) ? code.getParent() : code; // dossier du jar
            return fromAppLocation(start); // resolution standard
        } catch (URISyntaxException e) { // URI non convertible
            throw new IllegalStateException("Emplacement de l'application introuvable", e); // erreur fatale
        }
    }

    private void createDirs() { // cree l'arborescence des donnees
        try { // creation protegee
            Files.createDirectories(tslCache()); // cache des listes de confiance
            Files.createDirectories(anchorsDir()); // ancres de confiance utilisateur
            Files.createDirectories(logsDir()); // journaux
        } catch (IOException e) { // cle en lecture seule, par exemple
            throw new UncheckedIOException("Impossible de creer " + dataDir, e); // erreur explicite
        }
    }

    public Path root() { return root; } // racine portable
    public Path dataDir() { return dataDir; } // dossier donnees/
    public Path config() { return dataDir.resolve("config.properties"); } // fichier de configuration
    public Path tslCache() { return dataDir.resolve("cache").resolve("tsl"); } // cache LOTL
    public Path anchorsDir() { return dataDir.resolve("ancres"); } // ancres de confiance
    public Path logsDir() { return dataDir.resolve("logs"); } // journaux
}
