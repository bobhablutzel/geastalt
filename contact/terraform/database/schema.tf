# Create the contact schema
resource "postgresql_schema" "contact" {
  name     = "contact"
  database = var.db_name
  owner    = var.db_username
}

# Grant usage on schema to the application user
resource "postgresql_grant" "schema_usage" {
  database    = var.db_name
  role        = var.db_username
  schema      = postgresql_schema.contact.name
  object_type = "schema"
  privileges  = ["USAGE", "CREATE"]
}

# Execute DDL to create tables
resource "null_resource" "create_tables" {
  depends_on = [postgresql_schema.contact, postgresql_grant.schema_usage]

  provisioner "local-exec" {
    command = <<-EOT
      PGPASSWORD='${var.db_password}' psql -h ${var.db_host} -p ${var.db_port} -U ${var.db_username} -d ${var.db_name} -f ${path.module}/sql/postgresql/001_create_contacts_table.sql
    EOT
  }

  triggers = {
    schema_id = postgresql_schema.contact.id
    sql_hash  = filemd5("${path.module}/sql/postgresql/001_create_contacts_table.sql")
  }
}

# Create standardized_addresses table
resource "null_resource" "create_standardized_addresses_table" {
  depends_on = [null_resource.create_tables]

  provisioner "local-exec" {
    command = <<-EOT
      PGPASSWORD='${var.db_password}' psql -h ${var.db_host} -p ${var.db_port} -U ${var.db_username} -d ${var.db_name} -f ${path.module}/sql/postgresql/002_create_standardized_addresses_table.sql
    EOT
  }

  triggers = {
    depends_on = null_resource.create_tables.id
    sql_hash   = filemd5("${path.module}/sql/postgresql/002_create_standardized_addresses_table.sql")
  }
}

# Create contact_addresses table
resource "null_resource" "create_contact_addresses_table" {
  depends_on = [null_resource.create_standardized_addresses_table]

  provisioner "local-exec" {
    command = <<-EOT
      PGPASSWORD='${var.db_password}' psql -h ${var.db_host} -p ${var.db_port} -U ${var.db_username} -d ${var.db_name} -f ${path.module}/sql/postgresql/003_create_contact_addresses_table.sql
    EOT
  }

  triggers = {
    depends_on = null_resource.create_standardized_addresses_table.id
    sql_hash   = filemd5("${path.module}/sql/postgresql/003_create_contact_addresses_table.sql")
  }
}

# Create contact_emails table
resource "null_resource" "create_contact_emails_table" {
  depends_on = [null_resource.create_tables]

  provisioner "local-exec" {
    command = <<-EOT
      PGPASSWORD='${var.db_password}' psql -h ${var.db_host} -p ${var.db_port} -U ${var.db_username} -d ${var.db_name} -f ${path.module}/sql/postgresql/004_create_contact_emails_table.sql
    EOT
  }

  triggers = {
    depends_on = null_resource.create_tables.id
    sql_hash   = filemd5("${path.module}/sql/postgresql/004_create_contact_emails_table.sql")
  }
}

# Create contact_phones table
resource "null_resource" "create_contact_phones_table" {
  depends_on = [null_resource.create_tables]

  provisioner "local-exec" {
    command = <<-EOT
      PGPASSWORD='${var.db_password}' psql -h ${var.db_host} -p ${var.db_port} -U ${var.db_username} -d ${var.db_name} -f ${path.module}/sql/postgresql/005_create_contact_phones_table.sql
    EOT
  }

  triggers = {
    depends_on = null_resource.create_tables.id
    sql_hash   = filemd5("${path.module}/sql/postgresql/005_create_contact_phones_table.sql")
  }
}
