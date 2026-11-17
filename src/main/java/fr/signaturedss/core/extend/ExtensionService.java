package fr.signaturedss.core.extend; // paquet prolongation

import eu.europa.esig.dss.asic.cades.ASiCWithCAdESSignatureParameters; // parametres ASiC
import eu.europa.esig.dss.asic.cades.signature.ASiCWithCAdESService; // service ASiC CAdES
import eu.europa.esig.dss.cades.CAdESSignatureParameters; // parametres CAdES
import eu.europa.esig.dss.cades.signature.CAdESService; // service CAdES
import eu.europa.esig.dss.enumerations.ASiCContainerType; // type de conteneur
import eu.europa.esig.dss.enumerations.SignaturePackaging; // enveloppe
import eu.europa.esig.dss.model.DSSDocument; // document
import eu.europa.esig.dss.model.FileDocument; // document fichier
import eu.europa.esig.dss.pades.PAdESSignatureParameters; // parametres PAdES
import eu.europa.esig.dss.pades.signature.PAdESService; // service PAdES
import eu.europa.esig.dss.spi.x509.tsp.TSPSource; // TSA
import fr.signaturedss.core.SigningLevel; // niveau
import fr.signaturedss.core.sign.FormatPlan; // correspondances et nommage
import fr.signaturedss.core.trust.TrustContext; // confiance
import fr.signaturedss.core.validate.MissingOriginalException; // original manquant
import fr.signaturedss.core.validate.NotSignedException; // fichier non signe
import org.bouncycastle.cms.CMSException; // erreur CMS
import org.bouncycastle.cms.CMSSignedData; // structure CMS

import java.io.IOException; // erreurs d'E/S
import java.io.InputStream; // flux
import java.io.OutputStream; // flux de sortie
import java.io.UncheckedIOException; // erreur non verifiee
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.util.List; // liste

/** Prolonge une signature existante (B vers T, LT, LTA, ou nouvel horodatage d'archive). */
public class ExtensionService { // service de prolongation

    private final TrustContext trust; // confiance
    private final TSPSource tsp; // horodatage

    public ExtensionService(TrustContext trust, TSPSource tsp) { // constructeur
        this.trust = trust; // memorisation
        this.tsp = tsp; // memorisation
    }

    /** Ecrit "<nom>-prolonge.<ext>" a cote de l'entree ; l'entree n'est jamais modifiee. */
    public Path extend(Path signed, List<Path> originals, SigningLevel target) { // prolongation
        DSSDocument doc = new FileDocument(signed.toFile()); // document signe
        DSSDocument extended; // resultat
        if (FormatPlan.isPdf(signed)) { // PDF
            PAdESService s = new PAdESService(trust.verifier()); // service
            s.setTspSource(tsp); // horodatage
            PAdESSignatureParameters p = new PAdESSignatureParameters(); // parametres
            p.setSignatureLevel(FormatPlan.pades(target)); // niveau cible
            extended = s.extendDocument(doc, p); // prolongation
        } else if (isZip(signed)) { // conteneur ASiC
            ASiCWithCAdESService s = new ASiCWithCAdESService(trust.verifier()); // service
            s.setTspSource(tsp); // horodatage
            ASiCWithCAdESSignatureParameters p = new ASiCWithCAdESSignatureParameters(); // parametres
            p.setSignatureLevel(FormatPlan.cades(target)); // niveau cible
            p.aSiC().setContainerType(ASiCContainerType.ASiC_E); // ASiC-E
            extended = s.extendDocument(doc, p); // prolongation
        } else { // CAdES
            boolean detached = isDetachedCms(signed); // signature detachee ?
            if (detached && originals.isEmpty()) { throw new MissingOriginalException(); } // original requis
            CAdESService s = new CAdESService(trust.verifier()); // service
            s.setTspSource(tsp); // horodatage
            CAdESSignatureParameters p = new CAdESSignatureParameters(); // parametres
            p.setSignatureLevel(FormatPlan.cades(target)); // niveau cible
            p.setSignaturePackaging(detached ? SignaturePackaging.DETACHED : SignaturePackaging.ENVELOPING); // enveloppe
            if (detached) { p.setDetachedContents(originals.stream().map(o -> (DSSDocument) new FileDocument(o.toFile())).toList()); } // originaux
            extended = s.extendDocument(doc, p); // prolongation
        }
        Path out = FormatPlan.freeName(signed.resolveSibling(FormatPlan.insertBeforeExtension(signed.getFileName().toString(), "-prolonge"))); // nom libre
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp"); // temporaire
        try { // ecriture protegee
            try (OutputStream os = Files.newOutputStream(tmp)) { extended.writeTo(os); } // ecriture
            Files.move(tmp, out); // publication sans ecrasement
            return out; // fichier prolonge
        } catch (IOException e) { // echec
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { /* nettoyage au mieux */ } // nettoyage
            throw new UncheckedIOException(e); // erreur
        }
    }

    private static boolean isZip(Path f) { // en-tete ZIP "PK"
        try (InputStream in = Files.newInputStream(f)) { // lecture
            byte[] h = in.readNBytes(2); // deux octets
            return h.length == 2 && h[0] == 'P' && h[1] == 'K'; // signature ZIP
        } catch (IOException e) { // lecture impossible
            throw new UncheckedIOException(e); // erreur
        }
    }

    private static boolean isDetachedCms(Path f) { // CMS sans contenu encapsule
        try { // analyse
            return new CMSSignedData(Files.readAllBytes(f)).getSignedContent() == null; // contenu absent
        } catch (CMSException | IOException | RuntimeException e) { // pas un CMS
            throw new NotSignedException(e); // fichier non reconnu
        }
    }
}
