package com.markets.emailsync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mailboxes", indexes = {
        @Index(name = "idx_mailbox_email", columnList = "email_address"),
        @Index(name = "idx_mailbox_status", columnList = "sync_status")
})
public class MailboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_address", nullable = false, unique = true, length = 255)
    private String emailAddress;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "delta_link", length = 2048)
    private String deltaLink;

    @Column(name = "subscription_id", length = 100)
    private String subscriptionId;

    @Column(name = "subscription_expiration")
    private Instant subscriptionExpiration;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 50)
    private SyncStatus syncStatus = SyncStatus.NOT_INITIALIZED;

    @Column(name = "last_sync_time")
    private Instant lastSyncTime;

    @Column(name = "initial_sync_completed")
    private boolean initialSyncCompleted = false;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "retry_count")
    private int retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public enum SyncStatus {
        NOT_INITIALIZED,
        INITIALIZING,
        ACTIVE,
        ERROR,
        SUBSCRIPTION_EXPIRED,
        DISABLED
    }
}