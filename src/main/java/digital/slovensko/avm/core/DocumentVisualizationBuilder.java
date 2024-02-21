package digital.slovensko.avm.core;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import digital.slovensko.avm.util.AsicContainerUtils;
import eu.europa.esig.dss.model.DSSDocument;

import eu.europa.esig.dss.model.InMemoryDocument;
import org.xml.sax.SAXException;

import static digital.slovensko.avm.core.AutogramMimeType.*;

import digital.slovensko.avm.core.eforms.EFormUtils;
import digital.slovensko.avm.core.errors.AutogramException;
import eu.europa.esig.dss.enumerations.MimeTypeEnum;

public class DocumentVisualizationBuilder {

    private final DSSDocument document;
    private final SigningParameters parameters;

    private DocumentVisualizationBuilder(DSSDocument document, SigningParameters parameters) {
        this.document = document;
        this.parameters = parameters;
    }

    public static DSSDocument fromJob(SigningJob job) throws IOException, ParserConfigurationException, SAXException {
        return new DocumentVisualizationBuilder(job.getDocument(), job.getParameters()).build();
    }

    private DSSDocument build() {
        try {
            return createVisualization();
        } catch (IOException | ParserConfigurationException | SAXException e) {
            return null;
        }
    }

    private DSSDocument createVisualization() throws IOException, ParserConfigurationException, SAXException {
        var documentToDisplay = document;
        if (isAsice(documentToDisplay.getMimeType())) {
            try {
                documentToDisplay = AsicContainerUtils.getOriginalDocument(document);
            } catch (AutogramException e) {
                return null;
            }
        }

        var transformation = parameters.getTransformation();

        if (isDocumentSupportingTransformation(documentToDisplay) && isTranformationAvailable(transformation)) {
            var transformationOutputMimeType = parameters.getXsltDestinationType();

            if (transformationOutputMimeType.equals("HTML"))
                return new InMemoryDocument(EFormUtils.transform(documentToDisplay, transformation).getBytes(), documentToDisplay.getName(), documentToDisplay.getMimeType());

            if (transformationOutputMimeType.equals("TXT"))
                return new InMemoryDocument(EFormUtils.transform(documentToDisplay, transformation).getBytes(), documentToDisplay.getName(), documentToDisplay.getMimeType());

            return null;
        }

        if (documentToDisplay.getMimeType().equals(MimeTypeEnum.HTML))
            return new InMemoryDocument(EFormUtils.transform(documentToDisplay, transformation).getBytes(), documentToDisplay.getName(), documentToDisplay.getMimeType());

        if (documentToDisplay.getMimeType().equals(MimeTypeEnum.TEXT))
            return documentToDisplay;

        if (documentToDisplay.getMimeType().equals(MimeTypeEnum.PDF))
            return documentToDisplay;

        if (documentToDisplay.getMimeType().equals(MimeTypeEnum.JPEG) || documentToDisplay.getMimeType().equals(MimeTypeEnum.PNG))
            return transformImageToHTML(documentToDisplay);

        return null;
    }

    private static DSSDocument transformImageToHTML(DSSDocument documentToDisplay) {
        return new InMemoryDocument("""
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Image visualization</title>
                    </head>
                    <body style="background-color: green"></body>
                    </html>""".getBytes(),
                documentToDisplay.getName(),
                MimeTypeEnum.HTML);
    }

    private boolean isTranformationAvailable(String transformation) {
        return transformation != null;
    }

    private boolean isDocumentSupportingTransformation(DSSDocument document) {
        return document.getMimeType().equals(AutogramMimeType.XML_DATACONTAINER)
            || document.getMimeType().equals(AutogramMimeType.APPLICATION_XML)
            || document.getMimeType().equals(MimeTypeEnum.XML);
    }
}
