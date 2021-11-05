package nl.carosi.remarkablepocket;

import es.jlarriba.jrmapi.Jrmapi;
import java.util.concurrent.atomic.AtomicReference;
import nl.siegmann.epublib.epub.EpubReader;
import nl.siegmann.epublib.epub.EpubWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRetry
@Import({
    ArticleDownloader.class,
    DownloadService.class,
    EpubReader.class,
    EpubWriter.class,
    MetadataProvider.class,
    PocketService.class,
    RemarkableService.class,
    SyncService.class,
    TokenRefresher.class
})
public class SyncApplication {
    @Bean
    AtomicReference<Jrmapi> jrmapi(@Value("${rm.device-token}") String deviceToken) {
        return new AtomicReference<>(new Jrmapi(deviceToken));
    }
}
