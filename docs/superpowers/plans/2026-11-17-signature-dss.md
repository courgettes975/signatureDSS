# SignatureDSS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A portable desktop application (Windows, macOS, Debian) that signs PDF, DXF, audio and video files in PAdES / CAdES / ASiC-E at levels B, T, LT, LTA. It also validates and extends signatures with DSS 6.5.1, and offers optional OpenPGP signatures.

**Architecture:** There is one Maven module in Java 21. A UI-free `core` layer wraps DSS: tokens, trust, TSA chain, sign, validate, extend, PGP. A thin JavaFX `ui` layer talks to it through view models. `jpackage` builds one app-image per OS with an embedded runtime. GitHub Actions builds the three systems. All writable state lives in `donnees/` next to the app.

**Tech Stack:**
- Java 21 (Temurin) and OpenJFX 21.0.13
- DSS 6.5.1 (`eu.europa.ec.joinup.sd-dss`), which pulls in BouncyCastle 1.86 and PDFBox 3.0.8
- `bcpg-jdk18on` 1.86
- `fop-core` 2.11, the version DSS 6.5.1 pins
- `slf4j-simple` 2.0.20, the slf4j version DSS 6.5.1 pins
- JUnit 6.1.3 and Maven

**Spec:** `docs/superpowers/specs/2026-11-17-signature-dss-design.md`

**Reference implementation (read before writing the matching class):** `esig/dss-demonstrations`, tag `6.5.1`, module `dss-standalone-app`. It is the official DSS JavaFX app. Its `TLValidationJobExecutor`, `CertificateVerifierBuilder`, `TSPSourceLoader`, `FOPService` and `SigningTask` show the exact DSS wiring. Do not copy its UI: our UI follows the spec.

## Global Constraints

- DSS version `6.5.1` exactly, for every `eu.europa.ec.joinup.sd-dss` artifact. BouncyCastle artifacts at `1.86`, matching DSS.
- Java 21. The app runs from its own folder without installation or admin rights.
- **Every line of source code carries a comment** (explicit user requirement). Write comments in French.
- The UI text is French by default, and every user-visible string comes from `messages_*.properties`. No hard-coded UI text.
- The original file is never modified or overwritten. Outputs never overwrite an existing file.
- Certificate passwords, PINs and TSA passwords are never written to disk or to logs. They live only in memory, as `char[]`, wiped after use.
- All writable data goes under `donnees/`: `config.properties`, `cache/tsl/`, `ancres/`, `logs/`. Nothing goes in the user profile or the temp dir. This rule also applies to the LOTL cache: unlike the reference app, it does not use `java.io.tmpdir`.
- Default level is LT. PDF uses PAdES into a copy `<nom>-signe.pdf`. Other files default to CAdES detached `<fichier.ext>.p7s`. ASiC-E (`<fichier.ext>.asice`) is optional.
- The PDF visible signature is off by default.
- LOTL URL: `https://ec.europa.eu/tools/lotl/eu-lotl.xml`. OJ URL: `https://eur-lex.europa.eu/eli/C/2026/1944/oj`. OJ keystore: `keystore.p12` from `dss-standalone-app/src/main/resources/` at tag 6.5.1, type PKCS12, password `dss-password`. These values were read from that app's `app.config`.
- Package root: `fr.signaturedss`.

## Review Focus

1. **Paths with spaces, accents or a changed drive letter.** Examples: `E:\Mes documents\Plan étage.dxf`, or the USB stick mounting as `F:` next time. Everything must resolve relative to the app, and output names keep the accents. Test in Task 1 and Task 6.
2. **TSA unreachable while signing at T, LT or LTA.** A clear error names every TSA tried. No partial or B-level output file is left behind. Test in Task 7.
3. **Wrong PIN on the Feitian token.** Exactly one login attempt per user action, never an automatic retry, because tokens lock after a few failures. The message says the PIN was refused. Test in Task 11.
4. **Signing a file whose output name already exists** (re-signing). The app writes `<nom>-signe-2.pdf` or `<fichier.ext>-2.p7s` and never overwrites. Test in Task 6.
5. **Validating a detached `.p7s` without its original.** A clear message asks for the original file, instead of a DSS exception or a misleading FAILED. Test in Task 9.

---

### Task 1: Project skeleton, portable paths, translations

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/fr/signaturedss/Launcher.java`
- Create: `src/main/java/fr/signaturedss/core/PortablePaths.java`
- Create: `src/main/java/fr/signaturedss/i18n/Messages.java`
- Create: `src/main/resources/i18n/messages_fr.properties`
- Create: `src/main/resources/i18n/messages_en.properties`
- Create: `.gitignore`
- Test: `src/test/java/fr/signaturedss/core/PortablePathsTest.java`
- Test: `src/test/java/fr/signaturedss/i18n/MessagesTest.java`

**Interfaces:**
- Produces:
  - `PortablePaths.fromAppLocation(Path appLocation): PortablePaths` exposes `root()`, `dataDir()`, `config()`, `tslCache()`, `anchorsDir()` and `logsDir()`, and creates the directories.
  - `PortablePaths.detect(): PortablePaths` uses the system property `signaturedss.home` if set. Otherwise it walks up from the code-source location to the first folder that contains `donnees/`, or creates `donnees/` next to the app folder. On macOS this means next to `SignatureDSS.app`, never inside it.
  - `Messages.get(String key, Object... args): String`, `Messages.setLocale(Locale)` and `Messages.available(): List<Locale>`.

- [ ] **Step 1: Write the `pom.xml`.**
  - Dependencies, all at `6.5.1`: `dss-pades-pdfbox`, `dss-cades`, `dss-asic-cades`, `dss-xades`, `dss-jades`, `dss-asic-xades`, `dss-token`, `dss-service`, `dss-tsl-validation`, `dss-validation`, `dss-utils-apache-commons`, `dss-crl-parser-x509crl`.
  - Also: `bcpg-jdk18on:1.86`, `fop-core:2.11`, `slf4j-simple:2.0.20`, and `javafx-controls` plus `javafx-fxml` at `21.0.13`.
  - Test scope: `dss-pki-factory:6.5.1` and JUnit `6.1.3`.
  - Set `maven.compiler.release=21`, and copy runtime dependencies to `target/libs` (`maven-dependency-plugin`).
- [ ] **Step 2: Write the failing tests.**

```java
@Test void dataDirIsSiblingOfAppFolder(@TempDir Path usb) {
    Path app = Files.createDirectories(usb.resolve("SignatureDSS/Windows"));
    PortablePaths p = PortablePaths.fromAppLocation(app);
    assertEquals(usb.resolve("SignatureDSS/donnees"), p.dataDir());
    assertTrue(Files.isDirectory(p.tslCache()));
    assertTrue(Files.isDirectory(p.anchorsDir()));
}
@Test void macBundleNeverWritesInsideApp(@TempDir Path usb) {
    Path app = Files.createDirectories(usb.resolve("SignatureDSS/macOS/SignatureDSS.app/Contents/app"));
    assertEquals(usb.resolve("SignatureDSS/donnees"), PortablePaths.fromAppLocation(app).dataDir());
}
@Test void accentsAndSpacesSurvive(@TempDir Path usb) {   // Review Focus 1
    Path app = Files.createDirectories(usb.resolve("Clé USB/Signature DSS/Windows"));
    assertTrue(PortablePaths.fromAppLocation(app).dataDir().toString().contains("Clé USB"));
}
// MessagesTest
@Test void frAndEnHaveSameKeys() { assertEquals(keysOf("fr"), keysOf("en")); }
@Test void defaultIsFrench() { assertEquals(Locale.FRENCH, Messages.currentLocale()); }
```

- [ ] **Step 3: Run `mvn -q test`.** Expected: FAIL, classes missing.
- [ ] **Step 4: Implement `PortablePaths`, `Messages` and `Launcher`.**
  - `PortablePaths` rule: if the app folder is named `Windows`, `Linux` or `macOS`, or sits inside `*.app`, then `donnees/` is created under the parent of that platform folder.
  - `Messages` uses `ResourceBundle` with base name `i18n.messages`, encoded in UTF-8.
  - `Launcher.main` sets `org.slf4j.simpleLogger.logFile` to `logsDir()/signaturedss.log` before any logger is created, then calls `App.main`. Until Task 13 exists, a stub is enough.
- [ ] **Step 5: Run `mvn -q test`.** Expected: PASS.
- [ ] **Step 6: Commit.** Run `git init` if needed, then commit with `feat: squelette, chemins portables, i18n`.

### Task 2: Test PKI (test code only)

**Files:**
- Create: `src/test/java/fr/signaturedss/testpki/TestCertEntity.java`
- Create: `src/test/java/fr/signaturedss/testpki/TestPki.java`
- Test: `src/test/java/fr/signaturedss/testpki/TestPkiTest.java`

**Interfaces:**
- Consumes:
  - DSS `eu.europa.esig.dss.pki.model.CertEntity` (methods `getPrivateKey`, `getCertificateChain`, `getCertificateToken`, `getEncryptionAlgorithm`);
  - DSS `CertEntityRepository<T>` (methods `getByCertificateToken`, `getRevocationList`, `getRevocation`, `getIssuer`);
  - `PKITSPSource(CertEntity)`, `PKIOCSPSource(repo)`, `PKICRLSource(repo)`, `PKIAIASource(repo)`.
- Produces:
  - `TestPki.create(): TestPki`. It generates with BouncyCastle a root CA, a signer cert (RSA 3072) and a TSA cert with the `timeStamping` extended key usage. All three are valid from now minus 1 day to now plus 2 years.
  - Accessors `root(): CertificateToken`, `signerToken(): Pkcs12SignatureToken` (in-memory PKCS#12, password `test`), `signerP12(Path dir): Path`, `tsp(): TSPSource`, `ocsp(): OCSPSource`, `crl(): CRLSource`, `aia(): AIASource`.
  - `verifier(): CertificateVerifier`, with the root trusted and the sources above.
  - `revoke(CertificateToken)` and `withoutRevocationFor(CertificateToken): TestPki`, a variant whose OCSP and CRL sources return null for that cert.

- [ ] **Step 1: Write the failing test.** `signerChainBuildsAndTsaAnswers()`: build the PKI and assert two things. The signer chain has length 2, ending at `root()`. `tsp().getTimeStampResponse(DigestAlgorithm.SHA256, sha256("x"))` is not null.
- [ ] **Step 2: Run `mvn -q test -Dtest=TestPkiTest`.** Expected: FAIL.
- [ ] **Step 3: Implement `TestCertEntity` (record-like, Serializable) and `TestPki`.** The repository is an in-memory `Map<CertificateToken, TestCertEntity>`.
- [ ] **Step 4: Run the test.** Expected: PASS.
- [ ] **Step 5: Commit** with `test: PKI de test en memoire`.

### Task 3: Settings

**Files:**
- Create: `src/main/java/fr/signaturedss/core/Settings.java`
- Create: `src/main/java/fr/signaturedss/core/TsaConfig.java`
- Create: `src/main/java/fr/signaturedss/core/SigningLevel.java`
- Create: `src/main/java/fr/signaturedss/core/OutputMode.java`
- Test: `src/test/java/fr/signaturedss/core/SettingsTest.java`

**Interfaces:**
- Produces:
  - `enum SigningLevel { B, T, LT, LTA }`.
  - `enum OutputMode { CADES_DETACHED, ASICE }`.
  - `record TsaConfig(String url, String username)`. No password field: passwords are asked once per session and kept in memory only.
  - `Settings.load(Path configFile): Settings` and `save(Path)`, with getters and setters for:
    - `List<TsaConfig> tsas()`
    - `SigningLevel defaultLevel()`, default LT
    - `OutputMode defaultOutputMode()`, default CADES_DETACHED
    - `boolean pdfVisibleSignature()`, default false
    - `Optional<Path> pkcs11Library()`
    - `Optional<Path> lastP12()`
    - `Locale locale()`, default fr
  - Storage is `java.util.Properties` in UTF-8. Paths are stored relative to `PortablePaths.root()` when they live on the stick.

- [ ] **Step 1: Write the failing tests.**
  - `defaultsWhenFileMissing()`: LT, CADES_DETACHED, visible signature false, French, empty TSA list.
  - `roundTripKeepsTsaOrder()`.
  - `neverWritesPasswordKeys()`: the saved file contains no key matching `(?i)pass|pin|secret`.
  - `relativePathSurvivesDriveLetterChange()` (Review Focus 1): save with root `E:\SignatureDSS`, load with root `F:\SignatureDSS`, and assert that `lastP12` resolves under `F:`.
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement the classes.**
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** with `feat: parametres portables`.

### Task 4: TSA chain with failover

**Files:**
- Create: `src/main/java/fr/signaturedss/core/tsa/FailoverTspSource.java`
- Create: `src/main/java/fr/signaturedss/core/tsa/TsaFactory.java`
- Create: `src/main/java/fr/signaturedss/core/tsa/TsaUnavailableException.java`
- Test: `src/test/java/fr/signaturedss/core/tsa/FailoverTspSourceTest.java`

**Interfaces:**
- Consumes: `TsaConfig` (Task 3), `TestPki.tsp()` (Task 2).
- Produces:
  - `FailoverTspSource implements eu.europa.esig.dss.spi.x509.tsp.TSPSource`, built with `new FailoverTspSource(List<NamedTsp> sources)`, where `record NamedTsp(String label, TSPSource source)`. It tries the sources in order and returns the first non-null response. If all fail, it throws `TsaUnavailableException`, whose `tried(): List<String>` lists the labels and whose message key is `erreur.tsa.indisponible`.
  - `TsaFactory.create(TsaConfig cfg, char[] passwordOrNull): NamedTsp`. It builds an `OnlineTSPSource(url, TimestampDataLoader)`. When a username is set, it calls `addAuthentication(host, port, scheme, UserCredentials)`; verify the exact `UserCredentials` constructor in `dss-spi` 6.5.1 before writing it. Timeouts are 10 s.
  - `TsaFactory.test(NamedTsp): boolean` sends a SHA-256 request on a fixed digest.

- [ ] **Step 1: Write the failing tests.**
  - `firstFailingSecondAnswers()`: a stub that throws, then `TestPki.tsp()`; assert a response and that the second source was used.
  - `allFailingListsEveryLabel()`: two throwing stubs. Assert `TsaUnavailableException`, with `tried()` equal to `["A","B"]`.
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement the classes.**
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** with `feat: TSA avec bascule`.

### Task 5: Trust context (LOTL, user anchors, verifier)

**Files:**
- Create: `src/main/java/fr/signaturedss/core/trust/LotlService.java`
- Create: `src/main/java/fr/signaturedss/core/trust/TrustContext.java`
- Create: `src/main/resources/trust/oj-keystore.p12`, copied from the reference app (see Global Constraints)
- Test: `src/test/java/fr/signaturedss/core/trust/TrustContextTest.java`

**Interfaces:**
- Consumes: `PortablePaths` (Task 1).
- Produces:
  - `LotlService(PortablePaths)` with three methods:
    - `refreshOnline()`, which uses the `TLValidationJob` wiring of the reference `TLValidationJobExecutor`, with the cache at `tslCache()`;
    - `refreshOffline()`;
    - `certificateSource(): TrustedListsCertificateSource`.
  - `TrustContext.online(PortablePaths, LotlService): TrustContext` builds a `CommonCertificateVerifier` with:
    - `OnlineOCSPSource`, `OnlineCRLSource` and the default AIA source;
    - trusted sources `[LOTL source, user anchors]`, where user anchors are every `.cer`, `.crt` or `.pem` file in `anchorsDir()`, loaded into a `CommonTrustedCertificateSource`;
    - `setCheckRevocationForUntrustedChains(true)`.
  - `TrustContext.of(CertificateVerifier): TrustContext`, used by tests.
  - `verifier(): CertificateVerifier` and `anchorCount(): int`.

- [ ] **Step 1: Write the failing tests.**
  - `userAnchorsLoadedFromFolder()`: write `TestPki.root()` as PEM into `anchorsDir()`, and assert `anchorCount() == 1`.
  - `offlineRefreshWithEmptyCacheDoesNotThrow()`.
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement the classes.** At startup, the app runs `refreshOffline()` first and then `refreshOnline()` in the background (Task 13).
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** with `feat: confiance LOTL et ancres`.

### Task 6: Format resolution and output naming

**Files:**
- Create: `src/main/java/fr/signaturedss/core/sign/FormatPlan.java`
- Test: `src/test/java/fr/signaturedss/core/sign/FormatPlanTest.java`

**Interfaces:**
- Consumes: `SigningLevel`, `OutputMode` (Task 3).
- Produces:
  - `record FormatPlan(Kind kind, SignatureLevel dssLevel, Path output)` with `enum Kind { PADES, CADES_DETACHED, ASICE_CADES }`.
  - `FormatPlan.resolve(Path input, SigningLevel level, OutputMode mode): FormatPlan`. A PDF, detected by its `%PDF-` header rather than its extension, always gives PADES. Level mapping:
    - PADES maps B, T, LT, LTA to `PAdES_BASELINE_B`, `_T`, `_LT`, `_LTA`;
    - the other kinds map them to `CAdES_BASELINE_B`, `_T`, `_LT`, `_LTA`.
  - Output names: `<nom>-signe.pdf`, `<fichier.ext>.p7s` and `<fichier.ext>.asice`, in the same folder. When a name is taken, add `-2`, `-3` and so on before the last extension.

- [ ] **Step 1: Write the failing tests.**
  - `pdfByHeaderNotExtension()`: a file `x.bin` that starts with `%PDF-` gives PADES.
  - `dxfDefaultsToDetached()`: `plan.dxf` with LT gives `CAdES_BASELINE_LT` and `plan.dxf.p7s`.
  - `asiceMode()`.
  - `existingOutputGetsSuffix()` (Review Focus 4): `rapport-signe.pdf` already exists, so the output is `rapport-signe-2.pdf`.
  - `accentedNameKept()` (Review Focus 1): `Plan étage.dxf` gives `Plan étage.dxf.p7s`.
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement the class.**
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** with `feat: choix du format et nommage`.

### Task 7: Signing service

**Files:**
- Create: `src/main/java/fr/signaturedss/core/sign/SigningService.java`
- Create: `src/main/java/fr/signaturedss/core/sign/SignRequest.java`
- Create: `src/main/java/fr/signaturedss/core/sign/SignResult.java`
- Test: `src/test/java/fr/signaturedss/core/sign/SigningServiceTest.java`
- Test resources: generate small sample files in the test (a minimal PDF via PDFBox, DXF text, a WAV header plus silence, and MP4 `ftyp` bytes). Do not commit binaries.

**Interfaces:**
- Consumes: `FormatPlan` (Task 6), `TrustContext` (Task 5), `TSPSource` (Task 4), DSS `SignatureTokenConnection` and `DSSPrivateKeyEntry`.
- Produces:
  - `record SignRequest(List<Path> files, SigningLevel level, OutputMode mode, boolean pdfVisible, DSSPrivateKeyEntry key)`.
  - `record SignResult(Path input, Path output, FormatPlan plan)`.
  - `new SigningService(TrustContext trust, TSPSource tsp)`.
  - `sign(SignRequest req, SignatureTokenConnection token): List<SignResult>`. Each file is signed separately with the one already-open token, so the PIN is asked once per batch. It uses `PAdESService`, `CAdESService` (packaging `DETACHED`) or `ASiCWithCAdESService` (`ASiCContainerType.ASiC_E`), following the `getDataToSign`, `token.sign` and `signDocument` sequence. Inputs are `FileDocument`, so large files are streamed.
  - A failure on a file stops the batch, deletes any output already produced for that file, and is thrown as a `SigningException` carrying the file name.

- [ ] **Step 1: Write the failing tests.**
  - `@ParameterizedTest signsEveryFormatAtEveryLevel(kind, level)`: the four samples times B, T, LT, LTA, using `TestPki`. Assert that the output exists and that the SHA-256 of the input is unchanged.
  - `batchSignsEachFileSeparately()`: three files give three outputs.
  - `tsaDownLeavesNoOutput()` (Review Focus 2): with a `FailoverTspSource` of only failing stubs and level T, assert `SigningException` caused by `TsaUnavailableException`, and that no `*-signe*.pdf` or `.p7s` file exists afterwards.
  - `visiblePdfSignatureWhenRequested()`: the PDF has one signature field with a non-empty widget rectangle.
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement the classes.** Visible signature: `SignatureImageParameters` plus `SignatureImageTextParameters`, with the text `Signé par <CN> le <date>`, on the last page, bottom right.
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** with `feat: signature PAdES CAdES ASiC-E`.

### Task 8: Pre-flight revocation check and post-sign check

**Files:**
- Create: `src/main/java/fr/signaturedss/core/sign/RevocationPreflight.java`
- Create: `src/main/java/fr/signaturedss/core/sign/PostSignCheck.java`
- Test: `src/test/java/fr/signaturedss/core/sign/PreflightAndPostCheckTest.java`

**Interfaces:**
- Consumes: `TrustContext`, `TSPSource`, `ValidationService` (Task 9). **Execute Task 9 before Task 8.**
- The Settings tab (Task 13) uses `TsaFactory.test` for its "Tester" button.
- Produces:
  - `RevocationPreflight.check(List<CertificateToken> signerChain, TSPSource tsp, CertificateVerifier v): PreflightResult`, with `record PreflightResult(boolean revocationAvailable, List<String> missingSubjects)`. For every non-anchor cert of the signer chain, and of the TSA chain taken from a fresh timestamp token, it asks the OCSP source and then the CRL source of `v` for a revocation token against the issuer.
  - `PostSignCheck.check(Path output, List<Path> originals, SignatureLevel requested): PostSignOutcome`, with `record PostSignOutcome(boolean levelReached, SignatureLevel found, Indication verdict, List<String> warnings)`.
    - `levelReached` is false when the diagnostic data reports a lower format level than requested. That is an error in the UI.
    - A verdict other than `TOTAL_PASSED` produces a warning, not an error.

- [ ] **Step 1: Write the failing tests.**
  - `preflightOkWithFullPki()`.
  - `preflightReportsMissingRevocation()`: `TestPki.withoutRevocationFor(signer)` gives false, with the signer subject in the missing list.
  - `postCheckDetectsLevel()`: an LT signature asked as LT gives `levelReached`.
  - `postCheckFlagsUntrusted()`: a verifier without the root trusted gives a verdict other than `TOTAL_PASSED` and a warning.
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement the classes.**
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** with `feat: controles avant et apres signature`.

### Task 9: Validation and report export

**Files:**
- Create: `src/main/java/fr/signaturedss/core/validate/ValidationService.java`
- Create: `src/main/java/fr/signaturedss/core/validate/ValidationOutcome.java`
- Create: `src/main/java/fr/signaturedss/core/validate/ReportExporter.java`
- Create: `src/main/resources/fop/fop.xconf` and the fonts, following the reference `FOPService`
- Test: `src/test/java/fr/signaturedss/core/validate/ValidationServiceTest.java`

**Interfaces:**
- Consumes: `TrustContext` (Task 5).
- Produces:
  - `ValidationService(TrustContext)`.
  - `validate(Path signed, List<Path> originals): ValidationOutcome`. It uses `SignedDocumentValidator.fromDocument`, then `setCertificateVerifier`, `setDetachedContents` and `validateDocument()`, with the default policy.
  - `record ValidationOutcome(Reports reports, Indication verdict, SignatureLevel level, Optional<Date> lastTimestampExpiry)`. `lastTimestampExpiry` is the latest `getNotAfter()` of the signing certificates of the timestamps in `DiagnosticData`.
  - `class MissingOriginalException`, message key `erreur.original.manquant`. It is thrown when the document is a detached CAdES or a detached XAdES and `originals` is empty.
  - `ReportExporter.exportXml(Reports, Path dir, String baseName)` writes three files: simple, detailed and diagnostic.
  - `ReportExporter.exportPdf(Reports, Path pdf)` uses `SimpleReportFacade.newFacade().generatePdfReport(String, Result)` with FOP.

- [ ] **Step 1: Write the failing tests.**
  - `validatesOwnLtSignature()`: sign with Task 7 and `TestPki` at LT, then assert `TOTAL_PASSED` and `CAdES_BASELINE_LT`.
  - `validatesThirdPartyXades()`: produce a XAdES-BASELINE-B with `XAdESService` in the test, then assert the verdict is not null and the level is `XAdES_BASELINE_B`.
  - `tamperedOriginalFails()`: change one byte of the original, then assert `TOTAL_FAILED`.
  - `detachedWithoutOriginalAsksForIt()` (Review Focus 5): assert `MissingOriginalException`.
  - `exportsPdfReport()`: the file starts with `%PDF`.
  - `timestampExpiryReported()`: T level gives `lastTimestampExpiry` present and equal to the TSA cert `notAfter`.
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement the classes.**
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** with `feat: validation et rapports`.

### Task 10: Extension (B to T, LT, LTA, and LTA renewal)

**Files:**
- Create: `src/main/java/fr/signaturedss/core/extend/ExtensionService.java`
- Test: `src/test/java/fr/signaturedss/core/extend/ExtensionServiceTest.java`

**Interfaces:**
- Consumes: `TrustContext`, `TSPSource`, `FormatPlan` naming rules (Task 6), `ValidationService` (Task 9).
- Produces: `ExtensionService(TrustContext, TSPSource)` and `extend(Path signed, List<Path> originals, SigningLevel target): Path`.
  - It detects the format from the file: PDF, `.p7s` or `.asice`.
  - It calls `extendDocument` on the matching service, with `SignatureLevel` mapped as in Task 6.
  - It writes `<nom>-prolonge.<ext>` (with the same suffix rule), and never overwrites the input.

- [ ] **Step 1: Write the failing tests.**
  - `extendsBtoLt()`: the validated level after extension is LT.
  - `ltaRenewalAddsArchiveTimestamp()`: extending LTA to LTA increases the archive timestamp count in `DiagnosticData` by 1.
  - `inputUntouched()`.
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement the class.**
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** with `feat: prolongation`.

### Task 11: Token access (PKCS#12, PKCS#11 Feitian, MSCAPI, Apple)

**Files:**
- Create: `src/main/java/fr/signaturedss/core/token/TokenSource.java`
- Create: `src/main/java/fr/signaturedss/core/token/TokenProvider.java`
- Create: `src/main/java/fr/signaturedss/core/token/TokenException.java`
- Test: `src/test/java/fr/signaturedss/core/token/TokenProviderTest.java`

**Interfaces:**
- Produces:
  - `sealed interface TokenSource` with four records: `P12(Path file)`, `Pkcs11(Path library)`, `WindowsStore()` and `MacKeychain()`.
  - `TokenProvider.open(TokenSource src, char[] secret): SignatureTokenConnection`. It maps to `Pkcs12SignatureToken(File, PasswordProtection)`, `Pkcs11SignatureToken(String path, PasswordProtection)`, `MSCAPISignatureToken()` and `AppleSignatureToken()`. These constructors were checked in `dss-token` 6.5.1.
  - After opening, it calls `getKeys()` once and wipes `secret`.
  - `TokenProvider.available(): List<Class<? extends TokenSource>>` returns `WindowsStore` only on Windows and `MacKeychain` only on macOS.
  - `TokenException` has the message keys `erreur.token.motdepasse` (wrong password or PIN), `erreur.token.bibliotheque` (PKCS#11 library missing or unloadable) and `erreur.token.aucuncertificat`.
  - The Feitian library path is chosen by the user and saved in `Settings.pkcs11Library()`. **Do not hard-code Feitian library names.** They depend on the token model; see spec section 10. The docs (Task 15) tell the user where to find it.

- [ ] **Step 1: Write the failing tests.**
  - `opensP12AndListsKey()`, using `TestPki.signerP12`.
  - `wrongP12PasswordGivesClearError()`: assert key `erreur.token.motdepasse`.
  - `singleAttemptOnWrongPin()` (Review Focus 3): wrap the connection factory in a counter, open with a wrong secret, and assert exactly 1 login attempt.
  - `secretWipedAfterOpen()`: every char of the array is `'\0'` afterwards.
  - `missingPkcs11LibraryGivesClearError()`.
  - `secretNeverLogged()`: capture slf4j-simple output to a temp file while opening with the password `S3cr3t!`, then assert the log does not contain `S3cr3t!`.
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement the classes.**
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** with `feat: acces aux certificats`.

### Task 12: OpenPGP option

**Files:**
- Create: `src/main/java/fr/signaturedss/core/pgp/PgpService.java`
- Test: `src/test/java/fr/signaturedss/core/pgp/PgpServiceTest.java`

**Interfaces:**
- Produces:
  - `PgpService.signDetached(Path file, Path secretKeyRing, char[] passphrase): Path` writes an ASCII-armored detached signature `<fichier.ext>.asc` (same suffix rule as Task 6), streamed with SHA-256, using `bcpg-jdk18on` 1.86.
  - `PgpService.verify(Path file, Path asc, Path publicKeyRing): PgpVerification`, with `record PgpVerification(boolean valid, String keyId, String userId, Date created)`.

- [ ] **Step 1: Write the failing tests.**
  - `signAndVerify()`: generate an RSA key ring in the test.
  - `tamperedFileFails()`.
  - `wrongPassphraseClearError()`.
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement the class.**
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** with `feat: signature PGP optionnelle`.

### Task 13: JavaFX interface

**Files:**
- Create: `src/main/java/fr/signaturedss/App.java`
- Create: `src/main/java/fr/signaturedss/ui/MainWindow.java`
- Create: `src/main/java/fr/signaturedss/ui/SignTab.java`
- Create: `src/main/java/fr/signaturedss/ui/ValidateTab.java`
- Create: `src/main/java/fr/signaturedss/ui/ExtendTab.java`
- Create: `src/main/java/fr/signaturedss/ui/SettingsTab.java`
- Create: `src/main/java/fr/signaturedss/ui/SecretDialog.java`
- Create: `src/main/java/fr/signaturedss/ui/CertificateChooser.java`
- Create: `src/main/java/fr/signaturedss/ui/ReportView.java`
- Create: `src/main/java/fr/signaturedss/ui/vm/SignViewModel.java`
- Create: `src/main/java/fr/signaturedss/ui/vm/ValidateViewModel.java`
- Create: `src/main/java/fr/signaturedss/ui/vm/ExtendViewModel.java`
- Create: `src/main/resources/ui/light.css`
- Create: `src/main/resources/ui/dark.css`
- Test: `src/test/java/fr/signaturedss/ui/vm/SignViewModelTest.java`
- Test: `src/test/java/fr/signaturedss/ui/vm/ValidateViewModelTest.java`

**Interfaces:**
- Consumes: every core service from Tasks 3 to 12.
- Produces: the view models expose JavaFX properties and are free of `Stage` and `Node`, so they can be tested headless.
  - `SignViewModel` exposes `files`, `level`, `mode`, `pdfVisible` and `status`.
  - `SignViewModel.run(TokenSource, char[]): Task<List<SignResult>>`. In order, it:
    1. opens the token;
    2. lets the user choose the key when there are several;
    3. runs the preflight, and if it fails at LT or LTA, publishes a `needsDowngrade` event (the UI offers T or Annuler);
    4. signs;
    5. runs the post-check, and publishes errors and warnings.
  - `ValidateViewModel` exposes `signedFile`, `originals`, `outcome` and `needsOriginal`, which is set on `MissingOriginalException`.
  - The UI is one window with four tabs (Signer, Valider, Prolonger, Paramètres). Drag and drop works on Signer and Valider.
  - PGP is a separate section in Signer, labelled `pgp.mention.sans.eidas`.
  - Theme: dark when `Platform.Preferences.getColorScheme()` is DARK. That JavaFX API exists only from version 22, so on 21 fall back to light plus a manual switch in Paramètres. Verify against OpenJFX 21 javadoc, and do not call a missing API.
  - At startup, `refreshOffline()` runs, then a background `refreshOnline()`, with a status bar showing the LOTL state.
  - Validation results show the verdict, the level and `lastTimestampExpiry` ("Horodatage valable jusqu'au …"), with tabs for the simple, detailed and diagnostic reports, and export buttons.

- [ ] **Step 1: Write the failing view-model tests** with `TestPki`.
  - `preflightFailureAsksDowngrade()`.
  - `postCheckWarningSurfaced()`.
  - `missingOriginalSetsFlag()`.
  - `secretArrayWipedAfterRun()`.
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement the view models, then the views.** Each view is a thin class binding to its view model, and all text goes through `Messages.get`.
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Manual check.** Run `mvn -q javafx:run`, or `java -cp target/classes:target/libs/* fr.signaturedss.Launcher`. Then:
  1. Sign a sample PDF and a DXF with a `TestPki` P12 exported by a helper test.
  2. Validate both.
  3. Switch to English.
  4. Confirm that no file appears outside `donnees/` and the output folder.
- [ ] **Step 6: Commit** with `feat: interface JavaFX`.

### Task 14: Packaging, portable layout, CI

**Files:**
- Create: `packaging/jpackage-windows.ps1`
- Create: `packaging/jpackage-unix.sh`
- Create: `packaging/assemble-usb.sh`
- Create: `.github/workflows/build.yml`
- Create: `packaging/icons/` (`.ico`, `.icns`, `.png`)

**Interfaces:**
- Consumes: `target/signaturedss.jar` and `target/libs/` (Task 1), and `Launcher` as the main class.
- Produces: three app-images and a `SignatureDSS-usb.zip` with the layout `SignatureDSS/{Windows,macOS,Linux,donnees}`, plus a short `LISEZMOI.txt` at the root.

- [ ] **Step 1: Write the packaging scripts.**
  - `jpackage --type app-image --name SignatureDSS --input target/libs+jar --main-jar signaturedss.jar --main-class fr.signaturedss.Launcher`
  - Add `--java-options "-Dfile.encoding=UTF-8"`.
  - Add `--add-modules` for the modules DSS and JavaFX need. Use `jdeps --print-module-deps` to compute them.
- [ ] **Step 2: Add the `--self-check` mode to `Launcher`.**
  - It runs headless. It loads Messages, opens `PortablePaths`, runs `LotlService.refreshOffline()` and validates an embedded sample signed PDF (`src/main/resources/selfcheck/sample-signed.pdf`, generated once by a test helper and committed).
  - It exits 0 when the validator returns any verdict without an exception.
  - The test `LauncherSelfCheckTest` asserts exit code 0.
- [ ] **Step 3: Write the CI workflow.** Matrix `windows-latest`, `macos-latest`, `ubuntu-latest` with Temurin 21. Each job runs:
  1. `mvn -B verify`;
  2. the packaging script;
  3. `<app-image> --self-check`, which must exit 0;
  4. upload of the artifact.

  A final job runs `assemble-usb.sh` on the three artifacts. Leave Intel macOS as a commented matrix entry, pending spec section 10.
- [ ] **Step 4: Verify.** Run `act`, or push to a branch, and check that all three jobs are green and the USB zip contains the four folders.
- [ ] **Step 5: Commit** with `build: paquets portables et CI`.

### Task 15: Documentation and repository

**Files:**
- Create: `README.md`
- Create: `docs/DOCUMENTATION.md`
- Create: `LICENSE` (LGPL-2.1, compatible with DSS)
- Create: `NOTICE`, crediting DSS and the `dss-standalone-app` reference wiring

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Write `README.md`** in French: what it does, download and USB setup (exFAT), first launch on each OS (macOS right click then Ouvrir), building from source, and the language list.
- [ ] **Step 2: Write `docs/DOCUMENTATION.md`.** It covers:
  - the user guide: signing, levels and when to use LTA, validating, extending, and manual re-timestamping with the "Horodatage valable jusqu'au" date;
  - setting up a TSA, including Actalis with credentials;
  - finding the Feitian PKCS#11 library on each OS and pointing the app at it;
  - adding an anchor, PGP and its limits, adding a language;
  - the architecture;
  - the facts / assumptions / uncertainties table from spec section 10.
- [ ] **Step 3: Verify** that every message key in the docs exists in `messages_fr.properties`, and that every class name cited exists, with `grep`.
- [ ] **Step 4: Commit** with `docs: README et documentation`.
- [ ] **Step 5: Push to `courgettes975/signatureDSS`.** This is blocked until the user grants access (currently 404). Ask the user before pushing.
