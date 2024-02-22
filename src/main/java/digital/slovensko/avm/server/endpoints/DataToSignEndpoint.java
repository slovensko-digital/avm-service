package digital.slovensko.avm.server.endpoints;

import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import digital.slovensko.avm.core.AVM;
import digital.slovensko.avm.core.errors.AutogramException;
import digital.slovensko.avm.server.EndpointUtils;
import digital.slovensko.avm.server.dto.DataToSignRequestBody;
import digital.slovensko.avm.server.dto.ErrorResponse;
import digital.slovensko.avm.core.errors.MalformedBodyException;

import java.io.IOException;

public class DataToSignEndpoint implements HttpHandler {
    private final AVM avm;

    public DataToSignEndpoint(AVM avm) {
        this.avm = avm;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            var body = EndpointUtils.loadFromJsonExchange(exchange, DataToSignRequestBody.class);
            body.originalSignRequestBody().validateDocument();
            body.originalSignRequestBody().validateSigningParameters();

            var result = avm.getDataToSign(body.originalSignRequestBody(), body.signingCertificate());
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
