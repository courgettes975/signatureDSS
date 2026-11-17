#!/usr/bin/env bash
# Construit l'application portable (app-image) pour macOS ou Linux avec un JRE embarque.
# Usage : packaging/jpackage-unix.sh    (depuis n'importe quel dossier)
set -euo pipefail                                              # arret a la premiere erreur
cd "$(dirname "$0")/.."                                        # racine du projet
VERSION=1.0.0                                                  # version de l'application
mvn -B -q package -DskipTests                                  # jar + dependances dans target/libs
rm -rf target/input target/jpackage                            # nettoyage
mkdir -p target/input target/jpackage                          # dossiers de travail
cp target/signaturedss.jar target/libs/*.jar target/input/     # entree de jpackage
WANTED="java.base,java.desktop,java.xml,java.xml.crypto,java.naming,java.logging,java.sql,java.management,java.net.http,java.scripting,java.security.jgss,java.security.sasl,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.unsupported,jdk.localedata,jdk.charsets,jdk.zipfs,jdk.security.auth,jdk.net" # modules souhaites
AVAILABLE=$(java --list-modules | sed 's/@.*//')               # modules du JDK courant
MODS=""                                                        # modules retenus
for m in ${WANTED//,/ }; do                                    # chaque module souhaite
  if echo "$AVAILABLE" | grep -qx "$m"; then MODS="$MODS,$m"; fi # garde s'il existe dans ce JDK
done
MODS="${MODS#,}"                                               # retire la virgule initiale
EXTRA=()                                                       # options propres au systeme
ICON="packaging/icons/SignatureDSS.png"                        # icone Linux
if [[ "$(uname)" == "Darwin" ]]; then                          # macOS
  ICON="packaging/icons/SignatureDSS.icns"                     # icone macOS
  EXTRA+=(--mac-package-identifier fr.signaturedss.app --mac-package-name SignatureDSS) # identite du bundle
fi
jpackage --type app-image \
  --name SignatureDSS --app-version "$VERSION" --vendor "SignatureDSS" \
  --input target/input --main-jar signaturedss.jar --main-class fr.signaturedss.Launcher \
  --dest target/jpackage --icon "$ICON" \
  --add-modules "$MODS" \
  --java-options "-Dfile.encoding=UTF-8" \
  "${EXTRA[@]}"                                                # creation de l'application
echo "Application creee dans target/jpackage"                  # compte rendu
