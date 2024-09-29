package nl.carosi.remarkablepocket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PostConstruct;
import nl.carosi.remarkablepocket.model.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DownloadService {
    private static final Logger LOG = LoggerFactory.getLogger(DownloadService.class);

    private final ArticleDownloader downloader;
    private final ArticleValidator validator;
    private Path storageDir;

    DownloadService(ArticleDownloader downloader, ArticleValidator validator) {
        this.downloader = downloader;
        this.validator = validator;
    }

    @PostConstruct
    void createStorageDir() throws IOException {
        storageDir = Files.createTempDirectory(null);
        LOG.debug("Created temporary storage directory: {}.", storageDir);
    }

    @SuppressWarnings("UnstableApiUsage")
    List<Path> download(List<Article> articles, int articleLimit, int nArticlesOnRm) {
        validator.logInvalidArticles();
        int limit = articleLimit - nArticlesOnRm;
        int pocketCount = articles.size();
        int total = Math.min(pocketCount, limit);
        LOG.info(
                "Found {} unread article(s) on Remarkable. Downloading {} more from Pocket.",
                nArticlesOnRm,
                total);
        AtomicInteger count = new AtomicInteger(1);
        return articles.stream()
                .filter(article -> validator.isValid(article.title()))
                .map(article -> tryDownload(article, count, total))
                .flatMap(Optional::stream)
                .limit(limit)
                .toList();
    }

    private Optional<Path> tryDownload(Article article, AtomicInteger count, int total) {
        String title = article.title();
        LOG.info("({}/{}) Downloading: '{}'.", count.get(), total, title);
        Optional<Path> path = downloader.tryDownload(article, storageDir);
        if (path.isEmpty()) {
            validator.invalidate(title);
        } else {
            LOG.info("Download successful.");
            count.incrementAndGet();
        }
        return path;
    }

    void clearDownloads() throws IOException {
        try (DirectoryStream<Path> paths =
                Files.newDirectoryStream(storageDir, "*." + downloader.getFileType())) {
            for (Path p : paths) {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }
}
