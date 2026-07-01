package digital.slovensko.autogram.service.dto;

import digital.slovensko.autogram.core.server.dto.Document;
import digital.slovensko.autogram.core.server.errors.RequestValidationException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrepareSignatureFieldsRequestBodyTest {
    @Test
    void rejectsEmptyFields() {
        var request = new PrepareSignatureFieldsRequestBody(
                new Document("contract.pdf", Base64.getEncoder().encodeToString("pdf".getBytes()), "application/pdf;base64"),
                List.of()
        );

        var exception = assertThrows(RequestValidationException.class, request::validate);

        assertEquals("Fields are required", exception.getSubheading());
    }

    @Test
    void rejectsDuplicateFieldNamesInsideRequest() {
        var request = new PrepareSignatureFieldsRequestBody(
                new Document("contract.pdf", Base64.getEncoder().encodeToString("pdf".getBytes()), "application/pdf;base64"),
                List.of(
                        new PrepareSignatureFieldsRequestBody.SignatureFieldParameters("signature-field-alice", 1, 40, 40, 180, 64),
                        new PrepareSignatureFieldsRequestBody.SignatureFieldParameters("signature-field-alice", 1, 40, 140, 180, 64)
                )
        );

        var exception = assertThrows(RequestValidationException.class, request::validate);

        assertEquals("Field.FieldName must be unique", exception.getSubheading());
    }
}