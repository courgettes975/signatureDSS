package fr.signaturedss.core.sign; // paquet signature

import eu.europa.esig.dss.asic.cades.ASiCWithCAdESSignatureParameters; // parametres ASiC-E
import eu.europa.esig.dss.asic.cades.signature.ASiCWithCAdESService; // service ASiC CAdES
import eu.europa.esig.dss.cades.CAdESSignatureParameters; // parametres CAdES
import eu.europa.esig.dss.cades.signature.CAdESService; // service CAdES
import eu.europa.esig.dss.enumerations.ASiCContainerType; // type de conteneur
import eu.europa.esig.dss.enumerations.DigestAlgorithm; // algorithme d'empreinte
import eu.europa.esig.dss.enumerations.SignaturePackaging; // forme d'enveloppe
import eu.europa.esig.dss.model.DSSDocument; // document DSS
import eu.europa.esig.dss.model.FileDocument; // document fichier (lu en flux)
import eu.europa.esig.dss.model.SignatureValue; // valeur de signature
import eu.europa.esig.dss.model.ToBeSigned; // donnees a signer
import eu.europa.esig.dss.pades.PAdESSignatureParameters; // parametres PAdES
import eu.europa.esig.dss.pades.SignatureFieldParameters; // position du champ
import eu.europa.esig.dss.pades.SignatureImageParameters; // apparence
import eu.europa.esig.dss.pades.SignatureImageTextParameters; // texte de l'apparence
import eu.europa.esig.dss.pades.signature.PAdESService; // service PAdES
import eu.europa.esig.dss.spi.DSSASN1Utils; // utilitaires ASN.1
import eu.europa.esig.dss.spi.x509.tsp.TSPSource; // TSA
import eu.europa.esig.dss.token.DSSPrivateKeyEntry; // cle privee
import eu.europa.esig.dss.token.SignatureTokenConnection; // jeton ouvert
import fr.signaturedss.core.trust.TrustContext; // confiance
import fr.signaturedss.i18n.Messages; // textes traduits
import org.apache.pdfbox.Loader; // chargeur PDF
import org.apache.pdfbox.pdmodel.PDDocument; // document PDF
import org.apache.pdfbox.pdmodel.common.PDRectangle; // dimensions de page

import java.io.IOException; // erreurs d'E/S
import java.io.OutputStream; // flux de sortie
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.text.SimpleDateFormat; // format de date
import java.util.ArrayList; // liste modifiable
import java.util.Date; // date
import java.util.List; // liste

/** Signe des fichiers en PAdES, CAdES detache ou ASiC-E, aux niveaux B, T, LT, LTA. */
public class SigningService { // service de signature

    private static final DigestAlgorithm DIGEST = DigestAlgorithm.SHA256; // empreinte SHA-256
    private final TrustContext trust; // confiance et revocation
    private final TSPSource tsp; // horodatage

    public SigningService(TrustContext trust, TSPSource tsp) { // constructeur
        this.trust = trust; // memorisation
        this.tsp = tsp; // memorisation
    }

    /** Signe chaque fichier separement avec le jeton deja ouvert (un seul PIN par lot). */
    public List<SignResult> sign(SignRequest req, SignatureTokenConnection token) { // signature d'un lot
        List<SignResult> out = new ArrayList<>(); // resultats
        for (Path in : req.files()) { // chaque fichier
            FormatPlan plan = FormatPlan.resolve(in, req.level(), req.mode()); // format et sortie
            Path tmp = plan.output().resolveSibling(plan.output().getFileName() + ".tmp"); // fichier temporaire
            try { // signature protegee
                DSSDocument signed = signOne(in, plan, req, token); // signature DSS
                try (OutputStream os = Files.newOutputStream(tmp)) { signed.writeTo(os); } // ecriture temporaire
                Files.move(tmp, plan.output()); // publication ; echoue si le fichier existe (jamais d'ecrasement)
                out.add(new SignResult(in, plan.output(), plan)); // resultat
            } catch (IOException | RuntimeException e) { // echec
                deleteQuietly(tmp); // pas de fichier partiel ; la sortie n'est creee qu'au deplacement final
                throw new SigningException(in, e, out); // erreur explicite, avec les fichiers deja signes
            }
        }
        return out; // lot signe
    }

    private DSSDocument signOne(Path in, FormatPlan plan, SignRequest req, SignatureTokenConnection token) throws IOException { // signature d'un fichier
        DSSDocument doc = new FileDocument(in.toFile()); // lecture en flux
        DSSPrivateKeyEntry key = req.key(); // cle
        return switch (plan.kind()) { // selon le format
            case PADES -> { // PDF
                PAdESService s = new PAdESService(trust.verifier()); // service
                s.setTspSource(tsp); // horodatage
                PAdESSignatureParameters p = new PAdESSignatureParameters(); // parametres
                common(p, plan, key); // parametres communs
                if (req.pdfVisible()) { p.setImageParameters(appearance(in, key)); } // apparence visible
                ToBeSigned tbs = s.getDataToSign(doc, p); // donnees a signer
                SignatureValue sv = token.sign(tbs, DIGEST, key); // signature par le jeton
                yield s.signDocument(doc, p, sv); // document signe
            }
            case CADES_DETACHED -> { // signature detachee
                CAdESService s = new CAdESService(trust.verifier()); // service
                s.setTspSource(tsp); // horodatage
                CAdESSignatureParameters p = new CAdESSignatureParameters(); // parametres
                common(p, plan, key); // parametres communs
                p.setSignaturePackaging(SignaturePackaging.DETACHED); // detache
                ToBeSigned tbs = s.getDataToSign(doc, p); // donnees a signer
                SignatureValue sv = token.sign(tbs, DIGEST, key); // signature par le jeton
                yield s.signDocument(doc, p, sv); // signature .p7s
            }
            case ASICE_CADES -> { // conteneur
                ASiCWithCAdESService s = new ASiCWithCAdESService(trust.verifier()); // service
                s.setTspSource(tsp); // horodatage
                ASiCWithCAdESSignatureParameters p = new ASiCWithCAdESSignatureParameters(); // parametres
                common(p, plan, key); // parametres communs
                p.aSiC().setContainerType(ASiCContainerType.ASiC_E); // ASiC-E
                ToBeSigned tbs = s.getDataToSign(doc, p); // donnees a signer
                SignatureValue sv = token.sign(tbs, DIGEST, key); // signature par le jeton
                yield s.signDocument(doc, p, sv); // conteneur .asice
            }
        };
    }

    private static void common(eu.europa.esig.dss.signature.AbstractSignatureParameters<?> p, FormatPlan plan, DSSPrivateKeyEntry key) { // parametres partages
        p.setSignatureLevel(plan.dssLevel()); // niveau
        p.setDigestAlgorithm(DIGEST); // SHA-256
        p.setSigningCertificate(key.getCertificate()); // certificat du signataire
        p.setCertificateChain(key.getCertificateChain()); // chaine
    }

    private static SignatureImageParameters appearance(Path pdf, DSSPrivateKeyEntry key) throws IOException { // apparence visible
        int pages; // nombre de pages
        PDRectangle box; // taille de la derniere page
        try (PDDocument d = Loader.loadPDF(pdf.toFile())) { // lecture
            pages = d.getNumberOfPages(); // pages
            box = d.getPage(pages - 1).getMediaBox(); // derniere page
        }
        SignatureFieldParameters f = new SignatureFieldParameters(); // champ
        f.setPage(pages); // derniere page (numerotation a partir de 1)
        f.setWidth(220); // largeur
        f.setHeight(60); // hauteur
        f.setOriginX(Math.max(0, box.getWidth() - 240)); // en bas a droite
        f.setOriginY(Math.max(0, box.getHeight() - 80)); // origine en haut a gauche chez DSS
        SignatureImageTextParameters t = new SignatureImageTextParameters(); // texte
        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()); // date lisible
        t.setText(Messages.get("pdf.apparence", DSSASN1Utils.getSubjectCommonName(key.getCertificate()), date)); // "Signe par X le D"
        SignatureImageParameters img = new SignatureImageParameters(); // apparence
        img.setFieldParameters(f); // position
        img.setTextParameters(t); // texte
        return img; // apparence
    }

    private static void deleteQuietly(Path p) { // suppression silencieuse
        try { Files.deleteIfExists(p); } catch (IOException ignored) { /* nettoyage au mieux */ } // suppression
    }
}
