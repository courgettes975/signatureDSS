package fr.signaturedss.ui; // paquet de l'interface

import fr.signaturedss.core.OutputMode; // forme
import fr.signaturedss.core.SigningLevel; // niveau
import fr.signaturedss.core.TsaConfig; // configuration TSA
import fr.signaturedss.core.pgp.PgpService; // PGP
import fr.signaturedss.core.sign.SignResult; // resultat
import fr.signaturedss.core.token.TokenProvider; // sources disponibles
import fr.signaturedss.core.token.TokenSource; // source du certificat
import fr.signaturedss.i18n.Messages; // textes traduits
import fr.signaturedss.ui.vm.SignViewModel; // modele de vue
import javafx.geometry.Insets; // marges
import javafx.scene.control.Alert; // boite de dialogue
import javafx.scene.control.Button; // bouton
import javafx.scene.control.ButtonBar; // barre de boutons
import javafx.scene.control.ButtonType; // type de bouton
import javafx.scene.control.CheckBox; // case a cocher
import javafx.scene.control.ComboBox; // liste deroulante
import javafx.scene.control.Label; // etiquette
import javafx.scene.control.ListView; // liste
import javafx.scene.control.Tab; // onglet
import javafx.scene.control.TextArea; // zone de texte
import javafx.scene.control.TitledPane; // section repliable
import javafx.scene.layout.GridPane; // grille
import javafx.scene.layout.HBox; // ligne
import javafx.scene.layout.Priority; // extension
import javafx.scene.layout.VBox; // colonne
import javafx.stage.Window; // fenetre
import javafx.util.StringConverter; // conversion texte

import java.nio.file.Path; // chemins
import java.util.ArrayList; // liste modifiable
import java.util.List; // liste
import java.util.Optional; // valeur facultative

/** Onglet Signer. */
public class SignTab extends Tab { // onglet

    private final AppContext ctx; // contexte
    private final SignViewModel vm; // modele
    private final ComboBox<Class<? extends TokenSource>> source = new ComboBox<>(); // type de certificat
    private final Label p12Label = new Label(); // fichier P12 choisi
    private Path p12; // fichier P12
    private final TextArea log = new TextArea(); // journal des resultats

    public SignTab(AppContext ctx) { // constructeur
        super(Messages.get("onglet.signer")); // titre
        setClosable(false); // non fermable
        this.ctx = ctx; // memorisation
        this.vm = new SignViewModel(ctx::trust, ctx::tsp); // modele branche sur le contexte
        vm.level().set(ctx.settings().defaultLevel()); // niveau par defaut
        vm.mode().set(ctx.settings().defaultOutputMode()); // forme par defaut
        vm.pdfVisible().set(ctx.settings().pdfVisibleSignature()); // apparence par defaut
        p12 = ctx.settings().lastP12().orElse(null); // dernier P12
        setContent(build()); // contenu
    }

    private VBox build() { // construction de l'onglet
        Label drop = Ui.label("signer.depot"); // invite de depot
        drop.getStyleClass().add("drop-zone"); // style de zone de depot
        drop.setMaxWidth(Double.MAX_VALUE); // pleine largeur
        ListView<Path> list = new ListView<>(vm.files()); // liste des fichiers
        list.setPrefHeight(160); // hauteur
        Ui.dropTarget(drop, this::addFiles); // depot sur l'invite
        Ui.dropTarget(list, this::addFiles); // depot sur la liste
        Button add = new Button(Messages.get("bouton.ajouter")); // ajout
        add.setOnAction(e -> addFiles(Ui.chooseFiles(window(), "titre.choisir.fichiers"))); // selection
        Button remove = new Button(Messages.get("bouton.retirer")); // retrait
        remove.setOnAction(e -> vm.files().removeAll(new ArrayList<>(list.getSelectionModel().getSelectedItems()))); // retrait de la selection
        Button clear = new Button(Messages.get("bouton.vider")); // vider
        clear.setOnAction(e -> vm.files().clear()); // vidage
        HBox fileButtons = new HBox(8, add, remove, clear); // boutons de fichiers

        ComboBox<SigningLevel> level = new ComboBox<>(); // niveau
        level.getItems().setAll(SigningLevel.values()); // B, T, LT, LTA
        level.setConverter(enumText("niveau.")); // libelles traduits
        level.valueProperty().bindBidirectional(vm.level()); // liaison
        ComboBox<OutputMode> mode = new ComboBox<>(); // forme
        mode.getItems().setAll(OutputMode.values()); // detache ou conteneur
        mode.setConverter(enumText("mode.")); // libelles traduits
        mode.valueProperty().bindBidirectional(vm.mode()); // liaison
        CheckBox visible = new CheckBox(Messages.get("signer.visible")); // apparence PDF
        visible.selectedProperty().bindBidirectional(vm.pdfVisible()); // liaison
        source.getItems().setAll(TokenProvider.available()); // sources de ce systeme
        source.setConverter(new StringConverter<>() { // libelles
            @Override public String toString(Class<? extends TokenSource> c) { return c == null ? "" : Messages.get("source." + c.getSimpleName()); } // texte
            @Override public Class<? extends TokenSource> fromString(String s) { return null; } // non utilise
        });
        source.setValue(TokenSource.P12.class); // P12 par defaut
        Button chooseP12 = new Button(Messages.get("bouton.parcourir")); // choix du P12
        chooseP12.setOnAction(e -> { Path p = Ui.chooseFile(window(), "titre.choisir.p12"); if (p != null) { p12 = p; refreshP12(); ctx.settings().setLastP12(Optional.of(p)); ctx.settings().save(); } }); // memorisation
        chooseP12.visibleProperty().bind(source.valueProperty().isEqualTo(TokenSource.P12.class)); // visible pour P12
        p12Label.visibleProperty().bind(chooseP12.visibleProperty()); // idem
        refreshP12(); // affichage initial

        GridPane g = new GridPane(); // grille d'options
        g.setHgap(10); // espacement horizontal
        g.setVgap(8); // espacement vertical
        g.addRow(0, Ui.label("signer.niveau"), level, Ui.label("signer.niveau.aide")); // ligne niveau
        g.addRow(1, Ui.label("signer.forme"), mode, Ui.label("signer.forme.aide")); // ligne forme
        g.addRow(2, new Label(), visible); // ligne apparence
        g.addRow(3, Ui.label("signer.certificat"), source, new HBox(8, chooseP12, p12Label)); // ligne certificat

        Button sign = new Button(Messages.get("bouton.signer")); // bouton principal
        sign.setDefaultButton(true); // bouton par defaut
        sign.getStyleClass().add("primary"); // style principal
        sign.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(vm.files())); // actif si des fichiers
        sign.setOnAction(e -> sign()); // action

        log.setEditable(false); // lecture seule
        log.setPrefRowCount(6); // hauteur
        VBox.setVgrow(log, Priority.ALWAYS); // extension

        VBox root = new VBox(10, drop, list, fileButtons, g, sign, log, pgpPane()); // assemblage
        root.setPadding(new Insets(14)); // marges
        return root; // contenu
    }

    private TitledPane pgpPane() { // section PGP
        Label mention = Ui.label("pgp.mention.sans.eidas"); // mention obligatoire
        mention.setWrapText(true); // retour a la ligne
        Button signPgp = new Button(Messages.get("bouton.pgp.signer")); // signer en PGP
        signPgp.setOnAction(e -> signPgp()); // action
        Button verifyPgp = new Button(Messages.get("bouton.pgp.verifier")); // verifier une signature PGP
        verifyPgp.setOnAction(e -> verifyPgp()); // action
        TitledPane p = new TitledPane(Messages.get("pgp.titre"), new VBox(8, mention, new HBox(8, signPgp, verifyPgp))); // section
        p.setExpanded(false); // repliee par defaut
        return p; // section
    }

    private void addFiles(List<Path> files) { for (Path f : files) { if (!vm.files().contains(f) && java.nio.file.Files.isRegularFile(f)) { vm.files().add(f); } } } // ajout sans doublon

    private void refreshP12() { p12Label.setText(p12 == null ? Messages.get("signer.aucun.p12") : p12.getFileName().toString()); } // affichage du P12

    private Window window() { return getTabPane().getScene().getWindow(); } // fenetre parente

    private void sign() { // lancement de la signature
        TokenSource src = buildSource(); // source du certificat
        if (src == null) { return; } // source incomplete
        if (!askTsaPasswords()) { return; } // mots de passe TSA annules
        char[] secret = null; // secret
        if (src instanceof TokenSource.P12 || src instanceof TokenSource.Pkcs11) { // secret necessaire
            Optional<char[]> s = SecretDialog.ask(window(), "titre.secret", src instanceof TokenSource.P12 ? "secret.p12" : "secret.pin"); // demande
            if (s.isEmpty()) { return; } // annule
            secret = s.get(); // secret saisi
        }
        char[] finalSecret = secret; // pour la tache
        log.setText(Messages.get("signer.en.cours")); // etat
        Ui.background(getContent(), // travail en arriere-plan
                () -> vm.run(src, finalSecret, keys -> Ui.onFxAndWait(() -> CertificateChooser.choose(window(), keys)), this::askDowngrade), // execution
                this::showOutcome, // succes
                ex -> { log.setText(Ui.message(ex)); Ui.error(window(), Ui.message(ex)); }); // echec
    }

    private TokenSource buildSource() { // source choisie
        Class<? extends TokenSource> c = source.getValue(); // type
        if (c == TokenSource.P12.class) { // fichier P12
            if (p12 == null) { Ui.error(window(), Messages.get("erreur.p12.absent")); return null; } // non choisi
            return new TokenSource.P12(p12); // source
        }
        if (c == TokenSource.Pkcs11.class) { // jeton
            Optional<Path> lib = ctx.pkcs11Library(); // bibliotheque choisie, sinon pilote OpenSC embarque ou installe
            if (lib.isEmpty()) { Ui.error(window(), Messages.get("erreur.pkcs11.non.configure")); return null; } // non configuree
            return new TokenSource.Pkcs11(lib.get()); // source
        }
        return c == TokenSource.WindowsStore.class ? new TokenSource.WindowsStore() : new TokenSource.MacKeychain(); // magasin systeme
    }

    private boolean askTsaPasswords() { // demande les mots de passe TSA manquants
        if (vm.level().get() == SigningLevel.B) { return true; } // pas d'horodatage en B
        for (TsaConfig c : ctx.tsasMissingPassword()) { // chaque TSA protegee
            Optional<char[]> pw = SecretDialog.ask(window(), "titre.secret", "secret.tsa", c.url()); // demande
            if (pw.isEmpty()) { return false; } // annule
            ctx.rememberTsaPassword(c.url(), pw.get()); // memoire de session
        }
        return true; // pret
    }

    private Optional<SigningLevel> askDowngrade(List<String> missing) { // question en cas de revocation manquante
        return Ui.onFxAndWait(() -> { // sur le fil graphique
            ButtonType toT = new ButtonType(Messages.get("bouton.signer.en.t"), ButtonBar.ButtonData.OK_DONE); // signer en T
            Alert a = new Alert(Alert.AlertType.WARNING, Messages.get("revocation.manquante", String.join("\n", missing)), toT, ButtonType.CANCEL); // question
            a.initOwner(window()); // fenetre parente
            a.setHeaderText(null); // pas d'en-tete
            return a.showAndWait().filter(b -> b == toT).map(b -> SigningLevel.T); // choix
        });
    }

    private void showOutcome(SignViewModel.SignOutcome o) { // affichage du resultat
        ctx.forgetTsaPasswords(o.failedTsas()); // mots de passe des TSA en echec : redemandes la prochaine fois
        if (o.cancelled()) { log.setText(Messages.get("signer.annule")); return; } // annulation
        StringBuilder sb = new StringBuilder(); // texte
        for (SignResult r : o.results()) { sb.append(Messages.get("signer.ok", r.output())).append('\n'); } // fichiers produits
        for (String e : o.errors()) { sb.append("ERREUR : ").append(e).append('\n'); } // erreurs
        for (String w : o.warnings()) { sb.append(Messages.get("titre.avertissement")).append(" : ").append(w).append('\n'); } // avertissements
        log.setText(sb.toString()); // affichage
        if (!o.errors().isEmpty()) { Ui.error(window(), String.join("\n", o.errors())); } // erreurs mises en avant
        else if (!o.warnings().isEmpty()) { Ui.warn(window(), String.join("\n", o.warnings())); } // avertissements mis en avant
    }

    private void signPgp() { // signature PGP de la liste
        if (vm.files().isEmpty()) { Ui.error(window(), Messages.get("erreur.aucun.fichier")); return; } // rien a signer
        Path key = Ui.chooseFile(window(), "titre.choisir.cle.pgp"); // cle secrete
        if (key == null) { return; } // annule
        Optional<char[]> pass = SecretDialog.ask(window(), "titre.secret", "secret.pgp"); // phrase secrete
        if (pass.isEmpty()) { return; } // annule
        List<Path> files = List.copyOf(vm.files()); // fichiers
        Ui.background(getContent(), () -> { // arriere-plan
            List<Path> out = new ArrayList<>(); // sorties
            char[] p = pass.get(); // phrase
            try { for (Path f : files) { out.add(PgpService.signDetached(f, key, p.clone())); } } finally { java.util.Arrays.fill(p, '\0'); } // signatures, phrase effacee
            return out; // fichiers .asc
        }, out -> log.setText(Messages.get("pgp.ok", out.size())), ex -> Ui.error(window(), Ui.message(ex))); // resultat
    }

    private void verifyPgp() { // verification PGP
        Path file = Ui.chooseFile(window(), "titre.choisir.fichier.signe.pgp"); // fichier
        if (file == null) { return; } // annule
        Path asc = Ui.chooseFile(window(), "titre.choisir.asc"); // signature
        if (asc == null) { return; } // annule
        Path pub = Ui.chooseFile(window(), "titre.choisir.cle.publique"); // cle publique
        if (pub == null) { return; } // annule
        Ui.background(getContent(), () -> PgpService.verify(file, asc, pub), // verification
                v -> Ui.info(window(), Messages.get(v.valid() ? "pgp.valide" : "pgp.invalide", v.userId(), v.keyId(), v.created())), // resultat
                ex -> Ui.error(window(), Ui.message(ex))); // erreur
    }

    static <E extends Enum<E>> StringConverter<E> enumText(String prefix) { // libelles d'enumeration
        return new StringConverter<>() { // convertisseur
            @Override public String toString(E e) { return e == null ? "" : Messages.get(prefix + e.name()); } // texte traduit
            @Override public E fromString(String s) { return null; } // non utilise
        };
    }

}
