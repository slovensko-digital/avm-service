package digital.slovensko.autogram.service.endpoints;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import digital.slovensko.autogram.core.server.EndpointUtils;
import digital.slovensko.autogram.core.server.dto.Document;
import digital.slovensko.autogram.core.server.dto.ErrorResponse;
import digital.slovensko.autogram.service.PdfStamper;
import digital.slovensko.autogram.service.dto.StampPdfRequestBody;

import java.util.Base64;

public class StampPdfEndpoint implements HttpHandler {
    private final PdfStamper pdfStamper;

    public StampPdfEndpoint(PdfStamper pdfStamper) {
        this.pdfStamper = pdfStamper;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            var body = EndpointUtils.loadFromJsonExchange(exchange, StampPdfRequestBody.class);
            body.validate();

            var source = body.document().getDecodedContent().getBytes();
            var stamped = pdfStamper.stamp(source, body.stamp());
            var filename = body.document().filename() == null ? "stamped.pdf" : body.document().filename();

            EndpointUtils.respondWith(new Document(filename, Base64.getEncoder().encodeToString(stamped), "application/pdf;base64"), exchange);
        } catch (Exception e) {
            EndpointUtils.respondWithError(ErrorResponse.buildFromException(e), exchange);
        }
    }
}