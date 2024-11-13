package digital.slovensko.autogram.service;

import com.sun.net.httpserver.HttpServer;
import digital.slovensko.autogram.core.server.endpoints.*;
import digital.slovensko.autogram.core.server.filters.AutogramCorsFilter;
import digital.slovensko.autogram.service.endpoints.DataToSignEndpoint;
import digital.slovensko.autogram.service.endpoints.BuildSignatureEndpoint;
import digital.slovensko.autogram.service.endpoints.VisualizationEndpoint;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

public class Server {
    private final HttpServer server;
    private final AutogramService autogramService;

    public Server(AutogramService autogramService, String hostname, int port, ExecutorService executorService) {
        this.autogramService = autogramService;
        this.server = buildServer(hostname, port);
        this.server.setExecutor(executorService);
    }

    public void start() throws IOException {
        // GET Info
        server.createContext("/info", new InfoEndpoint("0.0.0")).getFilters()
                .add(new AutogramCorsFilter("GET"));

        // GET Documentation
        server.createContext("/docs", new DocumentationEndpoint(Objects.requireNonNull(getClass().getResourceAsStream("server.yml")).readAllBytes())).getFilters()
                .add(new AutogramCorsFilter("GET"));

        // Assets
        server.createContext("/assets", new AssetsEndpoint()).getFilters()
                .add(new AutogramCorsFilter("GET"));

        // GET favicon.png
        server.createContext("/favicon.png", new FaviconEndpoint()).getFilters()
                .add(new AutogramCorsFilter("GET"));


        // POST build-signature
        server.createContext("/build-signature", new BuildSignatureEndpoint(autogramService)).getFilters()
                .add(new AutogramCorsFilter("POST"));

        // POST DataToSign
        server.createContext("/datatosign", new DataToSignEndpoint(autogramService)).getFilters()
                .add(new AutogramCorsFilter("POST"));

        // POST Visualization
        server.createContext("/visualization", new VisualizationEndpoint(autogramService)).getFilters()
                .add(new AutogramCorsFilter("POST"));

        // POST Extend
        server.createContext("/extend", new ExtensionEndpoint(autogramService)).getFilters()
                .add(new AutogramCorsFilter("POST"));


        // POST Validation
        server.createContext("/validate", new ValidationEndpoint()).getFilters()
                .add(new AutogramCorsFilter("POST"));


        server.start();
    }

    private HttpServer buildServer(String hostname, int port) {
        try {
            return HttpServer.create(new InetSocketAddress(hostname, port), 0);

        } catch (BindException e) {
            throw new RuntimeException("error.launchFailed.header port is already in use", e); // TODO

        } catch (Exception e) {
            throw new RuntimeException("error.serverNotCreated", e); // TODO
        }
    }

    public void stop() {
        ((ExecutorService) server.getExecutor()).shutdown(); // TODO find out why requests hang
        server.stop(1);
    }
}
