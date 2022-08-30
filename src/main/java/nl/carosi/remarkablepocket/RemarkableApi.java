package nl.carosi.remarkablepocket;

import static java.lang.ProcessBuilder.Redirect.INHERIT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Predicate.not;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import nl.carosi.remarkablepocket.model.Document;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;

@DependsOn("pocket") // Forces pocket auth to happen before rm auth
public class RemarkableApi {
    private static final Logger LOG = LoggerFactory.getLogger(RemarkableApi.class);
    private static final List<String> RMAPI_WARNING_PREFIXES =
            List.of(
                    "Refreshing tree...",
                    "WARNING!!!",
                    "  Using the new 1.5 sync",
                    "  Make sure you have a backup");
    private static final String RMAPI_EXECUTABLE = "/usr/local/bin/rmapi";
    private final String rmStorageDir;
    private final ObjectMapper objectMapper;

    public RemarkableApi(
            ObjectMapper objectMapper, @Value("${rm.storage-dir}") String rmStorageDir) {
        this.objectMapper = objectMapper;
        this.rmStorageDir = rmStorageDir;
    }

    private static List<String> exec(String... command) {
        return exec(List.<String[]>of(command));
    }

    private static List<String> exec(List<String[]> commands) {
        List<ProcessBuilder> builders =
                commands.stream()
                        .map(ProcessBuilder::new)
                        .peek(builder -> LOG.debug("Executing command: {}", builder.command()))
                        .toList();
        try {
            List<Process> processes = ProcessBuilder.startPipeline(builders);
            Process last = processes.get(processes.size() - 1);

            last.errorReader(UTF_8)
                    .lines()
                    .filter(line -> RMAPI_WARNING_PREFIXES.stream().noneMatch(line::startsWith))
                    .forEach(LOG::error);

            return last.inputReader(UTF_8).lines().peek(LOG::debug).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void logStream(InputStream src, Consumer<String> consumer) {
        new Thread(
                        () -> {
                            Scanner sc = new Scanner(src);
                            sc.useDelimiter(Pattern.compile("\\n|: "));
                            while (sc.hasNext()) {
                                String token = sc.next();
                                if (token.equals("Refreshing tree...")) {
                                    consumer.accept("Refreshing cache... This may take a while.");
                                } else {
                                    consumer.accept(token);
                                }
                            }
                        })
                .start();
    }

    @PostConstruct
    public void login() {
        try {
            Process proc =
                    new ProcessBuilder(RMAPI_EXECUTABLE, "version").redirectInput(INHERIT).start();
            logStream(proc.getInputStream(), LOG::info);
            logStream(proc.getErrorStream(), LOG::error);
            int exitCode = proc.waitFor();
            LOG.info("");
            if (exitCode != 0) {
                throw new RuntimeException("Could not authenticate to Remarkable API");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Could not authenticate to Remarkable API", e);
        }
    }

    public String download(String name, String dest) {
        exec(RMAPI_EXECUTABLE, "-ni", "get", rmStorageDir + name);
        exec("mv", name + ".zip", dest);
        return dest + File.separator + name + ".zip";
    }

    public List<String> list() {
        return exec(
                List.of(
                        new String[] {RMAPI_EXECUTABLE, "-ni", "ls", rmStorageDir},
                        new String[] {"grep", "^\\[f\\]"},
                        new String[] {"cut", "-b5-"}));
    }

    public Document info(String name) {
        List<String> info =
                exec(
                        List.of(
                                new String[] {RMAPI_EXECUTABLE, "-ni", "stat", rmStorageDir + name},
                                new String[] {"sed", "/{/,$!d"}));
        try {
            return objectMapper.readValue(Strings.join(info, '\n'), Document.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error parsing Remarkable API response", e);
        }
    }

    public void upload(String path) {
        exec(RMAPI_EXECUTABLE, "-ni", "put", path, rmStorageDir);
    }

    public void delete(String name) {
        exec(RMAPI_EXECUTABLE, "-ni", "rm", rmStorageDir + name);
    }

    public void createDir(String path) {
        List<String> parts = Arrays.stream(path.split("/")).filter(not(String::isEmpty)).toList();
        for (int i = 1; i <= parts.size(); i++) {
            String subdir = String.join("/", parts.subList(0, i));
            exec(RMAPI_EXECUTABLE, "-ni", "mkdir", subdir);
        }
    }
}
