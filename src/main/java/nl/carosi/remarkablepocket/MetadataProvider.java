package nl.carosi.remarkablepocket;

import static nl.carosi.remarkablepocket.ArticleDownloader.POCKET_ID_SEPARATOR;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.jlarriba.jrmapi.Jrmapi;
import es.jlarriba.jrmapi.model.Document;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.PostConstruct;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;
import nl.carosi.remarkablepocket.model.DocumentMetadata;
import nl.siegmann.epublib.epub.EpubReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MetadataProvider {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataProvider.class);

    private final AtomicReference<Jrmapi> rmapi;
    private final EpubReader epubReader;
    private final ObjectMapper objectMapper;
    private Path workDir;

    public MetadataProvider(
            AtomicReference<Jrmapi> rmapi, EpubReader epubReader, ObjectMapper objectMapper) {
        this.rmapi = rmapi;
        this.epubReader = epubReader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void createWorkDir() throws IOException {
        workDir = Files.createTempDirectory(null);
        LOG.debug("Created temporary working directory: {}.", workDir);
    }

    DocumentMetadata getMetadata(Document doc) {
        LOG.debug("Getting metadata for document: {}.", doc.getVissibleName());
        ZipFile zip;
        String fileHash;
        try {
            zip = new ZipFile(rmapi.get().fetchZip(doc, workDir.toString() + File.separator));
            fileHash = getFileHash(zip);
        } catch (ZipException e) {
            throw new RuntimeException(e);
        }

        try (ZipInputStream lines = zip.getInputStream(zip.getFileHeader(fileHash + ".content"));
                ZipInputStream epub = zip.getInputStream(zip.getFileHeader(fileHash + ".epub"))) {
            int pageCount = objectMapper.readValue(lines, Lines.class).pageCount();
            String pocketId = extractPocketId(epub).split(POCKET_ID_SEPARATOR)[1];
            return new DocumentMetadata(doc, pageCount, pocketId);
        } catch (ZipException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getFileHash(ZipFile zip) throws ZipException {
        @SuppressWarnings("unchecked")
        List<FileHeader> fileHeaders =
                zip.getFileHeaders().stream().map(FileHeader.class::cast).toList();
        return fileHeaders.stream()
                .findFirst()
                .map(e -> e.getFileName().split("\\.")[0])
                .orElseThrow(() -> new RuntimeException("Zip file is empty"));
    }

    private String extractPocketId(ZipInputStream epub) throws IOException {
        // When creating the book the pocket ID was stored in the publisher's metadata field.
        return epubReader.readEpub(epub).getMetadata().getPublishers().get(0);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private final record Lines(int pageCount) {}
}
