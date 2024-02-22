package digital.slovensko.avm.server;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;

import com.sun.net.httpserver.HttpServer;

import digital.slovensko.avm.core.AVM;
import digital.slovensko.avm.server.endpoints.*;
import digital.slovensko.avm.server.filters.AutogramCorsFilter;

public class Server {
    private final HttpServer server;
    private final AVM avm;

    public Server(AVM avm, String hostname, int port, ExecutorService executorService) {
        this.avm = avm;
        this.server = buildServer(hostname, port);
        this.server.setExecutor(executorService);
    }

    public void start() {
        // GET Info
        server.createContext("/info", new InfoEndpoint()).getFilters()
                .add(new AutogramCorsFilter("GET"));

        // GET Documentation
        server.createContext("/docs", new DocumentationEndpoint()).getFilters()
                .add(new AutogramCorsFilter("GET"));

        // POST Sign
        server.createContext("/sign", new SignEndpoint(avm)).getFilters()
                .add(new AutogramCorsFilter("POST"));

        // POST DataToSign
        server.createContext("/datatosign", new DataToSignEndpoint(avm)).getFilters()
                .add(new AutogramCorsFilter("POST"));

        // POST Visualization
        server.createContext("/visualization", new VisualizationEndpoint(avm)).getFilters()
                .add(new AutogramCorsFilter("POST"));

        // POST ValidateParameters
        server.createContext("/parameters/validate", new ValidateParametersEndpoint()).getFilters()
                .add(new AutogramCorsFilter("POST"));

        // Start server
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
