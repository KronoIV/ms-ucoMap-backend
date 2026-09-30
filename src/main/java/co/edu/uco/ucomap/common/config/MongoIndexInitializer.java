package co.edu.uco.ucomap.common.config;

import co.edu.uco.ucomap.model.DeviceSession;
import co.edu.uco.ucomap.model.GraphEdge;
import co.edu.uco.ucomap.model.NavigationTrip;
import co.edu.uco.ucomap.model.PasswordResetToken;
import co.edu.uco.ucomap.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Crea los índices que usan las consultas frecuentes (sin ellos cada búsqueda recorre la colección completa).
 * Es idempotente: si el índice ya existe, MongoDB no hace nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mongo.ensure-indexes", havingValue = "true", matchIfMissing = true)
public class MongoIndexInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensure(DeviceSession.class, new Index().on("deviceId", Sort.Direction.ASC).unique());
        ensure(DeviceSession.class, new Index().on("lastSeen", Sort.Direction.DESC));

        ensure(NavigationTrip.class, new Index().on("tripId", Sort.Direction.ASC).unique());
        ensure(NavigationTrip.class, new Index().on("status", Sort.Direction.ASC));
        ensure(NavigationTrip.class, new Index().on("startedAt", Sort.Direction.DESC));
        ensure(NavigationTrip.class, new Index().on("building", Sort.Direction.ASC).on("startedAt", Sort.Direction.DESC));

        ensure(User.class, new Index().on("email", Sort.Direction.ASC).unique());

        ensure(PasswordResetToken.class, new Index().on("tokenHash", Sort.Direction.ASC).unique());
        ensure(PasswordResetToken.class, new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));

        ensure(GraphEdge.class, new Index().on("nodeA", Sort.Direction.ASC));
        ensure(GraphEdge.class, new Index().on("nodeB", Sort.Direction.ASC));
    }

    private void ensure(Class<?> entity, Index index) {
        String collection = mongoTemplate.getCollectionName(entity);
        try {
            mongoTemplate.indexOps(entity).createIndex(index);
        } catch (RuntimeException e) {
            // Un índice único falla si ya hay duplicados; la app sigue funcionando sin él
            log.warn("No se pudo crear el índice {} en {}: {}", index.getIndexKeys().toJson(), collection, e.getMessage());
        }
    }
}
