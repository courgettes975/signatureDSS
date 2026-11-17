package fr.signaturedss.ui; // paquet de l'interface

import fr.signaturedss.i18n.Messages; // textes traduits
import fr.signaturedss.ui.vm.ValidateViewModel; // modele de vue
import javafx.geometry.Insets; // marges
import javafx.scene.control.Button; // bouton
import javafx.scene.control.Label; // etiquette
import javafx.scene.control.ListView; // liste
import javafx.scene.control.Tab; // onglet
import javafx.scene.layout.HBox; // ligne
import javafx.scene.layout.Priority; // extension
import javafx.scene.layout.VBox; // colonne
import javafx.stage.Window; // fenetre

import java.nio.file.Path; // chemins
import java.util.List; // liste

/** Onglet Valider : verdict et rapports, comme la demo DSS. */
public class ValidateTab extends Tab { // onglet

    private final ValidateViewModel vm; // modele
    private final Label signedLabel = new Label(); // fichier signe choisi
    private final Label hint = new Label(); // message (original manquant, erreur)
    private final ReportView report = new ReportView(); // resultat

    public ValidateTab(AppContext ctx) { // constructeur
        super(Messages.get("onglet.valider")); // titre
        setClosable(false); // non fermable
        vm = new ValidateViewModel(ctx::trust); // modele branche sur le contexte
        setContent(build()); // contenu
    }

    private VBox build() { // construction
        Label drop = Ui.label("valider.depot"); // invite de depot
        drop.getStyleClass().add("drop-zone"); // style
        drop.setMaxWidth(Double.MAX_VALUE); // pleine largeur
        Ui.dropTarget(drop, files -> { if (!files.isEmpty()) { setSigned(files.get(0)); } }); // premier fichier depose
        Button choose = new Button(Messages.get("bouton.choisir.signe")); // choix du fichier signe
        choose.setOnAction(e -> { Path p = Ui.chooseFile(window(), "titre.choisir.signe"); if (p != null) { setSigned(p); } }); // selection
        ListView<Path> originals = new ListView<>(vm.originals()); // originaux
        originals.setPrefHeight(70); // hauteur
        Ui.dropTarget(originals, vm.originals()::addAll); // depot d'originaux
        Button addOriginal = new Button(Messages.get("bouton.ajouter.original")); // ajout d'original
        addOriginal.setOnAction(e -> vm.originals().addAll(Ui.chooseFiles(window(), "titre.choisir.original"))); // selection
        Button clearOriginals = new Button(Messages.get("bouton.vider")); // vider
        clearOriginals.setOnAction(e -> vm.originals().clear()); // vidage
        Button validate = new Button(Messages.get("bouton.valider")); // bouton principal
        validate.getStyleClass().add("primary"); // style
        validate.setDefaultButton(true); // bouton par defaut
        validate.disableProperty().bind(vm.signedFile().isNull()); // actif si un fichier
        validate.setOnAction(e -> run()); // action
        hint.setWrapText(true); // retour a la ligne
        hint.getStyleClass().add("hint"); // style
        VBox.setVgrow(report, Priority.ALWAYS); // extension
        VBox root = new VBox(10, drop, new HBox(8, choose, signedLabel), Ui.label("valider.originaux"), originals, // assemblage
                new HBox(8, addOriginal, clearOriginals), validate, hint, report); // suite
        root.setPadding(new Insets(14)); // marges
        return root; // contenu
    }

    private void setSigned(Path p) { vm.signedFile().set(p); signedLabel.setText(p.getFileName().toString()); report.show(null, null); hint.setText(""); } // nouveau fichier

    private Window window() { return getTabPane().getScene().getWindow(); } // fenetre parente

    private void run() { // validation en arriere-plan
        hint.setText(Messages.get("valider.en.cours")); // etat
        Path signed = vm.signedFile().get(); // fichier
        Ui.background(getContent(), () -> { vm.validate(); return List.of(); }, r -> { // travail
            hint.setText(vm.error().get()); // message eventuel
            report.show(vm.outcome().get(), signed); // resultat
        }, ex -> Ui.error(window(), Ui.message(ex))); // erreur
    }
}
