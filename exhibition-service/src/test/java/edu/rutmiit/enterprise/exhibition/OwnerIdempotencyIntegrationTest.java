package edu.rutmiit.enterprise.exhibition;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import edu.rutmiit.enterprise.exhibition.api.ApiException;
import edu.rutmiit.enterprise.exhibition.api.CreateOwnerRequest;
import edu.rutmiit.enterprise.exhibition.service.IdempotentOwnerService;

@SpringBootTest(properties = "management.server.port=0")
@ActiveProfiles("session-auth")
@Testcontainers(disabledWithoutDocker = true)
public class OwnerIdempotencyIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired IdempotentOwnerService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void sameKeyReplaysSameSnapshotWithoutCreatingAnotherOwner() {
        UUID key = UUID.randomUUID();
        String name = "Replay " + key;
        int age = 23;
        var first = service.create("admin", key, new CreateOwnerRequest(name, age));
        var repeat = service.create("admin", key, new CreateOwnerRequest(" " + name + " ", age));
        assertThat(first.replayed()).isFalse();
        assertThat(repeat.replayed()).isTrue();
        assertThat(repeat.owner()).isEqualTo(first.owner());
        assertThat(countOwners(name, age)).isEqualTo(1);
        jdbc.update("update owners set name = 'Renamed' where id = ?", first.owner().id());
        assertThat(service.create("admin", key, new CreateOwnerRequest(name, age)).owner()).isEqualTo(first.owner());
    }

    @Test
    void changedPayloadIsRejectedWithoutAnotherEffect() {
        UUID key = UUID.randomUUID();
        int age = 23;
        service.create("admin", key, new CreateOwnerRequest("First " + key, age));
        assertThatThrownBy(() -> service.create("admin", key, new CreateOwnerRequest("Second " + key, 23)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        assertThat(countOwners("Second " + key, age)).isZero();
    }

    @Test
    void sameKeyFromDifferentPrincipalsDoesNotLeakAnotherResult() {
        UUID key = UUID.randomUUID();
        int age = 23;
        var first = service.create("admin-a", key, new CreateOwnerRequest("Scoped " + key, age));
        var second = service.create("admin-b", key, new CreateOwnerRequest("Scoped " + key, age));
        assertThat(second.replayed()).isFalse();
        assertThat(second.owner().id()).isNotEqualTo(first.owner().id());
    }

    @Test
    void expiredKeyIsNotSilentlyReused() {
        UUID key = UUID.randomUUID();
        String name = "Expired " + key;
        int age = 23;
        service.create("admin", key, new CreateOwnerRequest(name, age));
        jdbc.update("update owner_idempotency set expires_at = clock_timestamp() - interval '1 second' where operation_key = ?", key);
        assertThatThrownBy(() -> service.create("admin", key, new CreateOwnerRequest(name, age)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE));
        assertThat(countOwners(name, age)).isEqualTo(1);
    }

    @Test
    void outerRollbackRemovesBothEffectAndKey() {
        UUID key = UUID.randomUUID();
        String name = "Rollback " + key;
        int age = 23;
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            service.create("admin", key, new CreateOwnerRequest(name, age));
            throw new IllegalStateException("Отказ до фиксации");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(countOwners(name, age)).isZero();
        assertThat(countKeys(key)).isZero();
        assertThat(service.create("admin", key, new CreateOwnerRequest(name, age)).replayed()).isFalse();
    }

    @Test
    void databaseErrorDoesNotLeaveAClaimedKey() {
        UUID key = UUID.randomUUID();
        int age = 23;
        assertThatThrownBy(() -> service.create("admin", key, new CreateOwnerRequest("x".repeat(201), age)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(countKeys(key)).isZero();
        assertThat(service.create("admin", key, new CreateOwnerRequest("Corrected " + key, age)).replayed()).isFalse();
    }

    @Test
    void differentKeysRepresentTwoIntentionsEvenWithTheSameNameAndAge() {
        String name = "Two intentions " + UUID.randomUUID();
        int age = 23;
        var first = service.create("admin", UUID.randomUUID(), new CreateOwnerRequest(name, age));
        var second = service.create("admin", UUID.randomUUID(), new CreateOwnerRequest(name, age));
        assertThat(second.owner().id()).isNotEqualTo(first.owner().id());
        assertThat(countOwners(name, age)).isEqualTo(2);
    }

    @Test
    void waitingForConcurrentOwnerIsBoundedAndDoesNotApplyAnotherEffect() throws Exception {
        UUID key = UUID.randomUUID();
        String name = "Lock owner " + key;
        int age = 23;
        var claimed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var owner = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                var result = service.create("admin", key, new CreateOwnerRequest(name, age));
                claimed.countDown();
                try {
                    if (!release.await(8, TimeUnit.SECONDS)) { throw new IllegalStateException("Тест не освободил владельца"); }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return result;
            }));
            try {
                assertThat(claimed.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service.create("admin", key, new CreateOwnerRequest(name, age)))
                        .isInstanceOfSatisfying(ApiException.class,
                                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
            } finally {
                release.countDown();
            }
            assertThat(owner.get(5, TimeUnit.SECONDS).replayed()).isFalse();
            assertThat(countOwners(name, age)).isEqualTo(1);
            assertThat(countKeys(key)).isEqualTo(1);
        }
    }

    @Test
    void concurrentDuplicatesCommitOneEffect() throws Exception {
        UUID key = UUID.randomUUID();
        String name = "Concurrent " + key;
        int age = 23;
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return service.create("admin", key, new CreateOwnerRequest(name, age)); });
            var second = pool.submit(() -> { start.await(); return service.create("admin", key, new CreateOwnerRequest(name, age)); });
            start.countDown();
            var a = first.get(8, TimeUnit.SECONDS);
            var b = second.get(8, TimeUnit.SECONDS);
            assertThat(a.owner()).isEqualTo(b.owner());
            assertThat(a.replayed()).isNotEqualTo(b.replayed());
            assertThat(countOwners(name, age)).isEqualTo(1);
            assertThat(countKeys(key)).isEqualTo(1);
        }
    }

    @Test
    void missingPrincipalIsRejectedBeforeAnyEffect() {
        String name = "Missing subject " + UUID.randomUUID();
        int age = 23;
        for (String principal : new String[]{null, "", " "}) {
            assertThatThrownBy(() -> service.create(principal, UUID.randomUUID(), new CreateOwnerRequest(name, age)))
                    .isInstanceOfSatisfying(ApiException.class, failure ->
                            assertThat(failure.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }
        assertThat(countOwners(name, age)).isZero();
    }

    int countOwners(String name, int age) {
        return jdbc.queryForObject("select count(*) from owners where name = ? and age = ?", Integer.class, name, age);
    }

    int countKeys(UUID key) {
        return jdbc.queryForObject("select count(*) from owner_idempotency where operation_key = ?", Integer.class, key);
    }
}