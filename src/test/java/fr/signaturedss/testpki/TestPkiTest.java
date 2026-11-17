package fr.signaturedss.testpki; // paquet de la PKI de test

import eu.europa.esig.dss.enumerations.DigestAlgorithm; // algorithme d'empreinte
import eu.europa.esig.dss.model.x509.CertificateToken; // certificat DSS
import eu.europa.esig.dss.spi.DSSUtils; // utilitaires DSS
import org.junit.jupiter.api.Test; // annotation de test

import java.nio.charset.StandardCharsets; // UTF-8

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertNotNull; // non nul
import static org.junit.jupiter.api.Assertions.assertNull; // nul

class TestPkiTest { // tests de la PKI de test

    @Test // chaine du signataire et TSA operationnelles
    void signerChainBuildsAndTsaAnswers() { // test principal
        TestPki pki = TestPki.create(); // generation de la PKI
        CertificateToken[] chain = pki.signerToken().getKeys().get(0).getCertificateChain(); // chaine du signataire
        assertEquals(2, chain.length); // signataire + racine
        assertEquals(pki.root(), chain[chain.length - 1]); // termine par la racine
        byte[] digest = DSSUtils.digest(DigestAlgorithm.SHA256, "x".getBytes(StandardCharsets.UTF_8)); // empreinte de test
        assertNotNull(pki.tsp().getTimeStampResponse(DigestAlgorithm.SHA256, digest)); // jeton d'horodatage obtenu
    }

    @Test // OCSP et CRL repondent pour le signataire
    void revocationSourcesAnswer() { // test des sources de revocation
        TestPki pki = TestPki.create(); // generation
        assertNotNull(pki.ocsp().getRevocationToken(pki.signer(), pki.root())); // reponse OCSP
        assertNotNull(pki.crl().getRevocationToken(pki.signer(), pki.root())); // CRL
    }

    @Test // variante sans revocation pour un certificat
    void withoutRevocationHidesCert() { // test de la variante
        TestPki pki = TestPki.create().withoutRevocationFor(TestPki.create().signer()); // certificat sans rapport : rien de masque
        assertNotNull(pki.ocsp().getRevocationToken(pki.signer(), pki.root())); // toujours disponible
        TestPki hidden = pki.withoutRevocationFor(pki.signer()); // masque le signataire
        assertNull(hidden.ocsp().getRevocationToken(hidden.signer(), hidden.root())); // OCSP muet
        assertNull(hidden.crl().getRevocationToken(hidden.signer(), hidden.root())); // CRL muette
    }
}
