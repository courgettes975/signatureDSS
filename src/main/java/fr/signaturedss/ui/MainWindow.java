package fr.signaturedss.ui; // paquet de l'interface

import fr.signaturedss.i18n.Messages; // textes traduits
import javafx.application.Platform; // fil graphique
import javafx.geometry.Insets; // marges
import javafx.scene.Scene; // scene
import javafx.scene.control.Label; // etiquette
import javafx.scene.control.TabPane; // onglets
import javafx.scene.layout.BorderPane; // disposition
import javafx.stage.Stage; // fenetre

/** Fenetre principale : quatre onglets et une barre d'etat. */
public class MainWindow { // fenetre

    private final AppContext ctx; // contexte
    private final Stage stage; // fenetre JavaFX
    private final BorderPane root = new BorderPane(); // disposition
    private final Label status = new Label(); // barre d'etat
    private final Scene scene; // scene

    public MainWindow(AppContext ctx, Stage stage) { // constructeur
        this.ctx = ctx; // memorisation
        this.stage = stage; // memorisation
        status.setPadding(new Insets(4, 10, 4, 10)); // marges
        status.getStyleClass().add("status"); // style
        status.setMaxWidth(Double.MAX_VALUE); // barre d'etat sur toute la largeur
        root.setBottom(status); // barre d'etat en bas
        scene = new Scene(root, 1000, 760); // taille initiale
        rebuild(); // construction des onglets
        stage.setScene(scene); // scene
        stage.setMinWidth(760); // largeur minimale
        stage.setMinHeight(560); // hauteur minimale
    }

    /** Reconstruit les onglets (apres changement de langue ou de theme). */
    public void rebuild() { // reconstruction
        stage.setTitle(Messages.get("app.titre")); // titre traduit
        TabPane tabs = new TabPane(new SignTab(ctx), new ValidateTab(ctx), new ExtendTab(ctx), new SettingsTab(ctx, this::rebuild)); // onglets
        root.setCenter(tabs); // insertion
        scene.getStylesheets().setAll(getClass().getResource("/ui/" + (ctx.settings().darkTheme() ? "dark" : "light") + ".css").toExternalForm()); // theme
    }

    /** Affiche la fenetre et charge les listes de confiance en arriere-plan. */
    public void show() { // affichage
        stage.show(); // fenetre visible
        status.setText(Messages.get("lotl.chargement")); // etat
        Thread t = new Thread(() -> { // fil de chargement
            ctx.lotl().refreshOffline(); // cache de la cle d'abord
            int cached = ctx.lotl().certificateSource().getNumberOfCertificates(); // certificats en cache
            Platform.runLater(() -> status.setText(Messages.get("lotl.cache", cached))); // etat intermediaire
            ctx.lotl().refreshOnline(); // mise a jour en ligne
            ctx.resetTrust(); // confiance a reconstruire
            int online = ctx.lotl().certificateSource().getNumberOfCertificates(); // certificats a jour
            Platform.runLater(() -> status.setText(online > 0 ? Messages.get("lotl.ok", online) : Messages.get("lotl.hors.ligne"))); // etat final
        }, "signaturedss-lotl"); // nom du fil
        t.setDaemon(true); // ne bloque pas la fermeture
        t.start(); // lancement
    }
}
