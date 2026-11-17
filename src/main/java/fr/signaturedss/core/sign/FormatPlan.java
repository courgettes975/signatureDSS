package fr.signaturedss.core.sign; // paquet signature

import eu.europa.esig.dss.enumerations.SignatureLevel; // niveaux DSS
import fr.signaturedss.core.OutputMode; // forme de sortie
import fr.signaturedss.core.SigningLevel; // niveau choisi

import java.io.IOException; // erreurs d'E/S
import java.io.InputStream; // flux
import java.io.UncheckedIOException; // erreur non verifiee
import java.nio.charset.StandardCharsets; // ASCII
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.util.Arrays; // comparaison de tableaux

/**
 * Format, niveau DSS et nom de sortie pour un fichier a signer.
 *
 * @param kind type de signature
 * @param dssLevel niveau DSS correspondant
 * @param output fichier produit (jamais un fichier existant)
 */
public record FormatPlan(Kind kind, SignatureLevel dssLevel, Path output) { // enregistrement immuable

    /** Types de signature produits. */
    public enum Kind { PADES, CADES_DETACHED, ASICE_CADES } // PAdES, CAdES detache, ASiC-E

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII); // en-tete PDF

    /** Determine le format pour un fichier. Un PDF est reconnu par son en-tete. */
    public static FormatPlan resolve(Path input, SigningLevel level, OutputMode mode) { // resolution
        String name = input.getFileName().toString(); // nom du fichier
        Path dir = input.toAbsolutePath().getParent(); // dossier de sortie = dossier d'origine
        if (isPdf(input)) { // fichier PDF
            return new FormatPlan(Kind.PADES, pades(level), freeName(dir.resolve(insertBeforeExtension(name, "-signe")))); // copie signee
        }
        if (mode == OutputMode.ASICE) { // conteneur demande
            return new FormatPlan(Kind.ASICE_CADES, cades(level), freeName(dir.resolve(name + ".asice"))); // conteneur
        }
        return new FormatPlan(Kind.CADES_DETACHED, cades(level), freeName(dir.resolve(name + ".p7s"))); // signature detachee
    }

    /** Vrai si le fichier commence par %PDF-. */
    public static boolean isPdf(Path f) { // detection par en-tete
        try (InputStream in = Files.newInputStream(f)) { // lecture
            byte[] head = in.readNBytes(PDF_MAGIC.length); // premiers octets
            return Arrays.equals(head, PDF_MAGIC); // comparaison
        } catch (IOException e) { // lecture impossible
            throw new UncheckedIOException(e); // erreur explicite
        }
    }

    /** Renvoie le chemin s'il est libre, sinon ajoute -2, -3... avant la derniere extension. */
    public static Path freeName(Path wanted) { // nom libre
        if (!Files.exists(wanted)) { return wanted; } // deja libre
        String name = wanted.getFileName().toString(); // nom voulu
        for (int i = 2; ; i++) { // essais successifs
            Path candidate = wanted.resolveSibling(insertBeforeExtension(name, "-" + i)); // candidat
            if (!Files.exists(candidate)) { return candidate; } // libre
        }
    }

    /** Insere un texte avant la derniere extension ("a.pdf" + "-x" = "a-x.pdf"). */
    public static String insertBeforeExtension(String name, String insert) { // insertion
        int dot = name.lastIndexOf('.'); // derniere extension
        return dot <= 0 ? name + insert : name.substring(0, dot) + insert + name.substring(dot); // insertion
    }

    /** Niveau PAdES. */
    public static SignatureLevel pades(SigningLevel l) { // correspondance PAdES
        return switch (l) { // selon le niveau
            case B -> SignatureLevel.PAdES_BASELINE_B; // B
            case T -> SignatureLevel.PAdES_BASELINE_T; // T
            case LT -> SignatureLevel.PAdES_BASELINE_LT; // LT
            case LTA -> SignatureLevel.PAdES_BASELINE_LTA; // LTA
        };
    }

    /** Niveau CAdES. */
    public static SignatureLevel cades(SigningLevel l) { // correspondance CAdES
        return switch (l) { // selon le niveau
            case B -> SignatureLevel.CAdES_BASELINE_B; // B
            case T -> SignatureLevel.CAdES_BASELINE_T; // T
            case LT -> SignatureLevel.CAdES_BASELINE_LT; // LT
            case LTA -> SignatureLevel.CAdES_BASELINE_LTA; // LTA
        };
    }
}
