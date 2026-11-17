package fr.signaturedss.core.validate; // paquet validation

import eu.europa.esig.dss.simplereport.SimpleReportFacade; // transformation du rapport simple
import eu.europa.esig.dss.validation.reports.Reports; // rapports DSS
import fr.signaturedss.core.sign.FormatPlan; // nommage sans ecrasement
import org.apache.fop.apps.EnvironmentalProfileFactory; // profil FOP
import org.apache.fop.apps.FOUserAgent; // agent FOP
import org.apache.fop.apps.Fop; // moteur FOP
import org.apache.fop.apps.FopConfParser; // configuration FOP
import org.apache.fop.apps.FopFactory; // fabrique FOP
import org.apache.fop.apps.MimeConstants; // types MIME
import org.apache.xmlgraphics.io.Resource; // ressource FOP
import org.apache.xmlgraphics.io.ResourceResolver; // resolution des polices

import javax.xml.transform.Result; // resultat de transformation
import javax.xml.transform.sax.SAXResult; // resultat SAX
import java.io.IOException; // erreurs d'E/S
import java.io.InputStream; // flux
import java.io.OutputStream; // flux de sortie
import java.net.URI; // URI
import java.nio.charset.StandardCharsets; // UTF-8
import java.nio.file.Files; // fichiers
import java.nio.file.Path; // chemins
import java.util.List; // liste

/** Export des rapports en XML et en PDF (cablage FOP repris de dss-standalone-app). */
public final class ReportExporter { // utilitaire statique

    private ReportExporter() { } // pas d'instance

    /** Ecrit les rapports simple, detaille et le diagnostic ; renvoie les fichiers crees. */
    public static List<Path> exportXml(Reports r, Path dir, String baseName) throws IOException { // export XML
        Path simple = write(dir, baseName + "-rapport-simple.xml", r.getXmlSimpleReport()); // rapport simple
        Path detailed = write(dir, baseName + "-rapport-detaille.xml", r.getXmlDetailedReport()); // rapport detaille
        Path diag = write(dir, baseName + "-diagnostic.xml", r.getXmlDiagnosticData()); // donnees de diagnostic
        return List.of(simple, detailed, diag); // fichiers
    }

    private static Path write(Path dir, String name, String xml) throws IOException { // ecrit sans ecraser
        Path f = FormatPlan.freeName(dir.resolve(name)); // nom libre
        Files.writeString(f, xml, StandardCharsets.UTF_8); // ecriture
        return f; // chemin
    }

    /** Ecrit le rapport simple en PDF. */
    public static void exportPdf(Reports r, Path pdf) throws IOException { // export PDF
        try (InputStream conf = ReportExporter.class.getResourceAsStream("/fop/fop.xconf"); OutputStream os = Files.newOutputStream(pdf)) { // configuration et sortie
            FopFactory factory = new FopConfParser(conf, EnvironmentalProfileFactory.createRestrictedIO(new URI("file:/"), new Fonts())).getFopFactoryBuilder().build(); // fabrique FOP
            FOUserAgent ua = factory.newFOUserAgent(); // agent
            ua.setCreator("SignatureDSS"); // createur du PDF
            Fop fop = factory.newFop(MimeConstants.MIME_PDF, ua, os); // moteur PDF
            Result res = new SAXResult(fop.getDefaultHandler()); // sortie SAX
            SimpleReportFacade.newFacade().generatePdfReport(r.getSimpleReportJaxb(), res); // transformation
        } catch (IOException e) { // E/S
            throw e; // propagation
        } catch (Exception e) { // FOP, XSLT, JAXB
            throw new IOException("Export PDF impossible : " + e.getMessage(), e); // erreur explicite
        }
    }

    /** Polices embarquees dans /fop/fonts. */
    private static final class Fonts implements ResourceResolver { // resolveur de ressources
        @Override public Resource getResource(URI uri) { // lecture d'une police
            String s = uri.toString(); // URI texte
            String name = s.substring(s.lastIndexOf('/') + 1); // nom de fichier
            return new Resource(ReportExporter.class.getResourceAsStream("/fop/fonts/" + name)); // ressource
        }
        @Override public OutputStream getOutputStream(URI uri) throws IOException { throw new IOException("Ecriture non prise en charge : " + uri); } // lecture seule
    }
}
