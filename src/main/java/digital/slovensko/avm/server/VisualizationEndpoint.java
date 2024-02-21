package digital.slovensko.avm.server;

import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import digital.slovensko.avm.core.AVM;
import digital.slovensko.avm.core.errors.AutogramException;
import digital.slovensko.avm.server.dto.ErrorResponse;
import digital.slovensko.avm.server.dto.OriginalSignRequestBody;
import digital.slovensko.avm.server.errors.MalformedBodyException;

import java.io.IOException;

public class VisualizationEndpoint implements HttpHandler {
    private final AVM avm;

    public VisualizationEndpoint(AVM avm) {
        this.avm = avm;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            var body = EndpointUtils.loadFromJsonExchange(exchange, OriginalSignRequestBody.class);
            body.validateDocument();
            body.validateSigningParameters();

            var result = avm.getVisualization(body);
            EndpointUtils.respondWith(result, exchange);

        } catch (JsonSyntaxException | IOException e) {
            var response = ErrorResponse.buildFromException(new MalformedBodyException(e.getMessage(), e));
            EndpointUtils.respondWithError(response, exchange);

        } catch (AutogramException e) {
            EndpointUtils.respondWithError(ErrorResponse.buildFromException(e), exchange);

        } catch (Exception e) {
            EndpointUtils.respondWithError(ErrorResponse.buildFromException(e), exchange);
        }
    }
}
