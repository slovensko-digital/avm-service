package digital.slovensko.autogram.service.dto;

import digital.slovensko.autogram.core.server.dto.Document;
import digital.slovensko.autogram.core.server.errors.RequestValidationException;

import java.util.HashSet;
import java.util.List;

public record PrepareSignatureFieldsRequestBody(Document document, List<SignatureFieldParameters> fields) {
    public record SignatureFieldParameters(String fieldName, int page, float x, float y, float width, float height) {
    }

    public void validate() {
        if (document == null)
            throw new RequestValidationException("Document is required", "");

        if (document.content() == null || document.content().isBlank())
            throw new RequestValidationException("Document.Content is required", "");

        if (document.mimeType() == null || !document.mimeType().split(";")[0].equals("application/pdf"))
            throw new RequestValidationException("Document.MimeType must be application/pdf", "Preparing signature fields is supported only for PDF documents");

        if (fields == null || fields.isEmpty())
            throw new RequestValidationException("Fields are required", "At least one signature field must be provided");

        var uniqueNames = new HashSet<String>();

        for (var field : fields) {
            if (field == null)
                throw new RequestValidationException("Field is required", "");

            if (field.fieldName() == null || field.fieldName().isBlank())
                throw new RequestValidationException("Field.FieldName is required", "Each signature field must have a unique fieldName");

            if (!uniqueNames.add(field.fieldName()))
                throw new RequestValidationException("Field.FieldName must be unique", "Each signature field in the request must have a unique fieldName");

            if (field.page() < 1)
                throw new RequestValidationException("Field.Page must be positive", "Page numbers are one-based");

            if (field.x() < 0 || field.y() < 0 || field.width() <= 0 || field.height() <= 0)
                throw new RequestValidationException("Field rectangle is invalid", "Coordinates must be non-negative and dimensions must be positive");
        }
    }
}