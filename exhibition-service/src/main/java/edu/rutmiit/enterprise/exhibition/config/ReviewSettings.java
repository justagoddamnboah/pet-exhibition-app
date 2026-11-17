package edu.rutmiit.enterprise.exhibition.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.review")
public record ReviewSettings (String baseUrl, Duration connectTimeout, Duration readTimeout, 
        Duration budget, Duration reserve, int maxAttempts, Duration initialWait, Duration maxWait,
        double jitter) {
    public ReviewSettings {
        if (baseUrl == null || connectTimeout == null || readTimeout == null || budget == null
                || reserve == null || initialWait == null || maxWait == null) {
            throw new IllegalArgumentException("Все параметры клиента отзывов обязательны");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()
                || budget.isNegative() || budget.isZero() || reserve.isNegative()
                || initialWait.isNegative() || maxWait.compareTo(initialWait) < 0
                || maxAttempts < 1 || maxAttempts > 2 || !Double.isFinite(jitter) || jitter < 0 || jitter > 1) {
            throw new IllegalArgumentException("Некорректная политика повторов");
        }
    }

    public Duration attemptAllowance() {
        return connectTimeout.plus(readTimeout);
    }
}