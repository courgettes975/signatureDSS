# SignatureDSS : documentation

Version 1.0.0. DSS 6.5.1, Java 21, JavaFX 21.0.13.

## Sommaire

1. [Principes](#1-principes)
2. [Guide utilisateur](#2-guide-utilisateur)
3. [Certificats et jeton Feitian](#3-certificats-et-jeton-feitian)
4. [Services d'horodatage](#4-services-dhorodatage)
5. [Confiance et validation](#5-confiance-et-validation)
6. [PGP](#6-pgp)
7. [Langues](#7-langues)
8. [Architecture technique](#8-architecture-technique)
9. [Construction, tests et livraison](#9-construction-tests-et-livraison)
10. [Faits, hypothèses et incertitudes](#10-faits-hypothèses-et-incertitudes)

---

## 1. Principes

- **L'original n'est jamais modifié.**
  - Un PDF est signé dans une copie `nom-signe.pdf`.
  - Les autres fichiers reçoivent une signature détachée `fichier.ext.p7s`, ou sont placés dans un conteneur `fichier.ext.asice`.
- **Aucun fichier existant n'est écrasé.** Si un nom est pris, le logiciel ajoute `-2`, `-3`… avant l'extension.
- **Rien n'est écrit sur l'ordinateur.**
  - Les paramètres, le cache des listes de confiance, le cache natif de JavaFX et les journaux vont dans `donnees/`, à côté de l'application.
  - Les chemins enregistrés qui pointent vers la clé sont relatifs : changer de lettre de lecteur (E: puis F:) ne casse rien.
- **Aucun secret n'est enregistré sur disque.**
  - Le mot de passe du certificat, le code PIN et la phrase PGP sont demandés à chaque usage, puis effacés de la mémoire.
  - Le mot de passe d'une TSA est gardé en mémoire **pendant la session**, seulement s'il a fonctionné. Il est oublié et redemandé si la TSA échoue.
- **Une seule tentative de PIN par action.** Un code refusé n'est jamais retenté automatiquement, pour ne pas bloquer le jeton.

## 2. Guide utilisateur

### 2.1 Niveaux de signature

| Niveau | Contenu | Usage |
|---|---|---|
| B | Signature et certificat | Usage interne, sans preuve de date |
| T | + horodatage | Preuve de date |
| LT | + données de révocation (OCSP/CRL) | **Par défaut.** Vérifiable seul, hors ligne, pendant la validité de l'horodatage |
| LTA | + horodatage d'archive | Archivage long, à prolonger avant expiration |

Formats produits :
- PDF : PAdES-BASELINE-B, -T, -LT ou -LTA.
- Autres fichiers : CAdES-BASELINE-B, -T, -LT ou -LTA (`.p7s` ou `.asice`).

### 2.2 Signer

1. Onglet **Signer**. Déposez les fichiers, ou cliquez sur **Ajouter…**.
2. Choisissez :
   - le **niveau** ;
   - la **forme** pour les fichiers non PDF (`.p7s` ou ASiC-E) ;
   - la **signature visible** sur les PDF, si vous en voulez une (en bas à droite de la dernière page) ;
   - le **certificat** (voir section 3).
3. Cliquez sur **Signer**. Le code PIN ou le mot de passe est demandé **une fois pour tout le lot**.
4. Contrôles automatiques :
   - **Avant la signature, en LT et LTA** : le logiciel vérifie qu'il obtient les données de révocation du certificat et de la TSA. S'il ne les obtient pas, il propose de **signer en T** ou d'**annuler**. Il ne produit jamais un LT incomplet en silence.
   - **Après la signature** : chaque fichier produit est validé.
     - Si le niveau obtenu est inférieur au niveau demandé, une **erreur** s'affiche.
     - Si le verdict n'est pas « Signature valide », un **avertissement** explique pourquoi (par exemple une TSA gratuite non qualifiée).
5. Si aucune TSA ne répond, une erreur liste les services essayés, et aucun fichier n'est créé.

### 2.3 Valider

1. Onglet **Valider**. Déposez le fichier signé (PDF, `.p7s`, `.asice`, XML, JSON…).
2. Pour une signature détachée `.p7s`, ajoutez le fichier d'origine. S'il manque, le logiciel le demande.
3. Le résultat comprend :
   - le **verdict** (valide, invalide ou indéterminé) ;
   - le **niveau** détecté ;
   - la date **« Horodatage valable jusqu'au… »** ;
   - les rapports DSS (simple, détaillé, données de diagnostic), exportables en XML et en PDF.

### 2.4 Prolonger (ré-horodatage manuel)

Un horodatage reste fiable tant que le certificat de la TSA est valide. Pour garder une preuve sur le long terme :

1. Validez régulièrement vos fichiers importants et notez la date « Horodatage valable jusqu'au… ».
2. **Avant cette date**, utilisez l'onglet **Prolonger**, avec le niveau **LTA**. Un nouvel horodatage d'archive est ajouté.
3. Le résultat est un nouveau fichier `nom-prolonge.ext`. Conservez-le avec l'original.

Le logiciel ne garde pas de liste de vos fichiers et n'envoie pas de rappel. C'est un choix de conception.

### 2.5 Si une signature de lot s'interrompt

Si un fichier échoue (TSA coupée, par exemple), le lot s'arrête. Les fichiers déjà signés sont listés et contrôlés. Relancez ensuite la signature seulement pour les fichiers restants.

## 3. Certificats et jeton Feitian

### 3.1 Fichier .p12 / .pfx

Choisissez **Fichier .p12 / .pfx**, puis **Parcourir…**. Le fichier peut rester sur la clé ; son chemin est mémorisé, son mot de passe ne l'est pas.

**Attention** : un fichier `.p12` sur une clé USB n'est protégé que par son mot de passe. En cas de perte de la clé, révoquez le certificat auprès d'Actalis.

### 3.2 Jeton USB (PKCS#11) : Feitian K49+ / K50+ et autres

**Pilote fourni.** Le paquet Windows contient le pilote PKCS#11 d'**OpenSC 0.27.1** (licence LGPL 2.1), dans `Windows/pilotes/opensc-pkcs11.dll`. Il fonctionne sans installation : le jeton passe par le lecteur de carte à puce intégré à Windows.

Si aucune bibliothèque n'est choisie dans les Paramètres, l'application cherche dans cet ordre :
1. le pilote fourni sur la clé ;
2. une installation d'OpenSC sur l'ordinateur :
   - Windows : `C:\Program Files\OpenSC Project\OpenSC\pkcs11\opensc-pkcs11.dll` ;
   - macOS : `/Library/OpenSC/lib/opensc-pkcs11.so` ou `/usr/local/lib/opensc-pkcs11.so` ;
   - Debian : `/usr/lib/x86_64-linux-gnu/opensc-pkcs11.so`.

Le pilote retenu est affiché dans **Paramètres > Jeton USB**.

**Mode d'emploi :**
1. Branchez la clé.
2. Dans **Signer**, choisissez **Jeton USB (PKCS#11)**.
3. Saisissez le code PIN. Une seule tentative est faite par action.

**Sous macOS et Linux**, OpenSC n'est pas fourni. Installez-le une fois :
- macOS : installeur officiel `OpenSC-0.27.1.dmg` sur [github.com/OpenSC/OpenSC/releases](https://github.com/OpenSC/OpenSC/releases) ;
- Debian : `sudo apt install opensc-pkcs11`.

**Feitian K49 / K50.** Ces modèles BioPass FIDO2 existent en deux versions :
- La K49 et la K50 standard ne stockent **pas** de certificat de signature (FIDO2 uniquement). Elles ne peuvent pas signer de documents.
- Les variantes **K49+ / K50+** ajoutent la fonction **PIV**, qui stocke des certificats X.509. OpenSC prend en charge le PIV, tout comme Windows et macOS en natif.

**Autres pilotes.** Si votre fournisseur impose son propre pilote (Feitian, Actalis), installez-le, puis désignez sa bibliothèque dans **Paramètres > Jeton USB**. Exemples relevés dans des documentations, à vérifier sur votre poste :

| Système | Exemples relevés | Source |
|---|---|---|
| Windows | `eps2003csp11.dll` (pilote EnterSafe de l'ePass2003) | [eps2003csp11-interface](https://gh.r-mj.com/melgmry0101b/eps2003csp11-interface) |
| macOS | `/usr/local/lib/libcastle_v2.1.0.0.dylib` | [guide HyperSecu macOS](https://hypersecu.com/_files/ugd/5aae8d_00cfe438c71d428e8857b01c695ce1fa.pdf) |
| Linux | `libcastle_v2.so.1.0.0` ou `/usr/lib/libePass2003.so` | [guide HyperSecu Linux](https://www.hypersecu.com/_files/ugd/5aae8d_4498a110b07c4e688813e50ce37b1558.pdf) |

### 3.3 Magasins du système

- **Windows** : si le pilote du jeton est installé, le certificat apparaît dans le magasin Windows. Choisissez **Magasin de certificats Windows** ; Windows demande lui-même le PIN.
- **macOS** : **Trousseau macOS**.

## 4. Services d'horodatage

Au premier lancement, trois TSA publiques gratuites sont configurées. Toutes trois ont répondu lors du test du 17/11/2026 :

1. `http://timestamp.digicert.com`
2. `http://timestamp.sectigo.com`
3. `https://freetsa.org/tsr`

Les services sont essayés dans l'ordre, et le premier qui répond est utilisé. Si un service en tête de liste échoue (par exemple la TSA Actalis avec un mauvais mot de passe), un avertissement le signale après la signature : la signature a alors été horodatée par un autre service. **Ces services ne sont pas qualifiés eIDAS.** L'horodatage est techniquement valide, mais le verdict DSS peut être « indéterminé » selon la politique de validation.

**TSA Actalis (qualifiée)** :
1. **Paramètres > Services d'horodatage**.
2. Saisissez l'URL fournie par Actalis et votre identifiant, puis cliquez sur **Ajouter…**.
3. Placez-la en tête avec **Monter**.
4. Vérifiez-la avec **Tester**. Le mot de passe est demandé une fois par session et n'est jamais enregistré.

L'URL exacte du service Actalis figure dans votre contrat ; elle n'est pas préremplie.

## 5. Confiance et validation

- **Listes de confiance européennes (LOTL)** :
  - URL : `https://ec.europa.eu/tools/lotl/eu-lotl.xml`.
  - Au démarrage, le cache de la clé (`donnees/cache/tsl`) est chargé, puis mis à jour en ligne en arrière-plan.
  - Hors ligne, le cache est utilisé.
  - La signature de la LOTL est vérifiée avec les certificats publiés au Journal officiel, selon le magasin et l'URL de `dss-standalone-app` 6.5.1.
- **Ancres personnelles** :
  - Déposez des certificats `.cer`, `.crt` ou `.pem` dans `donnees/ancres/`, puis cliquez sur **Recharger** dans les Paramètres.
  - Utile pour une PKI interne ou des tests.
- **Révocation** : OCSP et CRL en ligne, avec complétion de chaîne par AIA.
- **Politique** : la politique de validation par défaut de DSS.

## 6. PGP

- Section **Signature PGP (option)** de l'onglet Signer.
- Elle crée une signature OpenPGP détachée `fichier.ext.asc` (SHA-256) avec une clé privée importée depuis un fichier, et vérifie une signature `.asc` avec une clé publique.
- **Limites** : pas de valeur eIDAS, pas d'horodatage RFC 3161, pas de validation par les listes de confiance.

## 7. Langues

Les textes sont dans `src/main/resources/i18n/messages_xx.properties`, encodés en UTF-8.

Pour ajouter une langue :
1. Copiez `messages_fr.properties` en `messages_de.properties` et traduisez les valeurs.
2. Ajoutez `de` dans `src/main/resources/i18n/languages.txt`.
3. Recompilez.

Le test `MessagesTest` vérifie trois choses :
- les mêmes clés existent dans le français et l'anglais ;
- chaque clé utilisée dans le code existe ;
- chaque niveau, forme, source et verdict a son texte.

## 8. Architecture technique

```
fr.signaturedss
  Launcher            point d'entrée ; --self-check, --version ; journal et cache JavaFX dans donnees/
  App                 application JavaFX
  core/               aucun lien avec l'affichage
    PortablePaths     emplacement de donnees/ (Windows/, Linux/, macOS/*.app)
    Settings          config.properties (chemins relatifs à la clé, aucun secret)
    tsa/              FailoverTspSource (bascule), TsaFactory (création, test, HTTP Basic)
    trust/            LotlService (LOTL + cache), TrustContext (vérificateur DSS)
    sign/             FormatPlan, SigningService, RevocationPreflight, PostSignCheck
    validate/         ValidationService, ReportExporter (XML, PDF via FOP)
    extend/           ExtensionService
    token/            TokenProvider (PKCS#12, PKCS#11, MSCAPI, Apple)
    pgp/              PgpService (Bouncy Castle bcpg)
  i18n/Messages       textes traduits
  ui/                 onglets JavaFX ; ui/vm/ = modèles de vue testables sans écran
```

- **Choix du format** : un PDF est détecté par son en-tête `%PDF-`, et non par son extension.
- **Écriture des sorties** : fichier `.tmp`, puis déplacement sans écrasement. En cas d'échec, aucun fichier partiel ne reste.
- **Dépendances DSS** :
  - `dss-pades-pdfbox`, `dss-cades`, `dss-asic-cades` ;
  - `dss-xades`, `dss-jades`, `dss-asic-xades` (validation) ;
  - `dss-token`, `dss-service`, `dss-tsl-validation`, `dss-validation` ;
  - `dss-cms-object` et `dss-policy-jaxb`, exigés à l'exécution par DSS 6 ;
  - `dss-utils-apache-commons`, `dss-crl-parser-x509crl`.

## 9. Construction, tests et livraison

- `mvn verify` : 119 tests. Ils s'appuient sur une PKI de test entièrement en mémoire (racine, signataire, TSA, OCSP, CRL) et n'utilisent ni Internet ni de jeton. Ils couvrent notamment :
  - chaque format (PDF, DXF, WAV, MP4) à chaque niveau ;
  - la validation et la prolongation ;
  - une panne de TSA sans fichier résiduel ;
  - un PIN refusé, avec une seule tentative ;
  - un fichier de 256 Mo signé en ASiC-E avec seulement 96 Mo de mémoire (conteneur construit dans `donnees/tmp`) ;
  - un lot interrompu : les fichiers déjà signés sont rapportés ;
  - l'absence de secret dans les journaux ;
  - les chemins accentués et le changement de lettre de lecteur.
- Paquets :
  - `packaging/jpackage-windows.ps1` et `packaging/jpackage-unix.sh` construisent une app-image avec un JRE embarqué ;
  - `packaging/assemble-usb.sh` assemble la clé à partir des trois paquets.
- CI : `.github/workflows/build.yml`.
  - Matrice Windows, macOS et Ubuntu, avec Temurin 21.
  - Chaque système exécute les tests, construit le paquet et lance `--self-check` sur ce paquet.
  - Un dernier job assemble `SignatureDSS-usb.zip`.

## 10. Faits, hypothèses et incertitudes

| Statut | Élément |
|---|---|
| Fait vérifié | DSS 6.5.1 (balise du dépôt `esig/dss`), BouncyCastle 1.86, PDFBox 3.0.8, FOP 2.11 alignés sur son `pom.xml` |
| Fait vérifié | Les classes `Pkcs12SignatureToken`, `Pkcs11SignatureToken`, `MSCAPISignatureToken` et `AppleSignatureToken` existent dans `dss-token` 6.5.1 |
| Fait vérifié | Les 119 tests passent (Windows 11, JDK 26 OpenJ9, compilation `--release 21`) |
| Fait vérifié | Paquet Windows : `--self-check` renvoie 0, l'interface démarre, toutes les données sont dans `donnees/` |
| Fait vérifié | Les TSA DigiCert, Sectigo et FreeTSA ont répondu le 17/11/2026. Une signature T réelle a été produite avec un horodatage DigiCert valable jusqu'en 2037 |
| Fait vérifié | Le téléchargement de la LOTL fonctionne : 4920 certificats en cache, dont la liste italienne (Actalis) |
| Hypothèse | Le certificat qualifié Actalis de l'utilisateur sera reconnu via la liste de confiance italienne. Non testé : nécessite le jeton réel |
| Non vérifié | Paquets macOS et Linux : construits et contrôlés par la CI, jamais exécutés localement |
| Non vérifié | Signature avec le jeton Feitian réel (PKCS#11) : à tester par l'utilisateur |
| Fait vérifié | Le pilote OpenSC embarqué se charge dans DSS (Windows, chemin quelconque) et a dialogué avec un jeton branché sur le poste de développement |
| Non vérifié | Signature complète avec la clé Feitian K49+/K50+ et le certificat Actalis (PIN réel nécessaire) |
| Incertitude | Licence de redistribution du pilote propriétaire Feitian : il n'est pas copié sur la clé (OpenSC, sous LGPL, est fourni à sa place) |
| Incertitude | Mac Intel : seul le Mac Apple Silicon (`macos-latest`) est construit ; l'entrée Intel est commentée dans la CI |
| Limite | macOS : l'application n'est pas signée par Apple (premier lancement par clic droit puis Ouvrir). Ne déplacez pas `SignatureDSS.app` hors du dossier `macOS/` de la clé. Si macOS l'exécute depuis un emplacement en lecture seule (« App Translocation »), retirez la quarantaine : `xattr -dr com.apple.quarantine /Volumes/<clé>/SignatureDSS` |
| Limite | Le champ de saisie JavaFX conserve brièvement le secret sous forme de `String` avant sa copie et son effacement |
| Limite | Pas de suivi automatique du ré-horodatage (choix de conception) |
