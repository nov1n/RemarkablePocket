package nl.carosi.remarkablepocket;

import static java.time.temporal.ChronoUnit.SECONDS;
import static nl.carosi.remarkablepocket.ConnectivityChecker.ensureConnected;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nl.carosi.remarkablepocket.model.Article;
import nl.carosi.remarkablepocket.model.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

final class SyncService {
    private static final Logger LOG = LoggerFactory.getLogger(SyncService.class);
    private final PocketService pocketService;
    private final DownloadService downloadService;
    private final RemarkableService remarkableService;
    private final ApplicationContext appContext;
    private final int articleLimit;
    private final boolean archiveRead;
    private final Duration syncInterval;
    private final boolean runOnce;

    public SyncService(
            PocketService pocketService,
            DownloadService downloadService,
            RemarkableService remarkableService,
            ApplicationContext appContext,
            @Value("${rm.article-limit}") int articleLimit,
            @Value("${pocket.archive-read}") boolean archiveRead,
            @Value("${sync.interval}") Duration syncInterval,
            @Value("${sync.run-once}") boolean runOnce) {
        this.pocketService = pocketService;
        this.downloadService = downloadService;
        this.remarkableService = remarkableService;
        this.appContext = appContext;
        this.articleLimit = articleLimit;
        this.archiveRead = archiveRead;
        this.syncInterval = syncInterval;
        this.runOnce = runOnce;
    }

    private static String humanReadable(Duration duration) {
        return duration.truncatedTo(SECONDS)
                .toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
    }

    @Scheduled(fixedDelayString = "${sync.interval}")
    void sync() {
        ensureConnected(LOG::error);

        try {
            syncImpl();
        } catch (Exception e) {
            LOG.error("Error occurred during sync: {}", e.getMessage());
            LOG.debug("Stack trace:", e);
        }

        if (runOnce) {
            LOG.info("Run-once option was set. Exiting.");
            System.exit(SpringApplication.exit(appContext, () -> 0));
        }

        LOG.info("Next sync in {}.\n", humanReadable(syncInterval));
    }

    private void syncImpl() throws IOException {
        LOG.info("Starting sync...");
        Instant start = Instant.now();
        if (archiveRead) {
            archiveReadArticles();
        }

        Set<String> articlesOnRm = Set.copyOf(remarkableService.listDocumentNames());
        int nArticlesOnRm = articlesOnRm.size();
        if (nArticlesOnRm >= articleLimit) {
            LOG.info("No new articles synced. Remarkable already has {} article(s).", articleLimit);
            return;
        }
        List<Article> unsynced =
                pocketService.getArticles().stream()
                        .filter(e -> !articlesOnRm.contains(e.title()))
                        .collect(Collectors.toList());
        if (unsynced.size() == 0) {
            LOG.info("All Pocket articles are synced with Remarkable.");
            return;
        }

        downloadService.clearDownloads();
        List<Path> downloads = downloadService.download(unsynced, articleLimit, nArticlesOnRm);
        remarkableService.upload(downloads);
        LOG.info("Completed sync in {}.", humanReadable(Duration.between(start, Instant.now())));
    }

    private void archiveReadArticles() throws IOException {
        List<DocumentMetadata> documents = remarkableService.listReadDocuments();
        int nDocs = documents.size();
        LOG.info("Found {} read article(s) on Remarkable.", nDocs);
        for (int i = 0; i < nDocs; i++) {
            DocumentMetadata doc = documents.get(i);
            LOG.info(
                    "({}/{}) Marking '{}' as read on Pocket...",
                    i + 1,
                    nDocs,
                    doc.doc().getVissibleName());
            pocketService.archive(doc.pocketId());
            LOG.info(
                    "({}/{}) Deleting '{}' from Remarkable...",
                    i + 1,
                    nDocs,
                    doc.doc().getVissibleName());
            remarkableService.delete(doc.doc());
        }
    }
}
