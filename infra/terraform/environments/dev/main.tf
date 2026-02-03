terraform {
  required_version = ">= 1.6"

  backend "s3" {
    bucket = "ova-terraform-state"
    key    = "dev/terraform.tfstate"
    region = "eu-central-1"
  }
}

module "aws_eu" {
  source = "../../modules/aws-eu"

  environment     = "dev"
  project_name    = "ova"
  vpc_cidr        = "10.0.0.0/16"

  # Smaller instances for dev
  eks_node_instance_type = "t3.medium"
  eks_node_count         = 2
  rds_instance_class     = "db.t3.medium"
  redis_node_type        = "cache.t3.micro"
}

# TR environment optional for dev — uncomment when needed
# module "azure_tr" {
#   source = "../../modules/azure-tr"
#
#   environment  = "dev"
#   project_name = "ova"
#   location     = "Turkey Central"
# }

output "eks_cluster_endpoint" {
  value = module.aws_eu.eks_cluster_endpoint
}

output "rds_endpoint" {
  value     = module.aws_eu.rds_endpoint
  sensitive = true
}

output "redis_endpoint" {
  value = module.aws_eu.redis_endpoint
}

output "ecr_repository_urls" {
  value = module.aws_eu.ecr_repository_urls
}
