package co.edu.uco.ucomap.service;

import co.edu.uco.ucomap.model.MultiSetQuota;
import co.edu.uco.ucomap.model.NavigationTrip;
import co.edu.uco.ucomap.repository.MultiSetQuotaRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static co.edu.uco.ucomap.service.MultiSetUsageService.Action.DISABLE;
import static co.edu.uco.ucomap.service.MultiSetUsageService.Action.NONE;
import static co.edu.uco.ucomap.service.MultiSetUsageService.Action.RESTORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiSetUsageServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Mock MultiSetQuotaRepository quotaRepository;
    @Mock MongoTemplate mongo;
    @Mock AppSettingService settings;
    @InjectMocks MultiSetUsageService service;

    @Test
    void disablesOnlyOnceAndRestoresBelowThreshold() {
        assertThat(MultiSetUsageService.decide(98.0, 98, true, false)).isEqualTo(DISABLE);
        assertThat(MultiSetUsageService.decide(97.9, 98, true, false)).isEqualTo(NONE);
        // El administrador la volvió a encender con el cupo agotado: no se insiste
        assertThat(MultiSetUsageService.decide(99, 98, true, true)).isEqualTo(NONE);
        // Ya estaba apagada a mano: no se marca como apagado automático
        assertThat(MultiSetUsageService.decide(99, 98, false, false)).isEqualTo(NONE);
        // Nuevo mes o plan mayor
        assertThat(MultiSetUsageService.decide(3, 98, false, true)).isEqualTo(RESTORE);
        assertThat(MultiSetUsageService.decide(3, 98, true, false)).isEqualTo(NONE);
    }

    @Test
    void billingPeriodStartsOnResetDay() {
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, BOGOTA);

        assertThat(MultiSetUsageService.periodStart(true, 20, now))
                .isEqualTo(ZonedDateTime.of(2026, 10, 20, 0, 0, 0, 0, BOGOTA).toInstant());
        assertThat(MultiSetUsageService.periodStart(true, 17, now))
                .isEqualTo(ZonedDateTime.of(2026, 11, 17, 0, 0, 0, 0, BOGOTA).toInstant());
        assertThat(MultiSetUsageService.periodStart(false, 1, now)).isEqualTo(Instant.EPOCH);

        ZonedDateTime march = ZonedDateTime.of(2026, 3, 1, 8, 0, 0, 0, BOGOTA);
        Instant start = MultiSetUsageService.periodStart(true, 28, march);
        assertThat(start).isEqualTo(ZonedDateTime.of(2026, 2, 28, 0, 0, 0, 0, BOGOTA).toInstant());
        assertThat(MultiSetUsageService.periodEnd(true, 28, start))
                .isEqualTo(ZonedDateTime.of(2026, 3, 28, 0, 0, 0, 0, BOGOTA).toInstant());
    }

    @Test
    void reachingThresholdTurnsArOffLikeThePanel() {
        MultiSetQuota quota = MultiSetQuota.defaults();
        quota.setLimit(1000);
        quota.setMonthly(false);
        quota.setBaselineUsed(900L);
        quota.setBaselineAt(Instant.now().minusSeconds(3600));
        when(quotaRepository.findById(MultiSetQuota.ID)).thenReturn(Optional.of(quota));
        givenCalls(70, 10);
        when(settings.getValueAs(MultiSetUsageService.AR_SETTING, Boolean.class, true)).thenReturn(true);

        service.check(Instant.now());

        verify(settings).setValue(MultiSetUsageService.AR_SETTING, "false");
        assertThat(quota.getAutoDisabledAt()).isNotNull();
        verify(quotaRepository).save(quota);
    }

    @Test
    void belowThresholdLeavesArAlone() {
        MultiSetQuota quota = MultiSetQuota.defaults();
        quota.setLimit(10_000);
        when(quotaRepository.findById(MultiSetQuota.ID)).thenReturn(Optional.of(quota));
        givenCalls(500, 20);
        when(settings.getValueAs(MultiSetUsageService.AR_SETTING, Boolean.class, true)).thenReturn(true);

        service.check(Instant.now());

        verify(settings, never()).setValue(anyString(), anyString());
        verify(quotaRepository, never()).save(any());
    }

    @Test
    void unconfiguredPlanNeverDisables() {
        when(quotaRepository.findById(MultiSetQuota.ID)).thenReturn(Optional.empty());

        service.check(Instant.now());

        verify(settings, never()).setValue(anyString(), anyString());
        verify(mongo, never()).aggregate(any(Aggregation.class), eq(NavigationTrip.class), eq(Document.class));
    }

    private void givenCalls(long app, long backend) {
        when(mongo.aggregate(any(Aggregation.class), eq(NavigationTrip.class), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(new Document("total", app)), new Document()));
        when(mongo.aggregate(any(Aggregation.class), eq("multiset_calls"), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(new Document("total", backend)), new Document()));
    }
}
