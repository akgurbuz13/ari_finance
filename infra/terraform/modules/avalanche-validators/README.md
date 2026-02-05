# Avalanche L1 Validator Module

This Terraform module deploys self-hosted Avalanche validators for Ova's permissioned L1 chains.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        AWS VPC                                   │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Validator 1  │  │ Validator 2  │  │ Validator 3  │          │
│  │   AZ-a       │  │   AZ-b       │  │   AZ-c       │          │
│  │              │  │              │  │              │          │
│  │ AvalancheGo  │  │ AvalancheGo  │  │ AvalancheGo  │          │
│  │ Port 9651    │  │ Port 9651    │  │ Port 9651    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│         │                 │                 │                    │
│         └────────────────┼────────────────┘                    │
│                          │                                       │
│                    ┌─────┴─────┐                                │
│                    │ Internal  │                                │
│                    │   NLB     │                                │
│                    └───────────┘                                │
│                          │                                       │
│              ┌───────────┴───────────┐                          │
│              │    Blockchain         │                          │
│              │      Service          │                          │
│              └───────────────────────┘                          │
└─────────────────────────────────────────────────────────────────┘
```

## Post-Etna Avalanche Configuration

As of Avalanche Etna upgrade (December 2024):
- No 2000 AVAX stake requirement for permissioned L1s
- Validators managed through ValidatorManager smart contract
- Continuous fee of ~1.3 AVAX/month per validator
- BLS signature aggregation via ICM relayer

## Prerequisites

1. **AWS Secrets Manager secrets** for each validator:
   ```
   ova/{environment}/validator/{instance-id}
   {
     "staker_key": "...",
     "staker_cert": "...",
     "bls_key": "..."
   }
   ```

2. **ValidatorManager contract** deployed on C-Chain (for L1 management)

3. **External Secrets Operator** in Kubernetes cluster (for blockchain-service)

## Usage

```hcl
module "avalanche_validators_eu" {
  source = "./modules/avalanche-validators"

  environment            = "prod"
  region                 = "EU"
  l1_name                = "ova-eu"
  l1_chain_id            = 99998
  validator_count        = 3
  validator_instance_type = "m5.xlarge"
  validator_disk_size_gb = 500

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnet_ids
  ssh_key_name = "ova-prod-key"

  enable_monitoring = true
  alert_email       = "ops@ova.finance"

  tags = {
    Project = "Ova"
  }
}
```

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| environment | Environment name | string | - | yes |
| region | Ova region (TR or EU) | string | - | yes |
| l1_name | L1 chain name | string | "ova-l1" | no |
| l1_chain_id | Chain ID | number | - | yes |
| validator_count | Number of validators | number | 3 | no |
| validator_instance_type | EC2 instance type | string | "m5.xlarge" | no |
| validator_disk_size_gb | Disk size in GB | number | 500 | no |
| vpc_id | VPC ID | string | - | yes |
| subnet_ids | Subnet IDs | list(string) | - | yes |
| ssh_key_name | SSH key pair name | string | - | yes |
| avalanche_version | AvalancheGo version | string | "1.11.0" | no |
| enable_monitoring | Enable CloudWatch monitoring | bool | true | no |
| alert_email | Email for alerts | string | "" | no |

## Outputs

| Name | Description |
|------|-------------|
| security_group_id | Validator security group ID |
| autoscaling_group_name | ASG name |
| iam_role_arn | IAM role ARN |

## Security Considerations

1. **Network isolation**: Validators only expose staking port to each other
2. **IMDSv2**: Required for EC2 metadata
3. **Encrypted storage**: EBS volumes encrypted at rest
4. **Secret management**: Keys stored in AWS Secrets Manager
5. **Read-only root**: Systemd service uses ProtectSystem=strict

## Monitoring

When `enable_monitoring = true`:
- CloudWatch metrics: CPU, memory, disk usage
- CloudWatch logs: AvalancheGo main logs
- CloudWatch alarms: High CPU, low disk space

## Maintenance

### Updating AvalancheGo version

```bash
# Update variable and apply
terraform apply -var="avalanche_version=1.12.0"

# ASG will perform rolling update
```

### Key rotation

1. Generate new keys in Secrets Manager
2. Trigger ASG instance refresh:
   ```bash
   aws autoscaling start-instance-refresh \
     --auto-scaling-group-name <asg-name>
   ```

### Adding/removing validators

1. Update `validator_count` variable
2. Update ValidatorManager contract via timelock
3. Apply Terraform changes
