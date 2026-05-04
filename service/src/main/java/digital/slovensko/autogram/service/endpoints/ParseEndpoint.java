package digital.slovensko.autogram.service.endpoints;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import digital.slovensko.autogram.core.server.EndpointUtils;
import digital.slovensko.autogram.core.server.dto.Document;
import digital.slovensko.autogram.core.server.dto.ErrorResponse;
import digital.slovensko.autogram.core.server.errors.MalformedBodyException;
import digital.slovensko.autogram.service.DocumentParser;

public class ParseEndpoint implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) {
        try {
            var body = EndpointUtils.loadFromJsonExchange(exchange, Document.class);
            if (body.content() == null)
                throw new MalformedBodyException("Document content is null", "Document content is null");

            var result = DocumentParser.parse(body);
            EndpointUtils.respondWith(result, exchange);

        } catch (Exception e) {
            EndpointUtils.respondWithError(ErrorResponse.buildFromException(e), exchange);
        }
    }
}
