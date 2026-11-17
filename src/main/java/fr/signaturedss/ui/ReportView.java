package fr.signaturedss.ui; // paquet de l'interface

import eu.europa.esig.dss.enumerations.Indication; // verdict
import fr.signaturedss.core.validate.ReportExporter; // export
import fr.signaturedss.core.validate.ValidationOutcome; // resultat
import fr.signaturedss.i18n.Messages; // textes traduits
import javafx.geometry.Insets; // marges
import javafx.scene.control.Button; // bouton
import javafx.scene.control.Label; // etiquette
import javafx.scene.control.Tab; // onglet
import javafx.scene.control.TabPane; // onglets
import javafx.scene.control.TextArea; // zone de texte
import javafx.scene.layout.HBox; // ligne
import javafx.scene.layout.Priority; // extension
import javafx.scene.layout.VBox; // colonne
import javafx.stage.DirectoryChooser; // choix de dossier
import javafx.stage.FileChooser; // choix de fichier

import java.io.File; // fichier
import java.nio.file.Path; // chemins
import java.text.SimpleDateFormat; // format de date

/** Affichage d'un resultat de validation : verdict, niveau, horodatage et rapports. */
public class ReportView extends VBox { // composant

    private final Label verdict = new Label(); // verdict
    private final Label level = new Label(); // niveau
    private final Label expiry = new Label(); // fin de validite de l'horodatage
    private final TextArea simple = readOnly(); // rapport simple
    private final TextArea detailed = readOnly(); // rapport detaille
    private final TextArea diagnostic = readOnly(); // diagnostic
    private final Button exportXml = new Button(Messages.get("bouton.export.xml")); // export XML
    private final Button exportPdf = new Button(Messages.get("bouton.export.pdf")); // export PDF
    private ValidationOutcome current; // resultat affiche
    private String baseName = "rapport"; // nom de base des exports

    public ReportView() { // constructeur
        super(8); // espacement
        setPadding(new Insets(8, 0, 0, 0)); // marge haute
        verdict.getStyleClass().add("verdict"); // style du verdict
        TabPane tabs = new TabPane( // onglets des rapports
                tab("rapport.simple", simple), tab("rapport.detaille", detailed), tab("rapport.diagnostic", diagnostic)); // trois rapports
        VBox.setVgrow(tabs, Priority.ALWAYS); // occupe la hauteur
        exportXml.setOnAction(e -> exportXml()); // action XML
        exportPdf.setOnAction(e -> exportPdf()); // action PDF
        HBox buttons = new HBox(8, exportXml, exportPdf); // boutons
        getChildren().addAll(verdict, level, expiry, tabs, buttons); // assemblage
        show(null, null); // etat vide
    }

    /** Affiche un resultat (ou vide l'affichage si null). */
    public void show(ValidationOutcome o, Path signed) { // affichage
        current = o; // memorisation
        boolean has = o != null; // resultat present
        exportXml.setDisable(!has); // export possible
        exportPdf.setDisable(!has); // export possible
        verdict.getStyleClass().removeAll("ok", "ko", "indetermine"); // styles precedents
        if (!has) { verdict.setText(""); level.setText(""); expiry.setText(""); simple.clear(); detailed.clear(); diagnostic.clear(); return; } // vide
        baseName = signed == null ? "rapport" : signed.getFileName().toString(); // nom de base
        verdict.setText(Messages.get("verdict." + o.verdict().name())); // verdict traduit
        verdict.getStyleClass().add(o.verdict() == Indication.TOTAL_PASSED ? "ok" : o.verdict() == Indication.TOTAL_FAILED ? "ko" : "indetermine"); // couleur
        level.setText(Messages.get("resultat.niveau", o.level())); // niveau
        expiry.setText(o.lastTimestampExpiry().map(d -> Messages.get("resultat.horodatage", new SimpleDateFormat("dd/MM/yyyy").format(d))).orElse(Messages.get("resultat.sans.horodatage"))); // horodatage
        simple.setText(o.reports().getXmlSimpleReport()); // rapport simple
        detailed.setText(o.reports().getXmlDetailedReport()); // rapport detaille
        diagnostic.setText(o.reports().getXmlDiagnosticData()); // diagnostic
    }

    private void exportXml() { // export XML
        DirectoryChooser dc = new DirectoryChooser(); // choix du dossier
        dc.setTitle(Messages.get("bouton.export.xml")); // titre
        File dir = dc.showDialog(getScene().getWindow()); // dialogue
        if (dir == null) { return; } // annule
        try { ReportExporter.exportXml(current.reports(), dir.toPath(), baseName); Ui.info(getScene().getWindow(), Messages.get("info.export.ok")); } // export
        catch (Exception e) { Ui.error(getScene().getWindow(), Ui.message(e)); } // erreur
    }

    private void exportPdf() { // export PDF
        FileChooser fc = new FileChooser(); // choix du fichier
        fc.setTitle(Messages.get("bouton.export.pdf")); // titre
        fc.setInitialFileName(baseName + "-rapport.pdf"); // nom propose
        File f = fc.showSaveDialog(getScene().getWindow()); // dialogue (le systeme confirme un ecrasement)
        if (f == null) { return; } // annule
        try { ReportExporter.exportPdf(current.reports(), f.toPath()); Ui.info(getScene().getWindow(), Messages.get("info.export.ok")); } // export
        catch (Exception e) { Ui.error(getScene().getWindow(), Ui.message(e)); } // erreur
    }

    private static Tab tab(String key, TextArea area) { Tab t = new Tab(Messages.get(key), area); t.setClosable(false); return t; } // onglet non fermable

    private static TextArea readOnly() { TextArea a = new TextArea(); a.setEditable(false); a.setWrapText(false); a.getStyleClass().add("mono"); return a; } // zone en lecture seule
}
