# Contact Database Schema

Terraform configuration for managing the contact database schema across environments.

## Prerequisites

- Terraform >= 1.0
- PostgreSQL client (`psql`) installed locally
- Database credentials with schema creation privileges

## Usage

### Initialize Terraform

```bash
cd terraform/database
terraform init
```

### Deploy to an environment

```bash
# Dev environment
export TF_VAR_db_password="your-password"
terraform apply -var-file=environments/dev.tfvars

# QA environment
export TF_VAR_db_password="your-password"
terraform apply -var-file=environments/qa.tfvars

# Production environment
export TF_VAR_db_password="your-password"
terraform apply -var-file=environments/production.tfvars
```

### Or provide password inline (not recommended for production)

```bash
terraform apply -var-file=environments/dev.tfvars -var="db_password=your-password"
```

## Structure

```
terraform/database/
├── main.tf                 # Provider configuration
├── variables.tf            # Variable definitions
├── schema.tf               # Schema and table resources
├── outputs.tf              # Output values
├── sql/
│   └── 001_create_contacts_table.sql  # DDL script
└── environments/
    ├── dev.tfvars          # Dev environment variables
    ├── qa.tfvars           # QA environment variables
    └── production.tfvars   # Production environment variables
```

## Tables

### contact.contacts

| Column     | Type                     | Description           |
|------------|-------------------------|-----------------------|
| id         | BIGINT (identity)       | Primary key           |
| email      | VARCHAR(255)            | Contact email          |
| first_name | VARCHAR(255)            | First name            |
| last_name  | VARCHAR(255)            | Last name             |
| created_at | TIMESTAMP WITH TIME ZONE| Record creation time  |
| updated_at | TIMESTAMP WITH TIME ZONE| Last update time      |

## Adding New Tables

1. Create a new SQL file in `sql/` directory (e.g., `002_create_new_table.sql`)
2. Add a new `null_resource` in `schema.tf` to execute the script
3. Run `terraform apply` for each environment
