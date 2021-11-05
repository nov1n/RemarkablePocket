package nl.carosi.remarkablepocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.jlarriba.jrmapi.Jrmapi;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;

// Hacky, but unfortunately jrmapi does not refresh bearer tokens.
final class TokenRefresher {
    private static final Logger LOG = LoggerFactory.getLogger(TokenRefresher.class);

    private final ObjectMapper objectMapper;
    private final TaskScheduler scheduler;
    private final AtomicReference<Jrmapi> rmapi;
    private final String deviceToken;
    private final Base64.Decoder decoder = Base64.getDecoder();

    public TokenRefresher(
            ObjectMapper objectMapper,
            TaskScheduler scheduler,
            AtomicReference<Jrmapi> rmapi,
            @Value("${rm.device-token}") String deviceToken) {
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
        this.rmapi = rmapi;
        this.deviceToken = deviceToken;
    }

    @PostConstruct
    void scheduleRefresh() {
        try {
            scheduleRefreshImpl();
        } catch (NoSuchFieldException | IllegalAccessException | JsonProcessingException e) {
            throw new RuntimeException("Could not refresh jrmapi token.");
        }
    }

    private void scheduleRefreshImpl()
            throws NoSuchFieldException, IllegalAccessException, JsonProcessingException {
        Field tokenField = Jrmapi.class.getDeclaredField("userToken");
        tokenField.setAccessible(true);
        String token = (String) tokenField.get(rmapi.get());
        int exp = getExpiration(token);
        Instant nextRefresh = Instant.ofEpochSecond(exp - 60 * 10); // 10 min margin.
        scheduler.schedule(this::refresh, nextRefresh);
        LOG.debug("Next token refresh scheduled at: {}.", nextRefresh);
    }

    private void refresh() {
        rmapi.set(new Jrmapi(deviceToken));
        LOG.debug("Refreshed jrmapi token.");
        scheduleRefresh();
    }

    private int getExpiration(String jwt) throws JsonProcessingException {
        String[] chunks = jwt.split("\\.");
        String payload = new String(decoder.decode(chunks[1]));
        JWT exp = objectMapper.readValue(payload, JWT.class);
        return exp.exp();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record JWT(int exp) {}
}
