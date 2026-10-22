package digital.slovensko.autogram.service.endpoints;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import digital.slovensko.autogram.core.server.EndpointUtils;
import digital.slovensko.autogram.core.server.dto.ErrorResponse;
import digital.slovensko.autogram.core.server.dto.SignRequestBody;
import digital.slovensko.autogram.service.AutogramService;
import digital.slovensko.autogram.service.errors.VerifiableVisualizationNotSupportedException;

public class VerifiableVisualizationEndpoint implements HttpHandler {
    private final AutogramService autogramService;

    public VerifiableVisualizationEndpoint(AutogramService autogramService) {
        this.autogramService = autogramService;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            var body = EndpointUtils.loadFromJsonExchange(exchange, SignRequestBody.class);
            body.validateDocument();
            body.validateSigningParameters();

            var result = autogramService.buildVerifiableVisualization(body.getDocument(), body.getParameters());
            EndpointUtils.respondWith(result, exchange);

        } catch (VerifiableVisualizationNotSupportedException e) {
            EndpointUtils.respondWithError(new ErrorResponse(422, "VERIFIABLE_VISUALIZATION_NOT_SUPPORTED", e.getSubheading(), e.getDescription()), exchange);

        } catch (Exception e) {
            EndpointUtils.respondWithError(ErrorResponse.buildFromException(e), exchange);
        }
    }
}
