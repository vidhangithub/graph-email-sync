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
@Table(name = "emails", indexes = {
        @Index(name = "idx_email_message_id", columnList = "message_id"),
        @Index(name = "idx_email_mailbox", columnList = "mailbox_id"),
        @Index(name = "idx_email_received", columnList = "received_date_time"),
        @Index(name = "idx_email_change_type", columnList = "change_type")
})
public class EmailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true, length = 500)
    private String messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mailbox_id", nullable = false)
    private MailboxEntity mailbox;

    @Column(name = "subject", length = 1000)
    private String subject;

    @Column(name = "sender_email", length = 255)
    private String senderEmail;

    @Column(name = "sender_name", length = 255)
    private String senderName;

    @Column(name = "recipient_emails", length = 2000)
    private String recipientEmails;

    @Column(name = "received_date_time")
    private Instant receivedDateTime;

    @Column(name = "has_attachments")
    private boolean hasAttachments;

    @Column(name = "is_read")
    private boolean isRead;

    @Column(name = "importance", length = 20)
    private String importance;

    @Column(name = "body_preview", length = 500)
    private String bodyPreview;

    @Column(name = "body_content", columnDefinition = "TEXT")
    private String bodyContent;

    @Column(name = "body_content_type", length = 20)
    private String bodyContentType;

    @Column(name = "categories", length = 500)
    private String categories;

    @Column(name = "conversation_id", length = 500)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public enum ChangeType {
        CREATED,
        UPDATED,
        DELETED
    }
}