package com.markets.emailsync.service;

import com.markets.emailsync.config.MicrosoftGraphProperties;
import com.markets.emailsync.entity.MailboxEntity;
import com.markets.emailsync.repository.MailboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class MailboxInitializationService {

    private final MailboxRepository mailboxRepository;
    private final MicrosoftGraphProperties properties;
    private final EmailSyncService emailSyncService;
    private final SubscriptionService subscriptionService;

    public MailboxInitializationService(
            MailboxRepository mailboxRepository,
            MicrosoftGraphProperties properties,
            EmailSyncService emailSyncService,
            SubscriptionService subscriptionService) {
        this.mailboxRepository = mailboxRepository;
        this.properties = properties;
        this.emailSyncService = emailSyncService;
        this.subscriptionService = subscriptionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeMailboxes() {
        log.info("Initializing mailboxes from configuration");

        List<String> configuredMailboxes = properties.getMailboxes();
        log.info("Found {} configured mailboxes", configuredMailboxes.size());

        for (String emailAddress : configuredMailboxes) {
            try {
                initializeMailbox(emailAddress);
            } catch (Exception e) {
                log.error("Failed to initialize mailbox {}: {}",
                        emailAddress, e.getMessage(), e);
            }
        }

        log.info("Mailbox initialization completed");
    }

    @Transactional
    public void initializeMailbox(String emailAddress) {
        log.info("Initializing mailbox: {}", emailAddress);

        MailboxEntity mailbox = mailboxRepository.findByEmailAddress(emailAddress)
                .orElseGet(() -> {
                    MailboxEntity newMailbox = MailboxEntity.builder()
                            .emailAddress(emailAddress)
                            .syncStatus(MailboxEntity.SyncStatus.NOT_INITIALIZED)
                            .build();
                    return mailboxRepository.save(newMailbox);
                });

        if (mailbox.getSyncStatus() == MailboxEntity.SyncStatus.ACTIVE
                && mailbox.isInitialSyncCompleted()) {
            log.info("Mailbox {} is already initialized and active", emailAddress);
            return;
        }

        try {
            // Step 1: Perform initial delta sync
            if (!mailbox.isInitialSyncCompleted()) {
                log.info("Performing initial sync for {}", emailAddress);
                emailSyncService.performInitialSyncForMailbox(emailAddress);
            }

            // Step 2: Create subscription
            log.info("Creating subscription for {}", emailAddress);
            subscriptionService.createSubscriptionForMailbox(emailAddress);

            log.info("Mailbox {} initialized successfully", emailAddress);

        } catch (Exception e) {
            log.error("Failed to initialize mailbox {}: {}",
                    emailAddress, e.getMessage(), e);

            mailbox.setSyncStatus(MailboxEntity.SyncStatus.ERROR);
            mailbox.setErrorMessage(e.getMessage());
            mailboxRepository.save(mailbox);

            throw new RuntimeException("Mailbox initialization failed", e);
        }
    }

    @Transactional
    public void reinitializeMailbox(String emailAddress) {
        log.info("Re-initializing mailbox: {}", emailAddress);

        MailboxEntity mailbox = mailboxRepository.findByEmailAddress(emailAddress)
                .orElseThrow(() -> new IllegalStateException(
                        "Mailbox not found: " + emailAddress));

        // Delete existing subscription if any
        if (mailbox.getSubscriptionId() != null) {
            try {
                subscriptionService.deleteSubscriptionForMailbox(emailAddress);
            } catch (Exception e) {
                log.warn("Failed to delete subscription during reinitialization: {}",
                        e.getMessage());
            }
        }

        // Reset mailbox state
        mailbox.setDeltaLink(null);
        mailbox.setSubscriptionId(null);
        mailbox.setSubscriptionExpiration(null);
        mailbox.setInitialSyncCompleted(false);
        mailbox.setSyncStatus(MailboxEntity.SyncStatus.NOT_INITIALIZED);
        mailbox.setErrorMessage(null);
        mailbox.setRetryCount(0);
        mailboxRepository.save(mailbox);

        // Re-initialize
        initializeMailbox(emailAddress);
    }
}