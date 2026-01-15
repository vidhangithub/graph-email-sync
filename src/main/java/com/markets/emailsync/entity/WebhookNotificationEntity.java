package com.markets.emailsync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "webhook_notifications", indexes = {
        @Index(name = "idx_webhook_subscription", columnList = "subscription_id"),
        @Index(name = "idx_webhook_processed", columnList = "processed"),
        @Index(name = "idx_webhook_received", columnList = "received_at")
})
public class WebhookNotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false, length = 100)
    private String subscriptionId;

    @Column(name = "change_type", length = 50)
    private String changeType;

    @Column(name = "resource", length = 1000)
    private String resource;

    @Column(name = "client_state", length = 255)
    private String clientState;

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    @Column(name = "processing_error", length = 2000)
    private String processingError;

    @Column(name = "retry_count")
    private int retryCount = 0;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}