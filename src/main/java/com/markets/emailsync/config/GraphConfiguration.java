package com.markets.emailsync.config;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.requests.GraphServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class GraphConfiguration {

    private final MicrosoftGraphProperties properties;

    public GraphConfiguration(MicrosoftGraphProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ClientSecretCredential clientSecretCredential() {
        log.info("Configuring Azure AD authentication for tenant: {}", properties.getTenantId());

        return new ClientSecretCredentialBuilder()
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .tenantId(properties.getTenantId())
                .build();
    }

    @Bean
    public GraphServiceClient<okhttp3.Request> graphServiceClient(
            ClientSecretCredential credential) {

        log.info("Initializing Microsoft Graph Service Client");

        TokenCredentialAuthProvider authProvider = new TokenCredentialAuthProvider(
                List.of(properties.getScopes().toArray(new String[0])),
                credential
        );

        return GraphServiceClient.builder()
                .authenticationProvider(authProvider)
                .buildClient();
    }
}
