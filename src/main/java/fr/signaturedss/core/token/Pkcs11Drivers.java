package fr.signaturedss.core.token; // paquet jetons

import fr.signaturedss.core.PortablePaths; // chemins portables

import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.util.ArrayList; // liste modifiable
import java.util.List; // liste
import java.util.Optional; // valeur facultative

/**
 * Recherche d'un pilote PKCS#11 OpenSC : d'abord celui embarque sur la cle (Windows/pilotes),
 * puis une installation OpenSC existante sur l'ordinateur (emplacements standards).
 */
public final class Pkcs11Drivers { // utilitaire statique

    private Pkcs11Drivers() { } // pas d'instance

    /** Pilote a utiliser pour ce systeme, s'il en existe un. */
    public static Optional<Path> find(PortablePaths paths, String os) { return find(paths, os, systemCandidates(os)); } // emplacements standards

    /** Variante testable avec une liste d'emplacements systeme fournie. */
    public static Optional<Path> find(PortablePaths paths, String os, List<Path> system) { // recherche
        List<Path> candidates = new ArrayList<>(); // emplacements dans l'ordre de preference
        candidates.add(paths.root().resolve(platformDir(os)).resolve("pilotes").resolve(fileName(os))); // pilote embarque sur la cle
        candidates.addAll(system); // installation sur l'ordinateur
        return candidates.stream().filter(Files::isRegularFile).findFirst(); // premier present
    }

    /** Emplacements standards d'OpenSC (verifies dans OpenSC 0.27.1 et le paquet Debian opensc-pkcs11). */
    public static List<Path> systemCandidates(String os) { // par systeme
        return switch (os) { // selon le systeme
            case "windows" -> List.of(Path.of("C:\\Program Files\\OpenSC Project\\OpenSC\\pkcs11\\opensc-pkcs11.dll")); // installeur OpenSC
            case "macos" -> List.of(Path.of("/Library/OpenSC/lib/opensc-pkcs11.so"), Path.of("/usr/local/lib/opensc-pkcs11.so"), Path.of("/opt/homebrew/lib/opensc-pkcs11.so")); // installeur OpenSC, Homebrew
            default -> List.of(Path.of("/usr/lib/x86_64-linux-gnu/opensc-pkcs11.so"), Path.of("/usr/lib/x86_64-linux-gnu/pkcs11/opensc-pkcs11.so"), Path.of("/usr/lib/aarch64-linux-gnu/opensc-pkcs11.so"), Path.of("/usr/lib64/opensc-pkcs11.so"), Path.of("/usr/lib/opensc-pkcs11.so")); // Debian et autres
        };
    }

    private static String platformDir(String os) { return switch (os) { case "windows" -> "Windows"; case "macos" -> "macOS"; default -> "Linux"; }; } // dossier de plateforme sur la cle

    private static String fileName(String os) { return "windows".equals(os) ? "opensc-pkcs11.dll" : "opensc-pkcs11.so"; } // nom du pilote
}
