package fr.signaturedss.ui.vm; // paquet des modeles de vue

import fr.signaturedss.core.trust.TrustContext; // confiance
import fr.signaturedss.core.validate.MissingOriginalException; // original manquant
import fr.signaturedss.core.validate.ValidationOutcome; // resultat
import fr.signaturedss.core.validate.ValidationService; // validation
import javafx.beans.property.BooleanProperty; // propriete booleenne
import javafx.beans.property.ObjectProperty; // propriete objet
import javafx.beans.property.SimpleBooleanProperty; // implementation
import javafx.beans.property.SimpleObjectProperty; // implementation
import javafx.beans.property.SimpleStringProperty; // implementation
import javafx.beans.property.StringProperty; // propriete texte
import javafx.collections.FXCollections; // listes observables
import javafx.collections.ObservableList; // liste observable

import java.nio.file.Path; // chemins
import java.util.List; // liste
import java.util.function.Supplier; // fournisseur

/** Modele de l'onglet Valider. */
public class ValidateViewModel { // modele de vue

    private final ObjectProperty<Path> signedFile = new SimpleObjectProperty<>(); // fichier signe
    private final ObservableList<Path> originals = FXCollections.observableArrayList(); // originaux (signature detachee)
    private final ObjectProperty<ValidationOutcome> outcome = new SimpleObjectProperty<>(); // resultat
    private final BooleanProperty needsOriginal = new SimpleBooleanProperty(false); // original demande
    private final StringProperty error = new SimpleStringProperty(""); // message d'erreur
    private final Supplier<TrustContext> trust; // confiance

    public ValidateViewModel(Supplier<TrustContext> trust) { this.trust = trust; } // constructeur

    /** Valide le fichier ; met a jour le resultat, l'indicateur d'original manquant ou l'erreur. */
    public void validate() { // validation (hors fil graphique)
        outcome.set(null); // remise a zero
        needsOriginal.set(false); // remise a zero
        error.set(""); // remise a zero
        try { // validation protegee
            outcome.set(new ValidationService(trust.get()).validate(signedFile.get(), List.copyOf(originals))); // resultat
        } catch (MissingOriginalException e) { // original requis
            needsOriginal.set(true); // indicateur
            error.set(e.getMessage()); // explication
        } catch (RuntimeException e) { // autre erreur
            error.set(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); // message
        }
    }

    public ObjectProperty<Path> signedFile() { return signedFile; } // fichier signe
    public ObservableList<Path> originals() { return originals; } // originaux
    public ObjectProperty<ValidationOutcome> outcome() { return outcome; } // resultat
    public BooleanProperty needsOriginal() { return needsOriginal; } // indicateur
    public StringProperty error() { return error; } // erreur
}
