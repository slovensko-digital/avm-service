package digital.slovensko.avm.server.dto;

import eu.europa.esig.dss.model.DSSDocument;

import java.io.IOException;
import java.util.Base64;

public record DocumentResponse(String content, String mimeType, String filename) {
    public static DocumentResponse buildFormDSS(DSSDocument document) throws IOException {
        return new DocumentResponse(
                Base64.getEncoder().encodeToString(document.openStream().readAllBytes()),
                document.getMimeType().getMimeTypeString(),
                document.getName()
        );
    }
}
