package fr.signaturedss.core.validate; // paquet validation

import eu.europa.esig.dss.diagnostic.DiagnosticData; // donnees de diagnostic
import eu.europa.esig.dss.diagnostic.SignatureWrapper; // signature analysee
import eu.europa.esig.dss.diagnostic.TimestampWrapper; // horodatage analyse
import eu.europa.esig.dss.diagnostic.jaxb.XmlDigestMatcher; // reference signee
import eu.europa.esig.dss.model.DSSDocument; // document
import eu.europa.esig.dss.model.FileDocument; // document fichier
import eu.europa.esig.dss.validation.SignedDocumentValidator; // validateur
import eu.europa.esig.dss.validation.reports.Reports; // rapports
import fr.signaturedss.core.trust.TrustContext; // confiance

import java.nio.file.Path; // chemins
import java.util.Date; // date
import java.util.List; // liste
import java.util.Optional; // valeur facultative

/** Valide une signature (tous formats pris en charge par DSS) avec la politique par defaut. */
public class ValidationService { // service de validation

    private final TrustContext trust; // confiance

    public ValidationService(TrustContext trust) { this.trust = trust; } // constructeur

    /** Valide un fichier signe ; les originaux sont requis pour une signature detachee. */
    public ValidationOutcome validate(Path signed, List<Path> originals) { // validation
        SignedDocumentValidator v; // validateur adapte au format
        try { // detection du format
            v = SignedDocumentValidator.fromDocument(new FileDocument(signed.toFile())); // fabrique DSS
        } catch (RuntimeException e) { // format inconnu
            throw new NotSignedException(e); // fichier non signe
        }
        v.setCertificateVerifier(trust.verifier()); // confiance et revocation
        List<DSSDocument> detached = originals.stream().map(p -> (DSSDocument) new FileDocument(p.toFile())).toList(); // originaux
        v.setDetachedContents(detached); // contenus detaches
        Reports r = v.validateDocument(); // validation avec la politique par defaut
        DiagnosticData d = r.getDiagnosticData(); // diagnostic
        if (d.getSignatures().isEmpty()) { throw new NotSignedException(null); } // aucune signature
        if (originals.isEmpty() && missingData(d)) { throw new MissingOriginalException(); } // original requis
        String firstId = r.getSimpleReport().getFirstSignatureId(); // premiere signature
        SignatureWrapper first = d.getSignatureById(firstId); // signature analysee
        return new ValidationOutcome(r, r.getSimpleReport().getIndication(firstId), first.getSignatureFormat(), lastTimestampExpiry(d)); // resultat
    }

    private static boolean missingData(DiagnosticData d) { // une reference signee est introuvable
        for (SignatureWrapper s : d.getSignatures()) { // chaque signature
            for (XmlDigestMatcher m : s.getDigestMatchers()) { // chaque reference
                if (!m.isDataFound()) { return true; } // donnees absentes
            }
        }
        return false; // tout est present
    }

    private static Optional<Date> lastTimestampExpiry(DiagnosticData d) { // fin de validite du dernier horodatage
        Date best = null; // plus tardive
        for (TimestampWrapper t : d.getTimestampList()) { // chaque horodatage
            if (t.getSigningCertificate() == null) { continue; } // certificat inconnu
            Date na = t.getSigningCertificate().getNotAfter(); // fin de validite
            if (best == null || na.after(best)) { best = na; } // maximum
        }
        return Optional.ofNullable(best); // resultat
    }
}
