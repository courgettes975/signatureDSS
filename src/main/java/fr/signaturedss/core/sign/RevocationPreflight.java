package fr.signaturedss.core.sign; // paquet signature

import eu.europa.esig.dss.enumerations.DigestAlgorithm; // empreinte
import eu.europa.esig.dss.enumerations.TimestampType; // type d'horodatage
import eu.europa.esig.dss.model.TimestampBinary; // jeton brut
import eu.europa.esig.dss.model.x509.CertificateToken; // certificat
import eu.europa.esig.dss.spi.DSSUtils; // utilitaires DSS
import eu.europa.esig.dss.spi.validation.CertificateVerifier; // verificateur
import eu.europa.esig.dss.spi.x509.aia.AIASource; // completion de chaine
import eu.europa.esig.dss.spi.x509.revocation.RevocationSource; // source de revocation
import eu.europa.esig.dss.spi.x509.tsp.TSPSource; // TSA
import eu.europa.esig.dss.spi.x509.tsp.TimestampToken; // jeton analyse

import java.nio.charset.StandardCharsets; // UTF-8
import java.util.ArrayList; // liste modifiable
import java.util.LinkedHashSet; // ensemble ordonne
import java.util.List; // liste
import java.util.Set; // ensemble

/** Verifie, avant de signer en LT ou LTA, que la revocation est obtenable pour les chaines du signataire et de la TSA. */
public final class RevocationPreflight { // utilitaire statique

    /**
     * Resultat du controle.
     *
     * @param revocationAvailable vrai si chaque certificat non racine a une reponse OCSP ou une CRL
     * @param missingSubjects sujets des certificats sans revocation
     */
    public record PreflightResult(boolean revocationAvailable, List<String> missingSubjects) { } // enregistrement

    private RevocationPreflight() { } // pas d'instance

    /** Controle la revocation pour la chaine du signataire et celle de la TSA (obtenue par un jeton d'essai). */
    public static PreflightResult check(List<CertificateToken> signerChain, TSPSource tsp, CertificateVerifier v) { // controle
        List<String> missing = new ArrayList<>(); // certificats sans revocation
        checkChain(signerChain, v, missing); // chaine du signataire
        checkChain(tsaChain(tsp, v), v, missing); // chaine de la TSA
        return new PreflightResult(missing.isEmpty(), List.copyOf(missing)); // resultat
    }

    private static void checkChain(List<CertificateToken> chain, CertificateVerifier v, List<String> missing) { // parcours d'une chaine
        for (int i = 0; i < chain.size(); i++) { // chaque certificat
            CertificateToken c = chain.get(i); // certificat courant
            if (c.isSelfSigned()) { continue; } // racine : pas de revocation
            CertificateToken issuer = issuerOf(c, chain, v.getAIASource()); // emetteur
            String subject = c.getSubject().getRFC2253(); // sujet lisible
            if (issuer == null || !(answers(v.getOcspSource(), c, issuer) || answers(v.getCrlSource(), c, issuer))) { // aucune reponse
                if (!missing.contains(subject)) { missing.add(subject); } // ajout sans doublon
            }
        }
    }

    private static boolean answers(RevocationSource<?> src, CertificateToken c, CertificateToken issuer) { // la source repond-elle
        if (src == null) { return false; } // pas de source
        try { return src.getRevocationToken(c, issuer) != null; } catch (RuntimeException e) { return false; } // reponse ou panne
    }

    private static CertificateToken issuerOf(CertificateToken c, List<CertificateToken> chain, AIASource aia) { // trouve l'emetteur
        for (CertificateToken cand : chain) { if (!cand.equals(c) && c.isSignedBy(cand)) { return cand; } } // dans la chaine
        if (aia == null) { return null; } // pas d'AIA
        try { // via AIA
            for (CertificateToken cand : aia.getCertificatesByAIA(c)) { if (c.isSignedBy(cand)) { return cand; } } // emetteur telecharge
        } catch (RuntimeException ignored) { /* AIA injoignable */ } // panne
        return null; // introuvable
    }

    private static List<CertificateToken> tsaChain(TSPSource tsp, CertificateVerifier v) { // chaine de la TSA
        try { // jeton d'essai
            byte[] digest = DSSUtils.digest(DigestAlgorithm.SHA256, "SignatureDSS-preflight".getBytes(StandardCharsets.UTF_8)); // empreinte fixe
            TimestampBinary b = tsp.getTimeStampResponse(DigestAlgorithm.SHA256, digest); // demande
            TimestampToken t = new TimestampToken(b.getBytes(), TimestampType.SIGNATURE_TIMESTAMP); // analyse
            Set<CertificateToken> chain = new LinkedHashSet<>(t.getCertificates()); // certificats fournis
            for (CertificateToken c : List.copyOf(chain)) { // completion
                if (!c.isSelfSigned() && issuerOf(c, List.copyOf(chain), null) == null) { // emetteur absent
                    CertificateToken i = issuerOf(c, List.of(), v.getAIASource()); // via AIA
                    if (i != null) { chain.add(i); } // ajout
                }
            }
            return List.copyOf(chain); // chaine TSA
        } catch (Exception e) { // TSA injoignable : l'erreur sera signalee a la signature
            return List.of(); // rien a controler
        }
    }
}
