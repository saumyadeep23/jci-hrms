variable "aws_region" {
  description = "AWS region. Kept fixed to Mumbai for data residency."
  type        = string
  default     = "ap-south-1"
}

variable "project_name" {
  description = "Short project name used as a prefix for resource names."
  type        = string
  default     = "jci-hrms"
}

variable "environment" {
  description = "Environment name, e.g. dev, staging, prod."
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (ALB, NAT gateway)."
  type        = list(string)
  default     = ["10.20.0.0/24", "10.20.1.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets (ECS tasks, RDS)."
  type        = list(string)
  default     = ["10.20.10.0/24", "10.20.11.0/24"]
}

variable "availability_zones" {
  description = "AZs to spread subnets across."
  type        = list(string)
  default     = ["ap-south-1a", "ap-south-1b"]
}

variable "db_name" {
  description = "Initial database name."
  type        = string
  default     = "jcihrms"
}

variable "db_username" {
  description = "Master username for RDS PostgreSQL."
  type        = string
  default     = "jcihrms_admin"
}

variable "db_password" {
  description = "Master password for RDS PostgreSQL. Pass via TF_VAR_db_password or a tfvars file that is NOT committed to git."
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance class. Small/cheap for dev; upsize for staging/prod."
  type        = string
  default     = "db.t4g.micro"
}

variable "container_port" {
  description = "Port the backend container listens on."
  type        = number
  default     = 8080
}

variable "backend_image_tag" {
  description = "Docker image tag to deploy. Overridden by the CI/CD pipeline on each build."
  type        = string
  default     = "latest"
}

variable "acm_certificate_arn" {
  description = "ACM certificate ARN for the ALB's HTTPS listener. Leave empty to run HTTP-only (e.g. before a domain/cert exists in dev)."
  type        = string
  default     = ""
}
