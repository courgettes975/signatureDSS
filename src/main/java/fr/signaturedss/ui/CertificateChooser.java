package fr.signaturedss.ui; // paquet de l'interface

import eu.europa.esig.dss.spi.DSSASN1Utils; // utilitaires ASN.1
import eu.europa.esig.dss.token.DSSPrivateKeyEntry; // cle privee
import fr.signaturedss.i18n.Messages; // textes traduits
import javafx.scene.control.ChoiceDialog; // dialogue de choix
import javafx.stage.Window; // fenetre

import java.text.SimpleDateFormat; // format de date
import java.util.LinkedHashMap; // table ordonnee
import java.util.List; // liste
import java.util.Map; // table

/** Choix d'un certificat quand le jeton en contient plusieurs. */
public final class CertificateChooser { // dialogue de choix

    private CertificateChooser() { } // pas d'instance

    /** Renvoie la cle choisie, ou null si annule. */
    public static DSSPrivateKeyEntry choose(Window owner, List<DSSPrivateKeyEntry> keys) { // choix
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy"); // format de date
        Map<String, DSSPrivateKeyEntry> byLabel = new LinkedHashMap<>(); // libelle vers cle
        for (DSSPrivateKeyEntry k : keys) { // chaque cle
            String label = Messages.get("certificat.libelle", DSSASN1Utils.getSubjectCommonName(k.getCertificate()), // titulaire
                    DSSASN1Utils.getHumanReadableName(k.getCertificate().getIssuer()), df.format(k.getCertificate().getNotAfter())); // emetteur et fin de validite
            byLabel.put(byLabel.containsKey(label) ? label + " #" + byLabel.size() : label, k); // libelle unique
        }
        ChoiceDialog<String> d = new ChoiceDialog<>(byLabel.keySet().iterator().next(), byLabel.keySet()); // dialogue
        d.initOwner(owner); // fenetre parente
        d.setTitle(Messages.get("certificat.titre")); // titre
        d.setHeaderText(Messages.get("certificat.question")); // question
        return d.showAndWait().map(byLabel::get).orElse(null); // cle ou rien
    }
}
