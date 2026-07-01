package digital.slovensko.autogram.service.endpoints;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import digital.slovensko.autogram.core.server.EndpointUtils;
import digital.slovensko.autogram.core.server.dto.Document;
import digital.slovensko.autogram.core.server.dto.ErrorResponse;
import digital.slovensko.autogram.service.PdfSignatureFieldPreparer;
import digital.slovensko.autogram.service.dto.PrepareSignatureFieldsRequestBody;

import java.util.Base64;

public class PrepareSignatureFieldsEndpoint implements HttpHandler {
    private final PdfSignatureFieldPreparer pdfSignatureFieldPreparer;

    public PrepareSignatureFieldsEndpoint(PdfSignatureFieldPreparer pdfSignatureFieldPreparer) {
        this.pdfSignatureFieldPreparer = pdfSignatureFieldPreparer;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            var body = EndpointUtils.loadFromJsonExchange(exchange, PrepareSignatureFieldsRequestBody.class);
            body.validate();

            var source = body.document().getDecodedContent().getBytes();
            var prepared = pdfSignatureFieldPreparer.prepare(source, body.fields());
            var filename = body.document().filename() == null ? "prepared-signature-fields.pdf" : body.document().filename();

            EndpointUtils.respondWith(new Document(filename, Base64.getEncoder().encodeToString(prepared), "application/pdf;base64"), exchange);
        } catch (Exception e) {
            EndpointUtils.respondWithError(ErrorResponse.buildFromException(e), exchange);
        }
    }
}