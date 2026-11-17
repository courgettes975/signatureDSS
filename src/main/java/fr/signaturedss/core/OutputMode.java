package fr.signaturedss.core; // paquet du noyau

/** Forme de la signature pour les fichiers autres que PDF. */
public enum OutputMode { // enumeration
    CADES_DETACHED, // fichier .p7s a cote de l'original
    ASICE // conteneur .asice contenant l'original et la signature
}
