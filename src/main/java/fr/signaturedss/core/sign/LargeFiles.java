package fr.signaturedss.core.sign; // paquet signature

import eu.europa.esig.dss.asic.common.SecureContainerHandlerBuilder; // gestion securisee des conteneurs ZIP
import eu.europa.esig.dss.asic.common.ZipUtils; // utilitaire ZIP global de DSS
import eu.europa.esig.dss.signature.resources.TempFileResourcesHandlerBuilder; // ressources en fichiers temporaires

import java.io.IOException; // erreurs d'E/S
import java.io.UncheckedIOException; // erreur non verifiee
import java.nio.file.DirectoryStream; // parcours de dossier
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins

/**
 * Conteneurs ASiC de fichiers volumineux : DSS construit par defaut le conteneur en memoire.
 * On le fait passer par des fichiers temporaires dans donnees/tmp (sur la cle).
 */
public final class LargeFiles { // utilitaire statique

    private LargeFiles() { } // pas d'instance

    /** Configure DSS pour utiliser des fichiers temporaires dans ce dossier ; vide les restes d'une session precedente. */
    public static void configure(Path tmpDir) { // configuration globale
        try { // creation protegee
            Files.createDirectories(tmpDir); // dossier temporaire
            try (DirectoryStream<Path> old = Files.newDirectoryStream(tmpDir)) { // restes eventuels
                for (Path p : old) { try { Files.deleteIfExists(p); } catch (IOException ignored) { /* fichier encore ouvert */ } } // nettoyage
            }
        } catch (IOException e) { // dossier impossible a creer
            throw new UncheckedIOException(e); // erreur explicite
        }
        TempFileResourcesHandlerBuilder resources = new TempFileResourcesHandlerBuilder().setTempFileDirectory(tmpDir.toFile()); // ressources sur disque
        ZipUtils.getInstance().setZipContainerHandlerBuilder(new SecureContainerHandlerBuilder().setResourcesHandlerBuilder(resources)); // utilise par tous les services ASiC
    }
}
