package fr.signaturedss.ui; // paquet de l'interface

import fr.signaturedss.i18n.Messages; // textes traduits
import javafx.application.Platform; // fil graphique
import javafx.concurrent.Task; // tache de fond
import javafx.scene.Node; // noeud graphique
import javafx.scene.control.Alert; // boite de message
import javafx.scene.control.Label; // etiquette
import javafx.scene.input.DragEvent; // glisser-deposer
import javafx.scene.input.TransferMode; // mode de transfert
import javafx.stage.FileChooser; // choix de fichier
import javafx.stage.Window; // fenetre

import java.io.File; // fichier
import java.nio.file.Path; // chemins
import java.util.List; // liste
import java.util.concurrent.CompletableFuture; // resultat asynchrone
import java.util.function.Consumer; // consommateur
import java.util.function.Supplier; // fournisseur

/** Petits utilitaires d'interface partages par les onglets. */
public final class Ui { // utilitaire statique

    private Ui() { } // pas d'instance

    /** Etiquette traduite. */
    public static Label label(String key, Object... args) { return new Label(Messages.get(key, args)); } // etiquette

    /** Affiche une erreur. */
    public static void error(Window owner, String message) { show(owner, Alert.AlertType.ERROR, Messages.get("titre.erreur"), message); } // erreur

    /** Affiche une information. */
    public static void info(Window owner, String message) { show(owner, Alert.AlertType.INFORMATION, Messages.get("app.titre"), message); } // information

    /** Affiche un avertissement. */
    public static void warn(Window owner, String message) { show(owner, Alert.AlertType.WARNING, Messages.get("titre.avertissement"), message); } // avertissement

    private static void show(Window owner, Alert.AlertType type, String title, String message) { // boite de message
        Alert a = new Alert(type, message); // boite
        a.initOwner(owner); // fenetre parente
        a.setTitle(title); // titre
        a.setHeaderText(null); // pas d'en-tete
        a.getDialogPane().setMinWidth(480); // largeur minimale lisible
        a.showAndWait(); // affichage modal
    }

    /** Choix de plusieurs fichiers. */
    public static List<Path> chooseFiles(Window owner, String titleKey) { // selection multiple
        FileChooser fc = new FileChooser(); // selecteur
        fc.setTitle(Messages.get(titleKey)); // titre
        List<File> l = fc.showOpenMultipleDialog(owner); // dialogue
        return l == null ? List.of() : l.stream().map(File::toPath).toList(); // chemins
    }

    /** Choix d'un fichier. */
    public static Path chooseFile(Window owner, String titleKey) { // selection simple
        FileChooser fc = new FileChooser(); // selecteur
        fc.setTitle(Messages.get(titleKey)); // titre
        File f = fc.showOpenDialog(owner); // dialogue
        return f == null ? null : f.toPath(); // chemin ou rien
    }

    /** Fait d'un noeud une zone de depot de fichiers. */
    public static void dropTarget(Node node, Consumer<List<Path>> onFiles) { // glisser-deposer
        node.setOnDragOver((DragEvent e) -> { if (e.getDragboard().hasFiles()) { e.acceptTransferModes(TransferMode.COPY); } e.consume(); }); // accepte les fichiers
        node.setOnDragDropped((DragEvent e) -> { // depot
            boolean ok = e.getDragboard().hasFiles(); // fichiers presents
            if (ok) { onFiles.accept(e.getDragboard().getFiles().stream().map(File::toPath).toList()); } // transmission
            e.setDropCompleted(ok); // fin du depot
            e.consume(); // evenement traite
        });
    }

    /** Execute un travail en arriere-plan puis rappelle le fil graphique. */
    public static <T> void background(Node busy, Supplier<T> work, Consumer<T> onOk, Consumer<Throwable> onError) { // tache de fond
        Task<T> t = new Task<>() { @Override protected T call() { return work.get(); } }; // tache
        busy.setDisable(true); // bloque l'onglet pendant le travail
        t.setOnSucceeded(e -> { busy.setDisable(false); onOk.accept(t.getValue()); }); // succes
        t.setOnFailed(e -> { busy.setDisable(false); onError.accept(t.getException()); }); // echec
        Thread th = new Thread(t, "signaturedss-travail"); // fil de travail
        th.setDaemon(true); // ne bloque pas la fermeture
        th.start(); // lancement
    }

    /** Execute sur le fil graphique et attend le resultat (pour poser une question depuis un fil de travail). */
    public static <T> T onFxAndWait(Supplier<T> s) { // appel synchrone
        if (Platform.isFxApplicationThread()) { return s.get(); } // deja sur le fil graphique
        CompletableFuture<T> f = new CompletableFuture<>(); // resultat
        Platform.runLater(() -> { try { f.complete(s.get()); } catch (RuntimeException e) { f.completeExceptionally(e); } }); // execution
        return f.join(); // attente
    }

    /** Message lisible d'une exception (cause la plus utile). */
    public static String message(Throwable t) { // texte d'erreur
        Throwable c = t; // curseur
        while (c.getCause() != null && (c.getMessage() == null || c.getMessage().isBlank())) { c = c.getCause(); } // premier message renseigne
        return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage(); // texte
    }
}
