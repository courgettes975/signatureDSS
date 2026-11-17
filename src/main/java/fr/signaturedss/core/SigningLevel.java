package fr.signaturedss.core; // paquet du noyau

/** Niveaux de signature proposes (baseline ETSI). */
public enum SigningLevel { // enumeration
    B, // signature de base
    T, // avec horodatage
    LT, // avec donnees de validation (long terme)
    LTA // avec horodatage d'archive
}
