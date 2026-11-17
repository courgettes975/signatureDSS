package fr.signaturedss.ui.vm; // paquet des modeles de vue

import eu.europa.esig.dss.spi.x509.tsp.TSPSource; // TSA
import fr.signaturedss.core.SigningLevel; // niveau
import fr.signaturedss.core.extend.ExtensionService; // prolongation
import fr.signaturedss.core.trust.TrustContext; // confiance
import javafx.beans.property.ObjectProperty; // propriete objet
import javafx.beans.property.SimpleObjectProperty; // implementation
import javafx.collections.FXCollections; // listes observables
import javafx.collections.ObservableList; // liste observable

import java.nio.file.Path; // chemins
import java.util.List; // liste
import java.util.function.Supplier; // fournisseur

/** Modele de l'onglet Prolonger. */
public class ExtendViewModel { // modele de vue

    private final ObjectProperty<Path> signedFile = new SimpleObjectProperty<>(); // fichier signe
    private final ObservableList<Path> originals = FXCollections.observableArrayList(); // originaux
    private final ObjectProperty<SigningLevel> target = new SimpleObjectProperty<>(SigningLevel.LTA); // niveau vise
    private final Supplier<TrustContext> trust; // confiance
    private final Supplier<TSPSource> tsp; // TSA

    public ExtendViewModel(Supplier<TrustContext> trust, Supplier<TSPSource> tsp) { // constructeur
        this.trust = trust; // memorisation
        this.tsp = tsp; // memorisation
    }

    /** Prolonge la signature ; renvoie le fichier produit. A appeler hors du fil graphique. */
    public Path extend() { return new ExtensionService(trust.get(), tsp.get()).extend(signedFile.get(), List.copyOf(originals), target.get()); } // prolongation

    public ObjectProperty<Path> signedFile() { return signedFile; } // fichier signe
    public ObservableList<Path> originals() { return originals; } // originaux
    public ObjectProperty<SigningLevel> target() { return target; } // niveau vise
}
