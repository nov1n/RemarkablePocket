package nl.carosi.remarkablepocket;

import static java.util.concurrent.TimeUnit.MINUTES;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import pl.codeset.pocket.PocketAuth;
import pl.codeset.pocket.PocketAuthFactory;

public class PocketAuthenticator {
  private static final Logger LOG = LoggerFactory.getLogger(PocketAuthenticator.class);
  private static final String CONSUMER_KEY = "99428-51e4648a4528a1faa799c738";
  private static final String TOKEN_PROPERTY = "pocket.access.token";
  private static final String POCKET_AUTH_FILE = ".pocket-auth";
  private final Path authFile;
  private final int port;

  public PocketAuthenticator(
      @Value("${config.dir}") Path configDir, @Value("${pocket.server.port}") int port) {
    this.authFile = configDir.resolve(POCKET_AUTH_FILE);
    this.port = port;
  }

  public PocketAuth getAuth() throws IOException {
    if (Files.exists(authFile)) {
      return authFromFile();
    } else {
      return authAndStore();
    }
  }

  private PocketAuth authFromFile() throws IOException {
    Properties properties = new Properties();
    try (InputStream authStream = Files.newInputStream(authFile)) {
      properties.load(authStream);
    }
    return PocketAuthFactory.createForAccessToken(
        CONSUMER_KEY, properties.getProperty(TOKEN_PROPERTY));
  }

  private PocketAuth authAndStore() throws IOException {
    PocketAuth auth = authenticate();
    Properties properties = new Properties();
    properties.setProperty(TOKEN_PROPERTY, auth.getAccessToken());
    try (OutputStream authStream = Files.newOutputStream(authFile)) {
      properties.store(authStream, null);
    }
    return auth;
  }

  private PocketAuth authenticate() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    ExecutorService execService = Executors.newSingleThreadExecutor();
    server.setExecutor(execService);
    server.createContext("/redirect", new RedirectHandler(server, execService));
    server.start();

    PocketAuthFactory factory =
        PocketAuthFactory.create(CONSUMER_KEY, "http://localhost:" + port + "/redirect");
    String authUrl = factory.getAuthUrl();
    LOG.info("Visit {} and authorize this application.\n", authUrl);
    try {
      boolean terminated = execService.awaitTermination(5, MINUTES);
      if (!terminated) {
        throw new InterruptedException();
      }
    } catch (InterruptedException e) {
      LOG.info("Pocket authorization timed out. Please try again.");
      // System.exit doesn't work here. I suspect there is a deadlock in the
      // 'logStream' method where it blocks on stdin.
      Runtime.getRuntime().halt(1);
    }

    return factory.create();
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
