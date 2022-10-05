package nl.carosi.remarkablepocket;

import static javax.xml.xpath.XPathConstants.NODE;
import static nl.carosi.remarkablepocket.ArticleDownloader.POCKET_ID_SEPARATOR;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import nl.carosi.remarkablepocket.model.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

final class MetadataProvider {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataProvider.class);

    private final RemarkableApi rmapi;
    private final ObjectMapper objectMapper;
    private final DocumentBuilder documentBuilder;
    private final ArticleValidator validator;
    private final XPath publisherXpath;

    public MetadataProvider(
            RemarkableApi rmapi,
            ObjectMapper objectMapper,
            DocumentBuilder documentBuilder,
            ArticleValidator validator) {
        this.rmapi = rmapi;
        this.objectMapper = objectMapper;
        this.documentBuilder = documentBuilder;
        this.validator = validator;
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

    DocumentMetadata getMetadata(String name) {
        LOG.debug("Getting metadata for document: {}.", name);
        try (ZipFile zip = new ZipFile(rmapi.download(name).toFile())) {
            String fileHash = zip.entries().nextElement().getName().split("\\.")[0];
            if (zip.getEntry(fileHash + ".pdf") != null) {
                // In this case the article was converted to pdf because Remarkable
                // couldn't read the epub file. This means we lost the metadata,
                // so we delete it.
                throw new RuntimeException("Article was converted to PDF");
            }
            try (InputStream linesStream = zip.getInputStream(zip.getEntry(fileHash + ".content"));
                    InputStream epubStream = zip.getInputStream(zip.getEntry(fileHash + ".epub"))) {
                int pageCount = objectMapper.readValue(linesStream, Lines.class).pageCount();
                String pocketId = extractPocketId(epubStream);
                return new DocumentMetadata(rmapi.info(name), pageCount, pocketId);
            }
        } catch (Exception e) {
            LOG.info(
                    "Article '{}' is corrupted. Deleting file and retrieving new article in next sync.",
                    name);
            LOG.debug("Article invalid because", e);
            rmapi.delete(name);
            validator.invalidate(name);
            return null;
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
    private record Lines(int pageCount) {}
}
