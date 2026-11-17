package fr.signaturedss.ui; // paquet de l'interface

import fr.signaturedss.core.SigningLevel; // niveau
import fr.signaturedss.i18n.Messages; // textes traduits
import fr.signaturedss.ui.vm.ExtendViewModel; // modele de vue
import javafx.geometry.Insets; // marges
import javafx.scene.control.Button; // bouton
import javafx.scene.control.ComboBox; // liste deroulante
import javafx.scene.control.Label; // etiquette
import javafx.scene.control.ListView; // liste
import javafx.scene.control.Tab; // onglet
import javafx.scene.layout.HBox; // ligne
import javafx.scene.layout.VBox; // colonne
import javafx.stage.Window; // fenetre

import java.nio.file.Path; // chemins

/** Onglet Prolonger : passage a un niveau superieur ou nouvel horodatage d'archive (manuel). */
public class ExtendTab extends Tab { // onglet

    private final AppContext ctx; // contexte
    private final ExtendViewModel vm; // modele
    private final Label signedLabel = new Label(); // fichier choisi
    private final Label result = new Label(); // resultat

    public ExtendTab(AppContext ctx) { // constructeur
        super(Messages.get("onglet.prolonger")); // titre
        setClosable(false); // non fermable
        this.ctx = ctx; // memorisation
        vm = new ExtendViewModel(ctx::trust, ctx::tsp); // modele
        setContent(build()); // contenu
    }

    private VBox build() { // construction
        Label help = Ui.label("prolonger.aide"); // explication
        help.setWrapText(true); // retour a la ligne
        Label drop = Ui.label("prolonger.depot"); // invite de depot
        drop.getStyleClass().add("drop-zone"); // style
        drop.setMaxWidth(Double.MAX_VALUE); // pleine largeur
        Ui.dropTarget(drop, f -> { if (!f.isEmpty()) { setSigned(f.get(0)); } }); // depot
        Button choose = new Button(Messages.get("bouton.choisir.signe")); // choix
        choose.setOnAction(e -> { Path p = Ui.chooseFile(window(), "titre.choisir.signe"); if (p != null) { setSigned(p); } }); // selection
        ListView<Path> originals = new ListView<>(vm.originals()); // originaux
        originals.setPrefHeight(60); // hauteur
        Button addOriginal = new Button(Messages.get("bouton.ajouter.original")); // ajout d'original
        addOriginal.setOnAction(e -> vm.originals().addAll(Ui.chooseFiles(window(), "titre.choisir.original"))); // selection
        ComboBox<SigningLevel> target = new ComboBox<>(); // niveau vise
        target.getItems().setAll(SigningLevel.T, SigningLevel.LT, SigningLevel.LTA); // niveaux possibles
        target.setConverter(SignTab.enumText("niveau.")); // libelles
        target.valueProperty().bindBidirectional(vm.target()); // liaison
        Button run = new Button(Messages.get("bouton.prolonger")); // bouton principal
        run.getStyleClass().add("primary"); // style
        run.disableProperty().bind(vm.signedFile().isNull()); // actif si un fichier
        run.setOnAction(e -> extend()); // action
        result.setWrapText(true); // retour a la ligne
        VBox root = new VBox(10, help, drop, new HBox(8, choose, signedLabel), Ui.label("valider.originaux"), originals, addOriginal, // assemblage
                new HBox(8, Ui.label("prolonger.niveau"), target), run, result); // suite
        root.setPadding(new Insets(14)); // marges
        return root; // contenu
    }

    private void setSigned(Path p) { vm.signedFile().set(p); signedLabel.setText(p.getFileName().toString()); result.setText(""); } // nouveau fichier

    private Window window() { return getTabPane().getScene().getWindow(); } // fenetre parente

    private void extend() { // prolongation en arriere-plan
        for (var c : ctx.tsasMissingPassword()) { // mots de passe TSA manquants
            var pw = SecretDialog.ask(window(), "titre.secret", "secret.tsa", c.url()); // demande
            if (pw.isEmpty()) { return; } // annule
            ctx.rememberTsaPassword(c.url(), pw.get()); // memoire de session
        }
        result.setText(Messages.get("prolonger.en.cours")); // etat
        Ui.background(getContent(), vm::extend, out -> result.setText(Messages.get("prolonger.ok", out)), // succes
                ex -> { result.setText(Ui.message(ex)); Ui.error(window(), Ui.message(ex)); }); // echec
    }

}
