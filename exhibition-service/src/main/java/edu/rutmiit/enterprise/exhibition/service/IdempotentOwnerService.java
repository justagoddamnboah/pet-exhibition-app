package edu.rutmiit.enterprise.exhibition.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import edu.rutmiit.enterprise.exhibition.api.ApiException;
import edu.rutmiit.enterprise.exhibition.api.CreateOwnerRequest;
import edu.rutmiit.enterprise.exhibition.api.OwnerResponse;

@Service
public class IdempotentOwnerService {
    private static final String OPERATION = "create-owner-v1";
    private final JdbcTemplate jdbc;
    private final PetService pets;

    public IdempotentOwnerService(JdbcTemplate jdbc, PetService pets) {
        this.jdbc = jdbc;
        this.pets = pets;
    }

    public record Result(OwnerResponse owner, boolean replayed) {}
    private record Stored(String fingerprint, String state, UUID ownerId, String name, int age, boolean expired) {}

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 3)
    public Result create(String principal, UUID key, CreateOwnerRequest request) {
        if (principal == null || principal.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "В токене отсутствует идентификатор пользователя");
        }
        jdbc.execute("SET LOCAL lock_timeout = '1000ms'");
        jdbc.execute("SET LOCAL statement_timeout = '2000ms'");
        String name = request.name().trim();
        int age = request.age();
        String fingerprint = fingerprint(name);
        int claimed;
        try {
            claimed = jdbc.update("""
                INSERT INTO owner_idempotency(principal_id, operation, operation_key, request_fingerprint, state)
                VALUES (?, ?, ?, ?, 'IN_PROGRESS') ON CONFLICT DO NOTHING
                """, principal, OPERATION, key, fingerprint);
        } catch (DataAccessException failure) {
            if (failure.getMostSpecificCause() instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
                throw new ApiException(HttpStatus.CONFLICT, "Конкурирующая команда ещё выполняется; повторите тот же ключ позже");
            }
            throw failure;
        }
        if (claimed == 0) {
            Stored stored = jdbc.queryForObject("""
                    SELECT request_fingerprint, state, owner_id, response_name, response_age,
                           expires_at <= clock_timestamp() AS expired
                    FROM owner_idempotency WHERE principal_id = ? AND operation = ? AND operation_key = ?
                    """, (row, index) -> new Stored(row.getString("request_fingerprint"),
                    row.getString("state"),
                    row.getObject("owner_id", UUID.class),
                    row.getString("response_name"),
                    row.getInt("response_age"),
                    row.getBoolean("expired")),
                    principal, OPERATION, key);
            if (stored == null) { throw new IllegalStateException("Запись ключа не найдена после конфликта"); }
            if (stored.expired()) {
                throw new ApiException(HttpStatus.GONE, "Срок ключа истёк; сначала согласуйте результат операции");
            }
            if (!stored.fingerprint().equals(fingerprint)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Ключ уже использован с другим именем хозяина");
            }
            if (!stored.state().equals("COMPLETED")) {
                throw new ApiException(HttpStatus.CONFLICT, "Команда с этим ключом ещё выполняется");
            }
            return new Result(new OwnerResponse(stored.ownerId(), stored.name(), stored.age()), true);
        }

        OwnerResponse owner = pets.createOwner(new CreateOwnerRequest(name, age));
        int completed = jdbc.update("""
                UPDATE owner_idempotency SET state = 'COMPLETED', owner_id = ?, response_name = ?, response_age = ?
                WHERE principal_id = ? AND operation = ? AND operation_key = ?
                """, owner.id(), owner.name(), owner.age(), principal, OPERATION, key);
        if (completed != 1) { throw new IllegalStateException("Не удалось сохранить результат команды"); }
        return new Result(owner, false);
    }

    static String fingerprint(String name) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(name.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK обязан предоставлять SHA-256", impossible);
        }
    }
}