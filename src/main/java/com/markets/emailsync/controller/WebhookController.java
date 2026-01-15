package com.markets.emailsync.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.markets.emailsync.config.MicrosoftGraphProperties;
import com.markets.emailsync.service.WebhookProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookProcessingService webhookProcessingService;
    private final MicrosoftGraphProperties properties;
    private final ObjectMapper objectMapper;

    public WebhookController(
            WebhookProcessingService webhookProcessingService,
            MicrosoftGraphProperties properties,
            ObjectMapper objectMapper) {
        this.webhookProcessingService = webhookProcessingService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Webhook validation endpoint (GET request from Microsoft Graph)
     */
    @GetMapping("/graph")
    public ResponseEntity<String> validateWebhook(
            @RequestParam(value = "validationToken", required = false) String validationToken) {

        if (validationToken != null && !validationToken.isBlank()) {
            log.info("Webhook validation request received");
            // Microsoft Graph sends validation token, we must echo it back
            return ResponseEntity.ok()
                    .header("Content-Type", "text/plain")
                    .body(validationToken);
        }

        log.warn("Webhook validation request without token");
        return ResponseEntity.badRequest().body("Missing validation token");
    }

    /**
     * Webhook notification endpoint (POST request from Microsoft Graph)
     */
    @PostMapping("/graph")
    public ResponseEntity<Void> receiveNotification(@RequestBody String payload) {
        log.info("Webhook notification received");
        log.debug("Notification payload: {}", payload);

        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            JsonNode valueArray = rootNode.get("value");

            if (valueArray == null || !valueArray.isArray()) {
                log.error("Invalid webhook payload structure");
                return ResponseEntity.badRequest().build();
            }

            for (JsonNode notification : valueArray) {
                String subscriptionId = notification.get("subscriptionId").asText();
                String changeType = notification.get("changeType").asText();
                String resource = notification.get("resource").asText();
                String clientState = notification.has("clientState") ?
                        notification.get("clientState").asText() : null;

                // Validate client state
                if (!validateClientState(clientState)) {
                    log.error("Invalid client state in webhook notification");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                }

                // Process notification asynchronously
                webhookProcessingService.processNotification(
                        subscriptionId, changeType, resource, clientState, payload);
            }

            // Always return 202 Accepted immediately
            return ResponseEntity.accepted().build();

        } catch (Exception e) {
            log.error("Error processing webhook notification: {}", e.getMessage(), e);
            // Still return 202 to prevent Microsoft from disabling subscription
            return ResponseEntity.accepted().build();
        }
    }

    private boolean validateClientState(String clientState) {
        String expectedState = properties.getSubscription().getClientState();
        return expectedState != null && expectedState.equals(clientState);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "webhook-receiver"
        ));
    }
}