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
public interface WebhookNotificationRepository extends JpaRepository<WebhookNotificationEntity, Long> {

    List<WebhookNotificationEntity> findByProcessedFalseOrderByReceivedAtAsc();

    List<WebhookNotificationEntity> findBySubscriptionIdAndProcessedFalse(String subscriptionId);

    Optional<WebhookNotificationEntity> findFirstBySubscriptionIdAndProcessedFalseOrderByReceivedAtAsc(String subscriptionId);

    @Query("SELECT w FROM WebhookNotificationEntity w WHERE w.processed = false " +
            "AND w.retryCount < :maxRetries ORDER BY w.receivedAt ASC")
    List<WebhookNotificationEntity> findUnprocessedWithRetriesAvailable(int maxRetries);

    long countByProcessedFalse();
}
