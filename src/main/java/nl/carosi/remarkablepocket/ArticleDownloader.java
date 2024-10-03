package nl.carosi.remarkablepocket;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.CharStreams;
import nl.carosi.remarkablepocket.model.Article;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.Resources;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.epub.EpubReader;
import nl.siegmann.epublib.epub.EpubWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.*;

class ArticleDownloader {
    public static final String POCKET_ID_SEPARATOR = "\t\t\t";

    private static final Logger LOG = LoggerFactory.getLogger(ArticleDownloader.class);
    private static final String BASE_URL = "https://epub.press/api/v1/books";
    private static final String DOWNLOAD_URL_TEMPL = BASE_URL + "/%s/download";
    private static final String STATUS_URL_TEMPL = BASE_URL + "/%s/status";
    // Interval between epub press download retries.
    private static final int RETRY_INTERVAL = 5000;
    // Minimum character count for an epub to be considered valid.
    private static final int MIN_VALID_CONTENT_SIZE = 4000;
    private static final List<String> UNWANTED_RESOURCES =
            List.of(
                    "cover.xhtml",
                    "images/cover.png",
                    "content/s2.xhtml", // References
                    "content/toc.xhtml", // Table of contents
                    "toc.ncx");

    private final RestTemplate restTemplate;
    private final EpubReader epubReader;
    private final EpubWriter epubWriter;
    // Required to call @Retryable method from the same class.
    @Autowired
    private ArticleDownloader self;

    public ArticleDownloader(
            RestTemplateBuilder restTemplateBuilder, EpubReader epubReader, EpubWriter epubWriter) {
        this.restTemplate = restTemplateBuilder.build();
        this.epubReader = epubReader;
        this.epubWriter = epubWriter;
    }

    Optional<Path> tryDownload(Article article, Path storageDir) {
        try {
            return tryDownloadImpl(article, storageDir);
        } catch (IOException | RuntimeException e) {
            LOG.error("Failed to download article: {}.", e.getMessage());
            LOG.debug("Stack trace: ", e);
            return Optional.empty();
        }
    }

    private Optional<Path> tryDownloadImpl(Article article, Path storageDir) throws IOException {
        DownloadRequest req =
                new DownloadRequest(
                        article.url(),
                        "Pocket"
                                + POCKET_ID_SEPARATOR
                                + article.id(), // Hide Pocket ID from the Remarkable UI.
                        new String[]{article.url()});
        String downloadId = restTemplate.postForObject(BASE_URL, req, DownloadResponse.class).id();
        self.waitUntilReady(downloadId);

        Path downloadPath = storageDir.resolve(article.title() + "." + getFileType());
        try (OutputStream downloadStream =
                     Files.newOutputStream(downloadPath, CREATE, WRITE, TRUNCATE_EXISTING)) {
            restTemplate.execute(
                    String.format(DOWNLOAD_URL_TEMPL, downloadId),
                    HttpMethod.GET,
                    null,
                    res -> res.getBody().transferTo(downloadStream));
        }
        formatEpub(downloadPath, article.title());
        return isValid(downloadPath) ? Optional.of(downloadPath) : Optional.empty();
    }

    String getFileType() {
        return "epub";
    }

    private boolean isValid(Path article) throws IOException {
        Resource resource;
        try (InputStream articleStream = Files.newInputStream(article)) {
            resource =
                    epubReader
                            .readEpub(articleStream)
                            .getResources()
                            .getResourceMap()
                            .get("content/s1.xhtml");
        }

        try (BufferedReader contentReader =
                     new BufferedReader(
                             new InputStreamReader(
                                     resource.getInputStream(), resource.getInputEncoding()))) {
            String content = CharStreams.toString(contentReader);
            boolean isValid = content.length() > MIN_VALID_CONTENT_SIZE;
            if (!isValid) {
                LOG.warn(
                        "Downloaded article is invalid. See https://github.com/nov1n/RemarkablePocket#limitations for possible causes.");
            }
            return isValid;
        }
    }

    @Retryable(
            value = RuntimeException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = RETRY_INTERVAL))
    @VisibleForTesting
    void waitUntilReady(String downloadId) {
        StatusResponse res =
                restTemplate.getForObject(
                        String.format(STATUS_URL_TEMPL, downloadId), StatusResponse.class);
        int progress = res.progress();
        LOG.debug("Status: {} progress: {}%", res.message(), progress);
        if (progress != 100) {
            throw new RuntimeException("Epub generation error");
        }
    }

    private void formatEpub(Path path, String title) throws IOException {
        Book book;
        try (InputStream epubStream = Files.newInputStream(path)) {
            book = epubReader.readEpub(epubStream);
        }
        book.getMetadata().setTitles(List.of(title));
        Resources resources = book.getResources();
        for (String href : UNWANTED_RESOURCES) {
            resources.remove(href);
        }
        List<SpineReference> spineReferences =
                book.getSpine().getSpineReferences().stream()
                        .filter(e -> e.getResourceId().equals("s1"))
                        .toList();
        book.getSpine().setSpineReferences(spineReferences);
        book.setCoverImage(resources.getByHref("content/s1.xhtml"));
        // TODO: Remove navpoints and fix play order to start at 1, use a xml parser lib
        // TODO: Remove guide element from ocx file
        try (OutputStream bookStream = Files.newOutputStream(path)) {
            epubWriter.write(book, bookStream);
        }
    }

    private record DownloadRequest(String author, String publisher, String[] urls) {
    }

    private record DownloadResponse(String id) {
    }

    private record StatusResponse(String message, int progress) {
    }
}
