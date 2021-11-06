package nl.carosi.remarkablepocket;

import static java.util.Map.entry;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MINUTES;
import static nl.carosi.remarkablepocket.ConnectivityChecker.ensureConnected;
import static nl.carosi.remarkablepocket.PocketService.getAccessToken;
import static org.springframework.boot.Banner.Mode.OFF;
import static org.springframework.boot.WebApplicationType.NONE;
import static picocli.CommandLine.Help.Visibility.ALWAYS;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.jlarriba.jrmapi.Authentication;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "sync",
        description = "Synchronizes articles from Pocket to the Remarkable tablet.",
        sortOptions = false,
        usageHelpAutoWidth = true,
        // TODO: Read from gradle.properties
        version = "0.0.2",
        mixinStandardHelpOptions = true)
class SyncCommand implements Callable<Integer> {
    @Option(
            names = {"-f", "--tag-filter"},
            description = "Only download Pocket articles with the this tag.",
            arity = "1",
            defaultValue = "")
    private String tagFilter;

    @Option(
            names = {"-o", "--run-once"},
            description = "Run the synchronization once and then exit.",
            arity = "0")
    private boolean runOnce;

    @Option(
            names = {"-n", "--no-archive"},
            description = "Don't archive read articles.",
            arity = "0")
    private boolean noArchive;

    @Option(
            names = {"-l", "--article-limit"},
            description = "The maximum number of Pocket articles to be present on the Remarkable.",
            arity = "1",
            defaultValue = "10",
            showDefaultValue = ALWAYS)
    private String articleLimit;

    @Option(
            names = {"-i", "--interval"},
            description = "The interval between subsequent synchronizations.",
            arity = "1",
            defaultValue = "60m",
            showDefaultValue = ALWAYS)
    private String interval;

    @Option(
            names = {"-r", "--reset-credentials"},
            description = "Reset all credentials.",
            arity = "0")
    private boolean resetCredentials;

    @Option(
            names = {"-d", "--storage-dir"},
            description =
                    "The storage directory on the Remarkable in which to store downloaded Pocket articles.",
            arity = "1",
            defaultValue = "/Pocket/",
            showDefaultValue = ALWAYS)
    private String storageDir;

    @Option(
            names = {"-a", "--auth-file"},
            description = "The file in which to store the authentication credentials.",
            arity = "1",
            defaultValue = "~/.remarkable-pocket",
            hidden = true)
    private String authFile;

    // TODO: Create composite logger
    @Option(
            names = {"-v", "--verbose"},
            description = "Enable debug logging.",
            arity = "0")
    private boolean verbose;

    @Option(
            names = {"-p", "--port"},
            description = "The port for the authorization callback server.",
            arity = "1",
            defaultValue = "65112",
            hidden = true)
    private int port;

    public static void main(String... args) {
        new CommandLine(new SyncCommand()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        ensureConnected(System.err::println);

        Map<String, Object> cliProperties =
                Map.ofEntries(
                        entry("pocket.archive-read", Boolean.toString(!noArchive)),
                        entry("rm.storage-dir", storageDir),
                        entry("rm.article-limit", articleLimit),
                        entry("sync.interval", "PT" + interval),
                        entry("sync.run-once", Boolean.toString(runOnce)),
                        entry("pocket.tag-filter", tagFilter),
                        entry("logging.level" + this.getClass().getPackageName(), verbose ? "DEBUG" : "INFO")
                );

        new SpringApplicationBuilder(SyncApplication.class)
                .logStartupInfo(false)
                .profiles("default")
                .bannerMode(OFF)
                .web(NONE)
                .properties(cliProperties)
                .properties(authProperties())
                .run();

        return 0;
    }

    private Properties authProperties() throws IOException {
        Path authFilePath = Path.of(authFile.replaceFirst("^~", System.getProperty("user.home")));
        Path authDirPath = authFilePath.getParent();
        if (Files.notExists(authDirPath)) {
            Files.createDirectories(authDirPath);
        }

        if (Files.exists(authFilePath) && Files.size(authFilePath) > 0 && !resetCredentials) {
            Properties properties = new Properties();
            try (InputStream authStream = Files.newInputStream(authFilePath)) {
                properties.load(authStream);
            }
            return properties;
        } else {
            return createAuthProperties(authFilePath);
        }
    }

    private Properties createAuthProperties(Path authFilePath) throws IOException {
        Console console = System.console();
        if (console == null) {
            System.err.println(
                    "No console found. If you're using Docker please add the '-it' flags.\n");
            System.exit(1);
        }

        console.printf("""
        Welcome to Remarkable Pocket!
        Please follow the next steps to connect your Pocket and Remarkable accounts. You only need to do this once.
        
        """);

        PrintStream origOut = System.out;
        System.setOut(debugFilteringStream());
        String rmDeviceToken = obtainRmDeviceToken(console);
        System.setOut(origOut);

        String pocketAccessToken = obtainPocketAccessToken(console);

        Properties properties = new Properties();
        properties.setProperty("pocket.access-token", pocketAccessToken);
        properties.setProperty("rm.device-token", rmDeviceToken);
        try (OutputStream authStream = Files.newOutputStream(authFilePath)) {
            properties.store(authStream, null);
        }
        return properties;
    }

    // Hack to filter and format third-party logging until Spring is initialized.
    private PrintStream debugFilteringStream() {
        return new PrintStream(System.out) {
            @Override
            public void write(byte[] buf) {
                String msg = new String(buf, StandardCharsets.UTF_8);
                if(!msg.contains("DEBUG")) {
                    super.print(msg.split("- ")[1]);
                }
            }

        };
    }

    private String obtainRmDeviceToken(Console console) {
        String rmDeviceToken = null;
        while (rmDeviceToken == null) {
                String rmCloudCode =
                        console.readLine(
                                "Paste the code from https://my.remarkable.com/device/desktop/connect: ");
                console.printf("Verifying... ");
                rmDeviceToken = new Authentication().registerDevice(rmCloudCode, randomUUID());
        }
        console.printf("Success!\n");
        return rmDeviceToken;
    }

    private String obtainPocketAccessToken(Console console) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService execService = Executors.newSingleThreadExecutor();
        server.setExecutor(execService);
        server.createContext("/redirect", new RedirectHandler(server, execService));
        server.start();
        Consumer<String> waitForUserAuth =
                authUrl -> {
                    console.printf("Now visit %s and authorize this application.\n\n", authUrl);
                    try {
                        boolean terminated = execService.awaitTermination(1, MINUTES);
                        if (!terminated) {
                            throw new InterruptedException();
                        }
                    } catch (InterruptedException e) {
                        console.printf("Pocket authorization timed out. Please try again.\n");
                        System.exit(1);
                    }
                };
        return getAccessToken("http://localhost:" + port + "/redirect", waitForUserAuth);
    }

    private static final class RedirectHandler implements HttpHandler {
        private final HttpServer server;
        private final ExecutorService execService;

        RedirectHandler(HttpServer server, ExecutorService execService) {
            this.server = server;
            this.execService = execService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            OutputStream outputStream = exchange.getResponseBody();
            String res = "Authorization completed!";
            exchange.sendResponseHeaders(200, res.length());
            outputStream.write(res.getBytes());
            outputStream.flush();
            outputStream.close();
            server.stop(0);
            execService.shutdown();
        }
    }
}
