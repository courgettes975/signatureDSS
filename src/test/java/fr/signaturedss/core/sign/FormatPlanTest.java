package fr.signaturedss.core.sign; // paquet signature

import eu.europa.esig.dss.enumerations.SignatureLevel; // niveaux DSS
import fr.signaturedss.core.OutputMode; // forme de sortie
import fr.signaturedss.core.SigningLevel; // niveau choisi
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.io.IOException; // erreurs d'E/S
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite

class FormatPlanTest { // tests du choix de format

    @Test // PDF reconnu par son en-tete
    void pdfByHeaderNotExtension(@TempDir Path dir) throws IOException { // en-tete %PDF-
        Path f = Files.writeString(dir.resolve("x.bin"), "%PDF-1.7\n..."); // faux nom, vrai PDF
        FormatPlan p = FormatPlan.resolve(f, SigningLevel.T, OutputMode.CADES_DETACHED); // resolution
        assertEquals(FormatPlan.Kind.PADES, p.kind()); // PAdES
        assertEquals(SignatureLevel.PAdES_BASELINE_T, p.dssLevel()); // niveau T
        assertEquals(dir.resolve("x-signe.bin"), p.output()); // copie signee
    }

    @Test // DXF : CAdES detache par defaut
    void dxfDefaultsToDetached(@TempDir Path dir) throws IOException { // fichier DXF
        Path f = Files.writeString(dir.resolve("plan.dxf"), "0\nSECTION\n"); // contenu DXF
        FormatPlan p = FormatPlan.resolve(f, SigningLevel.LT, OutputMode.CADES_DETACHED); // resolution
        assertEquals(FormatPlan.Kind.CADES_DETACHED, p.kind()); // detache
        assertEquals(SignatureLevel.CAdES_BASELINE_LT, p.dssLevel()); // LT
        assertEquals(dir.resolve("plan.dxf.p7s"), p.output()); // fichier .p7s
    }

    @Test // mode ASiC-E
    void asiceMode(@TempDir Path dir) throws IOException { // conteneur
        Path f = Files.writeString(dir.resolve("son.wav"), "RIFF"); // fichier audio
        FormatPlan p = FormatPlan.resolve(f, SigningLevel.LTA, OutputMode.ASICE); // resolution
        assertEquals(FormatPlan.Kind.ASICE_CADES, p.kind()); // ASiC-E
        assertEquals(SignatureLevel.CAdES_BASELINE_LTA, p.dssLevel()); // LTA
        assertEquals(dir.resolve("son.wav.asice"), p.output()); // fichier .asice
    }

    @Test // sortie existante : suffixe (Review Focus 4)
    void existingOutputGetsSuffix(@TempDir Path dir) throws IOException { // re-signature
        Path f = Files.writeString(dir.resolve("rapport.pdf"), "%PDF-1.4"); // PDF
        Files.writeString(dir.resolve("rapport-signe.pdf"), "deja"); // sortie deja presente
        assertEquals(dir.resolve("rapport-signe-2.pdf"), FormatPlan.resolve(f, SigningLevel.B, OutputMode.CADES_DETACHED).output()); // suffixe -2
        Files.writeString(dir.resolve("rapport-signe-2.pdf"), "deja"); // -2 aussi present
        assertEquals(dir.resolve("rapport-signe-3.pdf"), FormatPlan.resolve(f, SigningLevel.B, OutputMode.CADES_DETACHED).output()); // suffixe -3
    }

    @Test // suffixe sur une signature detachee existante
    void existingP7sGetsSuffix(@TempDir Path dir) throws IOException { // .p7s deja present
        Path f = Files.writeString(dir.resolve("plan.dxf"), "0"); // DXF
        Files.writeString(dir.resolve("plan.dxf.p7s"), "deja"); // signature existante
        assertEquals(dir.resolve("plan.dxf-2.p7s"), FormatPlan.resolve(f, SigningLevel.B, OutputMode.CADES_DETACHED).output()); // suffixe avant l'extension
    }

    @Test // accents conserves (Review Focus 1)
    void accentedNameKept(@TempDir Path dir) throws IOException { // nom accentue
        Path f = Files.writeString(dir.resolve("Plan étage.dxf"), "0"); // fichier accentue
        assertEquals(dir.resolve("Plan étage.dxf.p7s"), FormatPlan.resolve(f, SigningLevel.B, OutputMode.CADES_DETACHED).output()); // nom conserve
    }

    @Test // utilitaire de nom libre partage avec la prolongation
    void freeNameHelper(@TempDir Path dir) throws IOException { // nommage generique
        Files.writeString(dir.resolve("a-prolonge.pdf"), "x"); // nom deja pris
        assertEquals(dir.resolve("a-prolonge-2.pdf"), FormatPlan.freeName(dir.resolve("a-prolonge.pdf"))); // nom libre
    }
}
