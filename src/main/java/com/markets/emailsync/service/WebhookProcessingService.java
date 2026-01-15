package com.markets.emailsync.service;

import com.markets.emailsync.entity.MailboxEntity;
import com.markets.emailsync.entity.WebhookNotificationEntity;
import com.markets.emailsync.repository.MailboxRepository;
import com.markets.emailsync.repository.WebhookNotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class WebhookProcessingService {

    private final WebhookNotificationRepository notificationRepository;
    private final MailboxRepository mailboxRepository;
    private final EmailSyncService emailSyncService;

    public WebhookProcessingService(
            WebhookNotificationRepository notificationRepository,
            MailboxRepository mailboxRepository,
            EmailSyncService emailSyncService) {
        this.notificationRepository = notificationRepository;
        this.mailboxRepository = mailboxRepository;
        this.emailSyncService = emailSyncService;
    }

    @Async
    @Transactional
    public void processNotification(String subscriptionId, String changeType,
                                    String resource, String clientState,
                                    String rawPayload) {
        log.info("Processing webhook notification for subscription: {}", subscriptionId);

        // Persist notification for idempotency and audit - keep reference for error handling
        WebhookNotificationEntity notification = WebhookNotificationEntity.builder()
                .subscriptionId(subscriptionId)
                .changeType(changeType)
                .resource(resource)
                .clientState(clientState)
                .rawPayload(rawPayload)
                .processed(false)
                .build();

        notification = notificationRepository.save(notification);

        try {
            // Find the mailbox for this subscription
            Optional<MailboxEntity> mailboxOpt = mailboxRepository
                    .findBySubscriptionId(subscriptionId);

            if (mailboxOpt.isEmpty()) {
                log.error("No mailbox found for subscription: {}", subscriptionId);
                notification.setProcessingError("Mailbox not found");
                notification.setRetryCount(notification.getRetryCount() + 1);
                notificationRepository.save(notification);
                return;
            }

            MailboxEntity mailbox = mailboxOpt.get();
            log.info("Processing notification for mailbox: {}", mailbox.getEmailAddress());

            // Perform delta sync to get the actual changes
            emailSyncService.performDeltaSyncForMailbox(mailbox.getEmailAddress());

            // Mark notification as processed
            notification.setProcessed(true);
            notification.setProcessedAt(Instant.now());
            notificationRepository.save(notification);

            log.info("Webhook notification processed successfully for: {}",
                    mailbox.getEmailAddress());

        } catch (Exception e) {
            log.error("Error processing webhook notification: {}", e.getMessage(), e);

            // Update notification with error using the reference we already have
            notification.setProcessingError(e.getMessage());
            notification.setRetryCount(notification.getRetryCount() + 1);
            notificationRepository.save(notification);
        }
    }

    /**
     * Scheduled task to retry failed webhook notifications
     */
    @Scheduled(fixedDelayString = "${webhook.retry.interval:300000}") // 5 minutes
    @Transactional
    public void retryFailedNotifications() {
        log.debug("Checking for failed webhook notifications to retry");

        List<WebhookNotificationEntity> failedNotifications =
                notificationRepository.findUnprocessedWithRetriesAvailable(5);

        if (failedNotifications.isEmpty()) {
            return;
        }

        log.info("Found {} failed notifications to retry", failedNotifications.size());

        for (WebhookNotificationEntity notification : failedNotifications) {
            try {
                Optional<MailboxEntity> mailboxOpt = mailboxRepository
                        .findBySubscriptionId(notification.getSubscriptionId());

                if (mailboxOpt.isPresent()) {
                    MailboxEntity mailbox = mailboxOpt.get();
                    emailSyncService.performDeltaSyncForMailbox(
                            mailbox.getEmailAddress());

                    notification.setProcessed(true);
                    notification.setProcessedAt(Instant.now());
                    notification.setProcessingError(null);
                } else {
                    notification.setProcessingError("Mailbox not found");
                    notification.setRetryCount(notification.getRetryCount() + 1);
                }

                notificationRepository.save(notification);

            } catch (Exception e) {
                log.error("Failed to retry notification {}: {}",
                        notification.getId(), e.getMessage());
                notification.setProcessingError(e.getMessage());
                notification.setRetryCount(notification.getRetryCount() + 1);
                notificationRepository.save(notification);
            }
        }
    }

    /**
     * Clean up old processed notifications
     */
    @Scheduled(cron = "${webhook.cleanup.cron:0 0 2 * * ?}") // 2 AM daily
    @Transactional
    public void cleanupOldNotifications() {
        log.info("Cleaning up old processed webhook notifications");

        // Delete notifications older than 30 days
        Instant cutoff = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);

        // Implementation would depend on adding a custom query method
        log.info("Cleanup task executed (implement custom query if needed)");
    }
}