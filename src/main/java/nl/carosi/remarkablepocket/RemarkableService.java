package nl.carosi.remarkablepocket;

import static com.google.common.base.Preconditions.checkArgument;

import es.jlarriba.jrmapi.Jrmapi;
import es.jlarriba.jrmapi.model.Document;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import nl.carosi.remarkablepocket.model.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

final class RemarkableService {
    private static final Logger LOG = LoggerFactory.getLogger(RemarkableService.class);
    private final Jrmapi rmapi;
    private final MetadataProvider metadataProvider;
    private final String rmStorageDir;
    private String rmStorageDirId = "";

    public RemarkableService(
            Jrmapi rmapi,
            MetadataProvider metadataProvider,
            @Value("${rm.storage-dir}") String rmStorageDir) {
        if(!rmStorageDir.endsWith("/")) {
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
    void findParentId() {
        String[] dirs = rmStorageDir.substring(1, rmStorageDir.length() - 1).split("/");
        for (final String dir : dirs) {
            rmStorageDirId =
                    rmapi.listDocs().stream()
                            .filter(e -> e.getParent().equals(rmStorageDirId))
                            .filter(e -> e.getVissibleName().equals(dir))
                            .findFirst()
                            .map(Document::getID)
                            .orElseGet(() -> rmapi.createDir(dir, rmStorageDirId));
        }
    }

    List<String> listDocumentNames() {
        return docsStream().map(Document::getVissibleName).toList();
    }

    List<DocumentMetadata> listReadDocuments() {
        return docsStream()
                .map(metadataProvider::getMetadata)
                .peek(this::logPages)
                // Current page starts counting at 0.
                .filter(e -> e.doc().getCurrentPage() + 1 == e.pageCount())
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

    void delete(Document doc) {
        rmapi.deleteEntry(doc);
    }

    private void logPages(DocumentMetadata meta) {
        LOG.debug(
                "{}: Current page: {}, page count: {}",
                meta.doc().getVissibleName(),
                meta.doc().getCurrentPage() + 1,
                meta.pageCount());
    }

    private Stream<Document> docsStream() {
        return rmapi.listDocs().stream().filter(e -> e.getParent().equals(rmStorageDirId));
    }

    private void upload(Path path) {
        rmapi.uploadDoc(path.toFile(), rmStorageDirId);
    }
}
