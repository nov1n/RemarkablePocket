package nl.carosi.remarkablepocket;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public class ArticleValidator {
    private static final Logger LOG = LoggerFactory.getLogger(ArticleValidator.class);
    private static final String DB_NAME = "validator.db";
    private final Path dbPath;
    private Connection conn;

    public ArticleValidator(@Value("${config.dir}") Path configDir) {
        this.dbPath = configDir.resolve(DB_NAME);
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            Files.createDirectories(dbPath.getParent());
            String url = "jdbc:sqlite:" + dbPath;
            conn = DriverManager.getConnection(url);

            String sql = "CREATE TABLE IF NOT EXISTS invalid_articles (" +
                    "article_name TEXT PRIMARY KEY)";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
            LOG.debug("Database initialized at: {}", dbPath);
        } catch (SQLException | IOException e) {
            LOG.error("Error initializing database", e);
        }
    }

    public void invalidate(String articleName) {
        String sql = "INSERT OR IGNORE INTO invalid_articles(article_name) VALUES(?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, articleName);
            pstmt.executeUpdate();
            LOG.debug("Invalidated article: {}", articleName);
        } catch (SQLException e) {
            LOG.error("Error invalidating article", e);
        }
    }

    public boolean isValid(String articleName) {
        if (articleName.isEmpty()) {
            return false;
        }

        String sql = "SELECT COUNT(*) FROM invalid_articles WHERE article_name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, articleName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            LOG.error("Error checking article validity", e);
        }
        return false;
    }

    @PreDestroy
    public void close() {
        try {
            if (conn != null) {
                conn.close();
                conn = null;
                LOG.info("Database connection closed");
            }
        } catch (SQLException e) {
            LOG.error("Error closing database connection", e);
        }
    }

    public void logInvalidArticles() {
        String sql = "SELECT article_name FROM invalid_articles";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            StringBuilder sb = new StringBuilder("Invalid articles:\n");
            while (rs.next()) {
                sb.append("- ")
                        .append(rs.getString("article_name"))
                        .append("\n");
            }
            LOG.debug(sb.toString());
        } catch (SQLException e) {
            LOG.error("Error logging invalid articles", e);
        }
    }
}
