output "schema_name" {
  description = "The name of the created schema"
  value       = postgresql_schema.member.name
}

output "database_name" {
  description = "The database name"
  value       = var.db_name
}

output "environment" {
  description = "The environment this was deployed to"
  value       = var.environment
}
