package fr.signaturedss; // paquet racine

import fr.signaturedss.core.PortablePaths; // chemins portables
import fr.signaturedss.core.trust.LotlService; // listes de confiance
import fr.signaturedss.core.trust.TrustContext; // confiance
import fr.signaturedss.core.validate.ValidationOutcome; // resultat
import fr.signaturedss.core.validate.ValidationService; // validation
import fr.signaturedss.i18n.Messages; // textes traduits

import java.io.InputStream; // flux
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.nio.file.StandardCopyOption; // copie
import java.util.Arrays; // tableaux
import java.util.List; // liste

/**
 * Point d'entree. Classe distincte de l'Application JavaFX pour permettre
 * le lancement sur le classpath (jpackage) sans module JavaFX declare.
 * Options : --self-check (controle sans affichage), --version.
 */
public final class Launcher { // classe de demarrage

    /** Version affichee par --version. */
    public static final String VERSION = "1.0.0"; // version de l'application

    private Launcher() { } // pas d'instance

    public static void main(String[] args) { // methode principale
        int code = start(args, PortablePaths::detect, Launcher::showStartupError); // demarrage
        if (code != 0) { System.exit(code); } // code d'erreur pour les scripts
    }

    /** Demarrage : resout donnees/ (erreur affichee si impossible), puis --version, --self-check ou l'interface. */
    static int start(String[] args, java.util.function.Supplier<PortablePaths> pathsSupplier, java.util.function.Consumer<String> errors) { // demarrage testable
        PortablePaths paths; // chemins portables
        try { // creation de donnees/
            paths = pathsSupplier.get(); // resolution
        } catch (RuntimeException e) { // cle en lecture seule, application deplacee...
            errors.accept(Messages.get("erreur.donnees", e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : String.valueOf(e.getMessage()))); // message clair
            return 5; // code d'erreur
        }
        configure(paths); // proprietes systeme avant tout chargement
        List<String> a = Arrays.asList(args); // arguments
        if (a.contains("--version")) { System.out.println("SignatureDSS " + VERSION); return 0; } // version
        if (a.contains("--self-check")) { return selfCheck(paths); } // controle du paquet
        App.main(args); // lancement de l'interface
        return 0; // fin normale
    }

    /** Affiche une erreur de demarrage : boite de dialogue si un ecran existe, sinon sortie d'erreur. */
    private static void showStartupError(String message) { // affichage minimal, sans JavaFX
        System.err.println(message); // console ou journal du systeme
        if (!java.awt.GraphicsEnvironment.isHeadless()) { javax.swing.JOptionPane.showMessageDialog(null, message, "SignatureDSS", javax.swing.JOptionPane.ERROR_MESSAGE); } // boite de dialogue
    }

    /** Dirige le journal et le cache natif JavaFX vers donnees/ (rien dans le profil utilisateur). */
    static void configure(PortablePaths paths) { // proprietes systeme
        System.setProperty("org.slf4j.simpleLogger.logFile", paths.logsDir().resolve("signaturedss.log").toString()); // journal sur la cle
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true"); // horodatage des lignes
        System.setProperty("javafx.cachedir", paths.dataDir().resolve("cache").resolve("javafx").toString()); // bibliotheques natives JavaFX sur la cle
        fr.signaturedss.core.sign.LargeFiles.configure(paths.dataDir().resolve("tmp")); // conteneurs ASiC volumineux sur disque, pas en memoire
    }

    /** Controle sans affichage : textes, cache LOTL, validation d'un exemple embarque. Renvoie 0 si tout fonctionne. */
    static int selfCheck(PortablePaths paths) { // auto-controle
        try (InputStream in = Launcher.class.getResourceAsStream("/selfcheck/sample-signed.pdf")) { // exemple signe
            if (in == null) { System.err.println("Exemple embarque manquant"); return 2; } // ressource absente
            if (Messages.get("app.titre").startsWith("!")) { System.err.println("Textes manquants"); return 3; } // traductions absentes
            LotlService lotl = new LotlService(paths); // listes de confiance
            lotl.refreshOffline(); // cache seulement (pas de reseau)
            Path tmp = Files.createTempFile(paths.dataDir(), "selfcheck", ".pdf"); // copie temporaire sur la cle
            try { // validation protegee
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING); // extraction
                ValidationOutcome o = new ValidationService(TrustContext.online(paths, lotl)).validate(tmp, List.of()); // validation
                System.out.println("SignatureDSS " + VERSION + " : auto-controle OK (" + o.level() + ", " + o.verdict() + ")"); // compte rendu
                return o.verdict() == null ? 4 : 0; // succes si un verdict est rendu
            } finally { // nettoyage
                Files.deleteIfExists(tmp); // suppression de la copie
            }
        } catch (Exception e) { // toute erreur
            e.printStackTrace(); // detail pour le diagnostic
            return 1; // echec
        }
    }
}
