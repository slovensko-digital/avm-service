package digital.slovensko.autogram.service.endpoints;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import digital.slovensko.autogram.core.SignatureComposer;
import digital.slovensko.autogram.core.server.EndpointUtils;
import digital.slovensko.autogram.core.server.dto.ErrorResponse;
import digital.slovensko.autogram.service.DeprecatedSignedDocumentAPIResponder;
import digital.slovensko.autogram.service.dto.BuildSignatureRequestBody;

public class DeprecatedSignEndpoint implements HttpHandler {
    private final SignatureComposer signatureComposer;

    public DeprecatedSignEndpoint(SignatureComposer signatureComposer) {
        this.signatureComposer = signatureComposer;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            var body = EndpointUtils.loadFromJsonExchange(exchange, BuildSignatureRequestBody.class);
            body.originalSignRequestBody().validateDocument();
            body.originalSignRequestBody().validateSigningParameters();

            var responder = new DeprecatedSignedDocumentAPIResponder(exchange);
            var originalBody = body.originalSignRequestBody();
            signatureComposer.buildSignedDocument(originalBody.getDocument(), originalBody.getParameters(), body.dataToSignStructure(), body.signedData(), responder);

        } catch (Exception e) {
            EndpointUtils.respondWithError(ErrorResponse.buildFromException(e), exchange);
        }
    }
}
