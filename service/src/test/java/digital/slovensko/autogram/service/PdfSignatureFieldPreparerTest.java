package digital.slovensko.autogram.service;

import digital.slovensko.autogram.core.server.errors.RequestValidationException;
import digital.slovensko.autogram.service.dto.PrepareSignatureFieldsRequestBody;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDObjectReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfSignatureFieldPreparerTest {
    @Test
    void preparesVisibleSignatureFields() throws IOException {
        var source = createPdf();
        var prepared = new PdfSignatureFieldPreparer().prepare(source, List.of(
                new PrepareSignatureFieldsRequestBody.SignatureFieldParameters("signature-field-alice", 1, 40, 40, 180, 64, "Podpis – Alica Nová (alica@example.com)"),
                new PrepareSignatureFieldsRequestBody.SignatureFieldParameters("signature-field-bob", 1, 40, 140, 180, 64, null)
        ));

        assertTrue(prepared.length > source.length);

        try (var pdf = Loader.loadPDF(prepared)) {
            assertEquals(1, pdf.getNumberOfPages());
            assertNotNull(pdf.getDocumentCatalog().getAcroForm());
            assertEquals(2, pdf.getDocumentCatalog().getAcroForm().getFields().size());

            var firstField = pdf.getDocumentCatalog().getAcroForm().getField("signature-field-alice");
            assertNotNull(firstField);
            assertEquals("signature-field-alice", firstField.getFullyQualifiedName());
            assertEquals("Podpis – Alica Nová (alica@example.com)", firstField.getAlternateFieldName());
            assertEquals("Signature field", pdf.getDocumentCatalog().getAcroForm().getField("signature-field-bob").getAlternateFieldName());
            assertNull(pdf.getDocumentCatalog().getStructureTreeRoot());

            var widget = firstField.getWidgets().getFirst();
            assertEquals(40f, widget.getRectangle().getLowerLeftX());
            assertEquals(40f, widget.getRectangle().getLowerLeftY());
            assertEquals(180f, widget.getRectangle().getWidth());
            assertEquals(64f, widget.getRectangle().getHeight());
            assertEquals(0, widget.getBorder().getInt(2));
            assertEquals(0f, widget.getBorderStyle().getWidth());
        }
    }

    @Test
    void tagsSignatureFieldsInTaggedPdf() throws IOException {
        var prepared = new PdfSignatureFieldPreparer().prepare(TaggedPdfFixtures.createTaggedPdf(), List.of(
                new PrepareSignatureFieldsRequestBody.SignatureFieldParameters("signature-field-alice", 1, 40, 40, 180, 64, "Podpis – Alica Nová"),
                new PrepareSignatureFieldsRequestBody.SignatureFieldParameters("signature-field-bob", 2, 40, 40, 180, 64, "Podpis – Boris Starý")
        ));

        try (var pdf = Loader.loadPDF(prepared)) {
            var root = pdf.getDocumentCatalog().getStructureTreeRoot();
            var document = (PDStructureElement) root.getKids().getFirst();
            var kids = document.getKids();
            assertEquals(4, kids.size());
            assertEquals(List.of("H1", "Form", "P", "Form"), kids.stream().map(kid -> ((PDStructureElement) kid).getStructureType()).toList());

            for (var name : List.of("signature-field-alice", "signature-field-bob")) {
                var widget = pdf.getDocumentCatalog().getAcroForm().getField(name).getWidgets().getFirst();
                var form = assertInstanceOf(COSDictionary.class, TaggedPdfFixtures.parentTreeValue(pdf, widget.getStructParent()));
                assertEquals(COSName.getPDFName("Form"), form.getCOSName(COSName.S));

                var reference = assertInstanceOf(PDObjectReference.class, new PDStructureElement(form).getKids().getFirst());
                assertSame(widget.getCOSObject(), reference.getReferencedObject().getCOSObject());
                assertEquals("S", widget.getPage().getCOSObject().getNameAsString(COSName.getPDFName("Tabs")));
            }

            assertEquals(0, pdf.getPage(0).getStructParents());
            assertEquals(4, root.getParentTreeNextKey());
        }
    }

    @Test
    void rejectsAlreadySignedPdf() throws IOException {
        var signedPdf = Files.readAllBytes(Path.of("..", "core", "src", "test", "resources", "digital", "slovensko", "autogram", "core", "sample_signed.pdf"));

        var exception = assertThrows(RequestValidationException.class, () ->
                new PdfSignatureFieldPreparer().prepare(signedPdf, List.of(
                        new PrepareSignatureFieldsRequestBody.SignatureFieldParameters("signature-field-alice", 1, 40, 40, 180, 64, null)
                ))
        );

        assertEquals("Document already contains signatures", exception.getSubheading());
    }

    @Test
    void rejectsDuplicateFieldNameAgainstExistingPdfField() throws IOException {
        var source = createPdf();
        var preparer = new PdfSignatureFieldPreparer();
        var firstPass = preparer.prepare(source, List.of(
                new PrepareSignatureFieldsRequestBody.SignatureFieldParameters("signature-field-alice", 1, 40, 40, 180, 64, null)
        ));

        var exception = assertThrows(RequestValidationException.class, () ->
                preparer.prepare(firstPass, List.of(
                        new PrepareSignatureFieldsRequestBody.SignatureFieldParameters("signature-field-alice", 1, 40, 140, 180, 64, null)
                ))
        );

        assertEquals("Signature field name already exists", exception.getSubheading());
    }

    private byte[] createPdf() throws IOException {
        try (var pdf = new PDDocument()) {
            pdf.addPage(new PDPage());

            var output = new ByteArrayOutputStream();
            pdf.save(output);
            return output.toByteArray();
        }
    }
}