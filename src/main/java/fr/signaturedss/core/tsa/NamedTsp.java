package fr.signaturedss.core.tsa; // paquet horodatage

import eu.europa.esig.dss.spi.x509.tsp.TSPSource; // interface TSA

/**
 * Service d'horodatage avec un libelle lisible.
 *
 * @param label libelle affiche (URL en general)
 * @param source source DSS
 */
public record NamedTsp(String label, TSPSource source) { } // enregistrement immuable
