package fr.signaturedss.ui; // paquet de l'interface

import fr.signaturedss.i18n.Messages; // textes traduits
import javafx.application.Platform; // fil graphique
import javafx.scene.control.ButtonType; // boutons
import javafx.scene.control.Dialog; // dialogue
import javafx.scene.control.Label; // etiquette
import javafx.scene.control.PasswordField; // champ masque
import javafx.scene.layout.VBox; // colonne
import javafx.stage.Window; // fenetre

import java.util.Optional; // valeur facultative

/** Demande un mot de passe ou un code PIN. Rien n'est enregistre. */
public final class SecretDialog { // dialogue de secret

    private SecretDialog() { } // pas d'instance

    /** Renvoie le secret saisi (a effacer par l'appelant) ou vide si annule. */
    public static Optional<char[]> ask(Window owner, String titleKey, String promptKey, Object... args) { // demande
        Dialog<ButtonType> d = new Dialog<>(); // dialogue
        d.initOwner(owner); // fenetre parente
        d.setTitle(Messages.get(titleKey)); // titre
        PasswordField pf = new PasswordField(); // champ masque
        VBox box = new VBox(8, new Label(Messages.get(promptKey, args)), pf); // contenu
        d.getDialogPane().setContent(box); // insertion
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL); // boutons (ordre selon le systeme)
        Platform.runLater(pf::requestFocus); // focus sur le champ
        Optional<ButtonType> r = d.showAndWait(); // affichage modal
        char[] secret = pf.getText().toCharArray(); // copie du texte saisi
        pf.clear(); // vide le champ
        if (r.isEmpty() || r.get() != ButtonType.OK) { java.util.Arrays.fill(secret, '\0'); return Optional.empty(); } // annulation
        return Optional.of(secret); // secret
    }
}
