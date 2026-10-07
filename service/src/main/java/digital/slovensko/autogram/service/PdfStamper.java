package digital.slovensko.autogram.service;

import digital.slovensko.autogram.core.server.errors.MalformedBodyException;
import digital.slovensko.autogram.core.server.errors.RequestValidationException;
import digital.slovensko.autogram.service.dto.StampPdfRequestBody;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDLayoutAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class PdfStamper {
    private static final float PADDING = 8;
    private static final float FONT_SIZE = 10;
    private static final float LINE_HEIGHT = 12;
    // Liberation Sans (SIL OFL) is bundled with PDFBox; embedding it keeps the stamp text renderable and extractable
    private static final String FONT_RESOURCE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final String DEFAULT_ALT_TEXT = "Visual signature";

    public byte[] stamp(byte[] content, StampPdfRequestBody.StampParameters stamp) {
        try (var pdf = Loader.loadPDF(content)) {
            validatePage(pdf, stamp.page());

            var page = pdf.getPage(stamp.page() - 1);
            validateRectangle(page.getMediaBox(), stamp);

            var mcid = tagStamp(pdf, page, stamp);

            try (var stream = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                if (mcid != null)
                    stream.beginMarkedContent(COSName.getPDFName(StandardStructureTypes.Figure), mcid);

                var textHeight = drawText(pdf, stream, stamp);
                drawImage(pdf, stream, stamp, textHeight);

                if (mcid != null)
                    stream.endMarkedContent();
            }

            var output = new ByteArrayOutputStream();
            pdf.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new MalformedBodyException("Invalid PDF content", e);
        }
    }

    private static Integer tagStamp(PDDocument pdf, PDPage page, StampPdfRequestBody.StampParameters stamp) throws IOException {
        var taggedPdf = TaggedPdf.of(pdf);
        if (taggedPdf.isEmpty())
            return null;

        var element = taggedPdf.get().addElement(StandardStructureTypes.Figure, page);
        element.setAlternateDescription(altText(stamp));

        var layout = new PDLayoutAttributeObject();
        layout.setBBox(new PDRectangle(stamp.x(), stamp.y(), stamp.width(), stamp.height()));
        element.addAttribute(layout);

        return taggedPdf.get().addMarkedContent(element, page);
    }

    private static String altText(StampPdfRequestBody.StampParameters stamp) {
        if (stamp.altText() != null && !stamp.altText().isBlank())
            return stamp.altText().strip();

        if (stamp.text() != null && !stamp.text().isBlank())
            return stamp.text().strip().replaceAll("\\s*\\R\\s*", ", ");

        return DEFAULT_ALT_TEXT;
    }

    private static float drawText(PDDocument pdf, PDPageContentStream stream, StampPdfRequestBody.StampParameters stamp) throws IOException {
        if (stamp.text() == null || stamp.text().isBlank())
            return 0;

        var font = loadFont(pdf);
        var lines = sanitizeText(font, stamp.text()).split("\\R");
        stream.beginText();
        stream.setNonStrokingColor(0.16f, 0.16f, 0.16f);
        stream.setFont(font, FONT_SIZE);
        stream.newLineAtOffset(stamp.x() + PADDING, stamp.y() + stamp.height() - FONT_SIZE - PADDING);

        for (int index = 0; index < lines.length; index++) {
            if (index > 0)
                stream.newLineAtOffset(0, -LINE_HEIGHT);

            stream.showText(lines[index]);
        }

        stream.endText();
        return lines.length * LINE_HEIGHT;
    }

    private static void drawImage(PDDocument pdf, PDPageContentStream stream, StampPdfRequestBody.StampParameters stamp, float textHeight) throws IOException {
        if (stamp.imageContent() == null || stamp.imageContent().isBlank())
            return;

        var imageBytes = decodeImage(stamp.imageContent());
        var image = PDImageXObject.createFromByteArray(pdf, imageBytes, "visual-stamp-image");
        var availableWidth = stamp.width() - (2 * PADDING);
        var availableHeight = stamp.height() - (2 * PADDING) - textHeight;
        if (availableWidth <= 0 || availableHeight <= 0)
            throw new RequestValidationException("Stamp image does not fit", "Increase stamp rectangle size");

        var scale = Math.min(availableWidth / image.getWidth(), availableHeight / image.getHeight());
        var imageWidth = image.getWidth() * scale;
        var imageHeight = image.getHeight() * scale;
        var imageX = stamp.x() + PADDING;
        var imageY = stamp.y() + PADDING;

        stream.drawImage(image, imageX, imageY, imageWidth, imageHeight);
    }

    private static byte[] decodeImage(String imageContent) {
        try {
            return Base64.getDecoder().decode(imageContent);
        } catch (IllegalArgumentException e) {
            throw new RequestValidationException("Stamp.ImageContent is invalid", "Image content must be valid base64");
        }
    }

    private static void validatePage(PDDocument pdf, int page) {
        if (page < 1 || page > pdf.getNumberOfPages())
            throw new RequestValidationException("Stamp page is out of range", "Page must be between 1 and " + pdf.getNumberOfPages());
    }

    private static void validateRectangle(PDRectangle mediaBox, StampPdfRequestBody.StampParameters stamp) {
        if (stamp.x() < 0 || stamp.y() < 0 || stamp.width() <= 0 || stamp.height() <= 0)
            throw new RequestValidationException("Stamp rectangle is invalid", "Coordinates must be non-negative and dimensions must be positive");

        if (stamp.x() + stamp.width() > mediaBox.getWidth() || stamp.y() + stamp.height() > mediaBox.getHeight())
            throw new RequestValidationException("Stamp rectangle is outside the page", "Stamp must fit inside the selected page");
    }

    private static PDFont loadFont(PDDocument pdf) throws IOException {
        try (var font = PdfStamper.class.getResourceAsStream(FONT_RESOURCE)) {
            if (font == null)
                throw new IOException("Stamp font is not available");

            return PDType0Font.load(pdf, font);
        }
    }

    private static String sanitizeText(PDFont font, String text) {
        var result = new StringBuilder();
        text.replace('\t', ' ').codePoints().forEach(codePoint -> {
            var character = Character.toString(codePoint);
            if (character.matches("\\R")) {
                result.append(character);
            } else if (!Character.isISOControl(codePoint)) {
                result.append(canEncode(font, character) ? character : "?");
            }
        });

        return result.toString();
    }

    private static boolean canEncode(PDFont font, String character) {
        try {
            font.encode(character);
            return true;
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }
}