package digital.slovensko.autogram.service.endpoints;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import digital.slovensko.autogram.core.VisualizationBuilder;
import digital.slovensko.autogram.core.server.responders.DocumentAPIResponder;
import digital.slovensko.autogram.core.server.dto.SignRequestBody;
import digital.slovensko.autogram.core.server.EndpointUtils;
import digital.slovensko.autogram.core.server.dto.ErrorResponse;

public class VisualizationEndpoint implements HttpHandler {
    private final VisualizationBuilder visualizationBuilder;

    public VisualizationEndpoint(VisualizationBuilder visualizationBuilder) {
        this.visualizationBuilder = visualizationBuilder;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            var body = EndpointUtils.loadFromJsonExchange(exchange, SignRequestBody.class);
            body.validateDocument();
            body.validateSigningParameters();

            visualizationBuilder.buildVisualizationAndRespond(
                    body.getDocument(),
                    body.getParameters(),
                    new DocumentAPIResponder(exchange));

        } catch (Exception e) {
            EndpointUtils.respondWithError(ErrorResponse.buildFromException(e), exchange);
        }
    }
}
