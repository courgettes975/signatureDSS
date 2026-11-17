# Construit l'application portable (app-image) pour Windows avec un JRE embarque.
# Usage : powershell -ExecutionPolicy Bypass -File packaging\jpackage-windows.ps1
$ErrorActionPreference = 'Stop'                                            # arret a la premiere erreur
Set-Location (Join-Path $PSScriptRoot '..')                                # racine du projet
$Version = '1.0.0'                                                         # version de l'application
mvn -B -q package -DskipTests                                              # jar + dependances dans target\libs
if ($LASTEXITCODE -ne 0) { throw 'Echec de la compilation Maven' }         # controle
Remove-Item -Recurse -Force target\input, target\jpackage -ErrorAction SilentlyContinue # nettoyage
New-Item -ItemType Directory -Force target\input, target\jpackage | Out-Null # dossiers de travail
Copy-Item target\signaturedss.jar, target\libs\*.jar target\input\         # entree de jpackage
$Wanted = 'java.base,java.desktop,java.xml,java.xml.crypto,java.naming,java.logging,java.sql,java.management,java.net.http,java.scripting,java.security.jgss,java.security.sasl,jdk.crypto.cryptoki,jdk.crypto.mscapi,jdk.crypto.ec,jdk.unsupported,jdk.localedata,jdk.charsets,jdk.zipfs,jdk.security.auth,jdk.net'.Split(',') # modules souhaites
$Available = (java --list-modules) | ForEach-Object { ($_ -split '@')[0] } # modules du JDK courant
$Mods = ($Wanted | Where-Object { $Available -contains $_ }) -join ','     # modules presents seulement
# Lanceur supplementaire avec console, pour --self-check et le diagnostic
Set-Content -Encoding ASCII target\console.properties "win-console=true"   # proprietes du lanceur console
jpackage --type app-image `
  --name SignatureDSS --app-version $Version --vendor SignatureDSS `
  --input target\input --main-jar signaturedss.jar --main-class fr.signaturedss.Launcher `
  --dest target\jpackage --icon packaging\icons\SignatureDSS.ico `
  --add-modules $Mods `
  --java-options '-Dfile.encoding=UTF-8' `
  --add-launcher SignatureDSS-console=target\console.properties            # creation de l'application
if ($LASTEXITCODE -ne 0) { throw 'Echec de jpackage' }                     # controle
$Pilotes = 'target\jpackage\SignatureDSS\pilotes'                           # pilotes embarques
New-Item -ItemType Directory -Force $Pilotes | Out-Null                    # dossier des pilotes
Copy-Item packaging\opensc\windows-x64\opensc-pkcs11.dll, packaging\opensc\COPYING, packaging\opensc\LISEZMOI.txt $Pilotes # OpenSC (LGPL 2.1) et sa licence
Write-Host 'Application creee dans target\jpackage'                        # compte rendu
