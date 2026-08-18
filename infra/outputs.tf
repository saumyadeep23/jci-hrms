output "vpc_id" {
  value = aws_vpc.main.id
}

output "alb_dns_name" {
  description = "URL to hit once the service is deployed, e.g. http://<this>/actuator/health"
  value       = aws_lb.main.dns_name
}

output "ecr_repository_url" {
  description = "Push backend Docker images here. Used by the CI/CD pipeline."
  value       = aws_ecr_repository.backend.repository_url
}

output "rds_endpoint" {
  value     = aws_db_instance.main.endpoint
  sensitive = true
}

output "s3_documents_bucket" {
  value = aws_s3_bucket.documents.bucket
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.backend.name
}
