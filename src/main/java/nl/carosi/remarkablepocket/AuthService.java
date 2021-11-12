package nl.carosi.remarkablepocket;

import static java.time.temporal.ChronoUnit.MINUTES;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.jlarriba.jrmapi.Jrmapi;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

// Hacky, but unfortunately jrmapi does not refresh bearer tokens.
final class AuthService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
    private static final Base64.Decoder B64_DECODER = Base64.getDecoder();
    private static final DateTimeFormatter TEMPORAL_FORMATTER =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    .withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper;
    private final AtomicReference<Jrmapi> rmapi;
    private final String deviceToken;
    private Instant nextRefresh;

    public AuthService(
            ObjectMapper objectMapper,
            AtomicReference<Jrmapi> rmapi,
            @Value("${rm.device-token}") String deviceToken) {
        this.objectMapper = objectMapper;
        this.rmapi = rmapi;
        this.deviceToken = deviceToken;
    }

    void ensureValid() {
        if (nextRefresh != null && Instant.now().isBefore(nextRefresh)) {
            return;
        }

        try {
            refreshToken();
        } catch (NoSuchFieldException | IllegalAccessException | JsonProcessingException e) {
            LOG.debug("Exception occurred while refreshing auth token:", e);
            throw new RuntimeException("Could not refresh auth token.");
        }
    }

    private void refreshToken()
            throws NoSuchFieldException, IllegalAccessException, JsonProcessingException {
        Field tokenField = Jrmapi.class.getDeclaredField("userToken");
        tokenField.setAccessible(true);
        String token = (String) tokenField.get(rmapi.get());
        Instant exp = getExpiration(token);
        nextRefresh = exp.minus(30, MINUTES);
        rmapi.set(new Jrmapi(deviceToken));
        LOG.debug(
                "Refreshed jrmapi token. Valid until: {}, refreshing at {}.",
                TEMPORAL_FORMATTER.format(exp),
                TEMPORAL_FORMATTER.format(nextRefresh));
    }

    private Instant getExpiration(String token) throws JsonProcessingException {
        String[] chunks = token.split("\\.");
        String payload = new String(B64_DECODER.decode(chunks[1]));
        return objectMapper.readValue(payload, JWT.class).exp();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record JWT(Instant exp) {}
}
