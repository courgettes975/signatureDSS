package fr.signaturedss.core.trust; // paquet confiance

import eu.europa.esig.dss.spi.DSSUtils; // utilitaires DSS
import fr.signaturedss.core.PortablePaths; // chemins portables
import fr.signaturedss.testpki.TestPki; // PKI de test
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.io.IOException; // erreurs d'E/S
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow; // absence d'exception
import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertNotNull; // non nul
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class TrustContextTest { // tests de la confiance

    @Test // ancre PEM chargee depuis le dossier
    void userAnchorsLoadedFromFolder(@TempDir Path dir) throws IOException { // ancres utilisateur
        PortablePaths paths = PortablePaths.fromAppLocation(dir); // chemins
        Files.writeString(paths.anchorsDir().resolve("racine.pem"), DSSUtils.convertToPEM(TestPki.create().root())); // ancre PEM
        Files.writeString(paths.anchorsDir().resolve("lisezmoi.txt"), "ignore"); // fichier ignore
        TrustContext t = TrustContext.online(paths, new LotlService(paths)); // contexte reel
        assertEquals(1, t.anchorCount()); // une ancre
        assertNotNull(t.verifier()); // verificateur pret
    }

    @Test // ancre illisible ignoree sans planter
    void corruptAnchorIgnored(@TempDir Path dir) throws IOException { // fichier corrompu
        PortablePaths paths = PortablePaths.fromAppLocation(dir); // chemins
        Files.writeString(paths.anchorsDir().resolve("casse.cer"), "pas un certificat"); // contenu invalide
        assertEquals(0, TrustContext.online(paths, new LotlService(paths)).anchorCount()); // aucune ancre
    }

    @Test // rafraichissement hors ligne avec cache vide
    void offlineRefreshWithEmptyCacheDoesNotThrow(@TempDir Path dir) { // cache vide
        LotlService lotl = new LotlService(PortablePaths.fromAppLocation(dir)); // service LOTL
        assertDoesNotThrow(lotl::refreshOffline); // pas d'exception
        assertTrue(lotl.certificateSource().getNumberOfCertificates() >= 0); // source utilisable
    }

    @Test // magasin du Journal officiel lisible
    void officialJournalKeystoreLoads() { // ressource embarquee
        assertTrue(!LotlService.officialJournalKeyStore().getCertificates().isEmpty()); // certificats presents
    }
}
