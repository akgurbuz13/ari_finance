terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.30"
    }
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.85"
    }
  }

  backend "s3" {
    bucket         = "ova-terraform-state"
    key            = "global/terraform.tfstate"
    region         = "eu-central-1"
    encrypt        = true
    dynamodb_table = "ova-terraform-locks"
  }
}

provider "aws" {
  region = "eu-central-1"

  default_tags {
    tags = {
      Project     = "ova"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

provider "azurerm" {
  features {}
}

variable "environment" {
  type        = string
  description = "Deployment environment (dev, staging, prod)"
  default     = "dev"
}

module "aws_eu" {
  source      = "./modules/aws-eu"
  environment = var.environment
}

module "azure_tr" {
  source      = "./modules/azure-tr"
  environment = var.environment
}
