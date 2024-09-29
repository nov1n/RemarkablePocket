package nl.carosi.remarkablepocket;

import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ArticleValidator {
    private static final Logger LOG = LoggerFactory.getLogger(ArticleValidator.class);
    private final HashSet<String> invalidArticles = new HashSet<>();

    void invalidate(String articleName) {
        invalidArticles.add(articleName);
        LOG.debug("Invalid articles: {}", invalidArticles);
    }

    boolean isValid(String articleName) {
        return !articleName.isEmpty() && !invalidArticles.contains(articleName);
    }
}
