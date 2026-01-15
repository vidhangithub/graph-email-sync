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
public interface EmailRepository extends JpaRepository<EmailEntity, Long> {

    Optional<EmailEntity> findByMessageId(String messageId);

    @Query("SELECT COUNT(e) FROM EmailEntity e WHERE e.mailbox.id = :mailboxId")
    long countByMailboxId(Long mailboxId);

    @Query("SELECT e FROM EmailEntity e WHERE e.mailbox.emailAddress = :emailAddress " +
            "AND e.receivedDateTime >= :since ORDER BY e.receivedDateTime DESC")
    List<EmailEntity> findRecentEmails(String emailAddress, Instant since);

    boolean existsByMessageId(String messageId);
}
