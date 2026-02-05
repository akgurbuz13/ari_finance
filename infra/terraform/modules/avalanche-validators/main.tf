# Avalanche L1 Validator Infrastructure Module
# Deploys self-hosted validators for Ova's permissioned Avalanche L1 chains

terraform {
  required_version = ">= 1.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
    azurerm = {
      source  = "hashicorp/azurerm"
      version = ">= 3.0"
    }
  }
}

# ============================================================
# VARIABLES
# ============================================================

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "region" {
  description = "Ova region (TR or EU)"
  type        = string
  validation {
    condition     = contains(["TR", "EU"], var.region)
    error_message = "Region must be TR or EU."
  }
}

variable "l1_name" {
  description = "Name of the L1 chain"
  type        = string
  default     = "ova-l1"
}

variable "l1_chain_id" {
  description = "Chain ID for the L1"
  type        = number
}

variable "validator_count" {
  description = "Number of validators to deploy"
  type        = number
  default     = 3
  validation {
    condition     = var.validator_count >= 3 && var.validator_count <= 10
    error_message = "Validator count must be between 3 and 10."
  }
}

variable "validator_instance_type" {
  description = "Instance type for validators"
  type        = string
  default     = "m5.xlarge"  # 4 vCPU, 16 GB RAM
}

variable "validator_disk_size_gb" {
  description = "Disk size for validators in GB"
  type        = number
  default     = 500
}

variable "vpc_id" {
  description = "VPC ID for deploying validators"
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for validators (should span multiple AZs)"
  type        = list(string)
}

variable "ssh_key_name" {
  description = "SSH key pair name for EC2 instances"
  type        = string
}

variable "allowed_ssh_cidrs" {
  description = "CIDR blocks allowed to SSH to validators"
  type        = list(string)
  default     = []
}

variable "avalanche_version" {
  description = "AvalancheGo version"
  type        = string
  default     = "1.11.0"  # Post-Etna version
}

variable "enable_monitoring" {
  description = "Enable Prometheus/Grafana monitoring"
  type        = bool
  default     = true
}

variable "alert_email" {
  description = "Email for CloudWatch alerts"
  type        = string
  default     = ""
}

variable "tags" {
  description = "Additional tags for resources"
  type        = map(string)
  default     = {}
}

# ============================================================
# LOCALS
# ============================================================

locals {
  name_prefix = "${var.environment}-${var.l1_name}-${lower(var.region)}"

  common_tags = merge(var.tags, {
    Environment = var.environment
    Region      = var.region
    Component   = "avalanche-validator"
    ManagedBy   = "terraform"
  })

  # Avalanche ports
  staking_port = 9651
  http_port    = 9650
  metrics_port = 9652
}

# ============================================================
# SECURITY GROUP
# ============================================================

resource "aws_security_group" "validator" {
  name_prefix = "${local.name_prefix}-validator-"
  description = "Security group for Avalanche validators"
  vpc_id      = var.vpc_id

  # Staking port - only from other validators
  ingress {
    description = "Avalanche staking port"
    from_port   = local.staking_port
    to_port     = local.staking_port
    protocol    = "tcp"
    self        = true
  }

  # HTTP API - internal only
  ingress {
    description = "Avalanche HTTP API"
    from_port   = local.http_port
    to_port     = local.http_port
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/8"]  # Internal VPC only
  }

  # Metrics - internal only
  ingress {
    description = "Prometheus metrics"
    from_port   = local.metrics_port
    to_port     = local.metrics_port
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/8"]
  }

  # SSH - restricted
  dynamic "ingress" {
    for_each = length(var.allowed_ssh_cidrs) > 0 ? [1] : []
    content {
      description = "SSH access"
      from_port   = 22
      to_port     = 22
      protocol    = "tcp"
      cidr_blocks = var.allowed_ssh_cidrs
    }
  }

  # All outbound
  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-validator-sg"
  })

  lifecycle {
    create_before_destroy = true
  }
}

# ============================================================
# IAM ROLE FOR VALIDATORS
# ============================================================

resource "aws_iam_role" "validator" {
  name_prefix = "${local.name_prefix}-validator-"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy" "validator_secrets" {
  name_prefix = "${local.name_prefix}-secrets-"
  role        = aws_iam_role.validator.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = [
          "arn:aws:secretsmanager:*:*:secret:ova/${var.environment}/validator/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt"
        ]
        Resource = [
          "arn:aws:kms:*:*:key/*"
        ]
        Condition = {
          StringEquals = {
            "kms:ViaService" = "secretsmanager.*.amazonaws.com"
          }
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "validator_cloudwatch" {
  name_prefix = "${local.name_prefix}-cloudwatch-"
  role        = aws_iam_role.validator.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "cloudwatch:PutMetricData"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_instance_profile" "validator" {
  name_prefix = "${local.name_prefix}-validator-"
  role        = aws_iam_role.validator.name
}

# ============================================================
# LAUNCH TEMPLATE
# ============================================================

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]  # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_launch_template" "validator" {
  name_prefix   = "${local.name_prefix}-validator-"
  image_id      = data.aws_ami.ubuntu.id
  instance_type = var.validator_instance_type
  key_name      = var.ssh_key_name

  iam_instance_profile {
    arn = aws_iam_instance_profile.validator.arn
  }

  network_interfaces {
    associate_public_ip_address = false
    security_groups             = [aws_security_group.validator.id]
  }

  block_device_mappings {
    device_name = "/dev/sda1"
    ebs {
      volume_size           = var.validator_disk_size_gb
      volume_type           = "gp3"
      iops                  = 3000
      throughput            = 125
      encrypted             = true
      delete_on_termination = false
    }
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"  # IMDSv2 required
    http_put_response_hop_limit = 1
  }

  monitoring {
    enabled = true
  }

  user_data = base64encode(templatefile("${path.module}/templates/validator-userdata.sh", {
    environment        = var.environment
    region             = var.region
    l1_name            = var.l1_name
    l1_chain_id        = var.l1_chain_id
    avalanche_version  = var.avalanche_version
    staking_port       = local.staking_port
    http_port          = local.http_port
    metrics_port       = local.metrics_port
    enable_monitoring  = var.enable_monitoring
  }))

  tags = local.common_tags

  tag_specifications {
    resource_type = "instance"
    tags = merge(local.common_tags, {
      Name = "${local.name_prefix}-validator"
    })
  }

  tag_specifications {
    resource_type = "volume"
    tags = merge(local.common_tags, {
      Name = "${local.name_prefix}-validator-volume"
    })
  }
}

# ============================================================
# AUTO SCALING GROUP
# ============================================================

resource "aws_autoscaling_group" "validators" {
  name_prefix         = "${local.name_prefix}-validators-"
  desired_capacity    = var.validator_count
  max_size            = var.validator_count
  min_size            = var.validator_count
  vpc_zone_identifier = var.subnet_ids

  launch_template {
    id      = aws_launch_template.validator.id
    version = "$Latest"
  }

  health_check_type         = "EC2"
  health_check_grace_period = 300

  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 66  # Keep 2/3 running during refresh
    }
  }

  dynamic "tag" {
    for_each = merge(local.common_tags, { Name = "${local.name_prefix}-validator" })
    content {
      key                 = tag.key
      value               = tag.value
      propagate_at_launch = true
    }
  }

  lifecycle {
    create_before_destroy = true
  }
}

# ============================================================
# CLOUDWATCH ALARMS
# ============================================================

resource "aws_cloudwatch_metric_alarm" "validator_cpu" {
  count = var.enable_monitoring ? 1 : 0

  alarm_name          = "${local.name_prefix}-validator-high-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Validator CPU utilization is high"
  alarm_actions       = var.alert_email != "" ? [aws_sns_topic.alerts[0].arn] : []

  dimensions = {
    AutoScalingGroupName = aws_autoscaling_group.validators.name
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "validator_disk" {
  count = var.enable_monitoring ? 1 : 0

  alarm_name          = "${local.name_prefix}-validator-low-disk"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 2
  metric_name         = "disk_used_percent"
  namespace           = "CWAgent"
  period              = 300
  statistic           = "Average"
  threshold           = 20  # Alert when less than 20% free
  alarm_description   = "Validator disk space is low"
  alarm_actions       = var.alert_email != "" ? [aws_sns_topic.alerts[0].arn] : []

  dimensions = {
    AutoScalingGroupName = aws_autoscaling_group.validators.name
    path                 = "/"
  }

  tags = local.common_tags
}

# SNS Topic for alerts
resource "aws_sns_topic" "alerts" {
  count = var.enable_monitoring && var.alert_email != "" ? 1 : 0

  name_prefix = "${local.name_prefix}-alerts-"
  tags        = local.common_tags
}

resource "aws_sns_topic_subscription" "alerts_email" {
  count = var.enable_monitoring && var.alert_email != "" ? 1 : 0

  topic_arn = aws_sns_topic.alerts[0].arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# ============================================================
# OUTPUTS
# ============================================================

output "security_group_id" {
  description = "Security group ID for validators"
  value       = aws_security_group.validator.id
}

output "autoscaling_group_name" {
  description = "Auto Scaling Group name"
  value       = aws_autoscaling_group.validators.name
}

output "iam_role_arn" {
  description = "IAM role ARN for validators"
  value       = aws_iam_role.validator.arn
}

output "launch_template_id" {
  description = "Launch template ID"
  value       = aws_launch_template.validator.id
}
