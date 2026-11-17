package fr.signaturedss.core.sign; // paquet signature

import fr.signaturedss.i18n.Messages; // textes traduits

import java.nio.file.Path; // chemins

/** Echec de signature d'un fichier ; aucune sortie n'est laissee pour ce fichier. */
public class SigningException extends RuntimeException { // erreur non verifiee

    private static final long serialVersionUID = 1L; // version de serialisation
    private final transient Path file; // fichier en cause
    private final transient java.util.List<SignResult> completed; // fichiers signes avant l'echec

    public SigningException(Path file, Throwable cause) { this(file, cause, java.util.List.of()); } // sans fichier deja signe

    public SigningException(Path file, Throwable cause, java.util.List<SignResult> completed) { // constructeur complet
        super(Messages.get("erreur.signature", file.getFileName(), usefulMessage(cause)), cause); // message traduit
        this.file = file; // memorisation
        this.completed = java.util.List.copyOf(completed); // copie immuable
    }

    /** Fichiers signes avant l'echec (le lot s'est arrete au fichier en cause). */
    public java.util.List<SignResult> completed() { return completed; } // accesseur

    /** Fichier en cause. */
    public Path file() { return file; } // accesseur

    private static String usefulMessage(Throwable t) { // message le plus utile
        Throwable c = t; // curseur
        while (c.getCause() != null && c.getCause() != c) { // descente vers la cause racine
            if (c instanceof fr.signaturedss.core.tsa.TsaUnavailableException) { break; } // message deja traduit : services essayes
            c = c.getCause(); // cause suivante
        }
        return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage(); // texte
    }
}
