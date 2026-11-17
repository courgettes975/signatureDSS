package fr.signaturedss.testpki; // paquet de la PKI de test

import eu.europa.esig.dss.model.x509.CertificateToken; // certificat DSS
import eu.europa.esig.dss.pki.model.CertEntity; // entite de certificat
import eu.europa.esig.dss.pki.model.CertEntityRepository; // depot d'entites
import eu.europa.esig.dss.pki.model.CertEntityRevocation; // revocation d'une entite
import eu.europa.esig.dss.pki.x509.aia.PKIAIASource; // source AIA de test
import eu.europa.esig.dss.pki.x509.revocation.crl.PKICRLSource; // source CRL de test
import eu.europa.esig.dss.pki.x509.revocation.ocsp.PKIOCSPSource; // source OCSP de test
import eu.europa.esig.dss.pki.x509.tsp.PKITSPSource; // TSA de test
import eu.europa.esig.dss.enumerations.RevocationReason; // motif de revocation
import eu.europa.esig.dss.spi.validation.CertificateVerifier; // verificateur de certificats
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier; // implementation standard
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource; // source de confiance
import eu.europa.esig.dss.spi.x509.aia.AIASource; // interface AIA
import eu.europa.esig.dss.spi.x509.revocation.crl.CRLSource; // interface CRL
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OCSPSource; // interface OCSP
import eu.europa.esig.dss.spi.x509.tsp.TSPSource; // interface TSA
import eu.europa.esig.dss.token.Pkcs12SignatureToken; // jeton PKCS#12
import org.bouncycastle.asn1.x500.X500Name; // nom distinctif
import org.bouncycastle.asn1.x509.AccessDescription; // entree AIA
import org.bouncycastle.asn1.x509.AuthorityInformationAccess; // extension AIA
import org.bouncycastle.asn1.x509.BasicConstraints; // contraintes de base
import org.bouncycastle.asn1.x509.CRLDistPoint; // points de distribution CRL
import org.bouncycastle.asn1.x509.DistributionPoint; // point de distribution
import org.bouncycastle.asn1.x509.DistributionPointName; // nom du point
import org.bouncycastle.asn1.x509.ExtendedKeyUsage; // usage etendu
import org.bouncycastle.asn1.x509.Extension; // identifiants d'extension
import org.bouncycastle.asn1.x509.GeneralName; // nom general
import org.bouncycastle.asn1.x509.GeneralNames; // liste de noms
import org.bouncycastle.asn1.x509.KeyPurposeId; // usage etendu
import org.bouncycastle.asn1.x509.KeyUsage; // usage de cle
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter; // conversion en X509Certificate
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils; // identifiants de cle
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder; // constructeur de certificat
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder; // signataire

import java.io.ByteArrayOutputStream; // tampon memoire
import java.io.IOException; // erreurs d'E/S
import java.math.BigInteger; // numero de serie
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.security.KeyPair; // paire de cles
import java.security.KeyPairGenerator; // generateur de cles
import java.security.KeyStore; // magasin de cles
import java.security.KeyStore.PasswordProtection; // protection par mot de passe
import java.security.PublicKey; // cle publique
import java.security.cert.X509Certificate; // certificat Java
import java.util.ArrayList; // liste modifiable
import java.util.Date; // date
import java.util.HashMap; // table
import java.util.List; // liste
import java.util.Map; // table
import java.util.concurrent.atomic.AtomicLong; // compteur de serie

/** PKI de test entierement en memoire : racine, signataire, TSA, OCSP, CRL, AIA. */
public final class TestPki { // classe de test

    public static final char[] P12_PASSWORD = "test".toCharArray(); // mot de passe du PKCS#12 de test
    private static final AtomicLong SERIAL = new AtomicLong(System.currentTimeMillis()); // numeros de serie uniques
    private static final KeyPair ROOT_KEYS = rsa(); // cles de la racine (generees une fois)
    private static final KeyPair SIGNER_KEYS = rsa(); // cles du signataire
    private static final KeyPair TSA_KEYS = rsa(); // cles de la TSA

    private final TestCertEntity root; // racine
    private final TestCertEntity signer; // signataire
    private final TestCertEntity tsa; // TSA
    private final Repo repo; // depot d'entites
    private final CertificateToken hidden; // certificat dont la revocation est masquee (ou null)

    private TestPki(TestCertEntity root, TestCertEntity signer, TestCertEntity tsa, Repo repo, CertificateToken hidden) { // constructeur
        this.root = root; // racine
        this.signer = signer; // signataire
        this.tsa = tsa; // TSA
        this.repo = repo; // depot
        this.hidden = hidden; // certificat masque
    }

    /** Genere une nouvelle PKI. */
    public static TestPki create() { // fabrique
        try { // generation protegee
            X509Certificate rootX = cert("CN=Racine Test SignatureDSS,O=Test,C=FR", ROOT_KEYS.getPublic(), new X500Name("CN=Racine Test SignatureDSS,O=Test,C=FR"), ROOT_KEYS, true, false); // racine autosignee
            CertificateToken rootT = new CertificateToken(rootX); // jeton racine
            TestCertEntity root = new TestCertEntity(ROOT_KEYS.getPrivate(), rootT, List.of(rootT), null); // entite racine
            X509Certificate signerX = cert("CN=Signataire Test,O=Test,C=FR", SIGNER_KEYS.getPublic(), X500Name.getInstance(rootX.getSubjectX500Principal().getEncoded()), ROOT_KEYS, false, false); // signataire
            CertificateToken signerT = new CertificateToken(signerX); // jeton signataire
            TestCertEntity signer = new TestCertEntity(SIGNER_KEYS.getPrivate(), signerT, List.of(signerT, rootT), root); // entite signataire
            X509Certificate tsaX = cert("CN=TSA Test,O=Test,C=FR", TSA_KEYS.getPublic(), X500Name.getInstance(rootX.getSubjectX500Principal().getEncoded()), ROOT_KEYS, false, true); // TSA
            CertificateToken tsaT = new CertificateToken(tsaX); // jeton TSA
            TestCertEntity tsa = new TestCertEntity(TSA_KEYS.getPrivate(), tsaT, List.of(tsaT, rootT), root); // entite TSA
            Repo repo = new Repo(); // depot
            repo.add(root); // ajout racine
            repo.add(signer); // ajout signataire
            repo.add(tsa); // ajout TSA
            return new TestPki(root, signer, tsa, repo, null); // PKI complete
        } catch (Exception e) { // erreur de generation
            throw new IllegalStateException(e); // echec du test
        }
    }

    /** Variante dont OCSP et CRL ne repondent pas pour ce certificat. */
    public TestPki withoutRevocationFor(CertificateToken cert) { return new TestPki(root, signer, tsa, repo, cert); } // copie avec masque

    /** Revoque un certificat de la PKI. */
    public void revoke(CertificateToken cert) { repo.revoked.put(cert, new CertEntityRevocation(new Date(), RevocationReason.KEY_COMPROMISE)); } // revocation

    public CertificateToken root() { return root.getCertificateToken(); } // certificat racine
    public CertificateToken signer() { return signer.getCertificateToken(); } // certificat signataire
    public CertificateToken tsaCert() { return tsa.getCertificateToken(); } // certificat TSA

    /** Jeton PKCS#12 en memoire contenant le signataire. */
    public Pkcs12SignatureToken signerToken() { return new Pkcs12SignatureToken(p12Bytes(), new PasswordProtection(P12_PASSWORD.clone())); } // jeton

    /** Ecrit le PKCS#12 du signataire dans un dossier et renvoie son chemin. */
    public Path signerP12(Path dir) throws IOException { // export fichier
        Path p = dir.resolve("signataire-test.p12"); // chemin cible
        Files.write(p, p12Bytes()); // ecriture
        return p; // chemin
    }

    private byte[] p12Bytes() { // serialise le PKCS#12
        try { // operation protegee
            KeyStore ks = KeyStore.getInstance("PKCS12"); // magasin PKCS#12
            ks.load(null, null); // magasin vide
            X509Certificate[] chain = { signer.getCertificateToken().getCertificate(), root.getCertificateToken().getCertificate() }; // chaine Java
            ks.setKeyEntry("signataire", signer.getPrivateKey(), P12_PASSWORD, chain); // entree de cle
            ByteArrayOutputStream out = new ByteArrayOutputStream(); // tampon
            ks.store(out, P12_PASSWORD); // serialisation
            return out.toByteArray(); // octets
        } catch (Exception e) { // erreur
            throw new IllegalStateException(e); // echec
        }
    }

    /** TSA de test. */
    public TSPSource tsp() { // source d'horodatage
        PKITSPSource s = new PKITSPSource(tsa); // TSA sur l'entite
        s.setTsaPolicy("1.2.3.4"); // politique obligatoire
        return s; // source
    }

    /** OCSP de test, muet pour le certificat masque. */
    public OCSPSource ocsp() { // source OCSP
        PKIOCSPSource real = new PKIOCSPSource(repo); // OCSP signe par l'emetteur
        return (c, i) -> c.equals(hidden) ? null : real.getRevocationToken(c, i); // masquage eventuel
    }

    /** CRL de test, muette pour le certificat masque. */
    public CRLSource crl() { // source CRL
        PKICRLSource real = new PKICRLSource(repo); // CRL signee par l'emetteur
        return (c, i) -> c.equals(hidden) ? null : real.getRevocationToken(c, i); // masquage eventuel
    }

    /** AIA de test. */
    public AIASource aia() { return new PKIAIASource(repo); } // source AIA

    /** Verificateur qui fait confiance a la racine de test. */
    public CertificateVerifier verifier() { return verifier(true); } // racine de confiance

    /** Verificateur, avec ou sans confiance dans la racine. */
    public CertificateVerifier verifier(boolean trustRoot) { // construction
        CommonCertificateVerifier v = new CommonCertificateVerifier(); // verificateur
        if (trustRoot) { // racine de confiance
            CommonTrustedCertificateSource trusted = new CommonTrustedCertificateSource(); // source de confiance
            trusted.addCertificate(root()); // ajout de la racine
            v.setTrustedCertSources(trusted); // enregistrement
        }
        v.setOcspSource(ocsp()); // OCSP
        v.setCrlSource(crl()); // CRL
        v.setAIASource(aia()); // AIA
        return v; // verificateur pret
    }

    private static KeyPair rsa() { // genere une paire RSA 3072
        try { // generation protegee
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA"); // generateur RSA
            g.initialize(3072); // taille de cle
            return g.generateKeyPair(); // paire
        } catch (Exception e) { // erreur
            throw new IllegalStateException(e); // echec
        }
    }

    private static X509Certificate cert(String subject, PublicKey pub, X500Name issuer, KeyPair issuerKeys, boolean ca, boolean tsa) throws Exception { // genere un certificat
        Date from = new Date(System.currentTimeMillis() - 86_400_000L); // valide depuis hier
        Date to = new Date(System.currentTimeMillis() + 2L * 365 * 86_400_000L); // valide deux ans
        JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(SERIAL.incrementAndGet()), from, to, new X500Name(subject), pub); // constructeur
        JcaX509ExtensionUtils u = new JcaX509ExtensionUtils(); // utilitaires d'extension
        b.addExtension(Extension.subjectKeyIdentifier, false, u.createSubjectKeyIdentifier(pub)); // identifiant de cle sujet
        b.addExtension(Extension.authorityKeyIdentifier, false, u.createAuthorityKeyIdentifier(issuerKeys.getPublic())); // identifiant de cle emetteur
        if (ca) { // certificat d'autorite
            b.addExtension(Extension.basicConstraints, true, new BasicConstraints(true)); // CA
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign)); // signature de certificats et CRL
        } else { // certificat final
            b.addExtension(Extension.basicConstraints, true, new BasicConstraints(false)); // non CA
            b.addExtension(Extension.keyUsage, true, new KeyUsage(tsa ? KeyUsage.digitalSignature : KeyUsage.digitalSignature | KeyUsage.nonRepudiation)); // usages
            if (tsa) { // TSA
                b.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping)); // horodatage
            }
            b.addExtension(Extension.authorityInfoAccess, false, new AuthorityInformationAccess(new AccessDescription[] { // AIA
                    new AccessDescription(AccessDescription.id_ad_ocsp, new GeneralName(GeneralName.uniformResourceIdentifier, "http://pki.test.local/ocsp")), // OCSP fictif
                    new AccessDescription(AccessDescription.id_ad_caIssuers, new GeneralName(GeneralName.uniformResourceIdentifier, "http://pki.test.local/root.crt")) })); // emetteur fictif
            DistributionPoint dp = new DistributionPoint(new DistributionPointName(new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier, "http://pki.test.local/root.crl"))), null, null); // point CRL
            b.addExtension(Extension.cRLDistributionPoints, false, new CRLDistPoint(new DistributionPoint[] { dp })); // CRL fictive
        }
        return new JcaX509CertificateConverter().getCertificate(b.build(new JcaContentSignerBuilder("SHA256withRSA").build(issuerKeys.getPrivate()))); // signature et conversion
    }

    /** Depot d'entites en memoire. */
    private static final class Repo implements CertEntityRepository<TestCertEntity> { // implementation simple
        private final Map<CertificateToken, TestCertEntity> byCert = new HashMap<>(); // index par certificat
        private final Map<CertificateToken, CertEntityRevocation> revoked = new HashMap<>(); // revocations

        void add(TestCertEntity e) { byCert.put(e.getCertificateToken(), e); } // ajout

        @Override public TestCertEntity getByCertificateToken(CertificateToken t) { return byCert.get(t); } // recherche

        @Override public Map<TestCertEntity, CertEntityRevocation> getRevocationList(TestCertEntity parent) { // revocations des enfants
            Map<TestCertEntity, CertEntityRevocation> out = new HashMap<>(); // resultat
            for (TestCertEntity e : new ArrayList<>(byCert.values())) { // parcours
                if (e.issuer() == parent && revoked.containsKey(e.getCertificateToken())) { // enfant revoque
                    out.put(e, revoked.get(e.getCertificateToken())); // ajout
                }
            }
            return out; // liste
        }

        @Override public CertEntityRevocation getRevocation(TestCertEntity e) { return revoked.get(e.getCertificateToken()); } // revocation d'une entite

        @Override public CertEntity getIssuer(TestCertEntity e) { return e.issuer(); } // emetteur
    }
}
