// package edu.rutmiit.enterprise.exhibition;

// import org.flywaydb.core.Flyway;
// import org.junit.jupiter.api.Test;
// import org.testcontainers.junit.jupiter.Container;
// import org.testcontainers.junit.jupiter.Testcontainers;
// import org.testcontainers.postgresql.PostgreSQLContainer;

// import java.sql.DriverManager;
// import java.util.UUID;

// import static org.assertj.core.api.Assertions.assertThat;

// @Testcontainers(disabledWithoutDocker = true)
// class SchemaMigrationTest {
//     @Container
//     static final PostgreSQLContainer POSTGRES =
//             new PostgreSQLContainer("postgres:17.6-alpine");

//     @Test
//     void laterMigrationBackfillsRowsCreatedByTheEarlierSchema() throws Exception {
//         Flyway upToVersionTwo = Flyway.configure()
//                 .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
//                 .locations("classpath:db/migration")
//                 .target("2")
//                 .load();

//         assertThat(upToVersionTwo.migrate().migrationsExecuted).isEqualTo(2);
//         try (var connection = DriverManager.getConnection(
//                 POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
//             UUID authorId = UUID.randomUUID();
//             try (var author = connection.prepareStatement(
//                     "insert into authors(id, name) values (?, ?)")) {
//                 author.setObject(1, authorId);
//                 author.setString(2, "Автор до миграции V3");
//                 author.executeUpdate();
//             }
//             try (var book = connection.prepareStatement(
//                     "insert into books(id, title, isbn, author_id, status, publication_year) " +
//                             "values (?, ?, ?, ?, ?, ?)")) {
//                 book.setObject(1, UUID.randomUUID());
//                 book.setString(2, "Книга до миграции V3");
//                 book.setString(3, "9780201633610");
//                 book.setObject(4, authorId);
//                 book.setString(5, "AVAILABLE");
//                 book.setInt(6, 1994);
//                 book.executeUpdate();
//             }
//         }

//         Flyway upToVersionThree = Flyway.configure()
//                 .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
//                 .locations("classpath:db/migration")
//                 .target("3")
//                 .load();
//         assertThat(upToVersionThree.migrate().migrationsExecuted).isOne();

//         try (var connection = DriverManager.getConnection(
//                 POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
//              var statement = connection.prepareStatement(
//                      "select publication_year, created_at from books where isbn = ?")) {
//             statement.setString(1, "9780201633610");
//             try (var result = statement.executeQuery()) {
//                 assertThat(result.next()).isTrue();
//                 assertThat(result.getInt("publication_year")).isEqualTo(1994);
//                 assertThat(result.getObject("created_at")).isNotNull();
//             }
//         }
//     }
// }