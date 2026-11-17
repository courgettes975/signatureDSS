package fr.signaturedss.core.tsa; // paquet horodatage

import eu.europa.esig.dss.model.DSSException; // erreur DSS (non verifiee)
import fr.signaturedss.i18n.Messages; // textes traduits

import java.util.List; // liste

/** Aucun service d'horodatage n'a repondu. */
public class TsaUnavailableException extends DSSException { // traverse les services DSS

    private static final long serialVersionUID = 1L; // version de serialisation
    private final List<String> tried; // services essayes

    public TsaUnavailableException(List<String> tried, Throwable last) { // constructeur
        super(Messages.get("erreur.tsa.indisponible", tried.isEmpty() ? "-" : String.join(", ", tried)), last); // message traduit
        this.tried = List.copyOf(tried); // copie immuable
    }

    /** Libelles des services essayes, dans l'ordre. */
    public List<String> tried() { return tried; } // accesseur
}
