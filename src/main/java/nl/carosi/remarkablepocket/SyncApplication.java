package nl.carosi.remarkablepocket;

import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import nl.siegmann.epublib.epub.EpubReader;
import nl.siegmann.epublib.epub.EpubWriter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import pl.codeset.pocket.Pocket;

@SpringBootApplication
@EnableScheduling
@EnableRetry
@Import({
  ArticleDownloader.class,
  ArticleValidator.class,
  DownloadService.class,
  EpubReader.class,
  EpubWriter.class,
  MetadataProvider.class,
  PocketService.class,
  PocketAuthenticator.class,
  RemarkableApi.class,
  RemarkableService.class,
  SyncService.class,
})
public class SyncApplication {

  @Bean
  DocumentBuilder documentBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    return builderFactory.newDocumentBuilder();
  }

  @Bean
  Pocket pocket(PocketAuthenticator authenticator) {
    try {
      return new Pocket(authenticator.getAuth());
    } catch (IOException e) {
      throw new RuntimeException("Could not connect to Pocket", e);
    }
  }
}
