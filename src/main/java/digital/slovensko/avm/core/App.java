package digital.slovensko.avm.core;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import digital.slovensko.avm.server.Server;
import org.apache.commons.cli.*;
import eu.europa.esig.dss.service.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.x509.tsp.CompositeTSPSource;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;

public class App {
    private static final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
    private static final ExecutorService cachedExecutorService = Executors.newFixedThreadPool(8);

    private static final Options options = new Options().
        addOption("h", "help", false, "Print this command line help.").
        addOption(null, "tsa-server", true, "Url of TimeStamp Authority servers that should be used for timestamping in signature level BASELINE_T. Multiple values must be separated by comma. Overrides TSA_SERVER environment variable.").
        addOption("p", "port", true, "Port to listen on.");

    public static void start(String[] args) {
        try {
            CommandLine cmd = new DefaultParser().parse(options, args);

            if (cmd.hasOption("h")) {
                printHelp();
                return;
            }

            var port = Integer.parseInt(cmd.getOptionValue("port", "7200"));
            var tsaServers = cmd.getOptionValue("tsa-server", System.getenv("TSA_SERVER"));
            if (tsaServers == null)
                tsaServers = "http://tsa.belgium.be/connect,http://ts.quovadisglobal.com/eu,http://tsa.sep.bg";

            var timestampDataLoader = new TimestampDataLoader();
            var tspSource = new CompositeTSPSource();
            var tspSources = new HashMap<String, TSPSource>();
            for (var tsaServer : tsaServers.split(","))
                tspSources.put(tsaServer, new OnlineTSPSource(tsaServer, timestampDataLoader));

            tspSource.setTspSources(tspSources);
            run(port, tspSource);

        } catch (ParseException e) {
            System.err.println("Unable to parse program args");
            System.err.println(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void printHelp() {
        final HelpFormatter formatter = new HelpFormatter();

        formatter.printHelp("avm", options);
    }

    public static void run(int port, TSPSource tspSource) throws Exception {
        var avm = new AVM(tspSource, false);

        new Thread(() -> {
            avm.initializeSignatureValidator(scheduledExecutorService, Executors.newFixedThreadPool(8));
        }).start();

        var server = new Server(avm, "0.0.0.0", port, cachedExecutorService);
        server.start();
    }
}
