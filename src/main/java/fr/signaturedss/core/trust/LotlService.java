package fr.signaturedss.core.trust; // paquet confiance

import eu.europa.esig.dss.service.http.commons.CommonsDataLoader; // client HTTP
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader; // chargeur avec cache fichier
import eu.europa.esig.dss.spi.client.http.DSSFileLoader; // interface de chargement
import eu.europa.esig.dss.spi.client.http.IgnoreDataLoader; // chargeur hors ligne
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource; // certificats des listes de confiance
import eu.europa.esig.dss.spi.x509.CertificateSource; // source de certificats
import eu.europa.esig.dss.spi.x509.KeyStoreCertificateSource; // source depuis un magasin
import eu.europa.esig.dss.tsl.function.OfficialJournalSchemeInformationURI; // annonce au Journal officiel
import eu.europa.esig.dss.tsl.job.TLValidationJob; // telechargement et validation des listes
import eu.europa.esig.dss.tsl.source.LOTLSource; // liste des listes
import eu.europa.esig.dss.validation.job.cache.CacheCleaner; // nettoyage du cache
import fr.signaturedss.core.PortablePaths; // chemins portables
import org.slf4j.Logger; // journal
import org.slf4j.LoggerFactory; // fabrique de journal

import java.io.InputStream; // flux de ressource

/**
 * Listes de confiance europeennes (LOTL), avec un cache sur la cle pour le travail hors ligne.
 * Cablage repris de dss-standalone-app (esig/dss-demonstrations 6.5.1, TLValidationJobExecutor).
 */
public class LotlService { // service des listes de confiance

    public static final String LOTL_URL = "https://ec.europa.eu/tools/lotl/eu-lotl.xml"; // URL de la LOTL
    public static final String OJ_URL = "https://eur-lex.europa.eu/eli/C/2026/1944/oj"; // annonce au Journal officiel
    private static final String OJ_KEYSTORE = "/trust/oj-keystore.p12"; // certificats de signature de la LOTL
    private static final char[] OJ_PASSWORD = "dss-password".toCharArray(); // mot de passe public du magasin de reference
    private static final int TIMEOUT_MS = 15_000; // delai reseau
    private static final Logger LOG = LoggerFactory.getLogger(LotlService.class); // journal

    private final PortablePaths paths; // chemins portables
    private final TrustedListsCertificateSource source = new TrustedListsCertificateSource(); // certificats de confiance obtenus
    private final TLValidationJob job; // tache de rafraichissement

    public LotlService(PortablePaths paths) { // constructeur
        this.paths = paths; // memorisation
        this.job = buildJob(); // construction de la tache
    }

    private TLValidationJob buildJob() { // assemble la tache DSS
        TLValidationJob j = new TLValidationJob(); // tache
        j.setOnlineDataLoader(onlineLoader()); // chargeur en ligne
        j.setOfflineDataLoader(offlineLoader()); // chargeur hors ligne
        j.setTrustedListCertificateSource(source); // destination
        CacheCleaner cleaner = new CacheCleaner(); // nettoyeur
        cleaner.setCleanMemory(true); // memoire
        cleaner.setCleanFileSystem(true); // fichiers
        cleaner.setDSSFileLoader(offlineLoader()); // cache a nettoyer
        j.setCacheCleaner(cleaner); // enregistrement
        LOTLSource lotl = new LOTLSource(); // LOTL europeenne
        lotl.setUrl(LOTL_URL); // URL
        lotl.setCertificateSource(officialJournalKeyStore()); // certificats attendus
        lotl.setSigningCertificatesAnnouncementPredicate(new OfficialJournalSchemeInformationURI(OJ_URL)); // annonce officielle
        lotl.setPivotSupport(true); // prise en charge des pivots
        j.setListOfTrustedListSources(lotl); // enregistrement
        return j; // tache prete
    }

    private DSSFileLoader onlineLoader() { // chargeur qui telecharge et met en cache
        CommonsDataLoader http = new CommonsDataLoader(); // client HTTP
        http.setTimeoutConnection(TIMEOUT_MS); // delai de connexion
        http.setTimeoutResponse(TIMEOUT_MS); // delai de reponse
        FileCacheDataLoader l = new FileCacheDataLoader(); // cache fichier
        l.setCacheExpirationTime(0); // toujours retelecharger en ligne
        l.setDataLoader(http); // client HTTP
        l.setFileCacheDirectory(paths.tslCache().toFile()); // cache sur la cle
        return l; // chargeur
    }

    private FileCacheDataLoader offlineLoader() { // chargeur qui lit uniquement le cache
        FileCacheDataLoader l = new FileCacheDataLoader(); // cache fichier
        l.setCacheExpirationTime(-1); // cache jamais expire
        l.setDataLoader(new IgnoreDataLoader()); // aucun acces reseau
        l.setFileCacheDirectory(paths.tslCache().toFile()); // cache sur la cle
        return l; // chargeur
    }

    /** Magasin des certificats de signature de la LOTL (Journal officiel). */
    public static CertificateSource officialJournalKeyStore() { // lecture de la ressource
        InputStream in = LotlService.class.getResourceAsStream(OJ_KEYSTORE); // ressource embarquee
        if (in == null) { throw new IllegalStateException("Ressource manquante : " + OJ_KEYSTORE); } // erreur de packaging
        return new KeyStoreCertificateSource(in, "PKCS12", OJ_PASSWORD.clone()); // source
    }

    /** Charge les listes depuis le cache de la cle (sans reseau). */
    public void refreshOffline() { // rafraichissement hors ligne
        try { job.offlineRefresh(); } catch (RuntimeException e) { LOG.warn("Cache LOTL inutilisable : {}", e.getMessage()); } // erreur non bloquante
    }

    /** Telecharge les listes et met a jour le cache. */
    public void refreshOnline() { // rafraichissement en ligne
        try { job.onlineRefresh(); } catch (RuntimeException e) { LOG.warn("Telechargement LOTL impossible : {}", e.getMessage()); } // hors ligne : on garde le cache
    }

    /** Certificats de confiance issus des listes. */
    public TrustedListsCertificateSource certificateSource() { return source; } // accesseur
}
