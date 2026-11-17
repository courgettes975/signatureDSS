package fr.signaturedss.core.pgp; // paquet PGP

import fr.signaturedss.i18n.Messages; // textes traduits

/** Erreur PGP avec une cle de message traduite. */
public class PgpException extends RuntimeException { // erreur non verifiee

    private static final long serialVersionUID = 1L; // version de serialisation
    private final String key; // cle de message

    public PgpException(String key, Throwable cause) { // constructeur
        super(Messages.get(key), cause); // message traduit
        this.key = key; // memorisation
    }

    /** Cle de message (erreur.pgp.*). */
    public String key() { return key; } // accesseur
}
