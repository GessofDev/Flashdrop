package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies V11-V13 merchant schema creation and ownership backfill against real PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
class MerchantMigrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migrateV11ToV13_backfillsOwnedStoresAndLeavesOrphansNull() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("10")
                .load()
                .migrate();

        // Two distinct owners (even with homonymous store names) + one ownerless store
        jdbc.update("insert into public.restaurant (id, user_id, name, address) values (101, 1001, 'Pizza Centro', 'Av. 1')");
        jdbc.update("insert into public.restaurant (id, user_id, name, address) values (102, 1002, 'Pizza Centro', 'Av. 2')");
        jdbc.update("insert into public.restaurant (id, user_id, name, address) values (103, null, 'Sin Dueño', 'Av. 3')");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Integer distinctOwners = jdbc.queryForObject(
                "select count(distinct user_id) from public.restaurant where user_id is not null", Integer.class);
        Integer merchantCount = jdbc.queryForObject(
                "select count(*) from public.merchants", Integer.class);
        Integer memberCount = jdbc.queryForObject(
                "select count(*) from public.merchant_members", Integer.class);
        Integer ownedWithoutMerchant = jdbc.queryForObject(
                "select count(*) from public.restaurant where user_id is not null and merchant_id is null",
                Integer.class);
        Integer orphanWithMerchant = jdbc.queryForObject(
                "select count(*) from public.restaurant where user_id is null and merchant_id is not null",
                Integer.class);

        assertThat(distinctOwners).isEqualTo(2);
        assertThat(merchantCount).isEqualTo(distinctOwners);
        assertThat(memberCount).isEqualTo(distinctOwners);
        assertThat(ownedWithoutMerchant).isZero();
        assertThat(orphanWithMerchant).isZero();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select r.id, r.user_id, r.merchant_id, mm.role " +
                        "from public.restaurant r " +
                        "join public.merchant_members mm on mm.merchant_id = r.merchant_id and mm.user_id = r.user_id " +
                        "order by r.id");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("merchant_id")).isNotEqualTo(rows.get(1).get("merchant_id"));
        assertThat(rows.get(0).get("role")).isEqualTo("DUEÑO");
        assertThat(rows.get(1).get("role")).isEqualTo("DUEÑO");
    }
}
