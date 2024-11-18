package digital.slovensko.autogram.service.dto;

import digital.slovensko.autogram.core.server.dto.Document;
import eu.europa.esig.dss.model.DSSDocument;

public record DeprecatedSignedDocumentResponse(Document documentResponse, Signer signer) {
    public static DeprecatedSignedDocumentResponse buildFormDSS(DSSDocument document, String signedBy, String issuedBy) {
        return new DeprecatedSignedDocumentResponse(
                (Document) Document.buildFromDSS(document),
                new Signer(signedBy, issuedBy)
        );
    }
}

record Signer(String signedBy, String issuedBy) {}