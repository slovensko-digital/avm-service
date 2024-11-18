package digital.slovensko.autogram.service.endpoints;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import digital.slovensko.autogram.core.SignatureComposer;
import digital.slovensko.autogram.core.server.EndpointUtils;
import digital.slovensko.autogram.service.dto.DataToSignRequestBody;
import digital.slovensko.autogram.core.server.dto.ErrorResponse;

public class DataToSignEndpoint implements HttpHandler {
    private final SignatureComposer signatureComposer;

    public DataToSignEndpoint(SignatureComposer signatureComposer) {
        this.signatureComposer = signatureComposer;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            var body = EndpointUtils.loadFromJsonExchange(exchange, DataToSignRequestBody.class);
            body.originalSignRequestBody().validateDocument();
            body.originalSignRequestBody().validateSigningParameters();

            var result = signatureComposer.getDataToSign(body.originalSignRequestBody().getDocument(), body.originalSignRequestBody().getParameters(), body.signingCertificate());
            EndpointUtils.respondWith(result, exchange);

        } catch (Exception e) {
            EndpointUtils.respondWithError(ErrorResponse.buildFromException(e), exchange);
        }
    }
}
