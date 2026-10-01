package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.common.error.ErrorCode;
import co.edu.uco.ucomap.dto.MapMeshDTO;
import co.edu.uco.ucomap.dto.VpsTokenDTO;
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

    // MultiSet emite tokens de 30 min; margen para no entregar uno a punto de vencer
    private static final Duration TOKEN_TTL = Duration.ofMinutes(25);
    private static final Duration VPS_TOKEN_MIN_REMAINING = Duration.ofMinutes(5);

    private final RestClient client;
    private final String clientId;
    private final String clientSecret;
    private final String mapSetCode;
    private final TokenCache adminTokens;
    private final TokenCache vpsTokens;

    public MultiSetService(
            @Value("${app.multiset.api-endpoint}") String apiEndpoint,
            @Value("${app.multiset.client-id}") String clientId,
            @Value("${app.multiset.client-secret}") String clientSecret,
            @Value("${app.multiset.map-set-code}") String mapSetCode,
            @Value("${app.multiset.query-client-id:}") String queryClientId,
            @Value("${app.multiset.query-client-secret:}") String queryClientSecret) {
        this.client = RestClient.builder().baseUrl(apiEndpoint).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.mapSetCode = mapSetCode;
        this.adminTokens = new TokenCache(clientId, clientSecret);
        if (queryClientId.isBlank() || queryClientSecret.isBlank()) {
            log.warn("MULTISET_QUERY_CLIENT_ID/SECRET no configurados: los tokens VPS públicos usan la credencial principal. "
                    + "Cree una credencial con alcance solo Query en el portal de MultiSet.");
            this.vpsTokens = adminTokens;
        } else {
            this.vpsTokens = new TokenCache(queryClientId, queryClientSecret);
        }
    }

    /** Token de corta duración para que la app localice contra el map set sin conocer el secreto. */
    public VpsTokenDTO getVpsToken() {
        if (vpsTokens.clientId.isBlank() || vpsTokens.clientSecret.isBlank() || mapSetCode.isBlank()) {
            log.error("MultiSet no configurado para tokens VPS");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        }
        try {
            TokenCache.Entry entry = vpsTokens.get(VPS_TOKEN_MIN_REMAINING);
            return new VpsTokenDTO(entry.token(), entry.expiresAt(), mapSetCode);
        } catch (RestClientException e) {
            log.error("Error obteniendo token VPS de MultiSet: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        }
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

    private String getToken() {
        return adminTokens.get(Duration.ZERO).token();
    }

    private final class TokenCache {
        record Entry(String token, Instant expiresAt) {}

        private final String clientId;
        private final String clientSecret;
        private Entry current;

        TokenCache(String clientId, String clientSecret) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        synchronized Entry get(Duration minRemaining) {
            if (current != null && Instant.now().plus(minRemaining).isBefore(current.expiresAt())) {
                return current;
            }
            String basic = Base64.getEncoder()
                    .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            JsonNode body = client.post()
                    .uri("m2m/token")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .retrieve()
                    .body(JsonNode.class);
            String token = body == null ? "" : body.path("token").asText("");
            if (token.isEmpty()) throw new RestClientException("MultiSet no devolvió token");
            current = new Entry(token, Instant.now().plus(TOKEN_TTL));
            log.debug("Token MultiSet renovado");
            return current;
        }
    }
}
