package digital.slovensko.avm.util;

import java.io.IOException;

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.pades.exception.InvalidPasswordException;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDocumentReader;

import static digital.slovensko.avm.core.AutogramMimeType.isPDF;

public class PDFUtils {
    public static boolean isPdfAndPasswordProtected(DSSDocument document) {
        if (isPDF(document.getMimeType())) {
            try {
                PdfBoxDocumentReader reader = new PdfBoxDocumentReader(document);
                reader.close();
            } catch (InvalidPasswordException e) {
                return true;
            } catch (IOException e) {
            }
        }
        return false;
    }

}
