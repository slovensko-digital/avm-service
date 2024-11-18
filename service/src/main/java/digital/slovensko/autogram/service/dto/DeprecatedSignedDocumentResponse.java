package digital.slovensko.autogram.service.dto;

import digital.slovensko.autogram.core.errors.UnrecognizedException;
import digital.slovensko.autogram.core.server.dto.Document;
import eu.europa.esig.dss.model.DSSDocument;

import java.io.IOException;
import java.util.Base64;

public record DeprecatedSignedDocumentResponse(Document documentResponse, Signer signer) {
        public static DeprecatedSignedDocumentResponse buildFormDSS(DSSDocument document, String signedBy, String issuedBy) {
        try (var stream = document.openStream()) {
            return new DeprecatedSignedDocumentResponse(
                    new Document(
                        Base64.getEncoder().encodeToString(stream.readAllBytes()),
                        document.getMimeType().getMimeTypeString() + ";base64",
                        document.getName()),
                    new Signer(signedBy, issuedBy)
            );
        } catch (IOException e) {
            throw new UnrecognizedException(e);
        }
    }
}

record Signer(String signedBy, String issuedBy) {}