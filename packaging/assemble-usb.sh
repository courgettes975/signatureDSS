#!/usr/bin/env bash
# Assemble la cle USB a partir des trois paquets produits par la CI.
# Usage : packaging/assemble-usb.sh <dossier des paquets> <fichier zip de sortie>
# Le dossier doit contenir : windows.zip, macos.tar.gz, linux.tar.gz
set -euo pipefail                                                  # arret a la premiere erreur
IN="$(cd "$1" && pwd)"                                             # dossier des paquets (absolu)
OUT="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"                # zip de sortie (chemin absolu)
ROOT="$(dirname "$0")/.."                                          # racine du projet
WORK="$(mktemp -d)"                                                # dossier de travail
USB="$WORK/SignatureDSS"                                           # racine de la cle
mkdir -p "$USB/Windows" "$USB/macOS" "$USB/Linux" "$USB/donnees/ancres" # arborescence de la cle
unzip -q "$IN/windows.zip" -d "$WORK/win"                          # paquet Windows
cp -R "$WORK/win/SignatureDSS/." "$USB/Windows/"                   # contenu dans Windows/
tar -xzf "$IN/macos.tar.gz" -C "$USB/macOS"                        # SignatureDSS.app dans macOS/
mkdir -p "$WORK/lin" && tar -xzf "$IN/linux.tar.gz" -C "$WORK/lin"     # paquet Linux (dossier separe de la cle)
cp -R "$WORK/lin/SignatureDSS/." "$USB/Linux/"                     # contenu dans Linux/
cp "$ROOT/packaging/LISEZMOI.txt" "$USB/LISEZMOI.txt"              # notice
(cd "$WORK" && zip -q -r -y "$OUT" SignatureDSS)           # zip (liens et droits conserves)
rm -rf "$WORK"                                                     # nettoyage
echo "Cle USB assemblee : $OUT"                                    # compte rendu
