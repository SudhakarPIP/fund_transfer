# Quick Mail Setup Guide

## 🚀 Quick Start: Use MailHog (Recommended for Testing)

MailHog is already configured! Just start it:

```bash
docker-compose up -d mailhog notification-service
```

Then view emails at: **http://localhost:8025**

No username/password needed! ✅

---

## 📧 Use Gmail SMTP (For Real Emails)

### Step 1: Get Gmail App Password

1. Go to: https://myaccount.google.com/apppasswords
2. Sign in with your Gmail account
3. Select "Mail" → "Other (Custom name)"
4. Name it: "Fund Transfer Service"
5. Click "Generate"
6. **Copy the 16-character password** (e.g., `abcd efgh ijkl mnop`)

### Step 2: Set Environment Variables

**Option A: Update docker-compose.yml**

Edit `docker-compose.yml` and update the `notification-service` environment:

```yaml
notification-service:
  environment:
    SPRING_MAIL_HOST: smtp.gmail.com
    SPRING_MAIL_PORT: "587"
    SPRING_MAIL_USERNAME: "sudhasvmr@gmail.com"        # Your Gmail address
    SPRING_MAIL_PASSWORD: "password"           # 16-char app password (no spaces)
    SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH: "true"
    SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE: "true"
    SPRING_MAIL_FROM: "sudhasvmr@gmail.com"
```

**Option B: Use Environment Variables**

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

### Step 3: Restart Service

```bash
docker-compose restart notification-service
```

### Step 4: Test

```bash
curl -X POST http://localhost:8083/notifications/transaction-completed \
  -H "Content-Type: application/json" \
  -d '{
    "toEmail": "test@example.com",
    "transactionRef": "TXN-001",
    "fromAccount": "ACC1001",
    "toAccount": "ACC1002",
    "amount": "100.00"
  }'
```

Check your Gmail inbox for the email!

---

## 📝 Example Values

### For Testing (MailHog)
```
Username: (empty)
Password: (empty)
Host: mailhog (in Docker) or localhost (local)
Port: 1025
```

### For Gmail
```
Username: your-email@gmail.com
Password: abcd efgh ijkl mnop  (16-char app password, remove spaces)
Host: smtp.gmail.com
Port: 587
```

### For Outlook
```
Username: your-email@outlook.com
Password: your-password
Host: smtp-mail.outlook.com
Port: 587
```

---

## ⚠️ Important Notes

1. **Gmail requires App Password**, not your regular password
2. **Enable 2-Step Verification** first (required for App Passwords)
3. **Remove spaces** from the App Password when using it
4. **Never commit passwords** to Git - use environment variables

---

## 🔍 Verify Configuration

Check if mail is configured correctly:

```bash
# Check notification service logs
docker-compose logs notification-service | grep -i mail

# Check health endpoint
curl http://localhost:8083/actuator/health
```

---

## 🎯 Recommendation

- **For Demo/Testing**: Use **MailHog** (no setup needed)
- **For Production**: Use **Gmail App Passwords** or dedicated SMTP service

