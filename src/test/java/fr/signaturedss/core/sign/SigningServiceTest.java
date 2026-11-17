package fr.signaturedss.core.sign; // paquet signature

import eu.europa.esig.dss.model.DSSException; // erreur DSS
import eu.europa.esig.dss.spi.x509.tsp.TSPSource; // interface TSA
import eu.europa.esig.dss.token.DSSPrivateKeyEntry; // cle privee
import eu.europa.esig.dss.token.Pkcs12SignatureToken; // jeton
import fr.signaturedss.core.OutputMode; // forme
import fr.signaturedss.core.SigningLevel; // niveau
import fr.signaturedss.core.trust.TrustContext; // confiance
import fr.signaturedss.core.tsa.FailoverTspSource; // bascule TSA
import fr.signaturedss.core.tsa.NamedTsp; // TSA nommee
import fr.signaturedss.core.tsa.TsaUnavailableException; // TSA indisponible
import fr.signaturedss.testpki.TestPki; // PKI de test
import org.apache.pdfbox.Loader; // chargeur PDF
import org.apache.pdfbox.pdmodel.PDDocument; // document PDF
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField; // champ de signature
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire
import org.junit.jupiter.params.ParameterizedTest; // test parametre
import org.junit.jupiter.params.provider.Arguments; // arguments
import org.junit.jupiter.params.provider.MethodSource; // source d'arguments

import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.util.ArrayList; // liste modifiable
import java.util.List; // liste
import java.util.stream.Stream; // flux

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertFalse; // condition fausse
import static org.junit.jupiter.api.Assertions.assertThrows; // exception attendue
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class SigningServiceTest { // tests de signature

    private static final TestPki PKI = TestPki.create(); // PKI partagee

    static Stream<Arguments> matrix() { // formats x niveaux
        List<Arguments> out = new ArrayList<>(); // combinaisons
        for (String kind : List.of("pdf", "dxf", "wav", "mp4")) { // formats
            for (SigningLevel l : SigningLevel.values()) { out.add(Arguments.of(kind, l)); } // niveaux
        }
        return out.stream(); // flux
    }

    private static Path sample(String kind, Path dir) throws Exception { // fabrique d'exemples
        return switch (kind) { // selon le format
            case "pdf" -> Samples.pdf(dir, "doc.pdf"); // PDF
            case "dxf" -> Samples.dxf(dir, "plan.dxf"); // DXF
            case "wav" -> Samples.wav(dir, "son.wav"); // audio
            default -> Samples.mp4(dir, "video.mp4"); // video
        };
    }

    private static SignRequest req(List<Path> files, SigningLevel l, OutputMode m, boolean visible, Pkcs12SignatureToken t) { // requete
        DSSPrivateKeyEntry key = t.getKeys().get(0); // premiere cle
        return new SignRequest(files, l, m, visible, key); // requete
    }

    @ParameterizedTest(name = "{0} {1}") // chaque format a chaque niveau
    @MethodSource("matrix") // combinaisons
    void signsEveryFormatAtEveryLevel(String kind, SigningLevel level, @TempDir Path dir) throws Exception { // matrice
        Path in = sample(kind, dir); // fichier d'entree
        String before = Samples.sha256(in); // empreinte avant
        SigningService s = new SigningService(TrustContext.of(PKI.verifier()), PKI.tsp()); // service
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton
            List<SignResult> r = s.sign(req(List.of(in), level, OutputMode.CADES_DETACHED, false, t), t); // signature
            assertEquals(1, r.size()); // un resultat
            assertTrue(Files.size(r.get(0).output()) > 0); // fichier produit
            List<Path> originals = r.get(0).plan().kind() == FormatPlan.Kind.CADES_DETACHED ? List.of(in) : List.of(); // original si detache
            PostSignCheck.PostSignOutcome o = new PostSignCheck(new fr.signaturedss.core.validate.ValidationService(TrustContext.of(PKI.verifier()))).check(r.get(0).output(), originals, r.get(0).plan().dssLevel()); // validation
            assertTrue(o.levelReached(), kind + " " + level + " -> " + o.found()); // niveau atteint
        }
        assertEquals(before, Samples.sha256(in)); // original intact
    }

    @Test // le message de panne TSA cite chaque service essaye
    void tsaDownMessageNamesEveryTsa(@TempDir Path dir) throws Exception { // message lisible
        Path dxf = Samples.dxf(dir, "plan.dxf"); // DXF
        TSPSource down = (a, d) -> { throw new DSSException("injoignable"); }; // TSA en panne
        SigningService s = new SigningService(TrustContext.of(PKI.verifier()), new FailoverTspSource(List.of(new NamedTsp("tsa-alpha", down), new NamedTsp("tsa-beta", down)))); // deux TSA
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton
            SigningException e = assertThrows(SigningException.class, () -> s.sign(req(List.of(dxf), SigningLevel.T, OutputMode.CADES_DETACHED, false, t), t)); // echec
            assertTrue(e.getMessage().contains("tsa-alpha"), e.getMessage()); // premier service cite
            assertTrue(e.getMessage().contains("tsa-beta"), e.getMessage()); // second service cite
        }
    }

    @Test // lot interrompu : les fichiers deja signes sont rapportes
    void batchFailureKeepsCompleted(@TempDir Path dir) throws Exception { // echec en cours de lot
        Path a = Samples.dxf(dir, "a.dxf"); // premier fichier
        Path b = Samples.dxf(dir, "b.dxf"); // second fichier
        TSPSource real = PKI.tsp(); // TSA de test
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger(); // compteur
        TSPSource once = (al, d) -> { if (calls.incrementAndGet() > 1) { throw new DSSException("coupure"); } return real.getTimeStampResponse(al, d); }; // repond une fois
        SigningService s = new SigningService(TrustContext.of(PKI.verifier()), once); // service
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton
            SigningException e = assertThrows(SigningException.class, () -> s.sign(req(List.of(a, b), SigningLevel.T, OutputMode.CADES_DETACHED, false, t), t)); // echec au second
            assertEquals(b, e.file()); // fichier en cause
            assertEquals(1, e.completed().size()); // premier fichier rapporte
            assertEquals(dir.resolve("a.dxf.p7s"), e.completed().get(0).output()); // sortie du premier
        }
    }

    @Test // ASiC-E produit un conteneur
    void asiceContainerProduced(@TempDir Path dir) throws Exception { // conteneur
        Path in = Samples.dxf(dir, "plan.dxf"); // DXF
        SigningService s = new SigningService(TrustContext.of(PKI.verifier()), PKI.tsp()); // service
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton
            Path out = s.sign(req(List.of(in), SigningLevel.LT, OutputMode.ASICE, false, t), t).get(0).output(); // signature
            assertTrue(out.getFileName().toString().endsWith(".asice")); // extension
            byte[] head = Files.readAllBytes(out); // contenu
            assertEquals('P', head[0]); // archive ZIP
            assertEquals('K', head[1]); // archive ZIP
        }
    }

    @Test // lot : une signature par fichier
    void batchSignsEachFileSeparately(@TempDir Path dir) throws Exception { // lot
        List<Path> files = List.of(Samples.dxf(dir, "a.dxf"), Samples.dxf(dir, "b.dxf"), Samples.pdf(dir, "c.pdf")); // trois fichiers
        SigningService s = new SigningService(TrustContext.of(PKI.verifier()), PKI.tsp()); // service
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton ouvert une fois
            List<SignResult> r = s.sign(req(files, SigningLevel.B, OutputMode.CADES_DETACHED, false, t), t); // signature
            assertEquals(3, r.size()); // trois resultats
            assertTrue(Files.exists(dir.resolve("a.dxf.p7s"))); // premier
            assertTrue(Files.exists(dir.resolve("b.dxf.p7s"))); // deuxieme
            assertTrue(Files.exists(dir.resolve("c-signe.pdf"))); // troisieme
        }
    }

    @Test // TSA en panne : aucune sortie (Review Focus 2)
    void tsaDownLeavesNoOutput(@TempDir Path dir) throws Exception { // panne TSA
        Path pdf = Samples.pdf(dir, "doc.pdf"); // PDF
        Path dxf = Samples.dxf(dir, "plan.dxf"); // DXF
        TSPSource down = (a, d) -> { throw new DSSException("injoignable"); }; // TSA en panne
        SigningService s = new SigningService(TrustContext.of(PKI.verifier()), new FailoverTspSource(List.of(new NamedTsp("A", down)))); // service
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton
            for (Path in : List.of(pdf, dxf)) { // chaque format
                SigningException e = assertThrows(SigningException.class, () -> s.sign(req(List.of(in), SigningLevel.T, OutputMode.CADES_DETACHED, false, t), t)); // echec
                assertTrue(hasCause(e, TsaUnavailableException.class)); // cause TSA
                assertEquals(in, e.file()); // fichier en cause
            }
        }
        try (Stream<Path> l = Files.list(dir)) { // contenu du dossier
            assertEquals(List.of("doc.pdf", "plan.dxf"), l.map(p -> p.getFileName().toString()).sorted().toList()); // aucune sortie ni fichier temporaire
        }
    }

    @Test // signature visible si demandee
    void visiblePdfSignatureWhenRequested(@TempDir Path dir) throws Exception { // apparence
        Path in = Samples.pdf(dir, "doc.pdf"); // PDF
        SigningService s = new SigningService(TrustContext.of(PKI.verifier()), PKI.tsp()); // service
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton
            Path out = s.sign(req(List.of(in), SigningLevel.B, OutputMode.CADES_DETACHED, true, t), t).get(0).output(); // signature visible
            try (PDDocument d = Loader.loadPDF(out.toFile())) { // lecture
                List<PDSignatureField> fields = d.getSignatureFields(); // champs
                assertEquals(1, fields.size()); // un champ
                assertTrue(fields.get(0).getWidgets().get(0).getRectangle().getWidth() > 0); // zone visible
            }
        }
    }

    @Test // signature invisible par defaut
    void invisiblePdfByDefault(@TempDir Path dir) throws Exception { // pas d'apparence
        Path in = Samples.pdf(dir, "doc.pdf"); // PDF
        SigningService s = new SigningService(TrustContext.of(PKI.verifier()), PKI.tsp()); // service
        try (Pkcs12SignatureToken t = PKI.signerToken()) { // jeton
            Path out = s.sign(req(List.of(in), SigningLevel.B, OutputMode.CADES_DETACHED, false, t), t).get(0).output(); // signature
            try (PDDocument d = Loader.loadPDF(out.toFile())) { // lecture
                PDSignatureField f = d.getSignatureFields().get(0); // champ
                assertFalse(f.getWidgets().get(0).getRectangle() != null && f.getWidgets().get(0).getRectangle().getWidth() > 0); // zone nulle
            }
        }
    }

    private static boolean hasCause(Throwable t, Class<?> type) { // cherche une cause
        for (Throwable c = t; c != null; c = c.getCause()) { if (type.isInstance(c)) { return true; } } // remontee
        return false; // absente
    }
}
