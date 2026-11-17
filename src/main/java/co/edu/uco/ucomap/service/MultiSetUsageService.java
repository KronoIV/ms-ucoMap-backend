package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.dto.MultiSetUsageDTO;
import co.edu.uco.ucomap.model.MultiSetQuota;
import co.edu.uco.ucomap.model.NavigationTrip;
import co.edu.uco.ucomap.repository.MultiSetQuotaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Estima cuántas llamadas del plan de MultiSet se han gastado sin llamar a MultiSet:
 * uso del portal anotado por el administrador + llamadas que la app informa en cada recorrido
 * ({@code vpsCalls}) + llamadas que hace el propio backend (tokens y mallas).
 * Al llegar al porcentaje configurado apaga {@code enableIndoorARNavigation}, igual que el panel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiSetUsageService {

    static final String AR_SETTING = "enableIndoorARNavigation";
    static final double WARNING_PERCENT = 80;
    private static final String CALLS_COLLECTION = "multiset_calls";
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    enum Action { NONE, DISABLE, RESTORE }

    record Estimate(Instant periodStart, Instant periodEnd, long used, long appCalls, long backendCalls) {}

    private final MultiSetQuotaRepository quotaRepository;
    private final MongoTemplate mongo;
    private final AppSettingService settings;

    /** Una petición del backend a MultiSet, en el contador del día. Nunca rompe la petición real. */
    public void recordBackendCall() {
        try {
            mongo.upsert(Query.query(Criteria.where("_id").is(LocalDate.now(ZONE).toString())),
                    new Update().inc("calls", 1), CALLS_COLLECTION);
        } catch (RuntimeException e) {
            log.warn("No se pudo contar la llamada a MultiSet: {}", e.getMessage());
        }
    }

    public MultiSetUsageDTO.Response usage() {
        Instant now = Instant.now();
        MultiSetQuota quota = check(now);
        return toResponse(quota, estimate(quota, now));
    }

    public MultiSetUsageDTO.Response update(MultiSetUsageDTO.Request request) {
        Instant now = Instant.now();
        MultiSetQuota quota = load();
        quota.setPlan(request.plan());
        quota.setLimit(request.limit());
        quota.setMonthly(request.monthly());
        quota.setResetDay(request.resetDay());
        quota.setAutoDisablePercent(request.autoDisablePercent());
        if (request.portalUsed() != null) {
            quota.setBaselineUsed(request.portalUsed());
            quota.setBaselineAt(now);
        }
        quota.setUpdatedAt(now);
        quotaRepository.save(quota);
        log.info("Plan de MultiSet actualizado — plan={} limite={} mensual={} umbral={}%",
                request.plan(), request.limit(), request.monthly(), request.autoDisablePercent());
        return usage();
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void scheduledCheck() {
        try {
            check(Instant.now());
        } catch (RuntimeException e) {
            log.warn("No se pudo revisar el cupo de MultiSet: {}", e.getMessage());
        }
    }

    synchronized MultiSetQuota check(Instant now) {
        MultiSetQuota quota = load();
        if (quota.getLimit() <= 0) return quota;
        Estimate estimate = estimate(quota, now);
        boolean arOn = settings.getValueAs(AR_SETTING, Boolean.class, true);
        double percent = percent(estimate.used(), quota.getLimit());

        switch (decide(percent, quota.getAutoDisablePercent(), arOn, quota.getAutoDisabledAt() != null)) {
            case DISABLE -> {
                settings.setValue(AR_SETTING, "false");
                quota.setAutoDisabledAt(now);
                quotaRepository.save(quota);
                log.warn("Guía con cámara apagada: MultiSet al {}% ({} de {} llamadas estimadas)",
                        Math.round(percent), estimate.used(), quota.getLimit());
            }
            case RESTORE -> {
                if (!arOn) settings.setValue(AR_SETTING, "true");
                quota.setAutoDisabledAt(null);
                quotaRepository.save(quota);
                log.info("Guía con cámara reactivada: el cupo de MultiSet volvió a {}%", Math.round(percent));
            }
            case NONE -> { }
        }
        return quota;
    }

    /**
     * Solo apaga una vez: si el administrador la vuelve a encender con el cupo agotado, se respeta.
     * Al bajar del umbral (nuevo mes, plan mayor o uso corregido) enciende lo que se apagó solo.
     */
    static Action decide(double percent, double threshold, boolean arOn, boolean autoDisabled) {
        if (percent >= threshold) return arOn && !autoDisabled ? Action.DISABLE : Action.NONE;
        return autoDisabled ? Action.RESTORE : Action.NONE;
    }

    /** Inicio del ciclo de facturación que contiene {@code now}; EPOCH si el cupo no se renueva. */
    static Instant periodStart(boolean monthly, int resetDay, ZonedDateTime now) {
        if (!monthly) return Instant.EPOCH;
        YearMonth month = YearMonth.from(now);
        ZonedDateTime start = resetAt(month, resetDay, now.getZone());
        if (start.isAfter(now)) start = resetAt(month.minusMonths(1), resetDay, now.getZone());
        return start.toInstant();
    }

    static Instant periodEnd(boolean monthly, int resetDay, Instant periodStart) {
        if (!monthly) return null;
        YearMonth month = YearMonth.from(periodStart.atZone(ZONE));
        return resetAt(month.plusMonths(1), resetDay, ZONE).toInstant();
    }

    private static ZonedDateTime resetAt(YearMonth month, int resetDay, ZoneId zone) {
        return month.atDay(Math.min(Math.max(resetDay, 1), month.lengthOfMonth())).atStartOfDay(zone);
    }

    static double percent(long used, long limit) {
        return limit <= 0 ? 0 : used * 100.0 / limit;
    }

    private Estimate estimate(MultiSetQuota quota, Instant now) {
        Instant start = periodStart(quota.isMonthly(), quota.getResetDay(), now.atZone(ZONE));
        Instant from = start;
        long base = 0;
        if (quota.getBaselineAt() != null && quota.getBaselineUsed() != null && !quota.getBaselineAt().isBefore(start)) {
            from = quota.getBaselineAt();
            base = quota.getBaselineUsed();
        }
        long app = appCallsSince(from);
        long backend = backendCallsSince(from);
        return new Estimate(start, periodEnd(quota.isMonthly(), quota.getResetDay(), start), base + app + backend, app, backend);
    }

    private long appCallsSince(Instant from) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("startedAt").gte(from).and("vpsCalls").gt(0)),
                Aggregation.group().sum("vpsCalls").as("total"));
        Document result = mongo.aggregate(agg, NavigationTrip.class, Document.class).getUniqueMappedResult();
        return total(result);
    }

    /** Por día: el día de {@code from} cuenta completo (sobreestima unas pocas llamadas, nunca subestima). */
    private long backendCallsSince(Instant from) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("_id").gte(from.atZone(ZONE).toLocalDate().toString())),
                Aggregation.group().sum("calls").as("total"));
        Document result = mongo.aggregate(agg, CALLS_COLLECTION, Document.class).getUniqueMappedResult();
        return total(result);
    }

    private static long total(Document result) {
        return result != null && result.get("total") instanceof Number n ? n.longValue() : 0;
    }

    private MultiSetQuota load() {
        return quotaRepository.findById(MultiSetQuota.ID).orElseGet(MultiSetQuota::defaults);
    }

    private MultiSetUsageDTO.Response toResponse(MultiSetQuota quota, Estimate estimate) {
        boolean arOn = settings.getValueAs(AR_SETTING, Boolean.class, true);
        double percent = percent(estimate.used(), quota.getLimit());
        String level;
        if (quota.getLimit() <= 0) level = "unconfigured";
        else if (quota.getAutoDisabledAt() != null && !arOn) level = "disabled";
        else if (percent >= quota.getAutoDisablePercent()) level = "critical";
        else if (percent >= WARNING_PERCENT) level = "warning";
        else level = "ok";
        return new MultiSetUsageDTO.Response(
                quota.getPlan(), quota.getLimit(), quota.isMonthly(), quota.getResetDay(), quota.getAutoDisablePercent(),
                quota.isMonthly() ? estimate.periodStart() : null, estimate.periodEnd(), estimate.used(), percent,
                quota.getBaselineUsed(), quota.getBaselineAt(), estimate.appCalls(), estimate.backendCalls(),
                arOn, quota.getAutoDisabledAt(), level);
    }
}
