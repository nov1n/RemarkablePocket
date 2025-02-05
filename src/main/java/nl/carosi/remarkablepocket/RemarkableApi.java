package nl.carosi.remarkablepocket;

import static java.lang.ProcessBuilder.Redirect.INHERIT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Predicate.not;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import nl.carosi.remarkablepocket.model.Document;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;

@DependsOn("pocket") // Forces pocket auth to happen before rm auth
public class RemarkableApi {
  private static final Logger LOG = LoggerFactory.getLogger(RemarkableApi.class);
  private static final String RMAPI_CONFIG_FILE = ".rmapi";
  private static final List<String> RMAPI_WARNING_PREFIXES =
      List.of(
          "Refreshing tree...",
          "WARNING!!!",
          "  Using the new 1.5 sync",
          "  Make sure you have a backup");
  private static final String RMAPI_EXECUTABLE =
      "/usr/local/bin/rmapi"
          + ((new File("/.dockerenv").exists() // Running in Docker
                  || new File("/run/.containerenv").exists()) // Running in Podman
              ? ("_" + System.getProperty("os.arch"))
              : "");
  private final String rmStorageDir;
  private final ObjectMapper objectMapper;
  private final String rmapiConfig;
  private String workDir;

  public RemarkableApi(
      ObjectMapper objectMapper,
      @Value("${rm.storage-dir}") String rmStorageDir,
      @Value("${config.dir}") String configDir) {
    this.objectMapper = objectMapper;
    this.rmStorageDir = rmStorageDir;
    this.rmapiConfig = configDir + "/" + RMAPI_CONFIG_FILE;
  }

  private static void logStream(InputStream src, Consumer<String> consumer) {
    new Thread(
            () -> {
              Scanner sc = new Scanner(src);
              sc.useDelimiter(Pattern.compile("\\n|\\): "));
              while (sc.hasNext()) {
                String token = sc.next();
                if (token.startsWith("Enter one-time code")) {
                  consumer.accept(token + "):");
                } else {
                  consumer.accept(token);
                }
              }
            })
        .start();
  }

  private List<String> exec(String... command) {
    return exec(List.<String[]>of(command));
  }

  private List<String> exec(List<String[]> commands) {
    List<ProcessBuilder> builders =
        commands.stream()
            .map(this::createProcessBuilder)
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

  @PostConstruct
  void createWorkDir() throws IOException {
    workDir = Files.createTempDirectory(null).toAbsolutePath().toString();
    LOG.debug("Created temporary working directory: {}.", workDir);
  }

  private ProcessBuilder createProcessBuilder(String... command) {
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.environment().put("RMAPI_CONFIG", rmapiConfig);
    return processBuilder;
  }

  @PostConstruct
  public void login() {
    try {
      Process proc =
          createProcessBuilder(RMAPI_EXECUTABLE, "account").redirectInput(INHERIT).start();
      logStream(proc.getInputStream(), LOG::info);
      logStream(proc.getErrorStream(), LOG::error);
      int exitCode = proc.waitFor();
      if (exitCode != 0) {
        throw new RuntimeException("Could not connect to Remarkable Cloud");
      }
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Could not connect to Remarkable Cloud", e);
    }
  }

  public Path download(String articleName) {
    exec(RMAPI_EXECUTABLE, "-ni", "get", rmStorageDir + articleName);
    exec("mv", articleName + ".rmdoc", workDir + "/" + articleName + ".zip");
    return Path.of(workDir, articleName + ".zip");
  }

  public List<String> list() {
    return exec(
        List.of(
            new String[] {RMAPI_EXECUTABLE, "-ni", "ls", rmStorageDir},
            new String[] {"grep", "^\\[f\\]"},
            new String[] {"cut", "-b5-"}));
  }

  public Document info(String articleName) {
    List<String> info =
        exec(
            List.of(
                new String[] {RMAPI_EXECUTABLE, "-ni", "stat", rmStorageDir + articleName},
                new String[] {"sed", "/{/,$!d"}));
    try {
      return objectMapper.readValue(Strings.join(info, '\n'), Document.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error parsing Remarkable Cloud response", e);
    }
  }

  public void upload(Path path) {
    exec(RMAPI_EXECUTABLE, "-ni", "put", path.toString(), rmStorageDir);
  }

  public void delete(String articleName) {
    exec(RMAPI_EXECUTABLE, "-ni", "rm", rmStorageDir + articleName);
  }

  public void createDir(String path) {
    List<String> parts = Arrays.stream(path.split("/")).filter(not(String::isEmpty)).toList();
    for (int i = 1; i <= parts.size(); i++) {
      String subdir = String.join("/", parts.subList(0, i));
      exec(RMAPI_EXECUTABLE, "-ni", "mkdir", subdir);
    }
  }
}
