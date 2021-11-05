package nl.carosi.remarkablepocket;

import com.google.common.collect.Streams;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import nl.carosi.remarkablepocket.model.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DownloadService {
    private static final Logger LOG = LoggerFactory.getLogger(DownloadService.class);

    private final ArticleDownloader downloader;
    private final HashSet<Article> invalidArticles = new HashSet<>();
    private Path storageDir;

    DownloadService(ArticleDownloader downloader) {
        this.downloader = downloader;
    }

    @PostConstruct
    void createStorageDir() throws IOException {
        storageDir = Files.createTempDirectory(null);
        LOG.debug("Created temporary storage directory: {}.", storageDir);
    }

    @SuppressWarnings("UnstableApiUsage")
    List<Path> download(List<Article> articles, int limit) {
        int pocketCount = articles.size();
        long total = Math.min(pocketCount, limit);
        LOG.info("Downloading {} unread article(s) from Pocket ({} in total).", total, pocketCount);
        return Streams.mapWithIndex(articles.stream(), (e, i) -> logProgress(e, i, total))
                .filter(e -> !invalidArticles.contains(e))
                .map(this::tryDownload)
                .flatMap(Optional::stream)
                .limit(limit)
                .toList();
    }

    private Optional<Path> tryDownload(Article e) {
        Optional<Path> path = downloader.tryDownload(e, storageDir);
        if (path.isEmpty()) {
            invalidArticles.add(e);
        }
        return path;
    }

    private Article logProgress(Article article, long index, long total) {
        LOG.info("({}/{}) Downloading: '{}'.", index + 1, total, article.title());
        return article;
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
