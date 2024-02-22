package digital.slovensko.avm.server.dto;

import java.util.Base64;

import digital.slovensko.avm.core.SignatureValidator;
import digital.slovensko.avm.core.errors.TransformationParsingErrorException;

import digital.slovensko.avm.core.SigningParameters;
import digital.slovensko.avm.core.errors.MalformedBodyException;
import digital.slovensko.avm.core.errors.RequestValidationException;
import eu.europa.esig.dss.enumerations.MimeType;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;

import static digital.slovensko.avm.core.AutogramMimeType.*;

public class OriginalSignRequestBody {
    private final Document document;
    private ServerSigningParameters parameters;
    private final String payloadMimeType;

    public OriginalSignRequestBody(Document document, ServerSigningParameters parameters, String payloadMimeType) {
        this.document = document;
        this.parameters = parameters;
        this.payloadMimeType = payloadMimeType;
    }

    public void validateDocument() throws RequestValidationException, MalformedBodyException {
        if (payloadMimeType == null)
            throw new RequestValidationException("PayloadMimeType is required", "");

        if (document == null)
            throw new RequestValidationException("Document is required", "");

        if (document.getContent() == null)
            throw new RequestValidationException("Document.Content is required", "");
    }

    public InMemoryDocument getDocument() {
        var content = decodeDocumentContent(document.getContent(), isBase64());
        var filename = document.getFilename();

        return new InMemoryDocument(content, filename, getMimetype());
    }

    public void validateSigningParameters() throws RequestValidationException, MalformedBodyException,
            TransformationParsingErrorException {

        var documentSignatureLevel = SignatureValidator.getSignedDocumentSignatureLevel(getDocument());

        if (parameters == null) {
            if (documentSignatureLevel == null)
                throw new RequestValidationException("Parameters are required for yet to be signed document", "");

            parameters = new ServerSigningParameters();
        }

        parameters.validate(getDocument().getMimeType(), documentSignatureLevel);
    }

    public SigningParameters getParameters(TSPSource tspSource, boolean plainXmlEnabled) {
        return parameters.getSigningParameters(isBase64(), getDocument(), tspSource, plainXmlEnabled);
    }

    private MimeType getMimetype() {
        return fromMimeTypeString(payloadMimeType.split(";")[0]);
    }

    private boolean isBase64() {
        return payloadMimeType.contains("base64");
    }

    private static byte[] decodeDocumentContent(String content, boolean isBase64) throws MalformedBodyException {
        if (isBase64)
            try {
                return Base64.getDecoder().decode(content);
            } catch (IllegalArgumentException e) {
                throw new MalformedBodyException("Base64 decoding failed", "Invalid document content");
            }

        return content.getBytes();
    }
}
