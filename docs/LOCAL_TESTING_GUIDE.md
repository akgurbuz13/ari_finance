# ARI Local Testing Guide

Step-by-step instructions to run and test ARI locally. Two modes available:
- **Simple Mode**: Web app + Core Banking only (no blockchain)
- **Full Mode**: Complete stack with blockchain service

---

## Prerequisites

### Required Software

| Software | Version | Check Command | Install |
|----------|---------|---------------|---------|
| Docker | 20+ | `docker --version` | [docker.com](https://docs.docker.com/get-docker/) |
| Docker Compose | 2+ | `docker compose version` | Included with Docker Desktop |
| Node.js | 20+ | `node --version` | [nodejs.org](https://nodejs.org/) or `brew install node` |
| Java | 21 | `java --version` | `brew install openjdk@21` |
| Git | 2+ | `git --version` | `brew install git` |

### macOS Quick Install (Homebrew)
```bash
# Install all prerequisites
brew install node openjdk@21 git
brew install --cask docker

# Add Java 21 to PATH
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
source ~/.zshrc
```

### Verify Installation
```bash
docker --version          # Docker version 24.x or higher
docker compose version    # Docker Compose version v2.x
node --version            # v20.x or higher
java --version            # openjdk 21.x
```

---

## Option A: Simple Mode (No Blockchain)

**Use this mode to**: Test web app, authentication, accounts, domestic transfers, and admin console without blockchain complexity.

**Limitations**: Cross-border transfers and blockchain settlement will show as "pending" (no actual on-chain operations).

### Step 1: Start Infrastructure

```bash
cd /Users/alikaangurbuz/ova_v1

# Start PostgreSQL and Redis
docker compose up -d

# Verify containers are running
docker compose ps
```

Expected output:
```
NAME           STATUS
ari-postgres   running (healthy)
ari-redis      running (healthy)
```

### Step 2: Start Core Banking Backend

Open a **new terminal**:

```bash
cd /Users/alikaangurbuz/ova_v1

# Run with dev profile (includes test secrets)
./gradlew :core-banking:bootRun --args='--spring.profiles.active=dev'
```

Wait for startup (about 30-60 seconds). Look for:
```
Started AriPlatformApplication in X seconds
```

**Verify backend is running**:
```bash
curl http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`

### Step 3: Start Web App

Open a **new terminal**:

```bash
cd /Users/alikaangurbuz/ova_v1/web

# Install dependencies (first time only)
npm install

# Start development server
npm run dev
```

Wait for:
```
✓ Ready in Xs
```

### Step 4: Access the Application

| Application | URL | Purpose |
|-------------|-----|---------|
| **Web App** | http://localhost:3000 | Customer-facing app |
| **API Docs** | http://localhost:8080/swagger-ui.html | API documentation |
| **Health Check** | http://localhost:8080/actuator/health | Backend status |

### Step 5: Test the Web App

#### 5.1 Create an Account

1. Go to http://localhost:3000
2. Click **"Get Started"** or **"Sign Up"**
3. Fill in registration form:
   - Email: `test@example.com`
   - Phone: `+905551234567`
   - Password: `TestPassword123!`
   - Region: **Turkey** (for TRY accounts)
4. Click **"Create Account"**

#### 5.2 Log In

1. Go to http://localhost:3000/login
2. Enter your credentials
3. You should see the dashboard

#### 5.3 Create a TRY Account

1. From dashboard, click **"Add Account"** or **"Create Account"**
2. Select **TRY** currency
3. Confirm creation
4. Note: Account starts with 0 balance

#### 5.4 Test Domestic Transfer (P2P)

To test transfers, you need two users:

**Create second user:**
1. Open incognito/private window
2. Go to http://localhost:3000
3. Sign up with different email: `test2@example.com`
4. Create a TRY account

**Fund first user via API** (for testing):
```bash
# Get access token by logging in
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"TestPassword123!"}'

# Note the accessToken from response
```

**Initiate transfer from web app:**
1. Log in as first user
2. Go to **Transfers** or **Send Money**
3. Enter second user's email or phone
4. Enter amount (e.g., 100 TRY)
5. Review and confirm

#### 5.5 View Transaction History

1. Go to **Activity** or **History**
2. You should see the transfer with status

### Step 6: Test Admin Console (Optional)

Open a **new terminal**:

```bash
cd /Users/alikaangurbuz/ova_v1/admin-console

# Install dependencies (first time only)
npm install

# Start admin console
npm run dev
```

Access: http://localhost:3001

**Note**: Admin login requires ADMIN role. You may need to update a user's role in the database:

```bash
# Connect to PostgreSQL
docker exec -it ari-postgres psql -U ari -d ari

# Grant admin role to a user (replace USER_ID)
UPDATE identity.users SET role = 'ADMIN' WHERE email = 'test@example.com';
\q
```

### Step 7: Stop Everything

```bash
# Stop web app: Ctrl+C in web terminal
# Stop backend: Ctrl+C in backend terminal

# Stop Docker containers
cd /Users/alikaangurbuz/ova_v1
docker compose down

# To also remove data (fresh start next time):
docker compose down -v
```

---

## Option B: Full Mode (With Blockchain)

**Use this mode to**: Test complete cross-border transfers with blockchain settlement on local Hardhat network.

**Requires**: All Simple Mode prerequisites + Hardhat for local blockchain.

### Step 1: Start Infrastructure

```bash
cd /Users/alikaangurbuz/ova_v1
docker compose up -d
```

### Step 2: Start Local Blockchain (Hardhat)

Open a **new terminal**:

```bash
cd /Users/alikaangurbuz/ova_v1/contracts

# Install dependencies (first time only)
npm install

# Start local Hardhat network
npx hardhat node
```

This starts a local Ethereum-compatible chain on `http://localhost:8545`.

Keep this terminal open. You'll see:
```
Started HTTP and WebSocket JSON-RPC server at http://127.0.0.1:8545/
Accounts
========
Account #0: 0xf39Fd6... (10000 ETH)
Private Key: 0xac0974bec...
...
```

### Step 3: Deploy Contracts to Local Network

Open a **new terminal**:

```bash
cd /Users/alikaangurbuz/ova_v1/contracts

# Deploy contracts
npx hardhat run scripts/deploy.ts --network localhost
```

Note the deployed contract addresses from output:
```
AriStablecoin deployed to: 0x5FbDB...
AriBridgeAdapter deployed to: 0xe7f17...
```

### Step 4: Configure Blockchain Service

Create/edit environment file:

```bash
cd /Users/alikaangurbuz/ova_v1

cat > .env.local << 'EOF'
# Local Hardhat network
TR_L1_RPC_URL=http://localhost:8545
TR_L1_CHAIN_ID=31337

# Use Hardhat's default account for testing
DEPLOYER_PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
MINTER_PRIVATE_KEY=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d
BRIDGE_OPERATOR_PRIVATE_KEY=0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a

# Contract addresses (update with your deployed addresses)
TR_STABLECOIN_ADDRESS=0x5FbDB2315678afecb367f032d93F642f64180aa3
TR_BRIDGE_ADAPTER_ADDRESS=0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512

# Internal API key (for dev)
INTERNAL_API_KEY=dev-internal-api-key-never-use-in-production
EOF
```

### Step 5: Start Backend Services

**Terminal 1 - Core Banking**:
```bash
cd /Users/alikaangurbuz/ova_v1
./gradlew :core-banking:bootRun --args='--spring.profiles.active=dev'
```

**Terminal 2 - Blockchain Service**:
```bash
cd /Users/alikaangurbuz/ova_v1

# Ensure Java 21 is used
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

./gradlew :blockchain-service:bootRun
```

### Step 6: Start Web App

```bash
cd /Users/alikaangurbuz/ova_v1/web
npm run dev
```

### Step 7: Test Cross-Border Transfer

1. Create two users (TR and EU regions)
2. Create TRY account for TR user
3. Create EUR account for EU user
4. Initiate cross-border transfer from TR to EU
5. Check Hardhat terminal for blockchain transactions
6. Verify transfer completes in web app

### Step 8: Stop Everything

```bash
# Stop all terminals with Ctrl+C
# Stop Docker
docker compose down
```

---

## Troubleshooting

### Backend won't start

**Error**: `Unable to connect to database`
```bash
# Check if PostgreSQL is running
docker compose ps

# Check PostgreSQL logs
docker compose logs postgres

# Restart PostgreSQL
docker compose restart postgres
```

**Error**: `Port 8080 already in use`
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process
kill -9 <PID>
```

### Web app won't start

**Error**: `Module not found`
```bash
cd web
rm -rf node_modules package-lock.json
npm install
```

**Error**: `Port 3000 already in use`
```bash
# Find process using port 3000
lsof -i :3000

# Kill the process
kill -9 <PID>

# Or run on different port
npm run dev -- --port 3002
```

### Database needs reset

```bash
# Stop backend first
# Then reset database
docker compose down -v
docker compose up -d

# Restart backend (Flyway will recreate tables)
./gradlew :core-banking:bootRun --args='--spring.profiles.active=dev'
```

### Java version issues

**Error**: `UnsupportedClassVersionError: class file version 65.0`
```bash
# Check Java version
java --version

# If not Java 21, set JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Stop Gradle daemon and retry
./gradlew --stop
./gradlew :blockchain-service:bootRun
```

### API returns 401 Unauthorized

```bash
# Check if you have a valid token
# Get new token:
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"your@email.com","password":"yourpassword"}'
```

### CORS errors in browser

Ensure backend is running with dev profile which allows localhost:3000:
```bash
./gradlew :core-banking:bootRun --args='--spring.profiles.active=dev'
```

---

## Quick Reference

### URLs

| Service | URL |
|---------|-----|
| Web App | http://localhost:3000 |
| Admin Console | http://localhost:3001 |
| Core Banking API | http://localhost:8080 |
| Blockchain Service API | http://localhost:8081 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | localhost:5432 |
| Redis | localhost:6379 |
| Hardhat (local chain) | http://localhost:8545 |

### Default Credentials (Dev Only)

| What | Value |
|------|-------|
| PostgreSQL User | `ari` |
| PostgreSQL Password | `ari_dev_password` |
| PostgreSQL Database | `ari` |

### Common Commands

```bash
# Start infrastructure
docker compose up -d

# Start core-banking
./gradlew :core-banking:bootRun --args='--spring.profiles.active=dev'

# Start blockchain-service
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :blockchain-service:bootRun

# Start web app
cd web && npm run dev

# Start admin console
cd admin-console && npm run dev

# Run backend tests
./gradlew :core-banking:test

# Run contract tests
cd contracts && npx hardhat test

# Stop everything
docker compose down
```

---

## What Works in Each Mode

| Feature | Simple Mode | Full Mode |
|---------|-------------|-----------|
| User Registration | ✅ | ✅ |
| User Login | ✅ | ✅ |
| 2FA Setup | ✅ | ✅ |
| Create TRY/EUR Account | ✅ | ✅ |
| View Balances | ✅ | ✅ |
| Domestic P2P Transfer | ✅ | ✅ |
| Transaction History | ✅ | ✅ |
| Cross-Border Transfer | ⚠️ Pending state only | ✅ Full flow |
| Blockchain Settlement | ❌ | ✅ |
| Admin Console | ✅ | ✅ |
| Compliance Dashboard | ✅ | ✅ |

---

## Next Steps

After successful local testing:

1. **Add mobile testing**: See `mobile/README.md` (coming soon)
2. **AWS deployment**: See `scripts/README.md` for validator setup
3. **Production deployment**: See `ARCHITECTURE.md` Section 10
