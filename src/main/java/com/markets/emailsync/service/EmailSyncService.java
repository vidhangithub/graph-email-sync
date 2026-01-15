package com.markets.emailsync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markets.emailsync.entity.EmailEntity;
import com.markets.emailsync.entity.MailboxEntity;
import com.markets.emailsync.repository.EmailRepository;
import com.markets.emailsync.repository.MailboxRepository;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailSyncService {

    private final GraphService graphService;
    private final MailboxRepository mailboxRepository;
    private final EmailRepository emailRepository;
    private final ObjectMapper objectMapper;

    public EmailSyncService(
            GraphService graphService,
            MailboxRepository mailboxRepository,
            EmailRepository emailRepository,
            ObjectMapper objectMapper) {
        this.graphService = graphService;
        this.mailboxRepository = mailboxRepository;
        this.emailRepository = emailRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void performInitialSyncForMailbox(String emailAddress) {
        log.info("Starting initial sync for mailbox: {}", emailAddress);

        MailboxEntity mailbox = mailboxRepository.findByEmailAddress(emailAddress)
                .orElseThrow(() -> new IllegalStateException(
                        "Mailbox not found: " + emailAddress));

        try {
            mailbox.setSyncStatus(MailboxEntity.SyncStatus.INITIALIZING);
            mailboxRepository.save(mailbox);

            GraphService.DeltaResult result = graphService.performInitialSync(emailAddress);

            int processed = processMessages(result.messages(), mailbox, true);

            mailbox.setDeltaLink(result.deltaLink());
            mailbox.setInitialSyncCompleted(true);
            mailbox.setSyncStatus(MailboxEntity.SyncStatus.ACTIVE);
            mailbox.setLastSyncTime(Instant.now());
            mailbox.setErrorMessage(null);
            mailbox.setRetryCount(0);
            mailboxRepository.save(mailbox);

            log.info("Initial sync completed for {}. Processed {} messages",
                    emailAddress, processed);

        } catch (Exception e) {
            log.error("Failed to perform initial sync for {}: {}",
                    emailAddress, e.getMessage(), e);
            mailbox.setSyncStatus(MailboxEntity.SyncStatus.ERROR);
            mailbox.setErrorMessage(e.getMessage());
            mailbox.setRetryCount(mailbox.getRetryCount() + 1);
            mailboxRepository.save(mailbox);
            throw new RuntimeException("Initial sync failed", e);
        }
    }

    @Transactional
    public void performDeltaSyncForMailbox(String emailAddress) {
        log.info("Starting delta sync for mailbox: {}", emailAddress);

        MailboxEntity mailbox = mailboxRepository.findByEmailAddress(emailAddress)
                .orElseThrow(() -> new IllegalStateException(
                        "Mailbox not found: " + emailAddress));

        if (mailbox.getDeltaLink() == null) {
            log.warn("No delta link found for {}. Performing initial sync instead.",
                    emailAddress);
            performInitialSyncForMailbox(emailAddress);
            return;
        }

        try {
            GraphService.DeltaResult result = graphService.performDeltaSync(
                    mailbox.getDeltaLink());

            int processed = processMessages(result.messages(), mailbox, false);

            if (result.deltaLink() != null) {
                mailbox.setDeltaLink(result.deltaLink());
            }
            mailbox.setLastSyncTime(Instant.now());
            mailbox.setErrorMessage(null);
            mailbox.setRetryCount(0);
            mailboxRepository.save(mailbox);

            log.info("Delta sync completed for {}. Processed {} changes",
                    emailAddress, processed);

        } catch (Exception e) {
            log.error("Failed to perform delta sync for {}: {}",
                    emailAddress, e.getMessage(), e);
            mailbox.setErrorMessage(e.getMessage());
            mailbox.setRetryCount(mailbox.getRetryCount() + 1);

            if (mailbox.getRetryCount() >= 5) {
                mailbox.setSyncStatus(MailboxEntity.SyncStatus.ERROR);
            }

            mailboxRepository.save(mailbox);
            throw new RuntimeException("Delta sync failed", e);
        }
    }

    private int processMessages(List<Message> messages, MailboxEntity mailbox,
                                boolean isInitialSync) {
        int processed = 0;

        for (Message message : messages) {
            try {
                if (message.id == null) {
                    log.warn("Skipping message with null ID");
                    continue;
                }

                Optional<EmailEntity> existing = emailRepository.findByMessageId(message.id);

                if (existing.isPresent()) {
                    // Update existing message
                    EmailEntity email = existing.get();
                    updateEmailFromMessage(email, message);
                    email.setChangeType(EmailEntity.ChangeType.UPDATED);
                    emailRepository.save(email);
                    log.debug("Updated existing email: {}", message.id);
                } else {
                    // Create new message
                    EmailEntity email = createEmailFromMessage(message, mailbox);
                    email.setChangeType(isInitialSync ?
                            EmailEntity.ChangeType.CREATED :
                            EmailEntity.ChangeType.CREATED);
                    emailRepository.save(email);
                    log.debug("Created new email: {}", message.id);
                }

                processed++;

            } catch (Exception e) {
                log.error("Failed to process message {}: {}",
                        message.id, e.getMessage(), e);
            }
        }

        return processed;
    }

    private EmailEntity createEmailFromMessage(Message message, MailboxEntity mailbox) {
        EmailEntity email = new EmailEntity();
        email.setMessageId(message.id);
        email.setMailbox(mailbox);
        updateEmailFromMessage(email, message);
        return email;
    }

    private void updateEmailFromMessage(EmailEntity email, Message message) {
        email.setSubject(message.subject);

        if (message.from != null && message.from.emailAddress != null) {
            email.setSenderEmail(message.from.emailAddress.address);
            email.setSenderName(message.from.emailAddress.name);
        }

        if (message.toRecipients != null && !message.toRecipients.isEmpty()) {
            String recipients = message.toRecipients.stream()
                    .map(r -> r.emailAddress.address)
                    .collect(Collectors.joining(", "));
            email.setRecipientEmails(recipients);
        }

        if (message.receivedDateTime != null) {
            email.setReceivedDateTime(
                    message.receivedDateTime.atZoneSameInstant(ZoneId.systemDefault())
                            .toInstant());
        }

        email.setHasAttachments(message.hasAttachments != null && message.hasAttachments);
        email.setIsRead(message.isRead != null && message.isRead);
        email.setImportance(message.importance != null ?
                message.importance.name() : null);
        email.setBodyPreview(message.bodyPreview);

        if (message.body != null) {
            email.setBodyContent(message.body.content);
            email.setBodyContentType(message.body.contentType != null ?
                    message.body.contentType.name() : null);
        }

        if (message.categories != null && !message.categories.isEmpty()) {
            email.setCategories(String.join(", ", message.categories));
        }

        email.setConversationId(message.conversationId);

        // Store raw message data (optional, can be large)
        try {
            email.setRawData(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize message to JSON: {}", e.getMessage());
        }
    }
}