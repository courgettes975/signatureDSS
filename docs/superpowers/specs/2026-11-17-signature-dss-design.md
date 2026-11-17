# SignatureDSS : document de conception

Date : 2026-11-17
Statut : brouillon soumis à validation

## 1. Intention

L'utilisateur signe ses propres fichiers (PDF, DXF, audio, vidéo) pour en protéger le contenu. Il doit pouvoir prouver plus tard :
- que le fichier n'a pas été modifié depuis la signature (intégrité) ;
- qu'il en est l'auteur (identité, via son certificat) ;
- que le fichier existait sous cette forme à une date donnée (horodatage). Ce point est ma déduction à partir de la demande d'un horodatage fonctionnel.

Le logiciel est une application de bureau portable, lancée depuis une clé USB, sans installation, sur Windows, macOS et Debian. Il s'inspire d'AutoFirma pour le principe, et de l'application de démonstration DSS de la Commission européenne pour la validation.

## 2. Objectifs, non-objectifs, anti-objectifs

**Objectifs**
- Signer un ou plusieurs fichiers aux niveaux B, T, LT et LTA.
- Valider une signature et afficher un rapport équivalent à celui de la démo DSS (rapport simple, détaillé, données de diagnostic).
- Prolonger une signature existante (par exemple B vers LT, ou un nouvel horodatage d'archive LTA).
- Utiliser un certificat en fichier `.p12`/`.pfx` ou sur jeton Feitian.
- Proposer en option une signature PGP.
- Interface en français, prête pour d'autres langues.

**Non-objectifs**
- Signature à distance (Actalis Remote Signing ou autre).
- Serveur, service web, application mobile.
- Signature XAdES ou JAdES de documents XML ou JSON : ce n'est pas demandé.
- Gestion ou émission de certificats.

**Anti-objectifs** (ce qui serait un échec même si « ça marche »)
- Modifier le fichier d'origine d'un format autre que PDF.
- Produire une signature T, LT ou LTA sans horodatage réel, sans le signaler clairement.
- Une signature qui ne peut plus être vérifiée hors ligne quelques années plus tard, alors qu'elle était annoncée comme LT ou LTA.
- Exiger des droits administrateur ou une installation sur l'ordinateur hôte (sauf le pilote Feitian, voir section 6).
- Laisser une clé privée ou un mot de passe en clair sur la clé USB ou dans les journaux.

## 3. Contraintes

- Bibliothèque : DSS 6.5.1 (`eu.europa.ec.joinup.sd-dss`, licence LGPL 2.1). Confirmé dans le `pom.xml` de la balise 6.5.1. DSS fonctionne de Java 8 à Java 26 et embarque BouncyCastle 1.86 et PDFBox 3.0.8.
- PAdES ne s'applique qu'au PDF. Pour les autres formats, on utilise CAdES détaché ou ASiC-E (CAdES).
- DSS ne gère pas OpenPGP. PGP sera donc une fonction séparée, via BouncyCastle `bcpg`, sans valeur eIDAS.
- Les niveaux T, LT et LTA exigent une autorité d'horodatage (TSA) joignable. LT et LTA exigent aussi l'accès aux OCSP/CRL au moment de la signature.
- La version macOS ne peut pas être compilée sous Windows. On utilise donc une intégration continue sur les trois systèmes.
- Chaque ligne du code source est commentée (demande explicite).
- Le dépôt cible `courgettes975/signatureDSS` renvoie 404 à la date de rédaction. Un accès en écriture est nécessaire avant publication.

## 4. Approches envisagées

1. **Java 21 + JavaFX + DSS, une application native par système avec JRE embarqué (`jpackage`).** C'est l'approche retenue par l'utilisateur. Elle utilise DSS directement dans son langage, avec une seule base de code et le moins de pièces mobiles. En contrepartie, elle pèse environ 150 à 200 Mo par système et l'interface est plus sobre qu'une interface web.
2. **Interface Tauri/Electron pilotant un moteur Java DSS.** L'interface serait plus moderne, mais il faudrait deux langages, un dialogue entre processus et plus de risques de panne. Écartée.
3. **Application web de démo DSS (Spring Boot) lancée en local.** Elle va vite et sa validation est identique à celle de la démo, mais elle n'est pas native, et elle est lourde à démarrer depuis une clé. Écartée.

## 4 bis. Certificats de l'utilisateur

- Jeton Feitian : certificat qualifié Actalis (confirmé par l'utilisateur). Sa chaîne figure sur la liste de confiance italienne, donc la validation peut aboutir à TOTAL_PASSED.
- Fichier `.p12`/`.pfx` : également disponible.
- Compte TSA Actalis payant : non précisé. *Mon choix* : les TSA gratuites sont configurées par défaut, et la TSA Actalis s'ajoute avec ses identifiants dans les Paramètres. Avec une TSA gratuite non reconnue, le verdict peut être INDETERMINATE, et l'avertissement de la section 5.1 l'explique.

## 5. Fonctions

### 5.1 Signer
- Glisser-déposer ou sélection d'un ou plusieurs fichiers.
- Format de signature choisi automatiquement :
  - PDF : PAdES (`dss-pades-pdfbox`), signature intégrée dans une copie `nom-signe.pdf`. *Mon choix* : on ne remplace jamais l'original.
  - Autres formats : au choix, CAdES détaché (`fichier.ext.p7s` à côté de l'original, qui n'est pas modifié) ou conteneur ASiC-E CAdES (`fichier.asice` contenant l'original et la signature). *Mon choix* : CAdES détaché par défaut.
- Niveau : B, T, LT, LTA. *Mon choix* : LT par défaut, LTA recommandé pour l'archivage long.
- PDF : apparence visible facultative (texte, page, position). *Mon choix* : désactivée par défaut.
- Fichiers volumineux (vidéo) : on signe une empreinte calculée en flux, sans charger le fichier en mémoire.
- Plusieurs fichiers : chaque fichier reçoit sa propre signature (un `.p7s`, un `.asice` ou un PDF signé par fichier). Le PIN ou le mot de passe n'est demandé qu'une fois pour tout le lot. *Mon choix.*
- Après la signature, une validation automatique fait deux contrôles. *Mon choix.*
  - Le niveau de format visé est-il réellement présent ? Si ce n'est pas le cas, c'est une erreur.
  - Quel est le verdict DSS ? S'il n'est pas TOTAL_PASSED (par exemple un certificat absent des listes de confiance), un avertissement explique pourquoi.
- Avant de signer en LT ou LTA, le logiciel vérifie qu'il obtient les données de révocation (OCSP/CRL) de la chaîne du signataire et de celle de la TSA. Si elles manquent, il propose de signer en T ou d'annuler, et ne produit jamais un LT incomplet en silence. *Mon choix.*
- Si un horodatage échoue, le niveau T, LT ou LTA n'est pas annoncé, et une erreur claire s'affiche.

### 5.2 Source du certificat
- **Fichier PKCS#12** (`Pkcs12SignatureToken`) : le chemin et le mot de passe sont demandés à chaque usage. Le mot de passe n'est jamais enregistré.
- **Jeton Feitian en PKCS#11** (`Pkcs11SignatureToken`) : le code PIN est demandé à chaque usage. Le chemin de la bibliothèque PKCS#11 est configurable, avec des emplacements par défaut proposés pour chaque système.
- **Magasin système** : `MSCAPISignatureToken` sous Windows, `AppleSignatureToken` sous macOS, en option pour un jeton déjà reconnu par le système. Les quatre classes sont présentes dans `dss-token` 6.5.1.
- Choix du certificat dans une liste (titulaire, émetteur, validité, usage) quand le jeton en contient plusieurs.

### 5.3 Horodatage
- Liste de TSA configurables, essayées dans l'ordre (bascule automatique). Elles sont gérées par `OnlineTSPSource`, avec une authentification HTTP Basic facultative pour une TSA payante comme celle d'Actalis.
- Livrées par défaut : quelques TSA publiques gratuites. Leurs adresses sont à vérifier par le constructeur au moment du développement. Elles ne sont pas qualifiées eIDAS, et l'interface le dit.
- Un bouton « Tester » vérifie chaque TSA en demandant un jeton d'horodatage.

### 5.4 Valider
- Entrée : un fichier signé, plus l'original pour une signature détachée.
- Formats acceptés : tous ceux que valide la démo DSS (PAdES, CAdES, XAdES, JAdES, ASiC-S et ASiC-E), y compris les signatures faites par des tiers. On inclut donc les modules `dss-xades`, `dss-jades` et `dss-asic-xades`. Cela découle de la demande « les mêmes outils que la démo ».
- Sortie : rapport simple (verdict TOTAL_PASSED / INDETERMINATE / TOTAL_FAILED), rapport détaillé et données de diagnostic. Export XML et PDF, sur le modèle de la démo DSS.
- Confiance :
  - liste des listes de confiance européenne (LOTL) via `dss-tsl-validation`, avec un cache sur la clé pour pouvoir travailler hors ligne ;
  - ancres de confiance ajoutées par l'utilisateur, par exemple la racine Actalis, ou un certificat personnel pour les tests.
- Politique de validation : la politique DSS par défaut.

### 5.5 Prolonger
- Faire passer une signature existante à un niveau supérieur (B vers T, LT ou LTA), ou ajouter un nouvel horodatage d'archive à une signature LTA.
- Le ré-horodatage est manuel (décision de l'utilisateur). Le logiciel ne garde pas de liste des fichiers signés et n'envoie aucun rappel. Le rapport de validation affiche la date d'expiration du dernier horodatage. La documentation explique quand et comment prolonger.

### 5.6 PGP (option)
- Signature détachée ASCII (`fichier.ext.asc`) et vérification, avec une clé privée OpenPGP importée depuis un fichier.
- Elle est présentée à part, avec la mention « sans valeur eIDAS ».
- L'horodatage RFC 3161 ne s'applique pas à PGP dans cette version.

## 6. Portabilité et clé USB

Structure de la clé :
```
SignatureDSS/
  Windows/   application + JRE
  macOS/     SignatureDSS.app
  Linux/     application + JRE (Debian, x86_64)
  donnees/   configuration, cache LOTL, journaux (partagés par les trois)
```
- Toutes les données sont écrites dans `donnees/`, à côté de l'application, et jamais dans le profil utilisateur. Sous macOS, `donnees/` est trouvé par rapport au bundle, car le `.app` ne doit pas être modifié.
- Formater la clé en exFAT : FAT32 bloque les vidéos de plus de 4 Go.
- macOS : l'application n'est pas signée par Apple, donc le premier lancement se fait par clic droit puis Ouvrir. Le cas Apple Silicon et Intel est à traiter (voir section 9).
- Feitian : le pilote doit être installé sur l'ordinateur hôte. On ne copie la bibliothèque PKCS#11 de Feitian sur la clé que si sa licence le permet.

## 7. Interface et langues

- Une fenêtre avec quatre onglets : Signer, Valider, Prolonger, Paramètres. Le glisser-déposer est la voie principale.
- Thème clair et sombre suivant le système, et conventions de chaque plateforme (ordre des boutons, menus macOS).
- Textes dans des fichiers `messages_fr.properties` (par défaut) et `messages_en.properties`. On ajoute une langue en ajoutant un fichier.

## 8. Qualité et livraison

- Maven, avec une intégration continue GitHub Actions sur Windows, macOS et Ubuntu. Chaque système produit une archive ZIP de l'application.
- Tests automatiques : signature puis validation de chaque format (PDF, DXF, MP3/WAV, MP4) à chaque niveau. Ils utilisent une PKI de test générée et une TSA de test locale, ce qui les rend indépendants d'Internet.
- Test manuel avant livraison : le jeton Feitian réel et la TSA réelle, sur chaque système. Je ne peux pas faire ce test moi-même ; c'est l'utilisateur qui le fera.
- Livrables dans le dépôt : le code source, `README.md` complété et `docs/DOCUMENTATION.md` (guide utilisateur et technique).

## 9. Laissé au constructeur

- Les versions exactes de JavaFX et des greffons Maven, à vérifier au moment du développement.
- Le choix des TSA publiques livrées par défaut, après vérification de leur disponibilité.
- La mise en page détaillée des écrans.
- La structure interne du code (paquets, classes).

## 10. Incertitudes

- La licence de la bibliothèque PKCS#11 Feitian, pour savoir si elle peut être copiée sur la clé.
- Le modèle exact du jeton Feitian (par exemple ePass2003), qui détermine le nom de la bibliothèque PKCS#11.
- Le Mac cible (Apple Silicon ou Intel), qui détermine s'il faut un ou deux paquets macOS.
- L'accès au dépôt GitHub cible.
