# Avalanche L1 Validators for Development
# Deploys 2 validators (minimum for testing) on AWS EU

# ==============================================================================
# VARIABLES
# ==============================================================================

variable "deploy_validators" {
  description = "Whether to deploy Avalanche validators"
  type        = bool
  default     = true
}

variable "validator_ssh_key_name" {
  description = "SSH key pair name for validator instances"
  type        = string
  default     = "ova-dev-validators"
}

variable "allowed_ssh_cidrs" {
  description = "CIDR blocks allowed to SSH to validators"
  type        = list(string)
  default     = []  # Set to your IP for debugging
}

variable "alert_email" {
  description = "Email for validator alerts"
  type        = string
  default     = ""
}

# ==============================================================================
# TR L1 VALIDATORS (99999)
# ==============================================================================

module "tr_l1_validators" {
  count  = var.deploy_validators ? 1 : 0
  source = "../../modules/avalanche-validators"

  environment             = "dev"
  region                  = "TR"
  l1_name                 = "ova-tr"
  l1_chain_id             = 99999
  validator_count         = 2  # Minimum for testing
  validator_instance_type = "t3.large"  # Cost-effective for dev
  validator_disk_size_gb  = 100  # Smaller for dev

  vpc_id            = module.aws_eu.vpc_id
  subnet_ids        = module.aws_eu.private_subnet_ids
  ssh_key_name      = var.validator_ssh_key_name
  allowed_ssh_cidrs = var.allowed_ssh_cidrs

  avalanche_version = "1.11.0"
  enable_monitoring = true
  alert_email       = var.alert_email

  tags = {
    Project = "ova"
    Chain   = "tr-l1"
  }
}

# ==============================================================================
# EU L1 VALIDATORS (99998)
# ==============================================================================

module "eu_l1_validators" {
  count  = var.deploy_validators ? 1 : 0
  source = "../../modules/avalanche-validators"

  environment             = "dev"
  region                  = "EU"
  l1_name                 = "ova-eu"
  l1_chain_id             = 99998
  validator_count         = 2  # Minimum for testing
  validator_instance_type = "t3.large"
  validator_disk_size_gb  = 100

  vpc_id            = module.aws_eu.vpc_id
  subnet_ids        = module.aws_eu.private_subnet_ids
  ssh_key_name      = var.validator_ssh_key_name
  allowed_ssh_cidrs = var.allowed_ssh_cidrs

  avalanche_version = "1.11.0"
  enable_monitoring = true
  alert_email       = var.alert_email

  tags = {
    Project = "ova"
    Chain   = "eu-l1"
  }
}

# ==============================================================================
# OUTPUTS
# ==============================================================================

output "tr_l1_validator_sg_id" {
  description = "TR L1 validator security group ID"
  value       = var.deploy_validators ? module.tr_l1_validators[0].security_group_id : null
}

output "eu_l1_validator_sg_id" {
  description = "EU L1 validator security group ID"
  value       = var.deploy_validators ? module.eu_l1_validators[0].security_group_id : null
}

output "tr_l1_asg_name" {
  description = "TR L1 Auto Scaling Group name"
  value       = var.deploy_validators ? module.tr_l1_validators[0].autoscaling_group_name : null
}

output "eu_l1_asg_name" {
  description = "EU L1 Auto Scaling Group name"
  value       = var.deploy_validators ? module.eu_l1_validators[0].autoscaling_group_name : null
}
