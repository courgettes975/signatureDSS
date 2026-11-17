package fr.signaturedss.core.sign; // paquet signature

import eu.europa.esig.dss.token.Pkcs12SignatureToken; // jeton
import fr.signaturedss.core.OutputMode; // forme
import fr.signaturedss.core.SigningLevel; // niveau
import fr.signaturedss.core.trust.TrustContext; // confiance
import fr.signaturedss.testpki.TestPki; // PKI de test

import java.io.OutputStream; // flux de sortie
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.util.List; // liste

/** Programme lance dans une JVM a petit tas : signe un gros fichier en ASiC-E. Code de sortie 0 si reussi. */
public final class LargeAsiceChild { // processus enfant de test

    private LargeAsiceChild() { } // pas d'instance

    public static void main(String[] args) throws Exception { // arguments : dossier, taille en Mo
        Path dir = Path.of(args[0]); // dossier de travail
        long mb = Long.parseLong(args[1]); // taille du fichier
        LargeFiles.configure(dir.resolve("tmp")); // fichiers temporaires plutot que memoire
        Path big = dir.resolve("video.mp4"); // gros fichier
        byte[] block = new byte[1 << 20]; // bloc de 1 Mo
        try (OutputStream os = Files.newOutputStream(big)) { java.util.Random rnd = new java.util.Random(42); for (long i = 0; i < mb; i++) { rnd.nextBytes(block); os.write(block); } } // ecriture (octets aleatoires, incompressibles)
        TestPki pki = TestPki.create(); // PKI de test
        try (Pkcs12SignatureToken t = pki.signerToken()) { // jeton
            List<SignResult> r = new SigningService(TrustContext.of(pki.verifier()), pki.tsp()).sign(new SignRequest(List.of(big), SigningLevel.B, OutputMode.ASICE, false, t.getKeys().get(0)), t); // signature
            System.exit(Files.size(r.get(0).output()) > mb * (1 << 20) ? 0 : 3); // conteneur complet
        }
    }
}
