# Mail Service Configuration Guide

## Option 1: MailHog (Recommended for Testing) ✅

MailHog is a local email testing tool that doesn't require real email credentials.

### Setup with Docker Compose

MailHog is already configured in `docker-compose.yml`. Just start it:

```bash
docker-compose up -d mailhog
```

### Configuration

**For Docker (already configured):**
- Host: `mailhog` (service name in docker-compose)
- Port: `1025`
- Username: (empty - not required)
- Password: (empty - not required)
- Auth: `false`

**For Local Development:**
- Host: `localhost`
- Port: `1025`
- Username: (empty)
- Password: (empty)
- Auth: `false`

### View Emails

Open http://localhost:8025 in your browser to see all emails sent by the notification service.

### Environment Variables (Docker)

No environment variables needed - MailHog is already configured in docker-compose.yml.

---

## Option 2: Gmail SMTP (For Production/Real Emails)

### Step 1: Enable Gmail App Password

1. **Enable 2-Step Verification**:
   - Go to https://myaccount.google.com/security
   - Enable "2-Step Verification"

2. **Generate App Password**:
   - Go to https://myaccount.google.com/apppasswords
   - Select "Mail" and "Other (Custom name)"
   - Enter name: "Fund Transfer Service"
   - Click "Generate"
   - Copy the 16-character password (e.g., `abcd efgh ijkl mnop`)

### Step 2: Configure Environment Variables

#### For Docker Compose

Update `docker-compose.yml`:

```yaml
notification-service:
  environment:
    SPRING_MAIL_HOST: smtp.gmail.com
    SPRING_MAIL_PORT: "587"
    SPRING_MAIL_USERNAME: "sudhasvmr@gmail.com"
    SPRING_MAIL_PASSWORD: "password"
    SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH: "true"
    SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE: "true"
    SPRING_MAIL_FROM: sudhasvmr@gmail.com
```

#### For Local Development

Create `.env` file in project root:

```bash
MAIL_USERNAME=sudhasvmr@gmail.com
MAIL_PASSWORD=password
MAIL_FROM=sudhasvmr@gmail.com
```

Or set environment variables:

**Windows PowerShell:**
```powershell
$env:MAIL_USERNAME="sudhasvmr@gmail.com"
$env:MAIL_PASSWORD="password"
$env:MAIL_FROM="sudhasvmr@gmail.com"
```

**Linux/Mac:**
```bash
export MAIL_USERNAME="sudhasvmr@gmail.com"
export MAIL_PASSWORD="password"
export MAIL_FROM="sudhasvmr@gmail.com"
```

### Step 3: Update application.yml (if needed)

The current configuration already supports Gmail. Just set the environment variables.

---

## Option 3: Other SMTP Providers

### Outlook/Hotmail

```yaml
SPRING_MAIL_HOST: smtp-mail.outlook.com
SPRING_MAIL_PORT: "587"
SPRING_MAIL_USERNAME: your-email@outlook.com
SPRING_MAIL_PASSWORD: your-password
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH: "true"
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE: "true"
```

### Yahoo Mail

```yaml
SPRING_MAIL_HOST: smtp.mail.yahoo.com
SPRING_MAIL_PORT: "587"
SPRING_MAIL_USERNAME: your-email@yahoo.com
SPRING_MAIL_PASSWORD: your-app-password
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH: "true"
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE: "true"
```

---

## Quick Test Configuration

### Test with MailHog (Easiest)

1. **Start MailHog**:
   ```bash
   docker-compose up -d mailhog
   ```

2. **Start notification service** (already configured for MailHog):
   ```bash
   docker-compose up -d notification-service
   ```

3. **Send test email**:
   ```bash
   curl -X POST http://localhost:8083/notifications/transaction-completed \
     -H "Content-Type: application/json" \
     -d '{
       "toEmail": "sudhasvmr@example.com",
       "transactionRef": "TXN-TEST-001",
       "fromAccount": "ACC1001",
       "toAccount": "ACC1002",
       "amount": "100.00"
     }'
   ```

4. **View email in MailHog**: http://localhost:8025

### Test with Gmail

1. **Set environment variables**:
   ```bash
   export MAIL_USERNAME="sudhasvmr@gmail.com"
   export MAIL_PASSWORD="password"
   ```

2. **Update docker-compose.yml** or set environment variables

3. **Restart service**:
   ```bash
   docker-compose restart notification-service
   ```

4. **Send test email** (same as above)

5. **Check your Gmail inbox** for the email

---

## Current Configuration

The `application.yml` supports both MailHog and Gmail:

```yaml
spring:
  mail:
    host: ${SPRING_MAIL_HOST:${MAIL_HOST:smtp.gmail.com}}
    port: ${SPRING_MAIL_PORT:${MAIL_PORT:587}}
    username: ${SPRING_MAIL_USERNAME:${MAIL_USERNAME:}}
    password: ${SPRING_MAIL_PASSWORD:${MAIL_PASSWORD:}}
    properties:
      mail:
        smtp:
          auth: ${SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH:${MAIL_SMTP_AUTH:true}}
          starttls:
            enable: ${SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE:${MAIL_SMTP_STARTTLS_ENABLE:true}}
```

### Default Behavior

- **Without environment variables**: Uses Gmail defaults (will fail without credentials)
- **With MailHog in Docker**: Automatically uses MailHog (configured in docker-compose.yml)
- **With environment variables**: Uses your specified SMTP server

---

## Security Best Practices

### ❌ Don't Do This

```yaml
# Never hardcode passwords in application.yml
password: mypassword123
```

### ✅ Do This

1. **Use environment variables**:
   ```bash
   export MAIL_PASSWORD="your-password"
   ```

2. **Use Docker secrets** (for production):
   ```yaml
   secrets:
     mail_password:
       external: true
   ```

3. **Use .env file** (add to .gitignore):
   ```bash
   MAIL_USERNAME=your-email@gmail.com
   MAIL_PASSWORD=your-password
   ```

---

## Troubleshooting

### Gmail Authentication Failed

1. **Check App Password**: Make sure you're using App Password, not regular password
2. **Check 2-Step Verification**: Must be enabled
3. **Check Username**: Use full email address (e.g., `user@gmail.com`)

### MailHog Not Working

1. **Check MailHog is running**:
   ```bash
   docker-compose ps mailhog
   ```

2. **Check MailHog logs**:
   ```bash
   docker-compose logs mailhog
   ```

3. **Verify port 1025 is available**:
   ```bash
   netstat -an | grep 1025
   ```

### Connection Timeout

1. **Check firewall settings**
2. **Verify SMTP server and port are correct**
3. **Check network connectivity**

---

## Example: Complete Gmail Setup

### 1. Get Gmail App Password

1. Go to https://myaccount.google.com/apppasswords
2. Generate password for "Mail"
3. Copy the password (remove spaces)

### 2. Update docker-compose.yml

```yaml
notification-service:
  environment:
    SPRING_MAIL_HOST: smtp.gmail.com
    SPRING_MAIL_PORT: "587"
    SPRING_MAIL_USERNAME: "sudhasvmr@gmail.com"
    SPRING_MAIL_PASSWORD: "password" 
    SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH: "true"
    SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE: "true"
    SPRING_MAIL_FROM: "sudhasvmr@gmail.com"
```

### 3. Restart Service

```bash
docker-compose restart notification-service
```

### 4. Test

```bash
curl -X POST http://localhost:8083/notifications/transaction-completed \
  -H "Content-Type: application/json" \
  -d '{
    "toEmail": "sudhasvmr@gmail.com",
    "transactionRef": "TXN-001",
    "fromAccount": "ACC1001",
    "toAccount": "ACC1002",
    "amount": "100.00"
  }'
```

---

## Recommendation

**For Testing/Demo**: Use **MailHog** (already configured)
- No credentials needed
- View emails in web UI
- Fast and easy

**For Production**: Use **Gmail App Passwords** or dedicated SMTP service
- Real email delivery
- Requires credentials
- More setup required

