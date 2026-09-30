package co.edu.uco.ucomap.dto;

import org.springframework.data.mongodb.core.query.Criteria;

import java.time.Instant;

/** Filtros de recorridos del panel: rango de inicio y edificio (todos opcionales). */
public record TripFilter(Instant from, Instant to, String building) {

    public Criteria toCriteria() {
        Criteria criteria = new Criteria();
        if (from != null || to != null) {
            Criteria startedAt = Criteria.where("startedAt");
            if (from != null) startedAt = startedAt.gte(from);
            if (to != null) startedAt = startedAt.lte(to);
            criteria = startedAt;
        }
        if (building != null && !building.isBlank()) {
            criteria = criteria.and("building").is(building);
        }
        return criteria;
    }
}
