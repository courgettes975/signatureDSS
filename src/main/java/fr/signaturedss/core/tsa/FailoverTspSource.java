package fr.signaturedss.core.tsa; // paquet horodatage

import eu.europa.esig.dss.enumerations.DigestAlgorithm; // algorithme d'empreinte
import eu.europa.esig.dss.model.TimestampBinary; // jeton d'horodatage
import eu.europa.esig.dss.spi.x509.tsp.TSPSource; // interface TSA
import org.slf4j.Logger; // journal
import org.slf4j.LoggerFactory; // fabrique de journal

import java.util.ArrayList; // liste modifiable
import java.util.List; // liste

/** Essaie chaque TSA dans l'ordre et renvoie la premiere reponse. */
public class FailoverTspSource implements TSPSource { // source DSS composite

    private static final long serialVersionUID = 1L; // version de serialisation
    private static final Logger LOG = LoggerFactory.getLogger(FailoverTspSource.class); // journal
    private final List<NamedTsp> sources; // TSA dans l'ordre d'essai
    private final List<String> skipped = new java.util.concurrent.CopyOnWriteArrayList<>(); // TSA en echec depuis la creation

    public FailoverTspSource(List<NamedTsp> sources) { this.sources = List.copyOf(sources); } // copie immuable

    @Override // demande d'horodatage
    public TimestampBinary getTimeStampResponse(DigestAlgorithm digestAlgorithm, byte[] digest) { // methode DSS
        List<String> tried = new ArrayList<>(); // services essayes
        Exception last = null; // derniere erreur
        for (NamedTsp n : sources) { // chaque TSA
            tried.add(n.label()); // memorisation
            try { // appel protege
                TimestampBinary r = n.source().getTimeStampResponse(digestAlgorithm, digest); // demande
                if (r != null) { return r; } // succes
                LOG.warn("TSA sans reponse : {}", n.label()); // reponse vide
                markSkipped(n.label()); // memorisation de l'echec
            } catch (RuntimeException e) { // panne
                LOG.warn("TSA en echec : {} ({})", n.label(), e.getMessage()); // journal sans secret
                last = e; // memorisation
                markSkipped(n.label()); // memorisation de l'echec
            }
        }
        throw new TsaUnavailableException(tried, last); // echec total
    }

    private void markSkipped(String label) { if (!skipped.contains(label)) { skipped.add(label); } } // ajout sans doublon

    /** TSA qui ont echoue depuis la creation de cette chaine (repli ou echec total). */
    public List<String> skipped() { return List.copyOf(skipped); } // copie

    /** Libelles des TSA configurees. */
    public List<String> labels() { return sources.stream().map(NamedTsp::label).toList(); } // accesseur
}
