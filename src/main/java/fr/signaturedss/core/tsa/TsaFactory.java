package fr.signaturedss.core.tsa; // paquet horodatage

import eu.europa.esig.dss.enumerations.DigestAlgorithm; // algorithme d'empreinte
import eu.europa.esig.dss.service.http.commons.TimestampDataLoader; // chargeur HTTP pour TSA
import eu.europa.esig.dss.service.tsp.OnlineTSPSource; // TSA en ligne
import eu.europa.esig.dss.spi.DSSUtils; // utilitaires DSS
import fr.signaturedss.core.TsaConfig; // configuration TSA

import java.net.URI; // analyse d'URL
import java.nio.charset.StandardCharsets; // UTF-8

/** Construit et teste les services d'horodatage. */
public final class TsaFactory { // utilitaire statique

    private static final int TIMEOUT_MS = 10_000; // delai reseau : 10 secondes

    private TsaFactory() { } // pas d'instance

    /** Cree une TSA en ligne ; le mot de passe n'est garde qu'en memoire. */
    public static NamedTsp create(TsaConfig cfg, char[] passwordOrNull) { // fabrique
        TimestampDataLoader loader = new TimestampDataLoader(); // chargeur HTTP dedie
        loader.setTimeoutConnection(TIMEOUT_MS); // delai de connexion
        loader.setTimeoutResponse(TIMEOUT_MS); // delai de reponse
        if (cfg.needsAuth() && passwordOrNull != null) { // authentification demandee
            URI u = URI.create(cfg.url()); // analyse de l'URL
            int port = u.getPort() != -1 ? u.getPort() : ("https".equalsIgnoreCase(u.getScheme()) ? 443 : 80); // port effectif
            loader.addAuthentication(u.getHost(), port, u.getScheme(), cfg.username(), passwordOrNull.clone()); // identifiants HTTP Basic
        }
        return new NamedTsp(cfg.url(), new OnlineTSPSource(cfg.url(), loader)); // TSA nommee
    }

    /** Vrai si la TSA renvoie un jeton pour une empreinte fixe. */
    public static boolean test(NamedTsp tsp) { // bouton Tester
        try { // appel protege
            byte[] digest = DSSUtils.digest(DigestAlgorithm.SHA256, "SignatureDSS".getBytes(StandardCharsets.UTF_8)); // empreinte fixe
            return tsp.source().getTimeStampResponse(DigestAlgorithm.SHA256, digest) != null; // reponse obtenue
        } catch (RuntimeException e) { // panne
            return false; // TSA indisponible
        }
    }
}
