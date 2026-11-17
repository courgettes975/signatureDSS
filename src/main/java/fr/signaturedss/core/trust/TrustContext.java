package fr.signaturedss.core.trust; // paquet confiance

import eu.europa.esig.dss.model.x509.CertificateToken; // certificat DSS
import eu.europa.esig.dss.service.crl.OnlineCRLSource; // CRL en ligne
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader; // client HTTP
import eu.europa.esig.dss.service.http.commons.OCSPDataLoader; // client HTTP OCSP
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource; // OCSP en ligne
import eu.europa.esig.dss.spi.DSSUtils; // utilitaires DSS
import eu.europa.esig.dss.spi.validation.CertificateVerifier; // verificateur
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier; // implementation standard
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource; // ancres utilisateur
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource; // AIA en ligne
import fr.signaturedss.core.PortablePaths; // chemins portables
import org.slf4j.Logger; // journal
import org.slf4j.LoggerFactory; // fabrique de journal

import java.io.IOException; // erreurs d'E/S
import java.io.InputStream; // flux
import java.nio.file.DirectoryStream; // parcours de dossier
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.util.Locale; // casse

/** Verificateur de certificats : listes europeennes + ancres de l'utilisateur + revocation en ligne. */
public final class TrustContext { // contexte immuable

    private static final Logger LOG = LoggerFactory.getLogger(TrustContext.class); // journal
    private static final int TIMEOUT_MS = 10_000; // delai reseau

    private final CertificateVerifier verifier; // verificateur DSS
    private final int anchorCount; // nombre d'ancres utilisateur

    private TrustContext(CertificateVerifier verifier, int anchorCount) { // constructeur prive
        this.verifier = verifier; // memorisation
        this.anchorCount = anchorCount; // memorisation
    }

    /** Contexte reel, avec acces reseau pour OCSP, CRL et AIA. */
    public static TrustContext online(PortablePaths paths, LotlService lotl) { // fabrique principale
        CommonsDataLoader http = new CommonsDataLoader(); // client HTTP
        http.setTimeoutConnection(TIMEOUT_MS); // delai de connexion
        http.setTimeoutResponse(TIMEOUT_MS); // delai de reponse
        OCSPDataLoader ocspHttp = new OCSPDataLoader(); // client OCSP
        ocspHttp.setTimeoutConnection(TIMEOUT_MS); // delai de connexion
        ocspHttp.setTimeoutResponse(TIMEOUT_MS); // delai de reponse
        CommonTrustedCertificateSource anchors = loadAnchors(paths.anchorsDir()); // ancres utilisateur
        CommonCertificateVerifier v = new CommonCertificateVerifier(); // verificateur
        v.setTrustedCertSources(lotl.certificateSource(), anchors); // confiance : LOTL + ancres
        v.setOcspSource(new OnlineOCSPSource(ocspHttp)); // OCSP en ligne
        v.setCrlSource(new OnlineCRLSource(http)); // CRL en ligne
        v.setAIASource(new DefaultAIASource(http)); // completion de chaine
        v.setCheckRevocationForUntrustedChains(true); // revocation meme hors confiance
        return new TrustContext(v, anchors.getNumberOfCertificates()); // contexte
    }

    /** Contexte construit autour d'un verificateur fourni (tests). */
    public static TrustContext of(CertificateVerifier v) { return new TrustContext(v, 0); } // fabrique de test

    private static CommonTrustedCertificateSource loadAnchors(Path dir) { // lit les .cer, .crt, .pem
        CommonTrustedCertificateSource s = new CommonTrustedCertificateSource(); // source
        if (!Files.isDirectory(dir)) { return s; } // dossier absent
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) { // parcours
            for (Path f : files) { // chaque fichier
                String n = f.getFileName().toString().toLowerCase(Locale.ROOT); // nom en minuscules
                if (!(n.endsWith(".cer") || n.endsWith(".crt") || n.endsWith(".pem"))) { continue; } // extension non retenue
                try (InputStream in = Files.newInputStream(f)) { // lecture
                    CertificateToken t = DSSUtils.loadCertificate(in); // analyse PEM ou DER
                    s.addCertificate(t); // ajout
                } catch (IOException | RuntimeException e) { // fichier invalide
                    LOG.warn("Ancre ignoree {} : {}", f.getFileName(), e.getMessage()); // journal
                }
            }
        } catch (IOException e) { // dossier illisible
            LOG.warn("Dossier des ancres illisible : {}", e.getMessage()); // journal
        }
        return s; // ancres chargees
    }

    public CertificateVerifier verifier() { return verifier; } // verificateur
    public int anchorCount() { return anchorCount; } // nombre d'ancres
}
