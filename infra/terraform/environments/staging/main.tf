terraform {
  required_version = ">= 1.6"

  backend "s3" {
    bucket = "ova-terraform-state"
    key    = "staging/terraform.tfstate"
    region = "eu-central-1"
  }
}

module "aws_eu" {
  source = "../../modules/aws-eu"

  environment     = "staging"
  project_name    = "ova"
  vpc_cidr        = "10.1.0.0/16"

  eks_node_instance_type = "t3.large"
  eks_node_count         = 3
  rds_instance_class     = "db.r6g.large"
  redis_node_type        = "cache.r6g.large"
}

module "azure_tr" {
  source = "../../modules/azure-tr"

  environment  = "staging"
  project_name = "ova"
  location     = "Turkey Central"
}

output "eu_eks_cluster_endpoint" {
  value = module.aws_eu.eks_cluster_endpoint
}

output "tr_aks_cluster_name" {
  value = module.azure_tr.aks_cluster_name
}
