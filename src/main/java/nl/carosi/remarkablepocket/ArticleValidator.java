package nl.carosi.remarkablepocket;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import nl.carosi.remarkablepocket.model.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ArticleValidator {
    private static final Logger LOG = LoggerFactory.getLogger(ArticleValidator.class);
    private final HashSet<Article> invalidArticles = new HashSet<>();
    private final MetadataProvider metadataProvider;
    private final RemarkableApi remarkableApi;

    public ArticleValidator(MetadataProvider metadataProvider, RemarkableApi remarkableApi) {
        this.metadataProvider = metadataProvider;
        this.remarkableApi = remarkableApi;
    }

    public Optional<Path> validate(Optional<Path> path, Article article) {
        if (path.isEmpty()) {
            invalidate(article);
            return path;
        }

        remarkableApi.upload(path.get());
        try {
            metadataProvider.getMetadata(article.title());
            remarkableApi.delete(article.title());
            LOG.debug("Article is valid: {}", article);
            return path;
        } catch (RuntimeException e) {
            invalidate(article);
            return Optional.empty();
        }
    }

    private void invalidate(Article article) {
        LOG.debug("Article is invalid: {}", article);
        invalidArticles.add(article);
    }

    public boolean isValid(Article article) {
        return !invalidArticles.contains(article);
    }
}
