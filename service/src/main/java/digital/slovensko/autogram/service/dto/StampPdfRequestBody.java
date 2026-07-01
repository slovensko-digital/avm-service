package digital.slovensko.autogram.service.dto;

import digital.slovensko.autogram.core.server.dto.Document;
import digital.slovensko.autogram.core.server.errors.RequestValidationException;

public record StampPdfRequestBody(Document document, StampParameters stamp) {
    public record StampParameters(int page, float x, float y, float width, float height, String text, String imageContent, String imageMimeType) {
    }

    public void validate() {
        if (document == null)
            throw new RequestValidationException("Document is required", "");

        if (document.content() == null || document.content().isBlank())
            throw new RequestValidationException("Document.Content is required", "");

        if (document.mimeType() == null || !document.mimeType().split(";")[0].equals("application/pdf"))
            throw new RequestValidationException("Document.MimeType must be application/pdf", "Stamping is supported only for PDF documents");

        if (stamp == null)
            throw new RequestValidationException("Stamp is required", "");

        if ((stamp.text() == null || stamp.text().isBlank()) && (stamp.imageContent() == null || stamp.imageContent().isBlank()))
            throw new RequestValidationException("Stamp.Text or Stamp.ImageContent is required", "");

        if (stamp.text() != null && stamp.text().length() > 500)
            throw new RequestValidationException("Stamp.Text is too long", "Maximum stamp text length is 500 characters");

        if (stamp.imageContent() != null && !stamp.imageContent().isBlank() &&
                (stamp.imageMimeType() == null || !(stamp.imageMimeType().equals("image/png") || stamp.imageMimeType().equals("image/jpeg"))))
            throw new RequestValidationException("Stamp.ImageMimeType is unsupported", "Supported image types are image/png and image/jpeg");
    }
}