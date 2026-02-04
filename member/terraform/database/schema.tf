# Create the member schema
resource "postgresql_schema" "member" {
  name     = "member"
  database = var.db_name
  owner    = var.db_username
}

# Grant usage on schema to the application user
resource "postgresql_grant" "schema_usage" {
  database    = var.db_name
  role        = var.db_username
  schema      = postgresql_schema.member.name
  object_type = "schema"
  privileges  = ["USAGE", "CREATE"]
}

# Execute DDL to create tables
resource "null_resource" "create_tables" {
  depends_on = [postgresql_schema.member, postgresql_grant.schema_usage]

  provisioner "local-exec" {
    command = <<-EOT
      PGPASSWORD='${var.db_password}' psql -h ${var.db_host} -p ${var.db_port} -U ${var.db_username} -d ${var.db_name} -f ${path.module}/sql/001_create_members_table.sql
    EOT
  }

  triggers = {
    schema_id = postgresql_schema.member.id
    sql_hash  = filemd5("${path.module}/sql/001_create_members_table.sql")
  }
}

# Create standardized_addresses table
resource "null_resource" "create_standardized_addresses_table" {
  depends_on = [null_resource.create_tables]

  provisioner "local-exec" {
    command = <<-EOT
      PGPASSWORD='${var.db_password}' psql -h ${var.db_host} -p ${var.db_port} -U ${var.db_username} -d ${var.db_name} -f ${path.module}/sql/002_create_standardized_addresses_table.sql
    EOT
  }

  triggers = {
    depends_on = null_resource.create_tables.id
    sql_hash   = filemd5("${path.module}/sql/002_create_standardized_addresses_table.sql")
  }
}

# Create member_addresses table
resource "null_resource" "create_member_addresses_table" {
  depends_on = [null_resource.create_standardized_addresses_table]

  provisioner "local-exec" {
    command = <<-EOT
      PGPASSWORD='${var.db_password}' psql -h ${var.db_host} -p ${var.db_port} -U ${var.db_username} -d ${var.db_name} -f ${path.module}/sql/003_create_member_addresses_table.sql
    EOT
  }

  triggers = {
    depends_on = null_resource.create_standardized_addresses_table.id
    sql_hash   = filemd5("${path.module}/sql/003_create_member_addresses_table.sql")
  }
}

# Create member_emails table
resource "null_resource" "create_member_emails_table" {
  depends_on = [null_resource.create_tables]

  provisioner "local-exec" {
    command = <<-EOT
      PGPASSWORD='${var.db_password}' psql -h ${var.db_host} -p ${var.db_port} -U ${var.db_username} -d ${var.db_name} -f ${path.module}/sql/004_create_member_emails_table.sql
    EOT
  }

  triggers = {
    depends_on = null_resource.create_tables.id
    sql_hash   = filemd5("${path.module}/sql/004_create_member_emails_table.sql")
  }
}

# Create member_phones table
resource "null_resource" "create_member_phones_table" {
  depends_on = [null_resource.create_tables]

  provisioner "local-exec" {
    command = <<-EOT
      PGPASSWORD='${var.db_password}' psql -h ${var.db_host} -p ${var.db_port} -U ${var.db_username} -d ${var.db_name} -f ${path.module}/sql/005_create_member_phones_table.sql
    EOT
  }

  triggers = {
    depends_on = null_resource.create_tables.id
    sql_hash   = filemd5("${path.module}/sql/005_create_member_phones_table.sql")
  }
}
