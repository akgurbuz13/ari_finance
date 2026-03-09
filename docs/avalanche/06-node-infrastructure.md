# 06 - Node Infrastructure for ARI

> **Key Takeaways**
> - ARI validators run Subnet-EVM (now part of the AvalancheGo monorepo) and sync ONLY the P-Chain and the ARI L1 — NOT the C-Chain or X-Chain
> - L1 nodes have low hardware requirements (~4 cores, 8 GB RAM, 500 GB SSD for medium throughput) since fintech transaction volumes are well under 100 TPS
> - Storage MUST be local NVMe; AWS EBS and Azure Managed Disks introduce fatal latency for validator consensus
> - The Prometheus + Grafana monitoring stack (provided by Ava Labs' installer script) is the standard and should be deployed alongside each validator
> - RPC endpoints should be exposed only to the blockchain-service VPC, never to the public internet
> - Node upgrades follow a rolling strategy: upgrade non-leader validators first, then the leader

---

## 1. Node Roles in ARI

ARI requires two types of nodes per L1 (ariTR and ariEU):

| Role | Count (recommended) | Purpose |
|------|--------------------|---------|
| Validator nodes | 2 per L1 (minimum), 3-5 for production | Participate in Snowman consensus, produce blocks |
| RPC-only node | 1 per region (optional) | Serve API requests without validator duties |

Since ARI's L1s are permissioned (PoA), validators are pre-approved institutional nodes. ARI's current AWS testnet infrastructure uses 2 validators per L1, which provides basic fault tolerance (1-of-2 required for consensus).

**Note on minimum validators**: Snowman consensus with 2 validators means 1 Byzantine fault tolerance (f=0 — neither validator can be Byzantine). A minimum of 3 validators is strongly recommended for production to achieve f=1. With 5 validators, f=1 (any 1 validator can fail and consensus still proceeds with the remaining 4).

---

## 2. Hardware Requirements

Based on the official Avalanche documentation for L1 validators at medium throughput (10-100 TPS), which covers ARI's expected fintech volume:

| Component | Specification | Notes |
|-----------|--------------|-------|
| CPU | 4 cores | AMD/Intel x86_64 |
| RAM | 8 GB | 16 GB recommended for buffer |
| Storage | 500 GB SSD | Local NVMe REQUIRED; no EBS/managed disks |
| Network | 100 Mbps symmetric | Stable, low-latency |
| OS | Ubuntu 22.04 LTS | Official support; 20.04 LTS also works |

### Critical Storage Warning

Cloud block storage (AWS EBS, GCP Persistent Disk, Azure Managed Disks) introduces network latency that causes:
- Poor consensus performance
- Missed blocks
- Potential "benching" (temporary exclusion from consensus)

Use only local NVMe storage. Recommended AWS instance types: `i3.xlarge` or `i4i.xlarge` (local NVMe). On Azure, use `Lsv3`-series instances with local NVMe.

### State Storage Estimate for ARI L1

Since ARI's L1 chains started recently and process limited transactions, state sizes are much smaller than the Primary Network:

| Chain | Estimated size after 1 year (100 TPS) | Estimated size after 1 year (10 TPS) |
|-------|---------------------------------------|--------------------------------------|
| ariTR L1 | ~50 GB | ~5 GB |
| ariEU L1 | ~50 GB | ~5 GB |
| P-Chain state (sync) | ~10 GB | ~10 GB |

Total: well within a 500 GB SSD for the first several years of operation.

---

## 3. AvalancheGo Installation

### Method 1: Install Script (Recommended for initial setup)

```bash
# Download the official install script
wget -nd -m https://raw.githubusercontent.com/ava-labs/avalanchego/master/scripts/install.sh
chmod 755 install.sh

# Install (creates systemd service)
./install.sh

# Verify
sudo systemctl status avalanchego
```

### Method 2: Build from Source (Recommended for ARI L1 nodes)

Since ARI L1 nodes need the Subnet-EVM plugin (now part of the monorepo), building from source is the most reliable approach:

```bash
# Install Go 1.21+
sudo apt-get update && sudo apt-get install -y golang-go

# Clone AvalancheGo (includes Subnet-EVM as graft)
git clone https://github.com/ava-labs/avalanchego.git
cd avalanchego
git checkout tags/v1.12.x  # Use the latest stable release

# Build AvalancheGo binary
./scripts/build.sh

# Build Subnet-EVM plugin for ARI's L1
# The VMID is the VM identifier for ARI's chains (from the Avalanche Explorer)
cd graft/subnet-evm
./scripts/build.sh ~/.avalanchego/plugins/<ARI_VMID>
```

Finding your VMID: After creating your L1 chain with `platform chain create`, look up the blockchain in the Fuji explorer (https://subnets-test.avax.network/) to find the VMID.

### Method 3: Docker (CI/test environments)

```bash
# Official AvalancheGo Docker image
docker pull avaplatform/avalanchego:v1.12.x

docker run -d \
  --name avalanchego \
  -v ~/.avalanchego:/root/.avalanchego \
  -p 9650:9650 \
  -p 9651:9651 \
  avaplatform/avalanchego:v1.12.x \
  --network-id=fuji \
  --track-subnets=<ARI_SUBNET_ID>
```

---

## 4. Node Configuration for ARI L1

### config.json (AvalancheGo root config)

```json
{
  "network-id": "fuji",
  "http-host": "0.0.0.0",
  "http-port": 9650,
  "staking-port": 9651,
  "log-level": "info",
  "track-subnets": "<ARI_SUBNET_ID>",
  "api-admin-enabled": false,
  "api-keystore-enabled": false,
  "api-metrics-enabled": true,
  "metrics-port": 9095,
  "state-sync-enabled": true,
  "db-dir": "/data/avalanchego/db",
  "log-dir": "/data/avalanchego/logs"
}
```

Key settings:
- `track-subnets`: The Subnet ID for the ARI L1 (same as returned by `platform subnet create`)
- `api-admin-enabled: false`: Never expose the admin API on production validators
- `state-sync-enabled: true`: Dramatically faster bootstrap for new nodes
- `api-metrics-enabled: true`: Required for Prometheus monitoring

### Chain Config for ARI L1 (Subnet-EVM)

Each tracked blockchain can have its own chain-level config file at:
`~/.avalanchego/configs/chains/<BLOCKCHAIN_ID>/config.json`

```json
{
  "log-level": "info",
  "pruning-enabled": true,
  "snapshot-async": true,
  "commit-interval": 4096,
  "allow-unfinalized-queries": false,
  "eth-apis": ["eth", "net", "web3", "internal-eth", "internal-blockchain", "internal-transaction"]
}
```

For a validator-only node, omit `debug` and `personal` APIs.

For an RPC-only node serving blockchain-service requests, add:
```json
{
  "eth-apis": ["eth", "net", "web3", "internal-eth", "internal-blockchain", "internal-transaction", "eth-filter", "debug-tracer"]
}
```

---

## 5. RPC Endpoint Reference

Once AvalancheGo is running with a tracked L1, the blockchain-service connects to:

```
# EVM JSON-RPC (web3j compatible)
http://<NODE_HOST>:9650/ext/bc/<BLOCKCHAIN_ID>/rpc

# WebSocket (for eth_subscribe)
ws://<NODE_HOST>:9650/ext/bc/<BLOCKCHAIN_ID>/ws

# Example for Fuji ariTR L1
http://validator1.ari.example:9650/ext/bc/2D7oZm5mVrAHPkPdqRbK4gHmZ8kQoELhxgmPJRBRTVXfHZ3E7/rpc
```

### blockchain-service Application Config

```yaml
# application-fuji.yml
ari:
  blockchain:
    tr-l1:
      rpc-url: "http://validator1-tr.ari.internal:9650/ext/bc/${TR_BLOCKCHAIN_ID}/rpc"
      ws-url: "ws://validator1-tr.ari.internal:9650/ext/bc/${TR_BLOCKCHAIN_ID}/ws"
    eu-l1:
      rpc-url: "http://validator1-eu.ari.internal:9650/ext/bc/${EU_BLOCKCHAIN_ID}/rpc"
      ws-url: "ws://validator1-eu.ari.internal:9650/ext/bc/${EU_BLOCKCHAIN_ID}/ws"
```

Always use internal VPC addresses, never public-facing RPC endpoints.

---

## 6. Multi-Region Deployment Topology

```
AWS eu-central-1 (Frankfurt / EU validator):          Azure Turkey Central (TR validator):
┌────────────────────────────────────┐              ┌───────────────────────────────────┐
│  Private VPC (10.0.0.0/16)        │              │  Private VNet (10.1.0.0/16)       │
│                                    │              │                                   │
│  ┌──────────────────────────────┐  │              │  ┌────────────────────────────┐   │
│  │  validator-eu-1               │  │   Staking    │  │  validator-tr-1             │   │
│  │  i4i.xlarge (NVMe)            │◄─┼─────────────┼─►│  Lsv3 (local NVMe)          │   │
│  │  - AvalancheGo                │  │  port 9651   │  │  - AvalancheGo              │   │
│  │  - ariEU L1 validator         │  │              │  │  - ariTR L1 validator        │   │
│  │  - ariTR L1 (non-validator)   │  │              │  │  - ariEU L1 (non-validator)  │   │
│  └──────────────────────────────┘  │              │  └────────────────────────────┘   │
│                                    │              │                                   │
│  ┌──────────────────────────────┐  │              │  ┌────────────────────────────┐   │
│  │  blockchain-service-eu       │  │              │  │  blockchain-service-tr      │   │
│  │  Spring Boot :8081           │  │              │  │  Spring Boot :8081          │   │
│  │  - reads ariEU L1 events     │  │              │  │  - reads ariTR L1 events    │   │
│  │  - mints ariEUR on ariEU     │  │              │  │  - mints ariTRY on ariTR    │   │
│  └──────────────────────────────┘  │              │  └────────────────────────────┘   │
│                                    │              │                                   │
│  Prometheus + Grafana              │              │  Prometheus + Grafana             │
└────────────────────────────────────┘              └───────────────────────────────────┘
                    │                                            │
                    └────────────────┬───────────────────────────┘
                                     │
                              P-Chain (Fuji/Mainnet)
                              - stores both L1 validator sets
                              - validates Warp messages
```

### Key Networking Rules

| Traffic | Direction | Port | Protocol |
|---------|-----------|------|----------|
| Staking/consensus | Between validators | 9651 | TCP |
| RPC (internal) | blockchain-service → validator | 9650 | HTTP/WS |
| Metrics | Prometheus → validator | 9095 | HTTP |
| RPC (public) | NEVER expose | - | - |

---

## 7. Monitoring Setup

The official Ava Labs monitoring installer sets up Prometheus + Grafana + node_exporter in approximately 10 minutes:

```bash
# Download monitoring installer
wget -nd -m https://raw.githubusercontent.com/ava-labs/avalanche-monitoring/main/grafana/monitoring-installer.sh
chmod 755 monitoring-installer.sh

# Step 1: Install Prometheus
./monitoring-installer.sh --1

# Step 2: Install Grafana
./monitoring-installer.sh --2

# Step 3: Install node_exporter (system metrics: CPU, RAM, disk)
./monitoring-installer.sh --3

# Step 4: Import Avalanche dashboards (7 pre-built dashboards)
./monitoring-installer.sh --4

# Step 5: Add L1-specific dashboard
./monitoring-installer.sh --5
```

### Pre-built Dashboards Available

- Avalanche Main Dashboard (node health overview)
- C-Chain metrics (not needed for ARI, skip)
- Database operations
- Machine metrics (CPU, memory, disk, network)
- Network connectivity and peer metrics
- P-Chain metrics
- X-Chain metrics
- Avalanche L1s dashboard (most relevant for ARI)

### Critical Metrics to Alert On

| Metric | Warning | Critical | Action |
|--------|---------|----------|--------|
| Disk usage | 80% | 90% | Run offline pruning or expand storage |
| CPU sustained | 70% | 90% | Upgrade instance type |
| RAM usage | 80% | 90% | Upgrade RAM |
| Peer count | < 3 | < 2 | Investigate network connectivity |
| Last accepted block age | > 10s | > 30s | Investigate consensus stall |
| Bootstrap complete | false | - | Wait or investigate |

### ARI-Specific Chain Health Check

Beyond node-level metrics, implement blockchain-service-level health checks:

```kotlin
// In blockchain-service: HealthIndicator for L1 connectivity
@Component
class BlockchainHealthIndicator(
    private val web3jTr: Web3j,
    private val web3jEu: Web3j
) : HealthIndicator {
    override fun health(): Health {
        return try {
            val trBlock = web3jTr.ethBlockNumber().send().blockNumber
            val euBlock = web3jEu.ethBlockNumber().send().blockNumber
            Health.up()
                .withDetail("tr-l1-block", trBlock)
                .withDetail("eu-l1-block", euBlock)
                .build()
        } catch (e: Exception) {
            Health.down(e).build()
        }
    }
}
```

Expose via Spring Actuator at `/actuator/health`.

---

## 8. Node Upgrade Procedure

### Rolling Upgrade (Zero Downtime)

With 3+ validators, perform a rolling upgrade — one node at a time:

```bash
# On the node being upgraded:

# 1. Backup staking certificates (identify this node on the network)
cp ~/.avalanchego/staking/staker.crt ~/staker.crt.backup
cp ~/.avalanchego/staking/staker.key ~/staker.key.backup

# 2. Stop the node (allow ~30 seconds for graceful shutdown)
sudo systemctl stop avalanchego

# 3. Download and install new version
git -C ~/avalanchego pull
cd ~/avalanchego && ./scripts/build.sh
cd graft/subnet-evm && ./scripts/build.sh ~/.avalanchego/plugins/<ARI_VMID>

# 4. Start the node
sudo systemctl start avalanchego

# 5. Wait for bootstrapping (check via health API)
curl http://localhost:9650/ext/info
# Look for "bootstrapped" status

# 6. Verify the L1 is synced before upgrading the next validator
curl http://localhost:9650/ext/bc/<BLOCKCHAIN_ID>/rpc \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}'
```

### Staker Key Backup

Staker keys identify a validator node on the P-Chain. Losing these keys means the validator cannot be recovered — the old validation registration must be expired or removed, and a new `RegisterL1ValidatorTx` must be submitted. Back up the keys securely:

- Store `staker.crt` and `staker.key` in AWS Secrets Manager or Azure Key Vault
- Never store in source code, S3 public buckets, or container images
- Rotate annually or after any suspected security incident

---

## 9. Disaster Recovery

### Recovery Scenarios

| Scenario | Recovery Action | Time Estimate |
|----------|-----------------|---------------|
| Single validator node failure | Boot spare from latest DB backup; sync via state sync | 30-60 min |
| All validators for one L1 down | Start with state sync from P-Chain data | 1-4 hours |
| Corrupt node database | Delete `db/` directory; restart (state sync will rebuild) | 1-4 hours |
| Lost staker key | Submit `DisableL1ValidatorTx` from P-Chain; re-register | 1-2 days |

### State Sync for Fast Recovery

State sync (`"state-sync-enabled": true` in config) allows a new node to download only the current chain state rather than replaying all historical blocks. For a young ARI L1 with months of history, state sync should complete in minutes.

### Regular Backups

Schedule nightly backups of the AvalancheGo database:

```bash
#!/bin/bash
# /etc/cron.d/avalanchego-backup
# Run at 02:00 UTC daily

# Stop node gracefully
sudo systemctl stop avalanchego

# Archive the DB
tar -czf /backup/avalanchego-$(date +%Y%m%d).tar.gz ~/.avalanchego/db/

# Restart node
sudo systemctl start avalanchego

# Upload to S3 (AWS) or Azure Blob Storage
aws s3 cp /backup/avalanchego-$(date +%Y%m%d).tar.gz s3://ari-node-backups/
```

Retain 7 days of daily backups.

---

## 10. Security Hardening

### Network Security

```bash
# UFW firewall rules for an ARI validator node
ufw default deny incoming
ufw default allow outgoing

# Allow staking traffic from other validators only
ufw allow from <VALIDATOR_2_IP> to any port 9651
ufw allow from <VALIDATOR_3_IP> to any port 9651

# Allow RPC from blockchain-service internal VPC only
ufw allow from <BLOCKCHAIN_SERVICE_VPC_CIDR> to any port 9650

# Allow Prometheus scraping from monitoring server
ufw allow from <PROMETHEUS_SERVER_IP> to any port 9095

# SSH from bastion only
ufw allow from <BASTION_IP> to any port 22

ufw enable
```

### Operational Security

- Run AvalancheGo as a non-root user (`avalanche` service account)
- Never enable `api-admin-enabled` or `api-keystore-enabled` on production nodes
- Rotate the operating system user's SSH keys quarterly
- Apply OS security patches on a monthly cycle (coordinate with rolling upgrade)
- Run intrusion detection (AWS GuardDuty / Azure Defender for Cloud) on validator nodes
- Separate the minter key (blockchain-service) from the node staker key — they are different keys with different roles

---

## 11. Cross-References

- Permissioned L1 creation and SubnetID: `docs/avalanche/02-permissioned-l1-setup.md`
- Minter key management: `docs/avalanche/08-reconciliation-security.md`
- RPC usage patterns in blockchain-service: `docs/avalanche/09-sdk-api-reference.md`
- Current Fuji setup guide: `docs/FUJI_L1_SETUP_GUIDE.md`
- Official node docs: `https://build.avax.network/docs/nodes/run-a-node`
- Official system requirements: `https://build.avax.network/docs/nodes/system-requirements`
- Official monitoring guide: `https://build.avax.network/docs/nodes/maintain/monitoring`
