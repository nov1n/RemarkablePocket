package nl.carosi.remarkablepocket;

import static java.util.Map.entry;
import static nl.carosi.remarkablepocket.ConnectivityChecker.ensureConnected;
import static org.springframework.boot.Banner.Mode.OFF;
import static org.springframework.boot.WebApplicationType.NONE;
import static picocli.CommandLine.Help.Visibility.ALWAYS;

import java.util.Map;
import java.util.concurrent.Callable;
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
        version = "0.2.1",
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
    public Integer call() {
        ensureConnected(System.err::println);

        // Handle sigterm (^C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> Runtime.getRuntime().halt(1)));

        Map<String, Object> cliProperties =
                Map.ofEntries(
                        entry("pocket.archive-read", Boolean.toString(!noArchive)),
                        entry("rm.storage-dir", storageDir),
                        entry("rm.article-limit", articleLimit),
                        entry("sync.interval", "PT" + interval),
                        entry("sync.run-once", Boolean.toString(runOnce)),
                        entry("pocket.tag-filter", tagFilter),
                        entry("pocket.server.port", port),
                        entry(
                                "pocket.auth.file",
                                authFile.replaceFirst("^~", System.getProperty("user.home"))),
                        entry(
                                "logging.level." + this.getClass().getPackageName(),
                                verbose ? "DEBUG" : "INFO"));

        new SpringApplicationBuilder(SyncApplication.class)
                .logStartupInfo(false)
                .profiles("default")
                .bannerMode(OFF)
                .web(NONE)
                .properties(cliProperties)
                .run();

        return 0;
    }
}
