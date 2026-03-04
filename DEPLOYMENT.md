# ARI MVP Production Deployment Guide

Deploy ARI to production on `arifinance.co` using entirely free-tier services.

**Cost: $0/month** + domain renewal (~$10-15/year)

## Architecture

```
arifinance.co (Dynadot DNS)
├── app.arifinance.co      → Vercel (Web App)               [FREE]
├── admin.arifinance.co    → Vercel (Admin Console)          [FREE]
├── api.arifinance.co      → Render.com (Core Banking)       [FREE]
├── Blockchain Service     → Render.com                      [FREE]
│   ├── PostgreSQL         → Neon (shared)                   [FREE]
│   └── Redis              → Upstash                         [FREE]
└── Avalanche L1s (Fuji testnet)
    ├── TR L1              → Builder Console managed node
    └── EU L1              → Builder Console managed node
```

**Free tier limits:**
- Render: 512MB RAM, sleeps after 15min inactivity, ~30-45s cold start
- Neon: 0.5GB storage, 5 max connections, 190 compute hours/month
- Upstash: 10K commands/day, 256MB, TLS required
- Builder Console nodes: 3-day auto-shutdown (redeploy before demos)

---

## Prerequisites

- [Node.js 20+](https://nodejs.org)
- [JDK 21](https://adoptium.net)
- Git access to this repo
- Domain `arifinance.co` on Dynadot

---

## Phase 1: Database & Cache Setup

### 1.1 Neon PostgreSQL

1. Go to [neon.tech](https://neon.tech) and sign up (GitHub OAuth)
2. Create project: **ari-mvp** in **eu-central-1**
3. Copy the connection string. You'll need:
   - `DB_HOST` — e.g. `ep-cool-name-123456.eu-central-1.aws.neon.tech`
   - `DB_NAME` — `neondb` (default)
   - `DB_USER` — from connection string
   - `DB_PASSWORD` — from connection string

> Flyway auto-runs 19 migrations on first backend startup.

### 1.2 Upstash Redis

1. Go to [console.upstash.com](https://console.upstash.com) and sign up
2. Create database: **ari-mvp** in **eu-central-1**
3. Copy:
   - `REDIS_HOST` — e.g. `eu1-cool-redis.upstash.io`
   - `REDIS_PORT` — `6379`
   - `REDIS_PASSWORD` — from dashboard

> TLS is required and auto-enabled in `application-prod.yml`.

---

## Phase 2: Avalanche L1 Setup (Builder Console)

### 2.1 Create Builder Account

1. Go to [build.avax.network](https://build.avax.network/login)
2. Sign up / connect wallet
3. Claim testnet AVAX from the faucet

### 2.2 Create TR L1

1. Builder Console → **Create L1**
2. Configure:
   - **Chain ID**: note this (use for `TR_L1_CHAIN_ID`)
   - **Token Symbol**: ariTRY
   - **VM**: SubnetEVM
   - **Consensus**: PoA (Proof of Authority)
3. Deploy to **Fuji testnet**
4. Set up managed validator node
5. Note the **RPC URL** → use for `TR_L1_RPC_URL`
6. Note the **Blockchain ID** (bytes32 hex) → use for `TR_BLOCKCHAIN_ID`

### 2.3 Create EU L1

Same process:
- **Token Symbol**: ariEUR
- Different Chain ID → `EU_L1_CHAIN_ID`
- Note RPC URL → `EU_L1_RPC_URL`
- Note Blockchain ID → `EU_BLOCKCHAIN_ID`

### 2.4 Deploy Contracts

Generate a deployer key and fund it from the Builder Console faucet:

```bash
cd contracts
npm install

# Deploy to TR L1
DEPLOYER_PRIVATE_KEY=0x<your-key> \
TELEPORTER_ADDRESS=0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf \
BLOCKCHAIN_ID=<TR_BLOCKCHAIN_ID> \
TR_L1_RPC_URL=<from-builder-console> \
TR_L1_CHAIN_ID=<from-builder-console> \
npx hardhat run scripts/deploy-fuji-l1s.ts --network ari-tr-testnet

# Deploy to EU L1
DEPLOYER_PRIVATE_KEY=0x<your-key> \
TELEPORTER_ADDRESS=0x253b2784c75e510dD0fF1da844684a1aC0aa5fcf \
BLOCKCHAIN_ID=<EU_BLOCKCHAIN_ID> \
EU_L1_RPC_URL=<from-builder-console> \
EU_L1_CHAIN_ID=<from-builder-console> \
npx hardhat run scripts/deploy-fuji-l1s.ts --network ari-eu-testnet
```

Save the output — it contains all contract addresses for env vars.

### 2.5 Configure ICTT Bridge

After deploying to both chains:

```bash
# Cross-register TokenHome ↔ TokenRemote
npx hardhat run scripts/configure-bridge.ts
```

---

## Phase 3: Backend Deployment (Render.com)

### 3.1 Sign Up

1. Go to [render.com](https://render.com) and sign up (GitHub OAuth)
2. Connect your GitHub repo

### 3.2 Deploy Core Banking

1. **New Web Service** → Connect repo
2. **Environment**: Docker
3. **Dockerfile Path**: `./core-banking/Dockerfile`
4. **Docker Context**: `.` (repo root)
5. **Plan**: Free
6. **Health Check Path**: `/actuator/health`

Set environment variables:

```
SPRING_PROFILES_ACTIVE=prod
DB_HOST=<from-neon>
DB_PORT=5432
DB_NAME=neondb
DB_USER=<from-neon>
DB_PASSWORD=<from-neon>
REDIS_HOST=<from-upstash>
REDIS_PORT=6379
REDIS_PASSWORD=<from-upstash>
JWT_SECRET=<generate: openssl rand -base64 48>
INTERNAL_API_KEY=<generate: openssl rand -hex 16>
CORS_ALLOWED_ORIGINS=https://app.arifinance.co,https://admin.arifinance.co
ARI_REGION=TR
BLOCKCHAIN_SERVICE_URL=https://<blockchain-svc-name>.onrender.com
JAVA_OPTS=-Xmx384m -XX:+UseSerialGC
```

### 3.3 Deploy Blockchain Service

1. **New Web Service** → Same repo
2. **Dockerfile Path**: `./blockchain-service/Dockerfile`
3. **Docker Context**: `.`
4. **Plan**: Free
5. **Health Check Path**: `/actuator/health`

Set environment variables:

```
SPRING_PROFILES_ACTIVE=prod
DB_HOST=<same-neon>
DB_PORT=5432
DB_NAME=neondb
DB_USER=<same>
DB_PASSWORD=<same>
REDIS_HOST=<same-upstash>
REDIS_PORT=6379
REDIS_PASSWORD=<same>
INTERNAL_API_KEY=<same-as-core-banking>
CORE_BANKING_URL=https://<core-banking-name>.onrender.com
TR_L1_RPC_URL=<from-builder-console>
TR_L1_CHAIN_ID=<from-builder-console>
TR_STABLECOIN_ADDRESS=<from-deployment>
EU_L1_RPC_URL=<from-builder-console>
EU_L1_CHAIN_ID=<from-builder-console>
EU_STABLECOIN_ADDRESS=<from-deployment>
TR_TOKEN_HOME_ADDRESS=<from-deployment>
EU_TOKEN_HOME_ADDRESS=<from-deployment>
TR_TOKEN_REMOTE_ADDRESS=<from-deployment>
EU_TOKEN_REMOTE_ADDRESS=<from-deployment>
TR_BRIDGE_ADAPTER_ADDRESS=<from-deployment>
EU_BRIDGE_ADAPTER_ADDRESS=<from-deployment>
TR_BLOCKCHAIN_ID=<from-builder-console>
EU_BLOCKCHAIN_ID=<from-builder-console>
WALLET_MASTER_KEY=<deployer-private-key>
MINTER_PRIVATE_KEY=<deployer-private-key>
ADMIN_PRIVATE_KEY=<deployer-private-key>
BRIDGE_OPERATOR_PRIVATE_KEY=<deployer-private-key>
RELAYER_PRIVATE_KEY=<deployer-private-key>
JAVA_OPTS=-Xmx384m -XX:+UseSerialGC
```

### 3.4 Update Core Banking URL

After blockchain service deploys, go back to core-banking env vars and set:
```
BLOCKCHAIN_SERVICE_URL=https://<blockchain-svc-name>.onrender.com
```

---

## Phase 4: Frontend Deployment (Vercel)

### 4.1 Web App

1. Go to [vercel.com](https://vercel.com) and sign up (GitHub OAuth)
2. **Import Project** → Select repo
3. **Root Directory**: `web`
4. **Framework Preset**: Next.js
5. **Environment Variables**:
   ```
   NEXT_PUBLIC_API_URL=https://api.arifinance.co
   ```
6. Deploy

### 4.2 Admin Console

1. **Import Project** → Same repo (new project)
2. **Root Directory**: `admin-console`
3. **Framework Preset**: Next.js
4. **Environment Variables**:
   ```
   NEXT_PUBLIC_API_URL=https://api.arifinance.co
   ```
5. Deploy

---

## Phase 5: DNS Configuration (Dynadot)

### 5.1 Add DNS Records

Log into [Dynadot](https://www.dynadot.com) and add these records for `arifinance.co`:

| Type | Host | Value |
|------|------|-------|
| CNAME | app | cname.vercel-dns.com |
| CNAME | admin | cname.vercel-dns.com |
| CNAME | api | `<core-banking-name>`.onrender.com |

### 5.2 Add Custom Domains

**Vercel:**
- Web app project → Settings → Domains → Add `app.arifinance.co`
- Admin project → Settings → Domains → Add `admin.arifinance.co`

**Render:**
- Core Banking service → Settings → Custom Domains → Add `api.arifinance.co`

SSL certificates auto-provision on both Vercel and Render.

### 5.3 Update CORS

After domains are live, verify `CORS_ALLOWED_ORIGINS` on core-banking Render service:
```
https://app.arifinance.co,https://admin.arifinance.co
```

---

## Verification

### Health Check
```bash
curl https://api.arifinance.co/actuator/health
# Expected: {"status":"UP"}
```

### Web App
1. Open https://app.arifinance.co
2. Sign up a test user
3. Log in → See dashboard with TRY/EUR accounts
4. Create a domestic transfer
5. Create a cross-border transfer (FX quote + ledger entries)

### Admin Console
1. Open https://admin.arifinance.co
2. Log in with admin credentials
3. View registered users

### Blockchain Settlement
After a transfer triggers an outbox event:
1. Check blockchain-service health
2. Verify mint transaction on Fuji L1 explorer

---

## Troubleshooting

### Backend won't start on Render
- Check build logs: Gradle download can timeout on free tier
- Verify all required env vars are set (especially `DB_HOST`, `JWT_SECRET`)
- Memory: ensure `JAVA_OPTS=-Xmx384m -XX:+UseSerialGC` is set

### Cold start is slow (~30-45s)
Normal for Render free tier. The service sleeps after 15 minutes of inactivity.
- First request after sleep triggers a cold start
- Use [UptimeRobot](https://uptimerobot.com) free tier to ping `/actuator/health` every 14 minutes

### Database connection errors
- Neon limit is 5 connections — both services share this pool
- Verify `sslmode=require` in connection string
- Check Neon dashboard for connection count

### Redis connection errors
- Upstash requires TLS — `application-prod.yml` enables this
- Verify password is correct
- Check Upstash dashboard for command count (10K/day limit)

### Builder Console nodes went down
Managed testnet nodes auto-shutdown after ~3 days.
1. Go to Builder Console
2. Redeploy/restart the managed nodes
3. Verify RPC URLs still work
4. No need to redeploy contracts — they persist on-chain

---

## Future Upgrades

| Upgrade | From | To | Cost |
|---------|------|----|------|
| Always-on backend | Render Free | Render Starter | +$7/mo |
| Persistent L1 nodes | Builder Console 3-day | Hetzner VPS | +$4/mo |
| Production database | Neon Free | Neon Pro | +$19/mo |
| Production Redis | Upstash Free | Upstash Pay-as-you-go | ~$1/mo |
| Email (password reset) | Token-in-response | SendGrid free tier | $0 |
| Keep-alive pings | Manual | UptimeRobot free | $0 |
