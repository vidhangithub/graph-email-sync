package com.markets.emailsync.service;

import com.markets.emailsync.config.MicrosoftGraphProperties;
import com.markets.emailsync.entity.MailboxEntity;
import com.markets.emailsync.repository.MailboxRepository;
import com.microsoft.graph.models.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
public class SubscriptionService {

    private final GraphService graphService;
    private final MailboxRepository mailboxRepository;
    private final MicrosoftGraphProperties properties;

    public SubscriptionService(
            GraphService graphService,
            MailboxRepository mailboxRepository,
            MicrosoftGraphProperties properties) {
        this.graphService = graphService;
        this.mailboxRepository = mailboxRepository;
        this.properties = properties;
    }

    @Transactional
    public void createSubscriptionForMailbox(String emailAddress) {
        log.info("Creating subscription for mailbox: {}", emailAddress);

        MailboxEntity mailbox = mailboxRepository.findByEmailAddress(emailAddress)
                .orElseThrow(() -> new IllegalStateException(
                        "Mailbox not found: " + emailAddress));

        try {
            // Delete existing subscription if any
            if (mailbox.getSubscriptionId() != null) {
                try {
                    graphService.deleteSubscription(mailbox.getSubscriptionId());
                } catch (Exception e) {
                    log.warn("Failed to delete existing subscription: {}", e.getMessage());
                }
            }

            // Create new subscription
            Subscription subscription = graphService.createSubscription(emailAddress);

            mailbox.setSubscriptionId(subscription.id);
            mailbox.setSubscriptionExpiration(
                    subscription.expirationDateTime.toInstant());
            mailboxRepository.save(mailbox);

            log.info("Subscription created successfully for {}: {}",
                    emailAddress, subscription.id);

        } catch (Exception e) {
            log.error("Failed to create subscription for {}: {}",
                    emailAddress, e.getMessage(), e);
            throw new RuntimeException("Subscription creation failed", e);
        }
    }

    @Transactional
    public void renewSubscriptionForMailbox(String emailAddress) {
        log.info("Renewing subscription for mailbox: {}", emailAddress);

        MailboxEntity mailbox = mailboxRepository.findByEmailAddress(emailAddress)
                .orElseThrow(() -> new IllegalStateException(
                        "Mailbox not found: " + emailAddress));

        if (mailbox.getSubscriptionId() == null) {
            log.warn("No subscription ID found for {}. Creating new subscription.",
                    emailAddress);
            createSubscriptionForMailbox(emailAddress);
            return;
        }

        try {
            Subscription renewed = graphService.renewSubscription(
                    mailbox.getSubscriptionId());

            mailbox.setSubscriptionExpiration(renewed.expirationDateTime.toInstant());
            mailboxRepository.save(mailbox);

            log.info("Subscription renewed successfully for {}: {}",
                    emailAddress, mailbox.getSubscriptionId());

        } catch (Exception e) {
            log.error("Failed to renew subscription for {}: {}",
                    emailAddress, e.getMessage(), e);

            // If renewal fails, try creating a new subscription
            log.info("Attempting to create new subscription after renewal failure");
            try {
                createSubscriptionForMailbox(emailAddress);
            } catch (Exception ex) {
                mailbox.setSyncStatus(MailboxEntity.SyncStatus.SUBSCRIPTION_EXPIRED);
                mailboxRepository.save(mailbox);
                throw new RuntimeException("Subscription renewal and recreation failed", ex);
            }
        }
    }

    @Scheduled(fixedDelayString = "${subscription.renewal.check.interval:3600000}") // 1 hour
    @Transactional
    public void checkAndRenewSubscriptions() {
        log.info("Checking for subscriptions that need renewal");

        Instant threshold = Instant.now().plus(
                properties.getSubscription().getRenewalBeforeHours(),
                ChronoUnit.HOURS);

        List<MailboxEntity> mailboxes = mailboxRepository
                .findMailboxesNeedingSubscriptionRenewal(threshold);

        log.info("Found {} subscriptions needing renewal", mailboxes.size());

        for (MailboxEntity mailbox : mailboxes) {
            try {
                renewSubscriptionForMailbox(mailbox.getEmailAddress());
            } catch (Exception e) {
                log.error("Failed to renew subscription for {}: {}",
                        mailbox.getEmailAddress(), e.getMessage());
            }
        }
    }

    @Transactional
    public void deleteSubscriptionForMailbox(String emailAddress) {
        log.info("Deleting subscription for mailbox: {}", emailAddress);

        MailboxEntity mailbox = mailboxRepository.findByEmailAddress(emailAddress)
                .orElseThrow(() -> new IllegalStateException(
                        "Mailbox not found: " + emailAddress));

        if (mailbox.getSubscriptionId() == null) {
            log.warn("No subscription ID found for {}", emailAddress);
            return;
        }

        try {
            graphService.deleteSubscription(mailbox.getSubscriptionId());

            mailbox.setSubscriptionId(null);
            mailbox.setSubscriptionExpiration(null);
            mailbox.setSyncStatus(MailboxEntity.SyncStatus.DISABLED);
            mailboxRepository.save(mailbox);

            log.info("Subscription deleted successfully for {}", emailAddress);

        } catch (Exception e) {
            log.error("Failed to delete subscription for {}: {}",
                    emailAddress, e.getMessage(), e);
            throw new RuntimeException("Subscription deletion failed", e);
        }
    }
}