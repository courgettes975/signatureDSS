package fr.signaturedss.core.sign; // paquet signature

import java.nio.file.Path; // chemins

/**
 * Resultat de la signature d'un fichier.
 *
 * @param input fichier d'origine (non modifie)
 * @param output fichier produit
 * @param plan format utilise
 */
public record SignResult(Path input, Path output, FormatPlan plan) { } // enregistrement
