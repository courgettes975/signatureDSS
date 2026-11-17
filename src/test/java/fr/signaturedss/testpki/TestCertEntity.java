package fr.signaturedss.testpki; // paquet de la PKI de test

import eu.europa.esig.dss.enumerations.EncryptionAlgorithm; // algorithme de chiffrement
import eu.europa.esig.dss.model.x509.CertificateToken; // certificat DSS
import eu.europa.esig.dss.pki.model.CertEntity; // entite de certificat DSS

import java.security.PrivateKey; // cle privee
import java.util.List; // liste

/** Certificat de test avec sa cle privee et sa chaine. */
final class TestCertEntity implements CertEntity { // implementation simple

    private static final long serialVersionUID = 1L; // version de serialisation

    private final PrivateKey key; // cle privee
    private final CertificateToken cert; // certificat
    private final List<CertificateToken> chain; // chaine complete (certificat en premier)
    private final TestCertEntity issuer; // emetteur (null pour la racine)

    TestCertEntity(PrivateKey key, CertificateToken cert, List<CertificateToken> chain, TestCertEntity issuer) { // constructeur
        this.key = key; // memorisation de la cle
        this.cert = cert; // memorisation du certificat
        this.chain = List.copyOf(chain); // copie immuable de la chaine
        this.issuer = issuer; // memorisation de l'emetteur
    }

    @Override public PrivateKey getPrivateKey() { return key; } // cle privee
    @Override public List<CertificateToken> getCertificateChain() { return chain; } // chaine
    @Override public CertificateToken getCertificateToken() { return cert; } // certificat
    @Override public EncryptionAlgorithm getEncryptionAlgorithm() { return EncryptionAlgorithm.RSA; } // RSA
    TestCertEntity issuer() { return issuer; } // emetteur
}
