package digital.slovensko.autogram.service;

import digital.slovensko.autogram.core.server.errors.MalformedBodyException;
import digital.slovensko.autogram.core.server.errors.RequestValidationException;
import digital.slovensko.autogram.service.dto.PrepareSignatureFieldsRequestBody;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PdfSignatureFieldPreparer {
    public byte[] prepare(byte[] content, List<PrepareSignatureFieldsRequestBody.SignatureFieldParameters> fields) {
        try (var pdf = Loader.loadPDF(content)) {
            validateSignedPdf(pdf);

            var acroForm = getOrCreateAcroForm(pdf);
            var existingFieldNames = collectExistingFieldNames(acroForm);

            for (var field : fields) {
                validatePage(pdf, field.page());

                var page = pdf.getPage(field.page() - 1);
                validateRectangle(page.getMediaBox(), field);
                validateFieldName(existingFieldNames, field.fieldName());

                var signatureField = new PDSignatureField(acroForm);
                signatureField.setPartialName(field.fieldName());

                var widget = signatureField.getWidgets().getFirst();
                configureWidget(page, widget, field);

                page.getAnnotations().add(widget);
                acroForm.getFields().add(signatureField);
                existingFieldNames.add(field.fieldName());
            }

            var output = new ByteArrayOutputStream();
            pdf.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new MalformedBodyException("Invalid PDF content", e);
        }
    }

    private static PDAcroForm getOrCreateAcroForm(PDDocument pdf) {
        var catalog = pdf.getDocumentCatalog();
        var acroForm = catalog.getAcroForm();
        if (acroForm != null)
            return acroForm;

        acroForm = new PDAcroForm(pdf);
        acroForm.setSignaturesExist(true);
        acroForm.setAppendOnly(true);
        catalog.setAcroForm(acroForm);
        return acroForm;
    }

    private static Set<String> collectExistingFieldNames(PDAcroForm acroForm) {
        var fieldNames = new HashSet<String>();
        for (var field : acroForm.getFieldTree()) {
            if (field.getFullyQualifiedName() != null)
                fieldNames.add(field.getFullyQualifiedName());
        }

        return fieldNames;
    }

    private static void configureWidget(org.apache.pdfbox.pdmodel.PDPage page,
                                        PDAnnotationWidget widget,
                                        PrepareSignatureFieldsRequestBody.SignatureFieldParameters field) {
        widget.setRectangle(new PDRectangle(field.x(), field.y(), field.width(), field.height()));
        widget.setBorder(new COSArray(List.of(COSInteger.ZERO, COSInteger.ZERO, COSInteger.ZERO)));

        var borderStyle = new PDBorderStyleDictionary();
        borderStyle.setWidth(0);
        widget.setBorderStyle(borderStyle);

        widget.setPage(page);
        widget.setPrinted(true);
    }

    private static void validateSignedPdf(PDDocument pdf) {
        if (!pdf.getSignatureDictionaries().isEmpty())
            throw new RequestValidationException(
                    "Document already contains signatures",
                    "Visible signature fields can only be prepared before the first signature is added"
            );
    }

    private static void validateFieldName(Set<String> existingFieldNames, String fieldName) {
        if (existingFieldNames.contains(fieldName))
            throw new RequestValidationException(
                    "Signature field name already exists",
                    "Every prepared signature field must have a unique fieldName"
            );
    }

    private static void validatePage(PDDocument pdf, int page) {
        if (page < 1 || page > pdf.getNumberOfPages())
            throw new RequestValidationException("Signature field page is out of range", "Page must be between 1 and " + pdf.getNumberOfPages());
    }

    private static void validateRectangle(PDRectangle mediaBox, PrepareSignatureFieldsRequestBody.SignatureFieldParameters field) {
        if (field.x() < 0 || field.y() < 0 || field.width() <= 0 || field.height() <= 0)
            throw new RequestValidationException("Signature field rectangle is invalid", "Coordinates must be non-negative and dimensions must be positive");

        if (field.x() + field.width() > mediaBox.getWidth() || field.y() + field.height() > mediaBox.getHeight())
            throw new RequestValidationException("Signature field rectangle is outside the page", "Signature field must fit inside the selected page");
    }
}