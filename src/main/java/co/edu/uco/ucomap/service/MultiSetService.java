package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.dto.MapMeshDTO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Proxy hacia la API de MultiSet: obtiene las URLs firmadas de las mallas 3D del map set
 * sin exponer clientId/clientSecret al navegador.
 */
@Slf4j
@Service
public class MultiSetService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final RestClient client;
    private final String clientId;
    private final String clientSecret;
    private final String mapSetCode;

    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public MultiSetService(
            @Value("${app.multiset.api-endpoint}") String apiEndpoint,
            @Value("${app.multiset.client-id}") String clientId,
            @Value("${app.multiset.client-secret}") String clientSecret,
            @Value("${app.multiset.map-set-code}") String mapSetCode) {
        this.client = RestClient.builder().baseUrl(apiEndpoint).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.mapSetCode = mapSetCode;
    }

    public List<MapMeshDTO> getMapMeshes(boolean textured) {
        if (clientId.isBlank() || clientSecret.isBlank() || mapSetCode.isBlank()) {
            log.error("MultiSet no configurado (MULTISET_CLIENT_ID / MULTISET_CLIENT_SECRET / MULTISET_MAP_SET_CODE)");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        }
        try {
            String token = getToken();
            JsonNode mapSet = client.get()
                    .uri("vps/map-set/{code}", mapSetCode)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);

            String meshType = textured ? "texturedMesh" : "rawMesh";
            List<MapMeshDTO> result = new ArrayList<>();
            for (JsonNode entry : mapSet.path("mapSet").path("mapSetData")) {
                JsonNode map = entry.path("map");
                String link = map.path("mapMesh").path(meshType).path("meshLink").asText("");
                if (link.isEmpty()) continue;

                JsonNode file = client.get()
                        .uri("file?key={key}", link)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .body(JsonNode.class);

                JsonNode pos = entry.path("relativePose").path("position");
                JsonNode rot = entry.path("relativePose").path("rotation");
                result.add(new MapMeshDTO(
                        map.path("mapName").asText(map.path("mapCode").asText("")),
                        file.path("url").asText(),
                        new MapMeshDTO.Position(pos.path("x").asDouble(), pos.path("y").asDouble(), pos.path("z").asDouble()),
                        new MapMeshDTO.Rotation(rot.path("qx").asDouble(), rot.path("qy").asDouble(),
                                rot.path("qz").asDouble(), rot.path("qw").asDouble(1))
                ));
            }
            log.info("Mallas MultiSet obtenidas — mapSet={} mapas={} tipo={}", mapSetCode, result.size(), meshType);
            return result;
        } catch (RestClientException e) {
            log.error("Error consultando MultiSet: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        }
    }

    private synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        String basic = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        JsonNode body = client.post()
                .uri("m2m/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .retrieve()
                .body(JsonNode.class);
        cachedToken = body.path("token").asText();
        tokenExpiresAt = Instant.now().plus(TOKEN_TTL);
        log.debug("Token MultiSet renovado");
        return cachedToken;
    }
}
