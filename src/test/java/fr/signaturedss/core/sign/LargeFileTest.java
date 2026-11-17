package fr.signaturedss.core.sign; // paquet signature

import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.nio.file.Path; // chemins
import java.util.concurrent.TimeUnit; // delai

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class LargeFileTest { // fichiers plus gros que la memoire

    @Test // ASiC-E d'un fichier de 256 Mo avec un tas de 96 Mo
    void asiceLargerThanHeap(@TempDir Path dir) throws Exception { // video volumineuse
        String javaExe = Path.of(System.getProperty("java.home"), "bin", "java").toString(); // executable Java courant
        ProcessBuilder pb = new ProcessBuilder(javaExe, "-Xmx96m", "-cp", System.getProperty("java.class.path"), LargeAsiceChild.class.getName(), dir.toString(), "256"); // JVM a petit tas
        pb.directory(dir.toFile()); // dossier courant temporaire (vidages eventuels de la JVM)
        pb.redirectErrorStream(true); // sortie combinee
        pb.redirectOutput(dir.resolve("enfant.log").toFile()); // journal
        Process p = pb.start(); // lancement
        assertTrue(p.waitFor(5, TimeUnit.MINUTES), "delai depasse"); // fin du processus
        assertEquals(0, p.exitValue(), () -> { try { return java.nio.file.Files.readString(dir.resolve("enfant.log")); } catch (Exception e) { return "?"; } }); // succes
    }
}
