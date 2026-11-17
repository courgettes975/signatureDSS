package fr.signaturedss; // paquet racine

import fr.signaturedss.core.PortablePaths; // chemins portables
import fr.signaturedss.i18n.Messages; // textes traduits
import fr.signaturedss.ui.AppContext; // contexte
import fr.signaturedss.ui.MainWindow; // fenetre principale
import javafx.application.Application; // application JavaFX
import javafx.scene.image.Image; // icone
import javafx.stage.Stage; // fenetre

import java.io.InputStream; // flux

/** Application JavaFX. Lancee par Launcher. */
public class App extends Application { // application

    /** Lance l'interface graphique. */
    public static void main(String[] args) { launch(args); } // demarrage JavaFX

    @Override // demarrage de l'interface
    public void start(Stage stage) { // fenetre principale
        AppContext ctx = new AppContext(PortablePaths.detect(), getHostServices()); // contexte
        Messages.setLocale(ctx.settings().locale()); // langue choisie
        try (InputStream icon = App.class.getResourceAsStream("/ui/icone.png")) { if (icon != null) { stage.getIcons().add(new Image(icon)); } } catch (java.io.IOException ignored) { /* icone facultative */ } // icone
        new MainWindow(ctx, stage).show(); // affichage
    }
}
