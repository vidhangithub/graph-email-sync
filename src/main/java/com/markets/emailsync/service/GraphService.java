package com.markets.emailsync.service;

import com.markets.emailsync.config.MicrosoftGraphProperties;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Subscription;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.MessageCollectionPage;
import com.microsoft.graph.requests.MessageDeltaCollectionPage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GraphService {

    private final GraphServiceClient<okhttp3.Request> graphClient;
    private final MicrosoftGraphProperties properties;

    public GraphService(
            GraphServiceClient<okhttp3.Request> graphClient,
            MicrosoftGraphProperties properties) {
        this.graphClient = graphClient;
        this.properties = properties;
    }

    @CircuitBreaker(name = "graphApi", fallbackMethod = "createSubscriptionFallback")
    @Retry(name = "graphApi")
    public Subscription createSubscription(String userEmail) {
        log.info("Creating subscription for mailbox: {}", userEmail);

        Subscription subscription = new Subscription();
        subscription.changeType = "created,updated,deleted";
        subscription.notificationUrl = properties.getSubscription().getNotificationUrl();
        subscription.resource = String.format("users/%s/messages", userEmail);
        subscription.expirationDateTime = OffsetDateTime.now(ZoneOffset.UTC)
                .plusHours(properties.getSubscription().getExpirationHours());
        subscription.clientState = properties.getSubscription().getClientState();

        Subscription created = graphClient.subscriptions()
                .buildRequest()
                .post(subscription);

        log.info("Subscription created successfully: {} for mailbox: {}",
                created.id, userEmail);
        return created;
    }

    @CircuitBreaker(name = "graphApi")
    @Retry(name = "graphApi")
    public Subscription renewSubscription(String subscriptionId) {
        log.info("Renewing subscription: {}", subscriptionId);

        Subscription subscription = new Subscription();
        subscription.expirationDateTime = OffsetDateTime.now(ZoneOffset.UTC)
                .plusHours(properties.getSubscription().getExpirationHours());

        Subscription renewed = graphClient.subscriptions(subscriptionId)
                .buildRequest()
                .patch(subscription);

        log.info("Subscription renewed successfully: {}", subscriptionId);
        return renewed;
    }

    @CircuitBreaker(name = "graphApi")
    @Retry(name = "graphApi")
    public void deleteSubscription(String subscriptionId) {
        log.info("Deleting subscription: {}", subscriptionId);

        try {
            graphClient.subscriptions(subscriptionId)
                    .buildRequest()
                    .delete();
            log.info("Subscription deleted successfully: {}", subscriptionId);
        } catch (GraphServiceException e) {
            if (e.getResponseCode() == 404) {
                log.warn("Subscription not found (already deleted?): {}", subscriptionId);
            } else {
                throw e;
            }
        }
    }

    @CircuitBreaker(name = "graphApi", fallbackMethod = "performInitialSyncFallback")
    @Retry(name = "graphApi")
    public DeltaResult performInitialSync(String userEmail) {
        log.info("Performing initial sync for mailbox: {}", userEmail);

        List<Message> allMessages = new ArrayList<>();
        String deltaLink = null;

        try {
            // Initial delta query with filter for recent messages
            OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC)
                    .minusDays(properties.getDelta().getInitialSyncDaysBack());

            String filter = String.format("receivedDateTime ge %s",
                    since.toString());

            MessageDeltaCollectionPage deltaPage = graphClient
                    .users(userEmail)
                    .messages()
                    .delta()
                    .buildRequest()
                    .select("id,subject,from,toRecipients,receivedDateTime," +
                            "hasAttachments,isRead,importance,bodyPreview," +
                            "body,categories,conversationId")
                    .top(properties.getDelta().getPageSize())
                    .filter(filter)
                    .get();

            while (deltaPage != null) {
                List<Message> messages = deltaPage.getCurrentPage();
                allMessages.addAll(messages);
                log.debug("Fetched {} messages in this page", messages.size());

                if (deltaPage.deltaLink() != null) {
                    deltaLink = deltaPage.deltaLink();
                    log.info("Delta link obtained: {}", deltaLink.substring(0,
                            Math.min(100, deltaLink.length())));
                    break;
                }

                if (deltaPage.getNextPage() != null) {
                    deltaPage = deltaPage.getNextPage().buildRequest().get();
                } else {
                    break;
                }
            }

            log.info("Initial sync completed. Retrieved {} messages for: {}",
                    allMessages.size(), userEmail);
            return new DeltaResult(allMessages, deltaLink);

        } catch (Exception e) {
            log.error("Error during initial sync for {}: {}", userEmail, e.getMessage(), e);
            throw new GraphSyncException("Failed to perform initial sync", e);
        }
    }

    @CircuitBreaker(name = "graphApi", fallbackMethod = "performDeltaSyncFallback")
    @Retry(name = "graphApi")
    public DeltaResult performDeltaSync(String deltaLink) {
        log.info("Performing delta sync with delta link");

        List<Message> changedMessages = new ArrayList<>();
        String newDeltaLink = null;

        try {
            MessageDeltaCollectionPage deltaPage = graphClient
                    .customRequest(deltaLink, MessageDeltaCollectionPage.class)
                    .buildRequest()
                    .get();

            while (deltaPage != null) {
                List<Message> messages = deltaPage.getCurrentPage();
                changedMessages.addAll(messages);
                log.debug("Fetched {} changed messages in this page", messages.size());

                if (deltaPage.deltaLink() != null) {
                    newDeltaLink = deltaPage.deltaLink();
                    break;
                }

                if (deltaPage.getNextPage() != null) {
                    deltaPage = deltaPage.getNextPage().buildRequest().get();
                } else {
                    break;
                }
            }

            log.info("Delta sync completed. Retrieved {} changed messages",
                    changedMessages.size());
            return new DeltaResult(changedMessages, newDeltaLink);

        } catch (Exception e) {
            log.error("Error during delta sync: {}", e.getMessage(), e);
            throw new GraphSyncException("Failed to perform delta sync", e);
        }
    }

    // Fallback methods
    private Subscription createSubscriptionFallback(String userEmail, Exception e) {
        log.error("Failed to create subscription for {} after retries: {}",
                userEmail, e.getMessage());
        throw new GraphSyncException("Subscription creation failed", e);
    }

    private DeltaResult performInitialSyncFallback(String userEmail, Exception e) {
        log.error("Failed to perform initial sync for {} after retries: {}",
                userEmail, e.getMessage());
        throw new GraphSyncException("Initial sync failed", e);
    }

    private DeltaResult performDeltaSyncFallback(String deltaLink, Exception e) {
        log.error("Failed to perform delta sync after retries: {}", e.getMessage());
        throw new GraphSyncException("Delta sync failed", e);
    }

    // Result wrapper
    public record DeltaResult(List<Message> messages, String deltaLink) {}

    // Custom exception
    public static class GraphSyncException extends RuntimeException {
        public GraphSyncException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}