package fr.signaturedss.ui.vm; // paquet des modeles de vue

import eu.europa.esig.dss.model.x509.CertificateToken; // certificat
import eu.europa.esig.dss.spi.x509.tsp.TSPSource; // TSA
import eu.europa.esig.dss.token.DSSPrivateKeyEntry; // cle privee
import eu.europa.esig.dss.token.SignatureTokenConnection; // jeton ouvert
import fr.signaturedss.core.OutputMode; // forme
import fr.signaturedss.core.SigningLevel; // niveau
import fr.signaturedss.core.sign.PostSignCheck; // controle apres signature
import fr.signaturedss.core.sign.RevocationPreflight; // controle avant signature
import fr.signaturedss.core.sign.SignRequest; // requete
import fr.signaturedss.core.sign.SignResult; // resultat
import fr.signaturedss.core.sign.SigningException; // echec de signature
import fr.signaturedss.core.tsa.FailoverTspSource; // bascule TSA
import fr.signaturedss.core.sign.SigningService; // signature
import fr.signaturedss.core.token.TokenProvider; // ouverture du jeton
import fr.signaturedss.core.token.TokenSource; // source du certificat
import fr.signaturedss.core.trust.TrustContext; // confiance
import fr.signaturedss.core.validate.ValidationService; // validation
import fr.signaturedss.i18n.Messages; // textes traduits
import javafx.beans.property.BooleanProperty; // propriete booleenne
import javafx.beans.property.ObjectProperty; // propriete objet
import javafx.beans.property.SimpleBooleanProperty; // implementation
import javafx.beans.property.SimpleObjectProperty; // implementation
import javafx.collections.FXCollections; // listes observables
import javafx.collections.ObservableList; // liste observable

import java.nio.file.Path; // chemins
import java.util.ArrayList; // liste modifiable
import java.util.Arrays; // tableaux
import java.util.List; // liste
import java.util.Optional; // valeur facultative
import java.util.function.Supplier; // fournisseur

/** Modele de l'onglet Signer, sans dependance a l'affichage (testable sans ecran). */
public class SignViewModel { // modele de vue

    /** Choix de la cle quand le jeton en contient plusieurs. */
    @FunctionalInterface public interface KeyChooser { DSSPrivateKeyEntry choose(List<DSSPrivateKeyEntry> keys); } // choix

    /** Decision quand la revocation manque : niveau de repli (T) ou vide pour annuler. */
    @FunctionalInterface public interface DowngradeDecider { Optional<SigningLevel> decide(List<String> missingSubjects); } // decision

    /**
     * Resultat d'une execution.
     *
     * @param results fichiers signes
     * @param errors erreurs (niveau non atteint)
     * @param warnings avertissements (verdict non valide)
     * @param cancelled annule par l'utilisateur
     * @param usedLevel niveau effectivement utilise
     * @param failedTsas TSA qui ont echoue (leur mot de passe de session doit etre oublie)
     */
    public record SignOutcome(List<SignResult> results, List<String> errors, List<String> warnings, boolean cancelled, SigningLevel usedLevel, List<String> failedTsas) { } // enregistrement

    private final ObservableList<Path> files = FXCollections.observableArrayList(); // fichiers a signer
    private final ObjectProperty<SigningLevel> level = new SimpleObjectProperty<>(SigningLevel.LT); // niveau, LT par defaut
    private final ObjectProperty<OutputMode> mode = new SimpleObjectProperty<>(OutputMode.CADES_DETACHED); // forme par defaut
    private final BooleanProperty pdfVisible = new SimpleBooleanProperty(false); // apparence desactivee par defaut
    private final Supplier<TrustContext> trust; // confiance (construite a la demande)
    private final Supplier<TSPSource> tsp; // TSA (construite a la demande)
    private final TokenProvider tokens; // ouverture des jetons

    public SignViewModel(Supplier<TrustContext> trust, Supplier<TSPSource> tsp) { this(trust, tsp, new TokenProvider()); } // constructeur standard

    public SignViewModel(Supplier<TrustContext> trust, Supplier<TSPSource> tsp, TokenProvider tokens) { // constructeur complet
        this.trust = trust; // memorisation
        this.tsp = tsp; // memorisation
        this.tokens = tokens; // memorisation
    }

    /** Ouvre le jeton, controle, signe et verifie. A appeler hors du fil graphique. Le secret est efface. */
    public SignOutcome run(TokenSource src, char[] secret, KeyChooser chooser, DowngradeDecider decider) { // execution
        TrustContext t; // confiance
        TSPSource ts; // TSA
        try { // preparation
            t = trust.get(); // confiance
            ts = tsp.get(); // TSA
        } catch (RuntimeException e) { // echec avant l'ouverture du jeton
            if (secret != null) { Arrays.fill(secret, '\0'); } // effacement du secret malgre tout
            throw e; // propagation
        }
        try (SignatureTokenConnection token = tokens.open(src, secret)) { // ouverture (une tentative, secret efface)
            List<DSSPrivateKeyEntry> keys = token.getKeys(); // cles disponibles
            DSSPrivateKeyEntry key = keys.size() == 1 ? keys.get(0) : chooser.choose(keys); // choix
            if (key == null) { return new SignOutcome(List.of(), List.of(), List.of(), true, level.get(), List.of()); } // choix annule
            SigningLevel used = level.get(); // niveau demande
            if (used == SigningLevel.LT || used == SigningLevel.LTA) { // revocation necessaire
                List<CertificateToken> chain = Arrays.asList(key.getCertificateChain()); // chaine
                RevocationPreflight.PreflightResult pre = RevocationPreflight.check(chain, ts, t.verifier()); // controle
                if (!pre.revocationAvailable()) { // revocation manquante
                    Optional<SigningLevel> d = decider.decide(pre.missingSubjects()); // question a l'utilisateur
                    if (d.isEmpty()) { return new SignOutcome(List.of(), List.of(), List.of(), true, used, failedTsas(ts)); } // annulation
                    used = d.get(); // niveau de repli
                }
            }
            List<String> errors = new ArrayList<>(); // erreurs
            List<String> warnings = new ArrayList<>(); // avertissements
            List<SignResult> results; // fichiers signes
            try { // signature du lot
                results = new SigningService(t, ts).sign(new SignRequest(List.copyOf(files), used, mode.get(), pdfVisible.get(), key), token); // signature
            } catch (SigningException e) { // lot interrompu
                results = e.completed(); // fichiers signes avant l'echec, controles ci-dessous
                errors.add(e.getMessage()); // fichier en cause et raison
            }
            List<String> failed = failedTsas(ts); // TSA en echec
            if (!failed.isEmpty() && !results.isEmpty()) { warnings.add(Messages.get("avert.tsa.repli", String.join(", ", failed))); } // repli signale
            PostSignCheck check = new PostSignCheck(new ValidationService(t)); // controleur
            for (SignResult r : results) { // chaque fichier
                List<Path> originals = r.plan().kind() == fr.signaturedss.core.sign.FormatPlan.Kind.CADES_DETACHED ? List.of(r.input()) : List.of(); // original si detache
                PostSignCheck.PostSignOutcome o = check.check(r.output(), originals, r.plan().dssLevel()); // controle
                String name = r.output().getFileName().toString(); // nom affiche
                if (!o.levelReached()) { errors.add(Messages.get("erreur.niveau.non.atteint", name, r.plan().dssLevel(), o.found())); } // niveau manque
                for (String w : o.warnings()) { warnings.add(name + " : " + w); } // avertissements
            }
            return new SignOutcome(List.copyOf(results), List.copyOf(errors), List.copyOf(warnings), false, used, failed); // resultat
        }
    }

    private static List<String> failedTsas(TSPSource ts) { return ts instanceof FailoverTspSource f ? f.skipped() : List.of(); } // TSA en echec

    public ObservableList<Path> files() { return files; } // fichiers
    public ObjectProperty<SigningLevel> level() { return level; } // niveau
    public ObjectProperty<OutputMode> mode() { return mode; } // forme
    public BooleanProperty pdfVisible() { return pdfVisible; } // apparence
}
