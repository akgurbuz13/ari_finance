# ARI MVP — Go-Live Deployment Guide

> **Goal**: Get `arifinance.co` fully live with working signup, login, transfers, admin console, and blockchain settlement — all on free-tier services.
>
> **Total cost**: $0/month (+ ~$12/year domain renewal)
>
> **Time estimate**: ~2-3 hours following this guide
>
> **Branch**: `deploy/mvp-live`

---

## What You'll Set Up

```
arifinance.co (Dynadot DNS)
│
├── app.arifinance.co      → Vercel (Next.js Web App)        [FREE]
├── admin.arifinance.co    → Vercel (Next.js Admin Console)   [FREE]
├── api.arifinance.co      → Render.com (Core Banking API)    [FREE]
│
├── Blockchain Service     → Render.com (2nd web service)     [FREE]
├── PostgreSQL             → Neon (shared by both backends)   [FREE]
├── Redis                  → Upstash (shared by both)         [FREE]
│
└── Avalanche L1s (Fuji testnet)
    ├── TR L1 (ariTRY)     → Builder Console managed node     [FREE]
    └── EU L1 (ariEUR)     → Builder Console managed node     [FREE]
```

### Free Tier Limits to Know

| Service | Limit | Impact |
|---------|-------|--------|
| Render | 512MB RAM, sleeps after 15min idle | ~30-45s cold start on first request |
| Neon | 0.5GB storage, 5 max connections | Both services share the pool |
| Upstash | 10K commands/day, 256MB | Sufficient for MVP demo traffic |
| Builder Console | Nodes auto-shutdown after ~3 days | Redeploy before demos |

---

## Before You Begin

Make sure you have the `deploy/mvp-live` branch pushed:

```bash
git checkout deploy/mvp-live
git log --oneline -3  # should show deployment commit
```

You'll need accounts on these services (all free, all support GitHub OAuth):
1. **Neon** (neon.tech) — PostgreSQL
2. **Upstash** (upstash.com) — Redis
3. **Render** (render.com) — Backend hosting
4. **Vercel** (vercel.com) — Frontend hosting
5. **Dynadot** (dynadot.com) — You already have this for `arifinance.co`
6. **Avalanche Builder Console** (build.avax.network) — Blockchain L1s

Generate secrets now — you'll need them later:

```bash
# Run these in terminal and save the output somewhere safe
echo "JWT_SECRET=$(openssl rand -base64 48)"
echo "INTERNAL_API_KEY=$(openssl rand -hex 16)"
```

Save those two values. They'll be used in Phase 3.

---

## Phase 1: Set Up PostgreSQL (Neon)

### Step 1: Create Account

1. Open https://neon.tech
2. Click **Sign Up** → **Continue with GitHub**
3. Authorize Neon to access your GitHub account

### Step 2: Create Project

1. Click **New Project**
2. **Project name**: `ari-mvp`
3. **Region**: Select **EU (Frankfurt)** / `eu-central-1` (closest to target users)
4. **PostgreSQL version**: Leave default (16)
5. Click **Create Project**

### Step 3: Get Connection Details

After creation, Neon shows you a connection string like:

```
postgresql://neondb_owner:AbCdEf123456@ep-cool-sunset-123456.eu-central-1.aws.neon.tech/neondb?sslmode=require
```

Extract and save these values:

| Variable | Value | Where to find it |
|----------|-------|-------------------|
| `DB_HOST` | `ep-cool-sunset-123456.eu-central-1.aws.neon.tech` | Between `@` and `/neondb` |
| `DB_NAME` | `neondb` | After the last `/` before `?` |
| `DB_USER` | `neondb_owner` | Between `://` and `:` |
| `DB_PASSWORD` | `AbCdEf123456` | Between first `:` and `@` |

> **Tip**: Click the "eye" icon on the Neon dashboard to reveal the password. You can also click "Copy" on the connection string.

### Step 4: Verify Connection (Optional)

```bash
psql "postgresql://neondb_owner:YOUR_PASSWORD@YOUR_HOST/neondb?sslmode=require"
# If it connects, type \q to exit
```

> You don't need to create any tables. Flyway (in our backend) will automatically run all 19 migrations on first startup.

---

## Phase 2: Set Up Redis (Upstash)

### Step 1: Create Account

1. Open https://console.upstash.com
2. Click **Sign Up** → **Sign up with GitHub**
3. Authorize Upstash

### Step 2: Create Redis Database

1. Click **Create Database**
2. **Name**: `ari-mvp`
3. **Type**: Regional
4. **Region**: Select **EU-Central-1 (Frankfurt)**
5. **Eviction**: Leave unchecked
6. Click **Create**

### Step 3: Get Connection Details

On the database details page, find the **REST API** section or **Details** tab:

| Variable | Value | Where to find it |
|----------|-------|-------------------|
| `REDIS_HOST` | `eu1-xxxxx-xxxxx.upstash.io` | "Endpoint" field |
| `REDIS_PORT` | `6379` | "Port" field (always 6379) |
| `REDIS_PASSWORD` | `AaBbCcDd...` | "Password" field (click eye icon to reveal) |

> **Important**: Upstash requires TLS. Our `application-prod.yml` already has `spring.data.redis.ssl.enabled: true` configured.

---

## Phase 3: Deploy Backend Services (Render)

### Step 1: Create Render Account

1. Open https://render.com
2. Click **Get Started for Free** → **GitHub**
3. Authorize Render to access your GitHub account

### Step 2: Connect Your Repository

1. After sign-up, go to **Dashboard**
2. You'll connect the repo when creating each service (next steps)

---

### Step 3: Deploy Core Banking API

This is the main backend service that handles auth, accounts, transfers, and serves the API.

1. Click **New** → **Web Service**
2. Click **Build and deploy from a Git repository** → **Next**
3. Connect your GitHub repo `akgurbuz13/ova_finance`
   - If you don't see it, click **Configure account** on GitHub to grant access
4. Click **Connect** next to `ova_finance`

#### Configure the service:

| Setting | Value |
|---------|-------|
| **Name** | `ari-core-banking` |
| **Region** | Frankfurt (EU Central) |
| **Branch** | `deploy/mvp-live` |
| **Runtime** | Docker |
| **Dockerfile Path** | `./core-banking/Dockerfile` |
| **Docker Context Directory** | `.` |
| **Instance Type** | Free |

#### Set Environment Variables:

Scroll down to **Environment Variables** and add each one. Click **Add Environment Variable** for each row:

| Key | Value |
|-----|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_HOST` | *(your Neon host from Phase 1, e.g. `ep-cool-sunset-123456.eu-central-1.aws.neon.tech`)* |
| `DB_PORT` | `5432` |
| `DB_NAME` | `neondb` |
| `DB_USER` | *(your Neon username from Phase 1)* |
| `DB_PASSWORD` | *(your Neon password from Phase 1)* |
| `REDIS_HOST` | *(your Upstash host from Phase 2, e.g. `eu1-xxxxx.upstash.io`)* |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | *(your Upstash password from Phase 2)* |
| `JWT_SECRET` | *(the value you generated in "Before You Begin")* |
| `INTERNAL_API_KEY` | *(the value you generated in "Before You Begin")* |
| `CORS_ALLOWED_ORIGINS` | `https://app.arifinance.co,https://admin.arifinance.co` |
| `ARI_REGION` | `TR` |
| `BLOCKCHAIN_SERVICE_URL` | `http://localhost:8081` |
| `JAVA_OPTS` | `-Xmx384m -XX:+UseSerialGC` |

> **Note**: We'll update `BLOCKCHAIN_SERVICE_URL` after the blockchain service is deployed. Set it to `http://localhost:8081` for now.

#### Advanced Settings:

Scroll down and click **Advanced**:

| Setting | Value |
|---------|-------|
| **Health Check Path** | `/actuator/health` |
| **Docker Command** | *(leave empty — Dockerfile ENTRYPOINT handles it)* |

5. Click **Create Web Service**

#### Wait for Build:

- The first build takes **5-10 minutes** (downloads Gradle, JDK, compiles Kotlin)
- Watch the **Logs** tab for progress
- You'll see `Started OvaPlatformApplication` when it's ready
- Render assigns a URL like `ari-core-banking.onrender.com`

#### Save the Render URL:

Note your core banking URL: `https://ari-core-banking.onrender.com`
(Your actual name may differ — check the dashboard)

#### Verify:

```bash
curl https://ari-core-banking.onrender.com/actuator/health
# Expected: {"status":"UP"}
```

> If you get a timeout, the service may be starting up. Wait 1-2 minutes and try again. First deploy can take up to 10 minutes.

---

### Step 4: Deploy Blockchain Service

1. Go back to Render Dashboard
2. Click **New** → **Web Service**
3. Connect the **same repo** (`ova_finance`)

#### Configure:

| Setting | Value |
|---------|-------|
| **Name** | `ari-blockchain-service` |
| **Region** | Frankfurt (EU Central) |
| **Branch** | `deploy/mvp-live` |
| **Runtime** | Docker |
| **Dockerfile Path** | `./blockchain-service/Dockerfile` |
| **Docker Context Directory** | `.` |
| **Instance Type** | Free |

#### Environment Variables:

| Key | Value |
|-----|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_HOST` | *(same Neon host)* |
| `DB_PORT` | `5432` |
| `DB_NAME` | `neondb` |
| `DB_USER` | *(same Neon user)* |
| `DB_PASSWORD` | *(same Neon password)* |
| `REDIS_HOST` | *(same Upstash host)* |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | *(same Upstash password)* |
| `INTERNAL_API_KEY` | *(same value as core-banking)* |
| `CORE_BANKING_URL` | `https://ari-core-banking.onrender.com` |
| `JAVA_OPTS` | `-Xmx384m -XX:+UseSerialGC` |

> **Blockchain contract addresses**: For now, leave the blockchain-specific env vars (`TR_L1_RPC_URL`, `TR_STABLECOIN_ADDRESS`, etc.) **unset**. The service will start without them — it just won't process blockchain operations until configured. You can add them after Phase 6 (Avalanche L1 setup).

#### Advanced Settings:

| Setting | Value |
|---------|-------|
| **Health Check Path** | `/actuator/health` |

4. Click **Create Web Service**
5. Wait for build and deployment (~5-10 minutes)

#### Save the URL:

Note your blockchain service URL: `https://ari-blockchain-service.onrender.com`

---

### Step 5: Cross-Link the Two Services

Now that both services have URLs, update the references:

1. Go to Render Dashboard → **ari-core-banking** → **Environment**
2. Find `BLOCKCHAIN_SERVICE_URL` and change it from `http://localhost:8081` to:
   ```
   https://ari-blockchain-service.onrender.com
   ```
3. Click **Save Changes** — the service will automatically redeploy

---

## Phase 4: Deploy Frontends (Vercel)

### Step 1: Create Vercel Account

1. Open https://vercel.com
2. Click **Sign Up** → **Continue with GitHub**
3. Authorize Vercel

---

### Step 2: Deploy Web App (`app.arifinance.co`)

1. From Vercel dashboard, click **Add New...** → **Project**
2. Click **Import** next to `ova_finance` repo
   - If you don't see it, click **Adjust GitHub App Permissions** and grant access

#### Configure:

| Setting | Value |
|---------|-------|
| **Project Name** | `ari-web` |
| **Framework Preset** | Next.js *(auto-detected)* |
| **Root Directory** | Click **Edit** → type `web` → click **Continue** |

#### Environment Variables:

Click **Environment Variables** and add:

| Key | Value |
|-----|-------|
| `NEXT_PUBLIC_API_URL` | `https://api.arifinance.co` |

> **Important**: We're using `api.arifinance.co` (the custom domain), not the Render URL. This will work once DNS is configured in Phase 5. During the gap between deploy and DNS setup, the app won't connect to the API.

#### Git Configuration:

Under **Git** section (if visible):
- **Branch**: `deploy/mvp-live`

3. Click **Deploy**
4. Wait ~1-2 minutes for the build

#### Verify:

Vercel gives you a URL like `ari-web.vercel.app`. Open it — you should see the ARI landing page. (API calls won't work yet until DNS is set up.)

---

### Step 3: Deploy Admin Console (`admin.arifinance.co`)

1. Go back to Vercel dashboard → **Add New...** → **Project**
2. **Import** the same `ova_finance` repo again (Vercel creates a separate project)

#### Configure:

| Setting | Value |
|---------|-------|
| **Project Name** | `ari-admin` |
| **Framework Preset** | Next.js |
| **Root Directory** | Click **Edit** → type `admin-console` → click **Continue** |

#### Environment Variables:

| Key | Value |
|-----|-------|
| `NEXT_PUBLIC_API_URL` | `https://api.arifinance.co` |
| `NEXT_PUBLIC_ADMIN_API_URL` | `https://api.arifinance.co/api/v1/admin` |

#### Git Configuration:

- **Branch**: `deploy/mvp-live`

3. Click **Deploy**
4. Wait ~1-2 minutes

---

## Phase 5: DNS Configuration (Dynadot)

This phase connects your custom domain `arifinance.co` to the services.

### Step 1: Log Into Dynadot

1. Open https://www.dynadot.com
2. Log in to your account
3. Go to **My Domains** → Click on `arifinance.co`
4. Click **DNS Settings** (or **Name Servers** → **Dynadot DNS**)

### Step 2: Add DNS Records

You need to add **3 CNAME records**. In the DNS records section:

#### Record 1: Web App

| Field | Value |
|-------|-------|
| **Record Type** | CNAME |
| **Subdomain/Host** | `app` |
| **Target/Value** | `cname.vercel-dns.com` |
| **TTL** | Default (or 300) |

#### Record 2: Admin Console

| Field | Value |
|-------|-------|
| **Record Type** | CNAME |
| **Subdomain/Host** | `admin` |
| **Target/Value** | `cname.vercel-dns.com` |
| **TTL** | Default (or 300) |

#### Record 3: API

| Field | Value |
|-------|-------|
| **Record Type** | CNAME |
| **Subdomain/Host** | `api` |
| **Target/Value** | `ari-core-banking.onrender.com` |
| **TTL** | Default (or 300) |

> **Important**: Replace `ari-core-banking.onrender.com` with your actual Render service hostname (check your Render dashboard).

5. Click **Save DNS** (or **Submit**)

### Step 3: Add Custom Domains on Vercel

DNS records alone aren't enough — you need to tell Vercel which domains to serve.

#### Web App:

1. Go to Vercel → **ari-web** project → **Settings** → **Domains**
2. Type `app.arifinance.co` → Click **Add**
3. Vercel will verify DNS. It may show:
   - **Valid Configuration** (green checkmark) — you're good!
   - **Invalid Configuration** — DNS hasn't propagated yet. Wait 5-10 minutes and click **Refresh**.
4. SSL certificate auto-provisions (takes 1-2 minutes)

#### Admin Console:

1. Go to Vercel → **ari-admin** project → **Settings** → **Domains**
2. Type `admin.arifinance.co` → Click **Add**
3. Wait for verification + SSL

### Step 4: Add Custom Domain on Render

1. Go to Render → **ari-core-banking** → **Settings**
2. Scroll to **Custom Domains**
3. Click **Add Custom Domain**
4. Type `api.arifinance.co` → Click **Save**
5. Render will verify the CNAME record. If it says "DNS not configured yet", wait a few minutes for propagation.
6. SSL certificate auto-provisions

### Step 5: Verify DNS Propagation

DNS changes can take 5-30 minutes to propagate. Check:

```bash
# Check each subdomain resolves
dig app.arifinance.co CNAME +short
# Expected: cname.vercel-dns.com.

dig admin.arifinance.co CNAME +short
# Expected: cname.vercel-dns.com.

dig api.arifinance.co CNAME +short
# Expected: ari-core-banking.onrender.com.
```

Or use https://dnschecker.org to check propagation worldwide.

---

## Phase 6: Avalanche L1 Setup (Optional for Initial Launch)

> **You can skip this phase** if you just want the web app, API, and admin console live. The backend works without blockchain — transfers are recorded in the ledger, and outbox events queue up. When L1s are configured later, the blockchain service will start processing them.

### Step 1: Create Builder Console Account

1. Open https://build.avax.network/login
2. Connect your wallet (MetaMask or similar)
3. Once logged in, go to the **Faucet** section
4. Request testnet AVAX (you'll need this for gas fees)

### Step 2: Create TR L1 Chain

1. Click **Create L1** (or **Subnets** → **Create**)
2. Configure your chain:

| Setting | Value |
|---------|-------|
| **Chain Name** | `ARI TR L1` |
| **Token Symbol** | `ariTRY` |
| **VM** | SubnetEVM |
| **Chain ID** | Note the auto-assigned ID → save as `TR_L1_CHAIN_ID` |

3. Click **Deploy to Fuji**
4. Set up a managed validator node when prompted
5. After deployment, go to your L1's detail page and note:
   - **RPC URL** → save as `TR_L1_RPC_URL` (looks like `https://subnets.avax.network/...`)
   - **Blockchain ID** → save as `TR_BLOCKCHAIN_ID` (a `0x...` hex string)

### Step 3: Create EU L1 Chain

Repeat the process:

| Setting | Value |
|---------|-------|
| **Chain Name** | `ARI EU L1` |
| **Token Symbol** | `ariEUR` |
| **Chain ID** | Note → save as `EU_L1_CHAIN_ID` |

Save the EU RPC URL and Blockchain ID similarly.

### Step 4: Deploy Smart Contracts

You need a deployer wallet with testnet AVAX on both L1s.

```bash
cd contracts
npm install

# Deploy to TR L1
DEPLOYER_PRIVATE_KEY=0x<your-wallet-private-key> \
TELEPORTER_ADDRESS=0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf \
BLOCKCHAIN_ID=<TR_BLOCKCHAIN_ID from Step 2> \
TR_L1_RPC_URL=<RPC URL from Step 2> \
TR_L1_CHAIN_ID=<Chain ID from Step 2> \
npx hardhat run scripts/deploy-fuji-l1s.ts --network ari-tr-testnet
```

The script outputs contract addresses. **Save them all**:

```
TR_STABLECOIN_ADDRESS=0x...
TR_TOKEN_HOME_ADDRESS=0x...
TR_TOKEN_REMOTE_ADDRESS=0x...
TR_BRIDGE_ADAPTER_ADDRESS=0x...
```

Now deploy to EU L1:

```bash
DEPLOYER_PRIVATE_KEY=0x<same-key> \
TELEPORTER_ADDRESS=0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf \
BLOCKCHAIN_ID=<EU_BLOCKCHAIN_ID from Step 3> \
EU_L1_RPC_URL=<RPC URL from Step 3> \
EU_L1_CHAIN_ID=<Chain ID from Step 3> \
npx hardhat run scripts/deploy-fuji-l1s.ts --network ari-eu-testnet
```

Save the EU contract addresses too.

### Step 5: Configure Bridge (Cross-Chain Registration)

```bash
npx hardhat run scripts/configure-bridge.ts
```

### Step 6: Update Blockchain Service Environment Variables

Go to Render → **ari-blockchain-service** → **Environment** and add:

| Key | Value |
|-----|-------|
| `TR_L1_RPC_URL` | *(from Step 2)* |
| `TR_L1_CHAIN_ID` | *(from Step 2)* |
| `TR_STABLECOIN_ADDRESS` | *(from Step 4 TR deployment)* |
| `EU_L1_RPC_URL` | *(from Step 3)* |
| `EU_L1_CHAIN_ID` | *(from Step 3)* |
| `EU_STABLECOIN_ADDRESS` | *(from Step 4 EU deployment)* |
| `TR_TOKEN_HOME_ADDRESS` | *(from Step 4)* |
| `EU_TOKEN_HOME_ADDRESS` | *(from Step 4)* |
| `TR_TOKEN_REMOTE_ADDRESS` | *(from Step 4)* |
| `EU_TOKEN_REMOTE_ADDRESS` | *(from Step 4)* |
| `TR_BRIDGE_ADAPTER_ADDRESS` | *(from Step 4)* |
| `EU_BRIDGE_ADAPTER_ADDRESS` | *(from Step 4)* |
| `TR_BLOCKCHAIN_ID` | *(from Step 2)* |
| `EU_BLOCKCHAIN_ID` | *(from Step 3)* |
| `WALLET_MASTER_KEY` | *(your deployer private key, without 0x prefix)* |
| `MINTER_PRIVATE_KEY` | *(same key)* |
| `ADMIN_PRIVATE_KEY` | *(same key)* |
| `BRIDGE_OPERATOR_PRIVATE_KEY` | *(same key)* |
| `RELAYER_PRIVATE_KEY` | *(same key)* |

Click **Save Changes** — the blockchain service will redeploy with L1 connectivity.

---

## Phase 7: Final Verification

### 7.1 API Health Check

```bash
curl https://api.arifinance.co/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

> If the service was sleeping, the first request takes 30-45 seconds. Be patient.

### 7.2 Web App

1. Open **https://app.arifinance.co**
2. You should see the ARI landing page
3. Click **Sign Up**
4. Register with:
   - Email: `test@example.com`
   - Phone: `+905551234567`
   - Password: `TestPassword123`
   - Region: `TR`
5. After signup, you'll be logged in and see the dashboard
6. You should see TRY and EUR accounts with 0.00 balances

### 7.3 Test Domestic Transfer

1. Sign up a **second user** (different email/phone)
2. From the first user's dashboard, initiate a transfer to the second user
3. Check that the transfer appears in transaction history

### 7.4 Test Cross-Border Transfer

1. From a user with a TRY account, initiate a cross-border transfer to a EUR account
2. You should see an FX quote (TRY → EUR conversion rate)
3. Confirm the transfer
4. Check that both accounts update with correct amounts

### 7.5 Admin Console

1. Open **https://admin.arifinance.co**
2. You need an admin user. Create one via API:

```bash
# First, sign up a user normally
curl -X POST https://api.arifinance.co/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@arifinance.co","phone":"+905559999999","password":"AdminPass123","region":"TR"}'
```

3. Promote to admin (requires direct DB access via Neon console):

```sql
-- Run this in Neon SQL Editor (Dashboard → SQL Editor)
UPDATE identity.users SET role = 'ADMIN' WHERE email = 'admin@arifinance.co';
```

4. Log into the admin console with admin credentials
5. You should see the user management dashboard

### 7.6 Test Password Reset

```bash
# Request reset token
curl -X POST https://api.arifinance.co/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com"}'

# Response includes resetToken (MVP mode — no email service)
# Use the token to reset:
curl -X POST https://api.arifinance.co/api/v1/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token":"<token-from-above>","newPassword":"NewPassword456"}'
```

### 7.7 Blockchain Settlement (Only After Phase 6)

If you completed the Avalanche L1 setup:

1. Make a transfer that generates an outbox event
2. Check blockchain service logs on Render for processing
3. Verify the mint transaction on the Fuji L1 explorer

---

## Optional: Keep Services Awake

Render free tier sleeps services after 15 minutes of inactivity. To prevent cold starts:

### UptimeRobot (Free)

1. Go to https://uptimerobot.com and sign up (free for 50 monitors)
2. Click **Add New Monitor**
3. Configure:

| Setting | Value |
|---------|-------|
| **Monitor Type** | HTTP(s) |
| **Friendly Name** | `ARI API` |
| **URL** | `https://api.arifinance.co/actuator/health` |
| **Monitoring Interval** | 5 minutes |

4. Click **Create Monitor**
5. Repeat for the blockchain service if desired:

| Setting | Value |
|---------|-------|
| **URL** | `https://ari-blockchain-service.onrender.com/actuator/health` |
| **Friendly Name** | `ARI Blockchain Service` |

> This pings your API every 5 minutes, preventing Render from putting it to sleep.

---

## Troubleshooting

### "Application error" on Render

1. Go to Render → Your service → **Logs** tab
2. Look for error messages. Common issues:
   - **`Connection refused` to database**: DB_HOST is wrong, or Neon is paused (check Neon dashboard)
   - **`FATAL: password authentication failed`**: DB_PASSWORD is wrong
   - **`OutOfMemoryError`**: JAVA_OPTS not set — add `-Xmx384m -XX:+UseSerialGC`
   - **Build timeout**: Render free tier has 15-minute build limit. Retry the deploy.

### Vercel shows 404

1. Check that **Root Directory** is correct (`web` for web app, `admin-console` for admin)
2. Check that the correct branch is selected (`deploy/mvp-live`)
3. Redeploy: Vercel → Project → **Deployments** → **Redeploy**

### API returns CORS error in browser

1. Check `CORS_ALLOWED_ORIGINS` on core-banking Render service
2. It must exactly match your frontend URLs:
   ```
   https://app.arifinance.co,https://admin.arifinance.co
   ```
3. No trailing slashes, no spaces, no `http://` (must be `https://`)

### DNS not resolving

1. Check Dynadot DNS settings are saved correctly
2. Use https://dnschecker.org to verify propagation
3. CNAME records can take up to 30 minutes to propagate
4. Make sure there are no conflicting A records for the same subdomains

### Database migrations failed

Check core-banking Render logs for Flyway errors. Common fix:
1. Go to Neon Dashboard → SQL Editor
2. Run: `SELECT * FROM public.flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;`
3. If a migration failed halfway, you may need to fix data and re-run

### Redis connection timeout

1. Verify `REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD` match Upstash dashboard
2. Upstash requires TLS — this is handled by `application-prod.yml`
3. Check Upstash dashboard → **Usage** to see if you've hit the 10K commands/day limit

### Builder Console nodes are down

Managed testnet nodes auto-shutdown after ~3 days.
1. Go to https://build.avax.network
2. Find your L1s and restart/redeploy the managed nodes
3. RPC URLs should remain the same
4. Contracts don't need redeployment — they persist on-chain

---

## Architecture Recap

After completing all phases, you'll have:

```
User → app.arifinance.co (Vercel)
         ↓ NEXT_PUBLIC_API_URL
       api.arifinance.co (Render: core-banking)
         ↓ BLOCKCHAIN_SERVICE_URL
       ari-blockchain-service.onrender.com (Render: blockchain-service)
         ↓ RPC calls
       Avalanche Fuji L1s (TR + EU)

Admin → admin.arifinance.co (Vercel)
         ↓ NEXT_PUBLIC_API_URL
       api.arifinance.co (Render: core-banking)

Both backends share:
  → Neon PostgreSQL (19 tables across 4 schemas)
  → Upstash Redis (caching, rate limiting)
```

---

## Future Upgrades

| What | Current (Free) | Upgrade To | Cost |
|------|---------------|------------|------|
| Backend response time | 30-45s cold start | Render Starter (always-on) | +$7/mo |
| L1 node uptime | 3-day auto-shutdown | Hetzner VPS (always-on) | +$4/mo |
| Database | 0.5GB / 5 connections | Neon Pro | +$19/mo |
| Redis | 10K commands/day | Upstash Pay-as-you-go | ~$1/mo |
| Password reset | Token returned in API | SendGrid email (free tier) | $0 |
| Monitoring | Manual health checks | UptimeRobot (free tier) | $0 |
