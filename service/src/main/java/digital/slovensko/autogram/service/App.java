package digital.slovensko.autogram.service;

import digital.slovensko.autogram.core.Settings;
import eu.europa.esig.dss.service.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.x509.tsp.CompositeTSPSource;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class App {
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
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

            var portStr = cmd.getOptionValue("port", System.getenv("PORT"));
            if (portStr == null)
                portStr = "7200";
            var port = Integer.parseInt(portStr);

            var tsaServers = cmd.getOptionValue("tsa-server", System.getenv("TSA_SERVER"));
            if (tsaServers == null)
                tsaServers = "http://ts.quovadisglobal.com/eu,http://tsa.baltstamp.lt,http://tsa.belgium.be/connect";

            var timestampDataLoader = new TimestampDataLoader();
            var tspSources = new HashMap<String, TSPSource>();
            for (var tsaServer : tsaServers.split(","))
                tspSources.put(tsaServer, new OnlineTSPSource(tsaServer, timestampDataLoader));

            var tspSource = new CompositeTSPSource();
            tspSource.setTspSources(tspSources);

            var tlCountries = "BE,BG,CZ,HR,CY,DK,EE,FI,FR,EL,NL,IE,LT,LV,LU,HU,MT,DE,PL,PT,AT,RO,SK,SI,ES,SE,IT";

            var settings = new Settings();
            settings.setTspSource(tspSource);
            settings.setTrustedList(tlCountries);
            settings.setEn319132(false);
            settings.setPlainXmlEnabled(false);

            run(port, settings);

        } catch (ParseException e) {
            LOGGER.error("Unable to parse program args", e);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void printHelp() {
        final HelpFormatter formatter = new HelpFormatter();

        formatter.printHelp("autogram-service", options);
    }

    public static void run(int port, Settings settings) throws Exception {
        var autogramService = new AutogramService(settings);

        new Thread(() ->
            autogramService.initializeSignatureValidator(scheduledExecutorService, Executors.newFixedThreadPool(8))
        ).start();

        var server = new Server(autogramService, "0.0.0.0", port, cachedExecutorService);

        LOGGER.info("Starting server on port {}", port);
        server.start();
    }
}
