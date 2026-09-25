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
        
        UUID ownerId = insertOwnerRowBeforeVersionTwo();
        UUID petId = insertPetRowBeforeVersionTwo(ownerId);

        Flyway upToVersionThree = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("3")
                .load();
        assertThat(upToVersionThree.migrate().migrationsExecuted).isOne();

        assertRowWasPreservedAndBackfilled(petId);
    }
    
    private UUID insertOwnerRowBeforeVersionTwo() throws Exception {
        UUID id = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var owner = connection.prepareStatement(
                    "insert into owners(id, name, age) values (?, ?, ?)")) {
                owner.setObject(1, id);
                owner.setString(2, "Кирилл");
                owner.setInt(3, 21);
                owner.executeUpdate();
            }
        }
        return id;
    }

    private UUID insertPetRowBeforeVersionTwo(UUID ownerId) throws Exception {
        UUID id = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var pet = connection.prepareStatement("insert into pets(id, pet_name, age_months, sex, species, breed, owner_id) " + "values (?, ?, ?, ?, ?, NULL, ?)")) {
                pet.setObject(1, id);
                pet.setString(2, "Эрни");
                pet.setInt(3, 7);
                pet.setString(4, "MALE");
                pet.setString(5, "DOG");
                pet.setObject(6, ownerId);
                pet.executeUpdate();
            }
        }
        return id;
    }

    private void assertRowWasPreservedAndBackfilled(UUID id) throws Exception {
        try (var conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            var ps = conn.prepareStatement(
                    "SELECT pet_name, breed FROM pets WHERE id = ?")) {
            ps.setObject(1, id);
            try (var rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("pet_name")).isEqualTo("Эрни");
                assertThat(rs.getString("breed")).isNotNull();
                assertThat(rs.getString("breed")).isEqualTo("unknown");
            }
        }
    }
}