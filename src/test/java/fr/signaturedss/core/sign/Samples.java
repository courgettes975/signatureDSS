package fr.signaturedss.core.sign; // paquet signature

import org.apache.pdfbox.pdmodel.PDDocument; // document PDF
import org.apache.pdfbox.pdmodel.PDPage; // page PDF

import java.io.IOException; // erreurs d'E/S
import java.nio.charset.StandardCharsets; // ASCII
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.security.MessageDigest; // empreinte
import java.util.HexFormat; // hexadecimal

/** Fichiers d'exemple generes pour les tests (aucun binaire versionne). */
public final class Samples { // utilitaire de test

    private Samples() { } // pas d'instance

    /** PDF d'une page. */
    public static Path pdf(Path dir, String name) throws IOException { // PDF minimal
        Path f = dir.resolve(name); // chemin
        try (PDDocument d = new PDDocument()) { // document vide
            d.addPage(new PDPage()); // une page
            d.save(f.toFile()); // ecriture
        }
        return f; // chemin
    }

    /** DXF texte minimal. */
    public static Path dxf(Path dir, String name) throws IOException { // DXF
        return Files.writeString(dir.resolve(name), "0\nSECTION\n2\nENTITIES\n0\nENDSEC\n0\nEOF\n", StandardCharsets.US_ASCII); // contenu
    }

    /** WAV : en-tete et silence. */
    public static Path wav(Path dir, String name) throws IOException { // audio
        byte[] b = new byte[44 + 8000]; // en-tete + une seconde de silence 8 kHz
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4); // signature RIFF
        System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4); // type WAVE
        return Files.write(dir.resolve(name), b); // ecriture
    }

    /** MP4 : boite ftyp et donnees. */
    public static Path mp4(Path dir, String name) throws IOException { // video
        byte[] b = new byte[4096]; // contenu
        b[3] = 0x18; // taille de la boite
        System.arraycopy("ftypisom".getBytes(StandardCharsets.US_ASCII), 0, b, 4, 8); // type ftyp
        return Files.write(dir.resolve(name), b); // ecriture
    }

    /** Empreinte SHA-256 en hexadecimal. */
    public static String sha256(Path f) throws Exception { // empreinte
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f))); // calcul
    }
}
