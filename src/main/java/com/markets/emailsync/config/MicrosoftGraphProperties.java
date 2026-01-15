package com.markets.emailsync.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@Validated
@ConfigurationProperties(prefix = "microsoft.graph")
public class MicrosoftGraphProperties {

    @NotBlank
    private String tenantId;

    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    @NotEmpty
    private List<String> scopes;

    @NotEmpty
    private List<String> mailboxes;

    private SubscriptionProperties subscription = new SubscriptionProperties();
    private DeltaProperties delta = new DeltaProperties();
    private RetryProperties retry = new RetryProperties();

    @Data
    public static class SubscriptionProperties {
        @NotBlank
        private String notificationUrl;

        @NotBlank
        private String clientState;

        @Positive
        private int expirationHours = 72;

        @Positive
        private int renewalBeforeHours = 12;
    }

    @Data
    public static class DeltaProperties {
        @Positive
        private int pageSize = 50;

        @Positive
        private int initialSyncDaysBack = 7;
    }

    @Data
    public static class RetryProperties {
        @Positive
        private int maxAttempts = 3;

        @Positive
        private long initialIntervalMs = 1000;

        @Positive
        private double multiplier = 2.0;

        @Positive
        private long maxIntervalMs = 10000;
    }
}
