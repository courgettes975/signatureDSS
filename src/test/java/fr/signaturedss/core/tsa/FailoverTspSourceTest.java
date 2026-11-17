package fr.signaturedss.core.tsa; // paquet horodatage

import eu.europa.esig.dss.enumerations.DigestAlgorithm; // algorithme d'empreinte
import eu.europa.esig.dss.model.DSSException; // erreur DSS
import eu.europa.esig.dss.spi.DSSUtils; // utilitaires DSS
import eu.europa.esig.dss.spi.x509.tsp.TSPSource; // interface TSA
import fr.signaturedss.core.TsaConfig; // configuration TSA
import fr.signaturedss.testpki.TestPki; // PKI de test
import org.junit.jupiter.api.Test; // annotation de test

import java.nio.charset.StandardCharsets; // UTF-8
import java.util.List; // liste
import java.util.concurrent.atomic.AtomicInteger; // compteur

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertFalse; // condition fausse
import static org.junit.jupiter.api.Assertions.assertNotNull; // non nul
import static org.junit.jupiter.api.Assertions.assertThrows; // exception attendue
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class FailoverTspSourceTest { // tests de la bascule TSA

    private static final byte[] DIGEST = DSSUtils.digest(DigestAlgorithm.SHA256, "x".getBytes(StandardCharsets.UTF_8)); // empreinte fixe
    private static final TSPSource FAILING = (a, d) -> { throw new DSSException("injoignable"); }; // TSA en panne

    @Test // la seconde TSA prend le relais
    void firstFailingSecondAnswers() { // bascule
        AtomicInteger used = new AtomicInteger(); // compteur d'appels de la seconde
        TSPSource real = TestPki.create().tsp(); // TSA de test
        TSPSource counting = (a, d) -> { used.incrementAndGet(); return real.getTimeStampResponse(a, d); }; // TSA comptee
        FailoverTspSource f = new FailoverTspSource(List.of(new NamedTsp("A", FAILING), new NamedTsp("B", counting))); // chaine
        assertNotNull(f.getTimeStampResponse(DigestAlgorithm.SHA256, DIGEST)); // reponse obtenue
        assertEquals(1, used.get()); // seconde utilisee une fois
    }

    @Test // les TSA en echec avant le repli sont memorisees
    void skippedListsFailedTsas() { // repli silencieux rendu visible
        FailoverTspSource f = new FailoverTspSource(List.of(new NamedTsp("A", FAILING), new NamedTsp("B", TestPki.create().tsp()))); // chaine
        f.getTimeStampResponse(DigestAlgorithm.SHA256, DIGEST); // demande
        assertEquals(List.of("A"), f.skipped()); // A a echoue
    }

    @Test // toutes en panne : liste des services essayes
    void allFailingListsEveryLabel() { // echec total
        FailoverTspSource f = new FailoverTspSource(List.of(new NamedTsp("A", FAILING), new NamedTsp("B", (a, d) -> null))); // deux pannes
        TsaUnavailableException e = assertThrows(TsaUnavailableException.class, () -> f.getTimeStampResponse(DigestAlgorithm.SHA256, DIGEST)); // exception
        assertEquals(List.of("A", "B"), e.tried()); // libelles essayes
        assertTrue(e.getMessage().contains("A")); // message explicite
    }

    @Test // liste vide : echec explicite
    void emptyChainFails() { // aucune TSA configuree
        FailoverTspSource f = new FailoverTspSource(List.of()); // chaine vide
        assertThrows(TsaUnavailableException.class, () -> f.getTimeStampResponse(DigestAlgorithm.SHA256, DIGEST)); // exception
    }

    @Test // test d'une TSA
    void testerReportsHealth() { // bouton Tester
        assertTrue(TsaFactory.test(new NamedTsp("ok", TestPki.create().tsp()))); // TSA saine
        assertFalse(TsaFactory.test(new NamedTsp("ko", FAILING))); // TSA en panne
    }

    @Test // creation depuis la configuration
    void factoryUsesUrlAsLabel() { // libelle
        NamedTsp n = TsaFactory.create(new TsaConfig("https://tsa.example/tsr", null), null); // sans authentification
        assertEquals("https://tsa.example/tsr", n.label()); // libelle = URL
    }
}
