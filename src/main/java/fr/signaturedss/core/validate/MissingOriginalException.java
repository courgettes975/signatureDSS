package fr.signaturedss.core.validate; // paquet validation

import fr.signaturedss.i18n.Messages; // textes traduits

/** Signature detachee validee sans son fichier d'origine. */
public class MissingOriginalException extends RuntimeException { // erreur non verifiee
    private static final long serialVersionUID = 1L; // version de serialisation
    public MissingOriginalException() { super(Messages.get("erreur.original.manquant")); } // message traduit
}
