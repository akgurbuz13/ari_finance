#!/bin/bash
set -euo pipefail

# ============================================================
# Avalanche L1 Validator Bootstrap Script
# Environment: ${environment}
# Region: ${region}
# L1 Name: ${l1_name}
# Chain ID: ${l1_chain_id}
# ============================================================

exec > >(tee /var/log/user-data.log|logger -t user-data -s 2>/dev/console) 2>&1

echo "=== Starting Avalanche validator setup ==="

# Variables from Terraform
ENVIRONMENT="${environment}"
REGION="${region}"
L1_NAME="${l1_name}"
L1_CHAIN_ID="${l1_chain_id}"
AVALANCHE_VERSION="${avalanche_version}"
STAKING_PORT="${staking_port}"
HTTP_PORT="${http_port}"
METRICS_PORT="${metrics_port}"
ENABLE_MONITORING="${enable_monitoring}"

# Install dependencies
apt-get update
apt-get install -y curl jq awscli unzip

# Create avalanche user
useradd -m -s /bin/bash avalanche || true
mkdir -p /home/avalanche/.avalanchego
chown -R avalanche:avalanche /home/avalanche

# Download AvalancheGo
echo "=== Downloading AvalancheGo v$AVALANCHE_VERSION ==="
cd /tmp
curl -sSL "https://github.com/ava-labs/avalanchego/releases/download/v$AVALANCHE_VERSION/avalanchego-linux-amd64-v$AVALANCHE_VERSION.tar.gz" -o avalanchego.tar.gz
tar -xzf avalanchego.tar.gz
mv avalanchego-v$AVALANCHE_VERSION /opt/avalanchego
chown -R avalanche:avalanche /opt/avalanchego

# Fetch staking keys from Secrets Manager
echo "=== Fetching validator keys from Secrets Manager ==="
INSTANCE_ID=$(curl -s http://169.254.169.254/latest/meta-data/instance-id)
aws secretsmanager get-secret-value \
    --secret-id "ova/$ENVIRONMENT/validator/$INSTANCE_ID" \
    --query 'SecretString' \
    --output text | jq -r '.staker_key' > /home/avalanche/.avalanchego/staking/staker.key
aws secretsmanager get-secret-value \
    --secret-id "ova/$ENVIRONMENT/validator/$INSTANCE_ID" \
    --query 'SecretString' \
    --output text | jq -r '.staker_cert' > /home/avalanche/.avalanchego/staking/staker.crt
aws secretsmanager get-secret-value \
    --secret-id "ova/$ENVIRONMENT/validator/$INSTANCE_ID" \
    --query 'SecretString' \
    --output text | jq -r '.bls_key' > /home/avalanche/.avalanchego/staking/signer.key

chmod 600 /home/avalanche/.avalanchego/staking/*
chown -R avalanche:avalanche /home/avalanche/.avalanchego

# Create config file
echo "=== Creating AvalancheGo config ==="
cat > /home/avalanche/.avalanchego/config.json << EOF
{
  "http-host": "0.0.0.0",
  "http-port": $HTTP_PORT,
  "staking-port": $STAKING_PORT,
  "public-ip-resolution-service": "opendns",
  "db-dir": "/home/avalanche/.avalanchego/db",
  "log-dir": "/home/avalanche/.avalanchego/logs",
  "log-level": "info",
  "log-display-level": "info",
  "log-format": "json",
  "network-id": "fuji",
  "track-subnets": "",
  "api-admin-enabled": false,
  "api-info-enabled": true,
  "api-keystore-enabled": false,
  "api-metrics-enabled": true,
  "health-check-frequency": "15s",
  "index-enabled": true,
  "staking-tls-cert-file": "/home/avalanche/.avalanchego/staking/staker.crt",
  "staking-tls-key-file": "/home/avalanche/.avalanchego/staking/staker.key",
  "staking-signer-key-file": "/home/avalanche/.avalanchego/staking/signer.key"
}
EOF

chown avalanche:avalanche /home/avalanche/.avalanchego/config.json

# Create L1 chain config
echo "=== Creating L1 chain config ==="
mkdir -p /home/avalanche/.avalanchego/configs/chains/$L1_CHAIN_ID
cat > /home/avalanche/.avalanchego/configs/chains/$L1_CHAIN_ID/config.json << EOF
{
  "snowman-api-enabled": true,
  "pruning-enabled": false,
  "state-sync-enabled": false,
  "allow-unfinalized-queries": false,
  "accepted-cache-size": 32,
  "tx-pool-price-limit": 0,
  "continuous-profiler-dir": "",
  "continuous-profiler-frequency": 900000000000,
  "continuous-profiler-max-files": 5
}
EOF

chown -R avalanche:avalanche /home/avalanche/.avalanchego/configs

# Create systemd service
echo "=== Creating systemd service ==="
cat > /etc/systemd/system/avalanchego.service << EOF
[Unit]
Description=AvalancheGo Node
After=network.target

[Service]
Type=simple
User=avalanche
Group=avalanche
ExecStart=/opt/avalanchego/avalanchego --config-file=/home/avalanche/.avalanchego/config.json
Restart=always
RestartSec=5
LimitNOFILE=65535

# Security hardening
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=read-only
ReadWritePaths=/home/avalanche/.avalanchego

[Install]
WantedBy=multi-user.target
EOF

# Enable and start service
systemctl daemon-reload
systemctl enable avalanchego
systemctl start avalanchego

# Install CloudWatch agent for monitoring
if [ "$ENABLE_MONITORING" = "true" ]; then
    echo "=== Installing CloudWatch agent ==="
    cd /tmp
    curl -sSL https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb -o cw-agent.deb
    dpkg -i cw-agent.deb

    # Configure CloudWatch agent
    cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json << EOF
{
  "metrics": {
    "namespace": "Ova/Avalanche",
    "metrics_collected": {
      "cpu": {
        "measurement": ["cpu_usage_active"],
        "totalcpu": true
      },
      "disk": {
        "measurement": ["disk_used_percent"],
        "resources": ["/"]
      },
      "mem": {
        "measurement": ["mem_used_percent"]
      }
    },
    "append_dimensions": {
      "Environment": "$ENVIRONMENT",
      "Region": "$REGION",
      "L1Name": "$L1_NAME"
    }
  },
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/home/avalanche/.avalanchego/logs/main.log",
            "log_group_name": "/ova/avalanche/$ENVIRONMENT/$REGION",
            "log_stream_name": "{instance_id}/main"
          }
        ]
      }
    }
  }
}
EOF

    # Start CloudWatch agent
    /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
        -a fetch-config \
        -m ec2 \
        -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json \
        -s
fi

# Health check script
cat > /usr/local/bin/check-validator-health.sh << 'HEALTHEOF'
#!/bin/bash
HEALTH=$(curl -s http://localhost:${http_port}/ext/health | jq -r '.healthy')
if [ "$HEALTH" != "true" ]; then
    echo "Validator unhealthy"
    exit 1
fi
echo "Validator healthy"
exit 0
HEALTHEOF
chmod +x /usr/local/bin/check-validator-health.sh

echo "=== Avalanche validator setup complete ==="
echo "Check status: systemctl status avalanchego"
echo "View logs: journalctl -u avalanchego -f"
