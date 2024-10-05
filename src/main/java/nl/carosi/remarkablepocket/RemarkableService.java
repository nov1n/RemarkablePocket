package nl.carosi.remarkablepocket;

import jakarta.annotation.PostConstruct;
import nl.carosi.remarkablepocket.model.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

final class RemarkableService {
    private static final Logger LOG = LoggerFactory.getLogger(RemarkableService.class);
    private final RemarkableApi rmapi;
    private final MetadataProvider metadataProvider;
    private final String rmStorageDir;

    public RemarkableService(
            RemarkableApi rmapi,
            MetadataProvider metadataProvider,
            @Value("${rm.storage-dir}") String rmStorageDir) {
        if (!rmStorageDir.endsWith("/")) {
            rmStorageDir += "/";
        }
        checkArgument(
                rmStorageDir.matches("^/([^:/\\\\*\"?|<>.']+/)+$"),
                "Invalid Remarkable storage dir. A valid example is: '/Articles/Pocket/'.");
        this.rmapi = rmapi;
        this.metadataProvider = metadataProvider;
        this.rmStorageDir = rmStorageDir;
    }

    @PostConstruct
    void createStorageDir() {
        rmapi.createDir(rmStorageDir);
    }

    List<String> listDocumentNames() {
        return rmapi.list();
    }

    List<DocumentMetadata> listReadDocuments() {
        return rmapi.list().stream()
                .map(metadataProvider::getMetadata)
                .filter(Objects::nonNull)
                .peek(this::logPages)
                // Current page starts counting at 0.
                .filter(e -> e.doc().currentPage() + 1 == e.pageCount())
                .toList();
    }

    void upload(List<Path> paths) {
        LOG.info("Uploading {} article(s) to Remarkable.", paths.size());
        int total = paths.size();
        for (int i = 0; i < total; i++) {
            Path path = paths.get(i);
            LOG.info("({}/{}) Uploading: '{}'.", i + 1, total, path.getFileName());
            upload(path);
        }
    }

    void delete(String name) {
        rmapi.delete(name);
    }

    private void logPages(DocumentMetadata meta) {
        LOG.debug(
                "{}: Current page: {}, page count: {}.",
                meta.doc().name(),
                meta.doc().currentPage() + 1,
                meta.pageCount() == 0 ? "unknown" : meta.pageCount());
    }

    private void upload(Path path) {
        rmapi.upload(path);
    }
}
