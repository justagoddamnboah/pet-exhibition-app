package edu.rutmiit.enterprise.exhibition;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class SchemaMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.6-alpine");

    @Test
    void laterMigrationBackfillsRowsCreatedByTheEarlierSchema() throws Exception {
        Flyway upToVersionTwo = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("2")
                .load();

        assertThat(upToVersionTwo.migrate().migrationsExecuted).isEqualTo(2);
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            UUID ownerId = UUID.randomUUID();
            try (var owner = connection.prepareStatement(
                    "insert into owners(id, name, age) values (?, ?, ?)")) {
                owner.setObject(1, ownerId);
                owner.setString(2, "Кирилл");
                owner.setInt(3, 21);
                owner.executeUpdate();
            }
            try (var pet = connection.prepareStatement(
                    "insert into pets(id, pet_name, age_months, sex, species, breed, owner_id) " +
                            "values (?, ?, ?, ?, ?, ?, ?)")) {
                pet.setObject(1, UUID.randomUUID());
                pet.setString(2, "Эрни");
                pet.setInt(3, 7);
                pet.setString(4, "MALE");
                pet.setString(5, "DOG");
                pet.setString(6, "Пудель");
                pet.setObject(7, ownerId);
                pet.executeUpdate();
            }
        }
    }
}