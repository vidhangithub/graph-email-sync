# Testing Guide for MS Graph Email Sync

## Prerequisites

### 1. Azure AD Application Setup

1. Go to [Azure Portal](https://portal.azure.com)
2. Navigate to **Azure Active Directory** → **App registrations** → **New registration**
3. Register application:
    - Name: `Email Sync Service`
    - Supported account types: `Accounts in this organizational directory only`
    - Redirect URI: Leave empty for daemon app
4. After registration, note down:
    - **Application (client) ID**
    - **Directory (tenant) ID**
5. Create client secret:
    - Go to **Certificates & secrets** → **New client secret**
    - Note down the **Value** (not ID)
6. Grant API permissions:
    - Go to **API permissions** → **Add a permission** → **Microsoft Graph** → **Application permissions**
    - Add these permissions:
        - `Mail.Read` (Read mail in all mailboxes)
        - `Mail.ReadWrite` (Read and write mail in all mailboxes) - if you need write access
    - Click **Grant admin consent**

### 2. Database Setup

```bash
# Start PostgreSQL with Docker
docker run --name email-sync-postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=email_sync \
  -p 5432:5432 \
  -d postgres:15-alpine

# Verify database is running
docker ps
docker logs email-sync-postgres
```

### 3. Environment Configuration

Create `.env` file in project root:

```bash
# Azure AD Configuration
AZURE_TENANT_ID=your-tenant-id
AZURE_CLIENT_ID=your-client-id
AZURE_CLIENT_SECRET=your-client-secret

# Webhook Configuration (for local testing with ngrok)
WEBHOOK_URL=https://your-ngrok-url.ngrok.io/api/webhooks/graph
WEBHOOK_CLIENT_STATE=my-super-secret-state-string-change-this

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/email_sync
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```

### 4. Setup Ngrok for Webhook Testing

Microsoft Graph requires a public HTTPS URL for webhooks.

```bash
# Install ngrok
brew install ngrok  # macOS
# or download from https://ngrok.com

# Start ngrok tunnel
ngrok http 8080

# Copy the HTTPS URL (e.g., https://abc123.ngrok.io)
# Update WEBHOOK_URL in application.yml or .env file
```

## Build and Run

```bash
# Build the application
mvn clean install

# Run the application
mvn spring-boot:run

# Or with environment variables
AZURE_TENANT_ID=xxx \
AZURE_CLIENT_ID=yyy \
AZURE_CLIENT_SECRET=zzz \
WEBHOOK_URL=https://your-ngrok.ngrok.io/api/webhooks/graph \
mvn spring-boot:run
```

## Testing with Bruno

### Install Bruno

```bash
# macOS
brew install bruno

# Or download from https://www.usebruno.com/downloads
```

### Import Bruno Collection

1. Open Bruno
2. Click **Import Collection**
3. Select the `bruno-collection` folder
4. Configure environment variables in Bruno:
    - `baseUrl`: `http://localhost:8080`
    - `mailboxEmail`: `user@yourdomain.com`

## Test Scenarios

### Scenario 1: Initialize New Mailbox

**Expected Flow:**
1. Application starts
2. Mailboxes from config are auto-initialized
3. Initial sync retrieves historical emails
4. Subscription is created
5. Mailbox status becomes ACTIVE

**Bruno Tests:**

```
1. GET /api/admin/mailboxes
   - Verify mailbox exists
   - Check sync_status

2. GET /api/admin/mailboxes/{email}
   - Verify initial_sync_completed = true
   - Check subscription_id is not null
   - Verify subscription_expiration

3. GET /api/admin/stats
   - Check totalEmails > 0
```

### Scenario 2: Manual Sync Trigger

**Bruno Test:**

```
POST /api/admin/mailboxes/{email}/sync
```

**Verification:**
```sql
SELECT * FROM emails 
WHERE mailbox_id = (SELECT id FROM mailboxes WHERE email_address = 'user@domain.com')
ORDER BY received_date_time DESC;
```

### Scenario 3: Webhook Notification

**Steps:**
1. Send a test email to the monitored mailbox
2. Wait for webhook notification (should arrive within seconds)
3. Check webhook_notifications table
4. Verify email appears in emails table

**Bruno Tests:**

```
1. Send test email manually (via Outlook/Gmail)

2. Wait 10 seconds

3. GET /api/admin/mailboxes/{email}
   - Check last_sync_time is recent

4. GET /api/admin/stats
   - Verify pendingNotifications = 0 (processed)
   - Check totalEmails increased
```

**Verify in Database:**

```sql
-- Check webhook notifications
SELECT * FROM webhook_notifications 
ORDER BY received_at DESC 
LIMIT 10;

-- Check if email was synced
SELECT subject, sender_email, received_date_time, change_type
FROM emails 
ORDER BY created_at DESC 
LIMIT 10;
```

### Scenario 4: Subscription Renewal

**Manual Test:**

```
POST /api/admin/mailboxes/{email}/subscription/renew
```

**Check Response:**
```json
{
  "status": "success",
  "message": "Subscription renewed successfully"
}
```

**Verify:**
```sql
SELECT subscription_id, subscription_expiration, sync_status
FROM mailboxes 
WHERE email_address = 'user@domain.com';
```

### Scenario 5: Error Recovery

**Test subscription expiration:**

1. Manually expire subscription in database:
```sql
UPDATE mailboxes 
SET subscription_expiration = NOW() - INTERVAL '1 hour'
WHERE email_address = 'user@domain.com';
```

2. Wait for scheduled renewal job (runs every hour)
3. Check logs for renewal attempt
4. Verify subscription was renewed

**Bruno Test:**
```
GET /api/admin/mailboxes/{email}
```

Verify `subscription_expiration` is in the future.

### Scenario 6: Reinitialization

**When to use:** If mailbox gets into a bad state

**Bruno Test:**
```
POST /api/admin/mailboxes/{email}/reinitialize
```

**Expected:**
- Old subscription deleted
- Delta link cleared
- Initial sync performed again
- New subscription created
- Status returns to ACTIVE

## Database Verification Queries

### Check Mailbox Status
```sql
SELECT 
    email_address,
    sync_status,
    initial_sync_completed,
    last_sync_time,
    subscription_expiration,
    error_message
FROM mailboxes;
```

### Check Recent Emails
```sql
SELECT 
    m.email_address,
    e.subject,
    e.sender_email,
    e.received_date_time,
    e.change_type,
    e.created_at
FROM emails e
JOIN mailboxes m ON e.mailbox_id = m.id
ORDER BY e.created_at DESC
LIMIT 20;
```

### Check Webhook Notifications
```sql
SELECT 
    subscription_id,
    change_type,
    processed,
    processing_error,
    retry_count,
    received_at,
    processed_at
FROM webhook_notifications
ORDER BY received_at DESC
LIMIT 20;
```

### Email Statistics by Mailbox
```sql
SELECT 
    m.email_address,
    COUNT(e.id) as total_emails,
    COUNT(CASE WHEN e.change_type = 'CREATED' THEN 1 END) as created,
    COUNT(CASE WHEN e.change_type = 'UPDATED' THEN 1 END) as updated,
    COUNT(CASE WHEN e.is_deleted THEN 1 END) as deleted
FROM mailboxes m
LEFT JOIN emails e ON e.mailbox_id = m.id
GROUP BY m.email_address;
```

## Common Issues and Solutions

### Issue 1: Webhook Validation Fails

**Symptoms:** Subscription creation fails

**Solution:**
- Ensure ngrok is running
- Check WEBHOOK_URL in config matches ngrok URL exactly
- Verify webhook endpoint is accessible: `curl https://your-ngrok.ngrok.io/api/webhooks/graph/health`

### Issue 2: No Emails Syncing

**Check:**
1. Initial sync completed?
   ```sql
   SELECT email_address, initial_sync_completed FROM mailboxes;
   ```

2. Delta link exists?
   ```sql
   SELECT email_address, delta_link FROM mailboxes;
   ```

3. Check application logs for errors

4. Manually trigger sync:
   ```
   POST /api/admin/mailboxes/{email}/sync
   ```

### Issue 3: Subscription Expired

**Solution:**
```
POST /api/admin/mailboxes/{email}/subscription/renew
```

Or wait for automatic renewal (runs every hour).

### Issue 4: Webhook Notifications Not Processing

**Check:**
1. Webhook notifications table:
   ```sql
   SELECT * FROM webhook_notifications WHERE processed = false;
   ```

2. Check retry_count - if > 5, manual intervention needed

3. Check processing_error for details

**Solution:**
- Fix the underlying issue
- Manually trigger sync to catch up:
  ```
  POST /api/admin/mailboxes/{email}/sync
  ```

## Monitoring Endpoints

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

### Prometheus Metrics
```bash
curl http://localhost:8080/actuator/prometheus
```

## Load Testing

### Apache Bench (Simple)
```bash
# Test webhook endpoint
ab -n 1000 -c 10 http://localhost:8080/api/webhooks/graph/health
```

### JMeter Test Plan

Create test plan to simulate:
1. Multiple concurrent webhook notifications
2. Concurrent manual sync requests
3. Admin API load

## Automated Tests

### Run Unit Tests
```bash
mvn test
```

### Run Integration Tests
```bash
mvn verify
```

### Test Coverage
```bash
mvn jacoco:report
# Report available at: target/site/jacoco/index.html
```

## Performance Benchmarks

Expected performance metrics:

- **Initial Sync:** 1000 emails in ~30 seconds
- **Delta Sync:** 100 changes in ~5 seconds
- **Webhook Processing:** < 1 second per notification
- **API Response Time:** < 100ms (95th percentile)

## Troubleshooting Commands

```bash
# Check application logs
tail -f logs/graph-email-sync.log

# Check database connections
docker exec -it email-sync-postgres psql -U postgres -d email_sync -c "SELECT count(*) FROM pg_stat_activity;"

# Restart application with debug logging
LOGGING_LEVEL_COM_MARKETS=DEBUG mvn spring-boot:run

# Check ngrok status
curl http://localhost:4040/api/tunnels
```