package digital.slovensko.autogram.service;

import digital.slovensko.autogram.core.AutogramMimeType;
import digital.slovensko.autogram.core.eforms.xdc.XDCValidator;
import digital.slovensko.autogram.core.errors.AutogramException;
import digital.slovensko.autogram.core.server.dto.Document;
import digital.slovensko.autogram.core.util.AsicContainerUtils;
import digital.slovensko.autogram.service.dto.ParseDocumentResponse;
import digital.slovensko.autogram.service.dto.ParsedSubdocument;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class DocumentParser {
    public static ParseDocumentResponse parse(Document document) {
        if (document.mimeType() == null || document.mimeType().isEmpty())
            throw new AutogramException("Document MIME type is missing", "Document MIME type is missing", "MALFORMED_REQUEST");

        var doc = new InMemoryDocument(document.content().getBytes(), document.filename(), AutogramMimeType.fromMimeTypeString(document.mimeType()));
        if (document.mimeType().contains("base64")) {
            doc = new InMemoryDocument(Base64.getDecoder().decode(doc.getBytes()), doc.getName(), AutogramMimeType.fromMimeTypeString(document.mimeType().replace(" ", "").replace(";base64", "")));
        }

        if (AutogramMimeType.isAsice(doc.getMimeType())) {
            return new ParseDocumentResponse(document.filename(), document.mimeType(), parseSubdocuments(doc));
        }

        if (AutogramMimeType.isXML(doc.getMimeType()) && XDCValidator.isXDCContent(doc)) {
            doc.setMimeType(AutogramMimeType.XML_DATACONTAINER);
        }

        return new ParseDocumentResponse(document.filename(), doc.getMimeType().getMimeTypeString(), null);
    }

    private static ArrayList<ParsedSubdocument> parseSubdocuments(DSSDocument document) {
        try {
            var originalDocument = AsicContainerUtils.getOriginalDocument(document);

            var doc = new ParsedSubdocument(originalDocument.getName(), originalDocument.getMimeType().getMimeTypeString());
            return new ArrayList<>(List.of(doc));

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ASiC-E container: " + e.getMessage(), e);
        }
    }
}
