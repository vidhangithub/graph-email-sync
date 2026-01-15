# Microsoft Graph Email Delta Sync Service

A production-grade Spring Boot application for synchronizing emails from Microsoft 365 mailboxes using Microsoft Graph API delta queries and webhooks.

## 🎯 Features

- **Delta Query Synchronization**: Efficiently sync only changed emails using Microsoft Graph delta queries
- **Real-time Updates**: Webhook subscriptions for instant email change notifications
- **Never Miss an Email**: Persistent delta links ensure reliable synchronization
- **Multi-Mailbox Support**: Monitor multiple mailboxes simultaneously
- **Automatic Recovery**: Circuit breakers, retries, and automatic subscription renewal
- **Production Ready**: Comprehensive logging, metrics, health checks, and monitoring
- **Idempotent Processing**: Handles duplicate webhook notifications gracefully
- **Database Persistence**: Full email metadata and content storage with PostgreSQL

## 🏗️ Architecture

```
┌─────────────┐
│  Microsoft  │
│  Graph API  │
└──────┬──────┘
       │
       │ Delta Queries
       ▼
┌─────────────────────────────────────┐
│     Spring Boot Application         │
│                                     │
│  ┌───────────────────────────────┐ │
│  │   GraphService                │ │
│  │   - Delta Queries             │ │
│  │   - Subscription Management   │ │
│  └───────────────────────────────┘ │
│                                     │
│  ┌───────────────────────────────┐ │
│  │   EmailSyncService            │ │
│  │   - Process Messages          │ │
│  │   - Update Database           │ │
│  └───────────────────────────────┘ │
│                                     │
│  ┌───────────────────────────────┐ │
│  │   WebhookController           │ │
│  │   - Receive Notifications     │ │
│  │   - Trigger Delta Sync        │ │
│  └───────────────────────────────┘ │
└─────────────────────────────────────┘
       │
       ▼
┌─────────────────┐
│   PostgreSQL    │
│   - Mailboxes   │
│   - Emails      │
│   - Webhooks    │
└─────────────────┘
```

## 📋 Prerequisites

- Java 17 or higher
- Maven 3.8+
- PostgreSQL 15+
- Azure AD application with Microsoft Graph permissions
- Public HTTPS endpoint for webhooks (ngrok for local development)

## 🚀 Quick Start

### 1. Clone and Configure

```bash
git clone <repository-url>
cd graph-email-sync
```

### 2. Set Up Azure AD Application

See [Azure AD Setup Guide](TESTING.md#1-azure-ad-application-setup)

Required Permissions:
- `Mail.Read` (Application permission)
- `Mail.ReadWrite` (Optional, for write operations)

### 3. Configure Application

Create `application-local.yml` or set environment variables:

```yaml
microsoft:
  graph:
    tenant-id: ${AZURE_TENANT_ID}
    client-id: ${AZURE_CLIENT_ID}
    client-secret: ${AZURE_CLIENT_SECRET}
    mailboxes:
      - user1@yourdomain.com
      - user2@yourdomain.com
    subscription:
      notification-url: ${WEBHOOK_URL}
      client-state: ${WEBHOOK_CLIENT_STATE}
```

### 4. Start Database

```bash
docker-compose up -d postgres
```

### 5. Run Application

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 📊 API Endpoints

### Admin APIs

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/mailboxes` | GET | List all mailboxes |
| `/api/admin/mailboxes/{email}` | GET | Get mailbox status |
| `/api/admin/mailboxes/{email}/initialize` | POST | Initialize mailbox |
| `/api/admin/mailboxes/{email}/sync` | POST | Trigger manual sync |
| `/api/admin/mailboxes/{email}/subscription/renew` | POST | Renew subscription |
| `/api/admin/mailboxes/{email}/reinitialize` | POST | Reinitialize mailbox |
| `/api/admin/stats` | GET | Get system statistics |

### Webhook APIs

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/webhooks/graph` | GET | Webhook validation |
| `/api/webhooks/graph` | POST | Receive notifications |
| `/api/webhooks/health` | GET | Webhook health check |

### Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health check |
| `/actuator/metrics` | Application metrics |
| `/actuator/prometheus` | Prometheus metrics |

## 🔄 How It Works

### Initial Synchronization

1. **Application Startup**: On startup, the application initializes all configured mailboxes
2. **Bootstrap Read**: Performs initial delta query to retrieve recent emails (last 7 days by default)
3. **Delta Link Storage**: Stores the delta link for future incremental syncs
4. **Subscription Creation**: Creates a webhook subscription for real-time updates

### Incremental Synchronization

1. **Webhook Notification**: Microsoft Graph sends notification when emails change
2. **Validation**: Application validates the client state
3. **Delta Query**: Fetches only the changed emails using the stored delta link
4. **Update Database**: Persists new/updated emails and updates delta link
5. **Acknowledgment**: Returns 202 Accepted to Microsoft Graph

### Reliability Features

- **Persistent Delta Links**: Never lose sync state
- **Automatic Subscription Renewal**: Renews subscriptions before expiration
- **Failed Notification Retry**: Automatically retries failed webhook processing
- **Circuit Breaker**: Prevents cascading failures
- **Idempotent Processing**: Handles duplicate notifications

## 🗄️ Database Schema

### Mailboxes Table

Stores mailbox configuration and sync state:

```sql
CREATE TABLE mailboxes (
    id BIGSERIAL PRIMARY KEY,
    email_address VARCHAR(255) UNIQUE NOT NULL,
    delta_link VARCHAR(2048),
    subscription_id VARCHAR(100),
    subscription_expiration TIMESTAMP,
    sync_status VARCHAR(50) NOT NULL,
    last_sync_time TIMESTAMP,
    initial_sync_completed BOOLEAN DEFAULT FALSE,
    error_message VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Emails Table

Stores email metadata and content:

```sql
CREATE TABLE emails (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(500) UNIQUE NOT NULL,
    mailbox_id BIGINT REFERENCES mailboxes(id),
    subject VARCHAR(1000),
    sender_email VARCHAR(255),
    received_date_time TIMESTAMP,
    body_content TEXT,
    change_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    -- ... additional fields
);
```

### Webhook Notifications Table

Audit log of webhook notifications:

```sql
CREATE TABLE webhook_notifications (
    id BIGSERIAL PRIMARY KEY,
    subscription_id VARCHAR(100) NOT NULL,
    change_type VARCHAR(50),
    processed BOOLEAN DEFAULT FALSE,
    processing_error VARCHAR(2000),
    retry_count INT DEFAULT 0,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 🧪 Testing

Comprehensive testing guide with Bruno API client: [TESTING.md](TESTING.md)

### Run Tests

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Test coverage report
mvn jacoco:report
```

### Bruno API Collection

Import `bruno-collection/bruno.json` into Bruno for interactive API testing.

## 📈 Monitoring

### Metrics

Application exposes Prometheus metrics:

```bash
curl http://localhost:8080/actuator/prometheus
```

Key metrics:
- `graph_api_calls_total` - Total Graph API calls
- `email_sync_duration_seconds` - Sync operation duration
- `webhook_notifications_total` - Webhook notifications received
- `subscription_renewals_total` - Subscription renewal count

### Health Checks

```bash
curl http://localhost:8080/actuator/health
```

Includes:
- Database connectivity
- Disk space
- Application status

### Logging

Logs are written to:
- Console (structured JSON in production)
- File: `logs/graph-email-sync.log`

Log levels configurable via:
```yaml
logging:
  level:
    com.markets: DEBUG
    com.microsoft.graph: INFO
```

## 🔧 Configuration

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `AZURE_TENANT_ID` | Azure AD tenant ID | Yes |
| `AZURE_CLIENT_ID` | Application client ID | Yes |
| `AZURE_CLIENT_SECRET` | Client secret | Yes |
| `WEBHOOK_URL` | Public webhook endpoint URL | Yes |
| `WEBHOOK_CLIENT_STATE` | Secret for webhook validation | Yes |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | Yes |
| `SPRING_DATASOURCE_USERNAME` | Database username | Yes |
| `SPRING_DATASOURCE_PASSWORD` | Database password | Yes |

### Application Properties

Key configuration options:

```yaml
microsoft:
  graph:
    delta:
      page-size: 50  # Emails per page
      initial-sync-days-back: 7  # Days of history
    subscription:
      expiration-hours: 72  # Subscription lifetime
      renewal-before-hours: 12  # Renew before expiration

resilience4j:
  circuitbreaker:
    instances:
      graphApi:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
```

## 🔐 Security

### Authentication

- Uses Azure AD client credentials flow
- Application permissions (daemon app)
- Secrets stored in environment variables or Azure Key Vault

### Webhook Security

- HTTPS required for production
- Client state validation
- Idempotent processing prevents replay attacks

### Database Security

- Connection pooling with HikariCP
- Prepared statements prevent SQL injection
- Encrypted connections in production

## 🚢 Deployment

### Docker

```bash
# Build image
docker build -t graph-email-sync:latest .

# Run container
docker run -d \
  -e AZURE_TENANT_ID=xxx \
  -e AZURE_CLIENT_ID=yyy \
  -e AZURE_CLIENT_SECRET=zzz \
  -p 8080:8080 \
  graph-email-sync:latest
```

### Kubernetes

See `k8s/` directory for Kubernetes manifests.

### Azure App Service

Deploy as a JAR file with application settings configured.

## 📝 Best Practices

### Mailbox Configuration

- Monitor only necessary mailboxes
- Use shared mailboxes for team inboxes
- Consider email volume and API limits

### Delta Query

- Default 7-day lookback is usually sufficient
- Increase for initial historical sync if needed
- Store delta links securely

### Subscription Management

- Monitor subscription expiration
- Auto-renewal runs hourly by default
- Implement alerting for expired subscriptions

### Error Handling

- Check application logs regularly
- Monitor failed webhook processing
- Set up alerts for sustained errors

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📄 License

[Your License Here]

## 🆘 Support

For issues and questions:
- Check [TESTING.md](TESTING.md) for troubleshooting
- Review application logs
- Consult Microsoft Graph API documentation

## 🔗 Resources

- [Microsoft Graph API Documentation](https://docs.microsoft.com/en-us/graph/api/overview)
- [Delta Query Documentation](https://docs.microsoft.com/en-us/graph/delta-query-overview)
- [Webhooks Documentation](https://docs.microsoft.com/en-us/graph/webhooks)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)