package fr.signaturedss.core.sign; // paquet signature

import eu.europa.esig.dss.token.DSSPrivateKeyEntry; // cle privee choisie
import fr.signaturedss.core.OutputMode; // forme de sortie
import fr.signaturedss.core.SigningLevel; // niveau

import java.util.List; // liste

/**
 * Demande de signature d'un lot de fichiers.
 *
 * @param files fichiers a signer (chacun recoit sa propre signature)
 * @param level niveau demande
 * @param mode forme pour les fichiers non PDF
 * @param pdfVisible apparence visible sur les PDF
 * @param key cle de signature
 */
public record SignRequest(List<java.nio.file.Path> files, SigningLevel level, OutputMode mode, boolean pdfVisible, DSSPrivateKeyEntry key) { // enregistrement
    public SignRequest { files = List.copyOf(files); } // copie immuable
}
