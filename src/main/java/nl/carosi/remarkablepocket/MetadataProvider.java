package nl.carosi.remarkablepocket;

import static javax.xml.xpath.XPathConstants.NODE;
import static nl.carosi.remarkablepocket.ArticleDownloader.POCKET_ID_SEPARATOR;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.jlarriba.jrmapi.Jrmapi;
import es.jlarriba.jrmapi.model.Document;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import nl.carosi.remarkablepocket.model.DocumentMetadata;
import nl.siegmann.epublib.epub.EpubReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

final class MetadataProvider {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataProvider.class);

    private final AtomicReference<Jrmapi> rmapi;
    private final EpubReader epubReader;
    private final ObjectMapper objectMapper;
    private final DocumentBuilder documentBuilder;
    private final XPath publisherXpath;
    private Path workDir;

    public MetadataProvider(
            AtomicReference<Jrmapi> rmapi,
            EpubReader epubReader,
            ObjectMapper objectMapper,
            DocumentBuilder documentBuilder) {
        this.rmapi = rmapi;
        this.epubReader = epubReader;
        this.objectMapper = objectMapper;
        this.documentBuilder = documentBuilder;
        this.publisherXpath = constructXpath();
    }

    private XPath constructXpath() {
        XPath opfXPath = XPathFactory.newInstance().newXPath();
        SimpleNamespaceContext nsCtx = new SimpleNamespaceContext();
        nsCtx.bindNamespaceUri("opf", "http://www.idpf.org/2007/opf");
        nsCtx.bindNamespaceUri("dc", "http://purl.org/dc/elements/1.1/");
        opfXPath.setNamespaceContext(nsCtx);
        return opfXPath;
    }

    @PostConstruct
    void createWorkDir() throws IOException {
        workDir = Files.createTempDirectory(null);
        LOG.debug("Created temporary working directory: {}.", workDir);
    }

    DocumentMetadata getMetadata(Document doc) {
        LOG.debug("Getting metadata for document: {}.", doc.getVissibleName());
        ZipFile zip;
        try {
            zip = new ZipFile(rmapi.get().fetchZip(doc, workDir.toString() + File.separator));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String fileHash = zip.entries().nextElement().getName().split("\\.")[0];

        try (InputStream lines = zip.getInputStream(zip.getEntry(fileHash + ".content"));
                InputStream epub = zip.getInputStream(zip.getEntry(fileHash + ".epub"))) {
            int pageCount = objectMapper.readValue(lines, Lines.class).pageCount();
            String pocketId = extractPocketId(epub);
            return new DocumentMetadata(doc, pageCount, pocketId);
        } catch (IOException | SAXException | XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    // When creating the book we stored the pocket ID in the publisher's metadata field.
    // We don't use EpubReader here because it will fail to parse the metadata if the CRC is
    // incorrect. This seems to happen when an epub containing illegal html elements is uploaded
    // to Remarkable. It can however still be read.
    private String extractPocketId(InputStream epub)
            throws IOException, SAXException, XPathExpressionException {
        org.w3c.dom.Document document = getOPFDocument(epub);
        String publisherExpr = "/package/metadata/publisher/text()";
        Node publisherNode = (Node) publisherXpath.evaluate(publisherExpr, document, NODE);
        return publisherNode.getNodeValue().split(POCKET_ID_SEPARATOR)[1];
    }

    private org.w3c.dom.Document getOPFDocument(InputStream epub) throws IOException, SAXException {
        try (ZipInputStream epubZIS = new ZipInputStream(epub)) {
            ZipEntry opf = epubZIS.getNextEntry();
            while (opf != null && !opf.getName().equals("OEBPS/content.opf")) {
                opf = epubZIS.getNextEntry();
            }
            if (opf == null) {
                throw new RuntimeException("Could not find content.opf");
            }
            return documentBuilder.parse(epubZIS);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private final record Lines(int pageCount) {}
}
