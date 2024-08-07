package digital.slovensko.avm.server.dto;

import digital.slovensko.avm.core.errors.AutogramException;

public class ErrorResponse {
    private final int statusCode;
    private final ErrorResponseBody body;

    private ErrorResponse(int statusCode, ErrorResponseBody body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    private ErrorResponse(int statusCode, String code, AutogramException e) {
        this(statusCode, new ErrorResponseBody(code, e.getSubheading(), e.getDescription()));
    }

    public ErrorResponse(int statusCode, String code, String message, String details) {
        this(statusCode, new ErrorResponseBody(code, message, details));
    }

    public int getStatusCode() {
        return statusCode;
    }

    public ErrorResponseBody getBody() {
        return body;
    }

    public static ErrorResponse buildFromException(Exception e) {
        // TODO maybe replace with pattern matching someday
        return switch (e.getClass().getSimpleName()) {
            case "UnrecognizedException" -> new ErrorResponse(502, "UNRECOGNIZED_DSS_ERROR", (AutogramException) e);
            case "UnsupportedSignatureLevelException" -> new ErrorResponse(422, "UNSUPPORTED_SIGNATURE_LEVEL", (AutogramException) e);
            case "RequestValidationException",
                "XMLValidationException",
                "SigningParametersException",
                "TransformationException",
                "TransformationParsingErrorException" -> new ErrorResponse(422, "UNPROCESSABLE_INPUT", (AutogramException) e);
            case "MultipleOriginalDocumentsFoundException" -> new ErrorResponse(422, "MULTIPLE_ORIGINAL_DOCUMENTS", (AutogramException) e);
            case "OriginalDocumentNotFoundException" -> new ErrorResponse(422, "ORIGINAL_DOCUMENT_NOT_FOUND", (AutogramException) e);
            case "CryptographicSignatureVerificationException" -> new ErrorResponse(400, "SIGNATURE_NOT_IN_TACT", (AutogramException) e);
            case "MalformedBodyException" -> new ErrorResponse(400, "MALFORMED_INPUT", (AutogramException) e);
            case "AutogramException" -> new ErrorResponse(502, "SIGNING_FAILED", (AutogramException) e);
            case "EmptyBodyException" -> new ErrorResponse(400, "EMPTY_BODY", (AutogramException) e);
            case "DataToSignMismatchException" -> new ErrorResponse(400, "DATATOSIGN_MISMATCH", (AutogramException) e);
            case "DocumentNotSignedYetException" -> new ErrorResponse(422, "DOCUMENT_NOT_SIGNED", (AutogramException) e);
            default -> new ErrorResponse(500, "INTERNAL_ERROR", "Unexpected exception signing document", e.getMessage());
        };
    }
}
