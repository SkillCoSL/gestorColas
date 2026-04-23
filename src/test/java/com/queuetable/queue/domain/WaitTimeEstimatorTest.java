package com.queuetable.queue.domain;

import com.queuetable.config.domain.RestaurantConfig;
import com.queuetable.table.domain.RestaurantTable;
import com.queuetable.table.domain.TableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WaitTimeEstimatorTest {

    private static final int AVG = 45;
    private static final int CLEANING = 5;
    private static final int BUFFER = 3;

    private WaitTimeEstimator estimator;
    private RestaurantConfig config;
    private Instant now;

    @BeforeEach
    void setUp() {
        estimator = new WaitTimeEstimator();
        config = newConfig(AVG, CLEANING, BUFFER);
        now = Instant.parse("2026-04-23T12:00:00Z");
    }

    @Test
    void returnsNullWhenNoTableFitsPartySize() {
        List<RestaurantTable> tables = List.of(
                newTable(2, TableStatus.FREE, null, null),
                newTable(2, TableStatus.FREE, null, null)
        );

        Integer estimate = estimator.estimate(4, 0, tables, Set.of(), config, now);

        assertThat(estimate).isNull();
    }

    @Test
    void returnsZeroWhenAnyCompatibleTableIsFree() {
        List<RestaurantTable> tables = List.of(
                newTable(2, TableStatus.OCCUPIED, now.minus(10, ChronoUnit.MINUTES), null),
                newTable(4, TableStatus.FREE, null, null)
        );

        Integer estimate = estimator.estimate(4, 0, tables, Set.of(), config, now);

        assertThat(estimate).isZero();
    }

    @Test
    void occupiedTableReturnsRemainingPlusCleaning() {
        // elapsed=10, avg=45 → remaining=35; +cleaning=40
        RestaurantTable t = newTable(4, TableStatus.OCCUPIED, now.minus(10, ChronoUnit.MINUTES), null);

        int remaining = estimator.remainingMinutes(t, config, now);

        assertThat(remaining).isEqualTo(40);
    }

    @Test
    void occupiedOverrunFallsBackToBufferPlusCleaning() {
        // elapsed=60, avg=45 → remaining negativo; usa buffer=3; +cleaning=5 → 8
        RestaurantTable t = newTable(4, TableStatus.OCCUPIED, now.minus(60, ChronoUnit.MINUTES), null);

        int remaining = estimator.remainingMinutes(t, config, now);

        assertThat(remaining).isEqualTo(BUFFER + CLEANING);
    }

    @Test
    void cleaningTableReturnsRemainingCleaning() {
        // elapsed=2, cleaning=5 → 3
        RestaurantTable t = newTable(4, TableStatus.CLEANING, null, now.minus(2, ChronoUnit.MINUTES));

        int remaining = estimator.remainingMinutes(t, config, now);

        assertThat(remaining).isEqualTo(3);
    }

    @Test
    void cleaningOverrunReturnsZero() {
        RestaurantTable t = newTable(4, TableStatus.CLEANING, null, now.minus(30, ChronoUnit.MINUTES));

        int remaining = estimator.remainingMinutes(t, config, now);

        assertThat(remaining).isZero();
    }

    @Test
    void occupiedWithNullTimestampUsesConservativeFallback() {
        RestaurantTable t = newTable(4, TableStatus.OCCUPIED, null, null);

        int remaining = estimator.remainingMinutes(t, config, now);

        assertThat(remaining).isEqualTo(AVG / 2 + CLEANING);
    }

    @Test
    void simulatesCompetitorsByConsumingEarliestSlots() {
        // Una mesa libre, 1 competidor por delante → ese competidor toma la mesa,
        // siguiente turno de la mesa = 0 + (avg+cleaning) = 50.
        List<RestaurantTable> tables = List.of(
                newTable(4, TableStatus.FREE, null, null)
        );

        Integer estimate = estimator.estimate(4, 1, tables, Set.of(), config, now);

        assertThat(estimate).isEqualTo(AVG + CLEANING);
    }

    @Test
    void simulationGreedilyPicksEarliestAcrossPool() {
        // Pool de 2 mesas: una libre, otra ocupada con 10 min de espera (35+5=40).
        // 2 competidores: el 1º toma la libre (0 → 50), el 2º toma la ocupada (40 → 90).
        // El siguiente disponible para mí = min(50, 90) = 50.
        List<RestaurantTable> tables = List.of(
                newTable(4, TableStatus.FREE, null, null),
                newTable(4, TableStatus.OCCUPIED, now.minus(10, ChronoUnit.MINUTES), null)
        );

        Integer estimate = estimator.estimate(4, 2, tables, Set.of(), config, now);

        assertThat(estimate).isEqualTo(AVG + CLEANING);
    }

    @Test
    void filteringByPartySizeIgnoresSmallerTables() {
        // 3 mesas pequeñas libres + 1 grande ocupada. Grupo de 6 solo cuenta la grande.
        List<RestaurantTable> tables = List.of(
                newTable(2, TableStatus.FREE, null, null),
                newTable(2, TableStatus.FREE, null, null),
                newTable(2, TableStatus.FREE, null, null),
                newTable(6, TableStatus.OCCUPIED, now.minus(20, ChronoUnit.MINUTES), null)
        );

        Integer estimate = estimator.estimate(6, 0, tables, Set.of(), config, now);

        // Mesa de 6: elapsed=20, remaining=25, +cleaning=30
        assertThat(estimate).isEqualTo(30);
    }

    @Test
    void protectedReservedTablesAreExcludedFromPool() {
        UUID reservedId = UUID.randomUUID();
        RestaurantTable reserved = newTable(reservedId, 4, TableStatus.FREE, null, null);
        RestaurantTable other = newTable(4, TableStatus.OCCUPIED, now.minus(10, ChronoUnit.MINUTES), null);

        Integer estimate = estimator.estimate(4, 0, List.of(reserved, other), Set.of(reservedId), config, now);

        assertThat(estimate).isEqualTo(40);
    }

    @Test
    void returnsNullWhenAllCompatibleTablesAreReserved() {
        UUID id = UUID.randomUUID();
        RestaurantTable table = newTable(id, 4, TableStatus.FREE, null, null);

        Integer estimate = estimator.estimate(4, 0, List.of(table), Set.of(id), config, now);

        assertThat(estimate).isNull();
    }

    @Test
    void invalidPartySizeReturnsNull() {
        Integer estimate = estimator.estimate(0, 0,
                List.of(newTable(4, TableStatus.FREE, null, null)),
                Set.of(), config, now);

        assertThat(estimate).isNull();
    }

    private RestaurantConfig newConfig(int avg, int cleaning, int buffer) {
        RestaurantConfig c = newInstance(RestaurantConfig.class);
        c.setConfirmationTimeoutMinutes(5);
        c.setNoshowGraceMinutes(15);
        c.setAvgTableDurationMinutes(avg);
        c.setReservationProtectionWindowMinutes(30);
        c.setCleaningDurationMinutes(cleaning);
        c.setWaitBufferMinutes(buffer);
        return c;
    }

    private RestaurantTable newTable(int capacity, TableStatus status, Instant occupiedAt, Instant cleaningStartedAt) {
        return newTable(UUID.randomUUID(), capacity, status, occupiedAt, cleaningStartedAt);
    }

    private RestaurantTable newTable(UUID id, int capacity, TableStatus status,
                                     Instant occupiedAt, Instant cleaningStartedAt) {
        RestaurantTable t = new RestaurantTable(UUID.randomUUID(), "T", capacity);
        setField(t, "id", id);
        t.setStatus(status);
        t.setOccupiedAt(occupiedAt);
        t.setCleaningStartedAt(cleaningStartedAt);
        return t;
    }

    private static <T> T newInstance(Class<T> clazz) {
        try {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate " + clazz, e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set field " + fieldName, e);
        }
    }
}
