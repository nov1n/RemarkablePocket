package nl.carosi.remarkablepocket;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.springframework.boot.builder.SpringApplicationBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.util.Map.entry;
import static nl.carosi.remarkablepocket.ConnectivityChecker.ensureConnected;
import static org.springframework.boot.Banner.Mode.OFF;
import static org.springframework.boot.WebApplicationType.NONE;
import static picocli.CommandLine.Help.Visibility.ALWAYS;

@Command(
        name = "remarkable-pocket",
        description = "Synchronizes articles from Pocket to the Remarkable tablet.",
        sortOptions = false,
        usageHelpAutoWidth = true,
        versionProvider = VersionProvider.class,
        mixinStandardHelpOptions = true)
class SyncCommand implements Callable<Integer> {
    private static final String USER_HOME = System.getProperty("user.home");
    private static final String APP_NAME = "RemarkablePocket";

    @Option(
            names = {"-o", "--run-once"},
            description = "Run the synchronization once and then exit.",
            arity = "0")
    private boolean runOnce;

    @Option(
            names = {"-r", "--reset"},
            description = "Resets all configuration before starting.",
            arity = "0")
    private boolean reset;

    @Option(
            names = {"-f", "--tag-filter"},
            description = "Only download Pocket articles with the this tag.",
            arity = "1",
            defaultValue = "")
    private String tagFilter;

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
            names = {"-a", "--config-dir"},
            description = "The directory in which to store the configuration files.",
            arity = "1",
            defaultValue = "~/.remarkable-pocket",
            hidden = true)
    private String configDir;

    @Option(
            names = {"-d", "--storage-dir"},
            description =
                    "The storage directory on the Remarkable in which to store downloaded Pocket articles.",
            arity = "1",
            defaultValue = "/Pocket/",
            showDefaultValue = ALWAYS)
    private String storageDir;

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

    private static String replaceUserHome(String path) {
        return path.replaceFirst("^~", USER_HOME);
    }

    private void resetConfiguration(Path configPath) {
        if (Files.exists(configPath)) {
            try {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(configPath)) {
                    for (Path path : stream) {
                        if (Files.isRegularFile(path)) {
                            MoreFiles.deleteRecursively(path, RecursiveDeleteOption.ALLOW_INSECURE);
                            System.out.println("Successfully deleted config file: " + path);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to reset configuration", e);
            }
        } else {
            System.out.printf("Config directory not found: %s%n", configPath);
        }
    }

    private void createConfigDir(Path configPath) {
        try {
            Files.createDirectories(configPath);
        } catch (IOException e) {
            System.err.printf("Failed to create config directory: %s%n", configPath);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer call() {
        ensureConnected(System.err::println);

        configDir = replaceUserHome(configDir);
        Path configPath = Path.of(configDir);

        if (reset) {
            resetConfiguration(configPath);
        }

        if (!Files.exists(configPath)) {
            createConfigDir(configPath);
        }

        // Handle sigterm (^C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> Runtime.getRuntime().halt(1)));

        Map<String, Object> cliProperties =
                Map.ofEntries(
                        entry("config.dir", configDir),
                        entry("rm.storage-dir", storageDir),
                        entry("rm.article-limit", articleLimit),
                        entry("sync.interval", "PT" + interval),
                        entry("sync.run-once", Boolean.toString(runOnce)),
                        entry("pocket.archive-read", Boolean.toString(!noArchive)),
                        entry("pocket.tag-filter", tagFilter),
                        entry("pocket.server.port", port),
                        entry("logging.level." + this.getClass().getPackageName(), verbose ? "TRACE" : "INFO"));

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

