package fr.signaturedss.core.validate; // paquet validation

import fr.signaturedss.i18n.Messages; // textes traduits

/** Le fichier ne contient aucune signature reconnue. */
public class NotSignedException extends RuntimeException { // erreur non verifiee
    private static final long serialVersionUID = 1L; // version de serialisation
    public NotSignedException(Throwable cause) { super(Messages.get("erreur.aucune.signature"), cause); } // message traduit
}
