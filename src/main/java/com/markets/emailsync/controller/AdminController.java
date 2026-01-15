package com.markets.emailsync.controller;

import com.markets.emailsync.entity.MailboxEntity;
import com.markets.emailsync.repository.EmailRepository;
import com.markets.emailsync.repository.MailboxRepository;
import com.markets.emailsync.repository.WebhookNotificationRepository;
import com.markets.emailsync.service.EmailSyncService;
import com.markets.emailsync.service.MailboxInitializationService;
import com.markets.emailsync.service.SubscriptionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final MailboxRepository mailboxRepository;
    private final EmailRepository emailRepository;
    private final WebhookNotificationRepository notificationRepository;
    private final MailboxInitializationService initializationService;
    private final EmailSyncService emailSyncService;
    private final SubscriptionService subscriptionService;

    public AdminController(
            MailboxRepository mailboxRepository,
            EmailRepository emailRepository,
            WebhookNotificationRepository notificationRepository,
            MailboxInitializationService initializationService,
            EmailSyncService emailSyncService,
            SubscriptionService subscriptionService) {
        this.mailboxRepository = mailboxRepository;
        this.emailRepository = emailRepository;
        this.notificationRepository = notificationRepository;
        this.initializationService = initializationService;
        this.emailSyncService = emailSyncService;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/mailboxes")
    public ResponseEntity<List<MailboxStatusDto>> getAllMailboxes() {
        List<MailboxEntity> mailboxes = mailboxRepository.findAll();

        List<MailboxStatusDto> dtos = mailboxes.stream()
                .map(this::toStatusDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/mailboxes/{emailAddress}")
    public ResponseEntity<MailboxStatusDto> getMailboxStatus(
            @PathVariable String emailAddress) {

        return mailboxRepository.findByEmailAddress(emailAddress)
                .map(this::toStatusDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/mailboxes/{emailAddress}/initialize")
    public ResponseEntity<Map<String, String>> initializeMailbox(
            @PathVariable String emailAddress) {

        log.info("Admin triggered initialization for mailbox: {}", emailAddress);

        try {
            initializationService.initializeMailbox(emailAddress);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Mailbox initialized successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to initialize mailbox: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/mailboxes/{emailAddress}/reinitialize")
    public ResponseEntity<Map<String, String>> reinitializeMailbox(
            @PathVariable String emailAddress) {

        log.info("Admin triggered reinitialization for mailbox: {}", emailAddress);

        try {
            initializationService.reinitializeMailbox(emailAddress);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Mailbox reinitialized successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to reinitialize mailbox: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/mailboxes/{emailAddress}/sync")
    public ResponseEntity<Map<String, String>> triggerManualSync(
            @PathVariable String emailAddress) {

        log.info("Admin triggered manual sync for mailbox: {}", emailAddress);

        try {
            emailSyncService.performDeltaSyncForMailbox(emailAddress);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Manual sync completed successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to perform manual sync: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/mailboxes/{emailAddress}/subscription/renew")
    public ResponseEntity<Map<String, String>> renewSubscription(
            @PathVariable String emailAddress) {

        log.info("Admin triggered subscription renewal for mailbox: {}", emailAddress);

        try {
            subscriptionService.renewSubscriptionForMailbox(emailAddress);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Subscription renewed successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to renew subscription: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<StatsDto> getStats() {
        long totalMailboxes = mailboxRepository.count();
        long activeMailboxes = mailboxRepository
                .findBySyncStatus(MailboxEntity.SyncStatus.ACTIVE).size();
        long totalEmails = emailRepository.count();
        long pendingNotifications = notificationRepository.countByProcessedFalse();

        StatsDto stats = new StatsDto();
        stats.setTotalMailboxes(totalMailboxes);
        stats.setActiveMailboxes(activeMailboxes);
        stats.setTotalEmails(totalEmails);
        stats.setPendingNotifications(pendingNotifications);

        return ResponseEntity.ok(stats);
    }

    private MailboxStatusDto toStatusDto(MailboxEntity mailbox) {
        MailboxStatusDto dto = new MailboxStatusDto();
        dto.setEmailAddress(mailbox.getEmailAddress());
        dto.setSyncStatus(mailbox.getSyncStatus().name());
        dto.setInitialSyncCompleted(mailbox.isInitialSyncCompleted());
        dto.setSubscriptionId(mailbox.getSubscriptionId());
        dto.setSubscriptionExpiration(mailbox.getSubscriptionExpiration());
        dto.setLastSyncTime(mailbox.getLastSyncTime());
        dto.setErrorMessage(mailbox.getErrorMessage());
        dto.setEmailCount(emailRepository.countByMailboxId(mailbox.getId()));
        return dto;
    }

    @Data
    public static class MailboxStatusDto {
        private String emailAddress;
        private String syncStatus;
        private boolean initialSyncCompleted;
        private String subscriptionId;
        private Instant subscriptionExpiration;
        private Instant lastSyncTime;
        private String errorMessage;
        private long emailCount;
    }

    @Data
    public static class StatsDto {
        private long totalMailboxes;
        private long activeMailboxes;
        private long totalEmails;
        private long pendingNotifications;
    }
}