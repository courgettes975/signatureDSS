package fr.signaturedss.core.token; // paquet jetons

import java.nio.file.Path; // chemins

/** Origine du certificat de signature. */
public sealed interface TokenSource permits TokenSource.P12, TokenSource.Pkcs11, TokenSource.WindowsStore, TokenSource.MacKeychain { // types fermes

    /** Fichier .p12 / .pfx protege par mot de passe. */
    record P12(Path file) implements TokenSource { } // fichier

    /** Jeton materiel (Feitian...) via sa bibliotheque PKCS#11, protege par PIN. */
    record Pkcs11(Path library) implements TokenSource { } // jeton USB

    /** Magasin de certificats Windows (MSCAPI). */
    record WindowsStore() implements TokenSource { } // Windows

    /** Trousseau macOS. */
    record MacKeychain() implements TokenSource { } // macOS
}
