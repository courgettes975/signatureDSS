package fr.signaturedss.ui; // paquet de l'interface

import fr.signaturedss.core.PortablePaths; // chemins portables
import fr.signaturedss.core.TsaConfig; // configuration TSA
import org.junit.jupiter.api.Test; // annotation de test
import org.junit.jupiter.api.io.TempDir; // dossier temporaire

import java.nio.file.Path; // chemins
import java.util.List; // liste

import static org.junit.jupiter.api.Assertions.assertEquals; // egalite
import static org.junit.jupiter.api.Assertions.assertTrue; // condition vraie

class AppContextTest { // tests du contexte

    @Test // sans choix dans les Parametres, le pilote OpenSC embarque est utilise
    void bundledDriverUsedByDefault(@TempDir Path usb) throws Exception { // pilote par defaut
        Path dll = java.nio.file.Files.createDirectories(usb.resolve("SignatureDSS/Windows/pilotes")).resolve("opensc-pkcs11.dll"); // emplacement embarque
        java.nio.file.Files.writeString(dll, "x"); // pilote simule
        AppContext ctx = new AppContext(PortablePaths.fromAppLocation(usb.resolve("SignatureDSS/Windows/app")), null); // contexte sur la cle
        assertEquals(java.util.Optional.of(dll), ctx.pkcs11Library("windows")); // pilote embarque
        java.nio.file.Path chosen = usb.resolve("autre.dll"); // choix explicite
        ctx.settings().setPkcs11Library(java.util.Optional.of(chosen)); // Parametres
        assertEquals(java.util.Optional.of(chosen), ctx.pkcs11Library("windows")); // le choix prime
    }

    @Test // un mot de passe TSA refuse est oublie et redemande
    void forgottenTsaPasswordIsAskedAgain(@TempDir Path dir) { // correction d'une faute de frappe
        AppContext ctx = new AppContext(PortablePaths.fromAppLocation(dir), null); // contexte sans hote graphique
        ctx.settings().setTsas(List.of(new TsaConfig("https://tsa.example/tsr", "moi"))); // TSA protegee
        char[] wrong = "faux".toCharArray(); // mot de passe errone
        ctx.rememberTsaPassword("https://tsa.example/tsr", wrong); // memorisation
        assertTrue(ctx.tsasMissingPassword().isEmpty()); // memorise
        ctx.forgetTsaPasswords(List.of("https://tsa.example/tsr")); // oubli
        assertEquals(1, ctx.tsasMissingPassword().size()); // redemande
        for (char c : wrong) { assertEquals('\0', c); } // ancienne valeur effacee
    }
}
