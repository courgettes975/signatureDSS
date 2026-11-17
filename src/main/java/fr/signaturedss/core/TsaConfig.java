package fr.signaturedss.core; // paquet du noyau

/**
 * Service d'horodatage configure. Pas de champ mot de passe :
 * il est demande une fois par session et garde en memoire seulement.
 *
 * @param url adresse du service RFC 3161
 * @param username identifiant HTTP Basic, ou null si aucun
 */
public record TsaConfig(String url, String username) { // enregistrement immuable

    /** Vrai si une authentification est requise. */
    public boolean needsAuth() { return username != null && !username.isBlank(); } // identifiant renseigne
}
