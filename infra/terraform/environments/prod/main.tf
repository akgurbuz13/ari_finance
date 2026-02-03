terraform {
  required_version = ">= 1.6"

  backend "s3" {
    bucket         = "ova-terraform-state"
    key            = "prod/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "ova-terraform-lock"
    encrypt        = true
  }
}

module "aws_eu" {
  source = "../../modules/aws-eu"

  environment     = "prod"
  project_name    = "ova"
  vpc_cidr        = "10.2.0.0/16"

  eks_node_instance_type = "m6i.xlarge"
  eks_node_count         = 3
  rds_instance_class     = "db.r6g.xlarge"
  redis_node_type        = "cache.r6g.large"
}

module "azure_tr" {
  source = "../../modules/azure-tr"

  environment  = "prod"
  project_name = "ova"
  location     = "Turkey Central"
}

output "eu_eks_cluster_endpoint" {
  value = module.aws_eu.eks_cluster_endpoint
}

output "tr_aks_cluster_name" {
  value = module.azure_tr.aks_cluster_name
}
