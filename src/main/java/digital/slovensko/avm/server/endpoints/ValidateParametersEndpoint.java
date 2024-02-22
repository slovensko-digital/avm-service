package digital.slovensko.avm.server.endpoints;

import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import digital.slovensko.avm.core.errors.AutogramException;
import digital.slovensko.avm.server.EndpointUtils;
import digital.slovensko.avm.server.dto.ErrorResponse;
import digital.slovensko.avm.server.dto.OriginalSignRequestBody;
import digital.slovensko.avm.core.errors.MalformedBodyException;
import digital.slovensko.avm.server.dto.ValidationResponse;

import java.io.IOException;

public class ValidateParametersEndpoint implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) {
        try {
            var body = EndpointUtils.loadFromJsonExchange(exchange, OriginalSignRequestBody.class);
            body.validateDocument();
            body.validateSigningParameters();

            EndpointUtils.respondWith(new ValidationResponse("OK"), exchange);

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
