package digital.slovensko.autogram.service;

import digital.slovensko.autogram.service.dto.StampPdfRequestBody;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDLayoutAttributeObject;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
                "image/png",
                null));

        assertTrue(stamped.length > source.length);

        try (var pdf = Loader.loadPDF(stamped)) {
            assertEquals(1, pdf.getNumberOfPages());
        }
    }

    @Test
    void stampsTextWithEmbeddedUnicodeFont() throws IOException {
        var stamped = new PdfStamper().stamp(createPdf(), stampParameters(1, "Ján Novák\nŽilina", null));

        try (var pdf = Loader.loadPDF(stamped)) {
            var page = pdf.getPage(0);
            var fontName = page.getResources().getFontNames().iterator().next();
            var font = assertInstanceOf(PDType0Font.class, page.getResources().getFont(fontName));

            assertTrue(font.isEmbedded());
            assertNotNull(font.getCOSObject().getDictionaryObject(COSName.TO_UNICODE));
            assertEquals("Ján Novák\nŽilina", new PDFTextStripper().getText(pdf).strip());
            assertNull(pdf.getDocumentCatalog().getStructureTreeRoot());
        }
    }

    @Test
    void tagsStampInTaggedPdf() throws IOException {
        var stamped = new PdfStamper().stamp(TaggedPdfFixtures.createTaggedPdf(), stampParameters(1, "Ján Novák", "Vizuálny podpis: Ján Novák"));

        try (var pdf = Loader.loadPDF(stamped)) {
            var document = (PDStructureElement) pdf.getDocumentCatalog().getStructureTreeRoot().getKids().getFirst();
            var kids = document.getKids();
            assertEquals(3, kids.size());

            // inserted after the content of page 1 and before the content of page 2
            var figure = assertInstanceOf(PDStructureElement.class, kids.get(1));
            assertEquals("Figure", figure.getStructureType());
            assertEquals("Vizuálny podpis: Ján Novák", figure.getAlternateDescription());
            assertEquals(pdf.getPage(0).getCOSObject(), figure.getPage().getCOSObject());
            assertEquals(List.of(1), figure.getKids());
            assertSame(document.getCOSObject(), figure.getParent().getCOSObject());

            var layout = assertInstanceOf(PDLayoutAttributeObject.class, figure.getAttributes().getObject(0));
            assertEquals(40f, layout.getBBox().getLowerLeftX());
            assertEquals(260f, layout.getBBox().getUpperRightX());

            var marks = assertInstanceOf(COSArray.class, TaggedPdfFixtures.parentTreeValue(pdf, pdf.getPage(0).getStructParents()));
            assertEquals(2, marks.size());
            assertSame(figure.getCOSObject(), marks.getObject(1));

            var contentStream = new String(pdf.getPage(0).getContents().readAllBytes(), StandardCharsets.ISO_8859_1);
            var markedContentStart = contentStream.indexOf("/Figure <</MCID 1>> BDC");
            assertTrue(markedContentStart >= 0);
            assertTrue(contentStream.indexOf("BT", markedContentStart) > markedContentStart);
            assertTrue(contentStream.indexOf("EMC", markedContentStart) > contentStream.indexOf("ET", markedContentStart));
        }
    }

    @Test
    void usesStampTextAsDefaultAltText() throws IOException {
        var stamped = new PdfStamper().stamp(TaggedPdfFixtures.createTaggedPdf(), stampParameters(2, "Ján Novák\n2. 10. 2026", null));

        try (var pdf = Loader.loadPDF(stamped)) {
            var document = (PDStructureElement) pdf.getDocumentCatalog().getStructureTreeRoot().getKids().getFirst();
            var figure = (PDStructureElement) document.getKids().getLast();
            assertEquals("Ján Novák, 2. 10. 2026", figure.getAlternateDescription());
            assertEquals(pdf.getPage(1).getCOSObject(), figure.getPage().getCOSObject());
        }
    }

    @Test
    void tagsImageOnlyStampWithDefaultAltText() throws IOException {
        var stamped = new PdfStamper().stamp(TaggedPdfFixtures.createTaggedPdf(), new StampPdfRequestBody.StampParameters(
                1, 40, 40, 220, 90, null, Base64.getEncoder().encodeToString(createPng()), "image/png", null));

        try (var pdf = Loader.loadPDF(stamped)) {
            var document = (PDStructureElement) pdf.getDocumentCatalog().getStructureTreeRoot().getKids().getFirst();
            var figure = (PDStructureElement) document.getKids().get(1);
            assertEquals("Visual signature", figure.getAlternateDescription());
        }
    }

    private static StampPdfRequestBody.StampParameters stampParameters(int page, String text, String altText) {
        return new StampPdfRequestBody.StampParameters(page, 40, 40, 220, 48, text, null, null, altText);
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