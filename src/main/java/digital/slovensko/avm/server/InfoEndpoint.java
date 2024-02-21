package digital.slovensko.avm.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import digital.slovensko.avm.server.dto.InfoResponse;
import static digital.slovensko.avm.server.dto.InfoResponse.*;

import java.io.IOException;

public class InfoEndpoint implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        var response = new InfoResponse(getStatus());
        var gson = new Gson();

        try (exchange) {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(gson.toJson(response).getBytes());
        }
    }
}
