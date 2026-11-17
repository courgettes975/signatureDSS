package fr.signaturedss.core.token; // paquet jetons

import fr.signaturedss.i18n.Messages; // textes traduits

/** Erreur d'acces au certificat, avec une cle de message traduite. Ne contient jamais le secret. */
public class TokenException extends RuntimeException { // erreur non verifiee

    private static final long serialVersionUID = 1L; // version de serialisation
    private final String key; // cle de message

    public TokenException(String key, Throwable cause) { // constructeur
        super(Messages.get(key), cause); // message traduit
        this.key = key; // memorisation
    }

    /** Cle de message (erreur.token.*). */
    public String key() { return key; } // accesseur
}
