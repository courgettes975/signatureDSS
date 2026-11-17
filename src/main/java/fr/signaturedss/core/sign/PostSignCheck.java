package fr.signaturedss.core.sign; // paquet signature

import eu.europa.esig.dss.enumerations.Indication; // verdict
import eu.europa.esig.dss.enumerations.SignatureLevel; // niveau DSS
import eu.europa.esig.dss.enumerations.SubIndication; // detail du verdict
import fr.signaturedss.core.validate.ValidationOutcome; // resultat de validation
import fr.signaturedss.core.validate.ValidationService; // validation
import fr.signaturedss.i18n.Messages; // textes traduits

import java.nio.file.Path; // chemins
import java.util.ArrayList; // liste modifiable
import java.util.List; // liste

/** Controle apres signature : niveau reellement atteint et verdict. */
public class PostSignCheck { // controleur

    /**
     * Resultat du controle.
     *
     * @param levelReached faux si le niveau detecte est inferieur au niveau demande (erreur)
     * @param found niveau detecte
     * @param verdict verdict DSS
     * @param warnings avertissements (verdict non valide, par exemple)
     */
    public record PostSignOutcome(boolean levelReached, SignatureLevel found, Indication verdict, List<String> warnings) { } // enregistrement

    private final ValidationService validation; // validation

    public PostSignCheck(ValidationService validation) { this.validation = validation; } // constructeur

    /** Valide la sortie et compare au niveau demande. */
    public PostSignOutcome check(Path output, List<Path> originals, SignatureLevel requested) { // controle
        ValidationOutcome o = validation.validate(output, originals); // validation
        List<String> warnings = new ArrayList<>(); // avertissements
        if (o.verdict() != Indication.TOTAL_PASSED) { // verdict non valide
            String firstId = o.reports().getSimpleReport().getFirstSignatureId(); // signature
            SubIndication sub = o.reports().getSimpleReport().getSubIndication(firstId); // detail
            warnings.add(Messages.get("avert.verdict", o.verdict(), sub == null ? "-" : sub)); // avertissement
        }
        return new PostSignOutcome(reached(o.level(), requested), o.level(), o.verdict(), List.copyOf(warnings)); // resultat
    }

    /** Vrai si "found" est de la meme famille et d'un rang au moins egal. */
    static boolean reached(SignatureLevel found, SignatureLevel requested) { // comparaison de niveaux
        if (found == null) { return false; } // rien detecte
        String f = found.name(); // nom detecte
        String r = requested.name(); // nom demande
        String famF = f.substring(0, f.indexOf('_')); // famille detectee (PAdES, CAdES...)
        String famR = r.substring(0, r.indexOf('_')); // famille demandee
        return famF.equals(famR) && rank(f) >= rank(r); // meme famille, rang suffisant
    }

    private static int rank(String level) { // rang du niveau
        if (level.endsWith("_LTA")) { return 4; } // LTA
        if (level.endsWith("_LT")) { return 3; } // LT
        if (level.endsWith("_T")) { return 2; } // T
        if (level.endsWith("_B")) { return 1; } // B
        return 0; // autre (non baseline)
    }
}
