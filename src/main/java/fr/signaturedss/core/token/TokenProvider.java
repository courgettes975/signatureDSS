package fr.signaturedss.core.token; // paquet jetons

import eu.europa.esig.dss.token.AppleSignatureToken; // trousseau macOS
import eu.europa.esig.dss.token.MSCAPISignatureToken; // magasin Windows
import eu.europa.esig.dss.token.Pkcs11SignatureToken; // jeton PKCS#11
import eu.europa.esig.dss.token.Pkcs12SignatureToken; // fichier PKCS#12
import eu.europa.esig.dss.token.SignatureTokenConnection; // jeton ouvert

import java.io.IOException; // erreurs d'E/S
import java.nio.file.Files; // fichiers
import java.security.KeyStore.PasswordProtection; // protection par secret
import java.security.UnrecoverableKeyException; // cle irrecuperable
import java.util.ArrayList; // liste modifiable
import java.util.Arrays; // effacement
import java.util.List; // liste
import java.util.Locale; // casse

/** Ouvre un jeton de signature. Une seule tentative par action ; le secret est efface ensuite. */
public class TokenProvider { // fournisseur de jetons

    /** Ouverture brute d'un jeton (injectable pour les tests). */
    @FunctionalInterface
    public interface Opener { SignatureTokenConnection open(TokenSource src, PasswordProtection pw) throws IOException; } // ouverture

    /** Ouverture par les classes DSS. */
    public static final Opener DEFAULT_OPENER = (src, pw) -> switch (src) { // selon la source
        case TokenSource.P12 p -> new Pkcs12SignatureToken(p.file().toFile(), pw); // fichier P12
        case TokenSource.Pkcs11 k -> new Pkcs11SignatureToken(pkcs11Path(k.library().toAbsolutePath().toString()), pw); // jeton PKCS#11
        case TokenSource.WindowsStore w -> new MSCAPISignatureToken(); // magasin Windows (PIN demande par Windows)
        case TokenSource.MacKeychain m -> new AppleSignatureToken(); // trousseau macOS
    };

    /** Chemin pour la configuration SunPKCS11 : barres obliques, sinon "\n" ou "\t" d'un chemin Windows sont interpretes. */
    static String pkcs11Path(String raw) { return raw.replace('\\', '/'); } // normalisation

    private final Opener opener; // ouverture utilisee

    public TokenProvider() { this(DEFAULT_OPENER); } // ouverture DSS
    public TokenProvider(Opener opener) { this.opener = opener; } // ouverture injectee

    /** Ouvre le jeton et verifie qu'il contient une cle ; efface toujours le secret. */
    public SignatureTokenConnection open(TokenSource src, char[] secret) { // ouverture
        try { // ouverture protegee
            precheck(src); // fichiers presents
            SignatureTokenConnection c = null; // jeton
            try { // une seule tentative
                c = opener.open(src, new PasswordProtection(secret == null ? new char[0] : secret.clone())); // ouverture (copie du secret)
                if (c.getKeys().isEmpty()) { throw new TokenException("erreur.token.aucuncertificat", null); } // aucune cle
                return c; // jeton pret
            } catch (TokenException e) { // erreur deja traduite
                closeQuietly(c); // fermeture
                throw e; // propagation
            } catch (IOException | RuntimeException e) { // erreur d'ouverture
                closeQuietly(c); // fermeture
                throw new TokenException(classify(src, e), e); // erreur traduite
            }
        } finally { // dans tous les cas
            if (secret != null) { Arrays.fill(secret, '\0'); } // effacement du secret
        }
    }

    private static void precheck(TokenSource src) { // verifie les fichiers
        if (src instanceof TokenSource.P12 p && !Files.isRegularFile(p.file())) { throw new TokenException("erreur.token.fichier", null); } // P12 absent
        if (src instanceof TokenSource.Pkcs11 k && !Files.isRegularFile(k.library())) { throw new TokenException("erreur.token.bibliotheque", null); } // bibliotheque absente
    }

    private static String classify(TokenSource src, Throwable e) { // cle de message selon la cause
        for (Throwable c = e; c != null; c = c.getCause()) { // remontee des causes
            String m = c.getMessage() == null ? "" : c.getMessage().toLowerCase(Locale.ROOT); // message
            if (src instanceof TokenSource.Pkcs11 && (m.contains("pkcs11 not found") || m.contains("token_not_present") || m.contains("token not present"))) { return "erreur.token.absent"; } // bibliotheque chargee, aucun jeton
            if (c instanceof UnrecoverableKeyException || m.contains("password") || m.contains("pin") || m.contains("mac check")) { return "erreur.token.motdepasse"; } // secret refuse
            if (src instanceof TokenSource.Pkcs11 && (m.contains("pkcs11") || m.contains("pkcs#11") || m.contains("library"))) { return "erreur.token.bibliotheque"; } // bibliotheque inutilisable
        }
        return src instanceof TokenSource.P12 ? "erreur.token.motdepasse" : "erreur.token.ouverture"; // P12 illisible : en pratique mot de passe ; sinon generique
    }

    private static void closeQuietly(SignatureTokenConnection c) { if (c != null) { try { c.close(); } catch (RuntimeException ignored) { /* fermeture au mieux */ } } } // fermeture

    /** Sources proposees sur ce systeme. */
    public static List<Class<? extends TokenSource>> available() { // liste par systeme
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT); // systeme
        List<Class<? extends TokenSource>> l = new ArrayList<>(List.of(TokenSource.P12.class, TokenSource.Pkcs11.class)); // partout
        if (os.contains("win")) { l.add(TokenSource.WindowsStore.class); } // Windows
        if (os.contains("mac")) { l.add(TokenSource.MacKeychain.class); } // macOS
        return List.copyOf(l); // liste
    }
}
