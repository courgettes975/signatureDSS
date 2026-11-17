package fr.signaturedss.core.pgp; // paquet PGP

import org.bouncycastle.bcpg.ArmoredOutputStream; // sortie ASCII
import org.bouncycastle.bcpg.HashAlgorithmTags; // algorithmes d'empreinte
import org.bouncycastle.openpgp.PGPEncryptedData; // chiffrement symetrique
import org.bouncycastle.openpgp.PGPKeyPair; // paire PGP
import org.bouncycastle.openpgp.PGPPublicKey; // cle publique PGP
import org.bouncycastle.openpgp.PGPSecretKey; // cle secrete PGP
import org.bouncycastle.openpgp.PGPSignature; // signature PGP
import org.bouncycastle.openpgp.operator.PGPDigestCalculator; // empreinte
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder; // signataire
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder; // calculateur
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair; // conversion de paire
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder; // protection par phrase
import org.junit.jupiter.api.BeforeAll; // preparation
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.io.OutputStream; // flux de sortie
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.security.KeyPairGenerator; // generateur RSA
import java.util.Date; // date

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertFalse; // condition fausse
import static org.junit.jupiter.api.Assertions.assertThrows; // exception attendue
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class PgpServiceTest { // tests PGP

    private static final char[] PASS = "phrase de test".toCharArray(); // phrase secrete de test
    @TempDir static Path keys; // dossier des cles
    private static Path secretFile; // cle secrete
    private static Path publicFile; // cle publique

    @BeforeAll // genere une paire de cles
    static void generate() throws Exception { // generation
        PgpService.ensureProvider(); // fournisseur BouncyCastle
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA"); // RSA
        g.initialize(3072); // taille
        PGPKeyPair kp = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, g.generateKeyPair(), new Date()); // paire PGP
        PGPDigestCalculator sha1 = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1); // empreinte de controle
        PGPSecretKey sk = new PGPSecretKey(PGPSignature.DEFAULT_CERTIFICATION, kp, "Test <test@example.org>", sha1, null, null, // cle secrete
                new JcaPGPContentSignerBuilder(kp.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256), // auto-certification
                new JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, sha1).build(PASS.clone())); // protection
        secretFile = keys.resolve("secret.asc"); // fichier secret
        try (OutputStream o = new ArmoredOutputStream(Files.newOutputStream(secretFile))) { sk.encode(o); } // ecriture
        publicFile = keys.resolve("public.asc"); // fichier public
        try (OutputStream o = new ArmoredOutputStream(Files.newOutputStream(publicFile))) { sk.getPublicKey().encode(o); } // ecriture
    }

    @Test // signer puis verifier
    void signAndVerify(@TempDir Path dir) throws Exception { // cas nominal
        Path f = Files.writeString(dir.resolve("plan.dxf"), "0\nSECTION\n"); // fichier
        Path asc = PgpService.signDetached(f, secretFile, PASS.clone()); // signature
        assertEquals(dir.resolve("plan.dxf.asc"), asc); // nom
        PgpService.PgpVerification v = PgpService.verify(f, asc, publicFile); // verification
        assertTrue(v.valid()); // valide
        assertTrue(v.userId().contains("test@example.org")); // identite
    }

    @Test // fichier modifie : invalide
    void tamperedFileFails(@TempDir Path dir) throws Exception { // falsification
        Path f = Files.writeString(dir.resolve("a.txt"), "original"); // fichier
        Path asc = PgpService.signDetached(f, secretFile, PASS.clone()); // signature
        Files.writeString(f, "modifie"); // modification
        assertFalse(PgpService.verify(f, asc, publicFile).valid()); // invalide
    }

    @Test // mauvaise phrase secrete
    void wrongPassphraseClearError(@TempDir Path dir) throws Exception { // phrase fausse
        Path f = Files.writeString(dir.resolve("a.txt"), "x"); // fichier
        PgpException e = assertThrows(PgpException.class, () -> PgpService.signDetached(f, secretFile, "faux".toCharArray())); // echec
        assertEquals("erreur.pgp.phrase", e.key()); // cle de message
    }

    @Test // pas d'ecrasement d'une signature existante
    void existingAscGetsSuffix(@TempDir Path dir) throws Exception { // nom libre
        Path f = Files.writeString(dir.resolve("a.txt"), "x"); // fichier
        Files.writeString(dir.resolve("a.txt.asc"), "deja"); // existant
        assertEquals(dir.resolve("a.txt-2.asc"), PgpService.signDetached(f, secretFile, PASS.clone())); // suffixe
    }
}
