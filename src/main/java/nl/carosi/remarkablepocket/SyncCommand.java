package nl.carosi.remarkablepocket;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.springframework.boot.builder.SpringApplicationBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.util.Map.entry;
import static nl.carosi.remarkablepocket.ConnectivityChecker.ensureConnected;
import static org.springframework.boot.Banner.Mode.OFF;
import static org.springframework.boot.WebApplicationType.NONE;
import static picocli.CommandLine.Help.Visibility.ALWAYS;

@Command(
        name = "sync",
        description = "Synchronizes articles from Pocket to the Remarkable tablet.",
        sortOptions = false,
        usageHelpAutoWidth = true,
        versionProvider = VersionProvider.class,
        mixinStandardHelpOptions = true)
class SyncCommand implements Callable<Integer> {
    private static final String USER_HOME = System.getProperty("user.home");
    private static final String APP_NAME = "RemarkablePocket";
    private static final String RMAPI_CONFIG = replaceUserHome("~/.rmapi");
    private static final String RMAPI_CACHE = replaceUserHome("~/.rmapi-cache");

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
            names = {"-r", "--reset"},
            description = "Resets all configuration before starting.",
            arity = "0")
    private boolean reset;

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
            names = {"-db", "--database-path"},
            description =
                    "The directory path in which to store the invalid articles database.",
            arity = "1")
    private String dbPath;

    @Option(
            names = {"-a", "--auth-file"},
            description = "The file in which to store the authentication credentials.",
            arity = "1",
            defaultValue = "~/.remarkable-pocket",
            hidden = true)
    private String authFile;

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

    private static void resetConfiguration(List<String> configurationFiles) {
        for (String pathString : configurationFiles) {
            Path path = Paths.get(pathString);
            if (Files.exists(path)) {
                try {
                    MoreFiles.deleteRecursively(path, RecursiveDeleteOption.ALLOW_INSECURE);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                System.out.printf("Successfully deleted directory: %s%n", pathString);
            } else {
                System.out.printf("File or directory does not exist: %s, skipping...%n", pathString);
            }
        }
    }

    private static Path getAppDataPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return Paths.get(System.getenv("APPDATA"), APP_NAME);
        } else if (os.contains("mac")) {
            return Paths.get(USER_HOME, "Library", "Application Support", APP_NAME);
        } else {
            return Paths.get(USER_HOME, ".local", "share", APP_NAME);
        }
    }

    private static String replaceUserHome(String path) {
        return path.replaceFirst("^~", USER_HOME);
    }

    @Override
    public Integer call() {
        ensureConnected(System.err::println);

        if (dbPath == null) {
            dbPath = getAppDataPath().toString();
        }

        authFile = replaceUserHome(authFile);

        if (reset) {
            resetConfiguration(List.of(dbPath, authFile, RMAPI_CONFIG, RMAPI_CACHE));
        }

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
                        entry("db.path", dbPath),
                        entry("pocket.auth.file", authFile),
                        entry("logging.level." + this.getClass().getPackageName(), verbose ? "DEBUG" : "INFO"));

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

