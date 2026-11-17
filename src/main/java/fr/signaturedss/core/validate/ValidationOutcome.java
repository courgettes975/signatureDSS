package fr.signaturedss.core.validate; // paquet validation

import eu.europa.esig.dss.enumerations.Indication; // verdict
import eu.europa.esig.dss.enumerations.SignatureLevel; // niveau DSS
import eu.europa.esig.dss.validation.reports.Reports; // rapports DSS

import java.util.Date; // date
import java.util.Optional; // valeur facultative

/**
 * Resultat d'une validation.
 *
 * @param reports rapports DSS complets
 * @param verdict verdict de la premiere signature
 * @param level niveau de format detecte
 * @param lastTimestampExpiry fin de validite du certificat du dernier horodatage
 */
public record ValidationOutcome(Reports reports, Indication verdict, SignatureLevel level, Optional<Date> lastTimestampExpiry) { } // enregistrement
