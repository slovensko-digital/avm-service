package digital.slovensko.autogram.service;

import digital.slovensko.autogram.service.dto.StampPdfRequestBody;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfStamperTest {
    @Test
    void stampsPdf() throws IOException {
        var source = createPdf();
        var stamped = new PdfStamper().stamp(source, new StampPdfRequestBody.StampParameters(
                1,
                40,
                40,
                220,
                48,
                "Document signed electronically",
                null,
                null));

        assertTrue(stamped.length > source.length);

        try (var pdf = Loader.loadPDF(stamped)) {
            assertEquals(1, pdf.getNumberOfPages());
            var contentStream = new String(pdf.getPage(0).getContents().readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(contentStream.contains(" re\nS") || contentStream.contains(" re\r\nS"));
            assertFalse(contentStream.contains(" rg") && contentStream.contains(" re\n"));
        }
    }

    @Test
    void stampsPdfWithImage() throws IOException {
        var source = createPdf();
        var stamped = new PdfStamper().stamp(source, new StampPdfRequestBody.StampParameters(
                1,
                40,
                40,
                220,
                90,
                "Document signed electronically",
                Base64.getEncoder().encodeToString(createPng()),
                "image/png"));

        assertTrue(stamped.length > source.length);

        try (var pdf = Loader.loadPDF(stamped)) {
            assertEquals(1, pdf.getNumberOfPages());
        }
    }

    private byte[] createPdf() throws IOException {
        try (var pdf = new PDDocument()) {
            pdf.addPage(new PDPage());

            var output = new ByteArrayOutputStream();
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createPng() throws IOException {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 16, 16);
        graphics.dispose();

        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}