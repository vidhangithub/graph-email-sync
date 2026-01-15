package com.markets.emailsync.repository;

import com.markets.emailsync.entity.MailboxEntity;
import com.markets.emailsync.entity.EmailEntity;
import com.markets.emailsync.entity.WebhookNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MailboxRepository extends JpaRepository<MailboxEntity, Long> {

    Optional<MailboxEntity> findByEmailAddress(String emailAddress);

    Optional<MailboxEntity> findBySubscriptionId(String subscriptionId);

    List<MailboxEntity> findBySyncStatus(MailboxEntity.SyncStatus syncStatus);

    @Query("SELECT m FROM MailboxEntity m WHERE m.subscriptionExpiration IS NOT NULL " +
            "AND m.subscriptionExpiration < :expirationThreshold " +
            "AND m.syncStatus = 'ACTIVE'")
    List<MailboxEntity> findMailboxesNeedingSubscriptionRenewal(Instant expirationThreshold);

    @Query("SELECT m FROM MailboxEntity m WHERE m.syncStatus = 'ACTIVE' " +
            "AND (m.lastSyncTime IS NULL OR m.lastSyncTime < :threshold)")
    List<MailboxEntity> findStaleMailboxes(Instant threshold);
}