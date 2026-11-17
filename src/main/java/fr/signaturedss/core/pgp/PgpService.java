package fr.signaturedss.core.pgp; // paquet PGP

import fr.signaturedss.core.sign.FormatPlan; // nommage sans ecrasement
import org.bouncycastle.bcpg.ArmoredOutputStream; // sortie ASCII
import org.bouncycastle.bcpg.BCPGOutputStream; // flux de paquets PGP
import org.bouncycastle.bcpg.HashAlgorithmTags; // algorithmes d'empreinte
import org.bouncycastle.jce.provider.BouncyCastleProvider; // fournisseur BouncyCastle
import org.bouncycastle.openpgp.PGPException; // erreur PGP
import org.bouncycastle.openpgp.PGPPrivateKey; // cle privee
import org.bouncycastle.openpgp.PGPPublicKey; // cle publique
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection; // anneaux publics
import org.bouncycastle.openpgp.PGPSecretKey; // cle secrete
import org.bouncycastle.openpgp.PGPSecretKeyRing; // anneau secret
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection; // anneaux secrets
import org.bouncycastle.openpgp.PGPSignature; // signature
import org.bouncycastle.openpgp.PGPSignatureGenerator; // generateur
import org.bouncycastle.openpgp.PGPSignatureList; // liste de signatures
import org.bouncycastle.openpgp.PGPUtil; // utilitaires
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory; // lecture d'objets
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator; // empreintes de cle
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder; // signataire
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider; // verificateur
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder; // dechiffrement de la cle

import java.io.IOException; // erreurs d'E/S
import java.io.InputStream; // flux
import java.io.OutputStream; // flux de sortie
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.security.Security; // fournisseurs
import java.util.Arrays; // effacement
import java.util.Date; // date
import java.util.Iterator; // parcours

/** Signature OpenPGP detachee (option, sans valeur eIDAS). */
public final class PgpService { // utilitaire statique

    /**
     * Resultat de verification.
     *
     * @param valid signature correcte
     * @param keyId identifiant de cle (hexadecimal)
     * @param userId identite de la cle
     * @param created date de signature
     */
    public record PgpVerification(boolean valid, String keyId, String userId, Date created) { } // enregistrement

    private PgpService() { } // pas d'instance

    /** Enregistre BouncyCastle si besoin. */
    public static void ensureProvider() { if (Security.getProvider("BC") == null) { Security.addProvider(new BouncyCastleProvider()); } } // fournisseur

    /** Signe un fichier ; ecrit "<fichier>.asc" (suffixe -2... si deja present). La phrase est effacee. */
    public static Path signDetached(Path file, Path secretKeyRing, char[] passphrase) { // signature
        ensureProvider(); // fournisseur
        try { // operation protegee
            PGPSecretKey sk = firstSigningKey(secretKeyRing); // cle de signature
            PGPPrivateKey pk; // cle privee
            try { // dechiffrement
                pk = sk.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(passphrase)); // phrase
            } catch (PGPException e) { // phrase fausse
                throw new PgpException("erreur.pgp.phrase", e); // erreur claire
            }
            PGPSignatureGenerator gen = new PGPSignatureGenerator(new JcaPGPContentSignerBuilder(sk.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256).setProvider("BC"), sk.getPublicKey()); // generateur SHA-256
            gen.init(PGPSignature.BINARY_DOCUMENT, pk); // document binaire
            try (InputStream in = Files.newInputStream(file)) { // lecture en flux
                byte[] buf = new byte[65536]; // tampon
                int n; // octets lus
                while ((n = in.read(buf)) > 0) { gen.update(buf, 0, n); } // empreinte progressive
            }
            Path out = FormatPlan.freeName(file.resolveSibling(file.getFileName() + ".asc")); // nom libre
            try (OutputStream os = new ArmoredOutputStream(Files.newOutputStream(out)); BCPGOutputStream b = new BCPGOutputStream(os)) { // sortie ASCII
                gen.generate().encode(b); // ecriture de la signature
            }
            return out; // fichier .asc
        } catch (IOException | PGPException e) { // fichier ou cle invalide
            throw new PgpException("erreur.pgp.cle", e); // erreur claire
        } finally { // toujours
            Arrays.fill(passphrase, '\0'); // effacement de la phrase
        }
    }

    /** Verifie une signature detachee avec une cle publique. */
    public static PgpVerification verify(Path file, Path asc, Path publicKeyRing) { // verification
        ensureProvider(); // fournisseur
        try (InputStream sigIn = PGPUtil.getDecoderStream(Files.newInputStream(asc)); InputStream keyIn = PGPUtil.getDecoderStream(Files.newInputStream(publicKeyRing))) { // lectures
            Object o = new JcaPGPObjectFactory(sigIn).nextObject(); // premier objet
            if (!(o instanceof PGPSignatureList list) || list.isEmpty()) { throw new PgpException("erreur.pgp.signature", null); } // pas une signature
            PGPSignature sig = list.get(0); // signature
            PGPPublicKey pub = new PGPPublicKeyRingCollection(keyIn, new JcaKeyFingerprintCalculator()).getPublicKey(sig.getKeyID()); // cle correspondante
            if (pub == null) { throw new PgpException("erreur.pgp.clepublique", null); } // cle inconnue
            sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), pub); // verificateur
            try (InputStream in = Files.newInputStream(file)) { // lecture du fichier
                byte[] buf = new byte[65536]; // tampon
                int n; // octets lus
                while ((n = in.read(buf)) > 0) { sig.update(buf, 0, n); } // empreinte progressive
            }
            Iterator<String> ids = pub.getUserIDs(); // identites
            String uid = ids.hasNext() ? ids.next() : ""; // premiere identite
            return new PgpVerification(sig.verify(), Long.toHexString(sig.getKeyID()).toUpperCase(), uid, sig.getCreationTime()); // resultat
        } catch (IOException | PGPException e) { // lecture ou format invalide
            throw new PgpException("erreur.pgp.signature", e); // erreur claire
        }
    }

    private static PGPSecretKey firstSigningKey(Path ring) throws IOException, PGPException { // premiere cle capable de signer
        try (InputStream in = PGPUtil.getDecoderStream(Files.newInputStream(ring))) { // lecture
            PGPSecretKeyRingCollection c = new PGPSecretKeyRingCollection(in, new JcaKeyFingerprintCalculator()); // anneaux
            for (PGPSecretKeyRing r : c) { // chaque anneau
                for (PGPSecretKey k : r) { if (k.isSigningKey()) { return k; } } // cle de signature
            }
        }
        throw new PgpException("erreur.pgp.cle", null); // aucune cle
    }
}
