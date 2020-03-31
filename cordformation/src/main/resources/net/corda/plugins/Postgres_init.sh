#!/usr/bin/env bash
# Postgres database initialisation script when using Docker images

dbUser=${POSTGRES_USER:-"myuser"}
dbPassword=${POSTGRES_PASSWORD:-"mypassword"}
dbSchema=${POSTGRES_DB_SCHEMA:-"myschema"}
dbName=${POSTGRES_DB:-"mydb"}

psql -v ON_ERROR_STOP=1 --username "$dbUser"  --dbname "$dbName" <<-EOSQL
        CREATE SCHEMA $dbSchema;
        GRANT USAGE, CREATE ON SCHEMA $dbSchema TO $dbUser;
        GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA $dbSchema TO $dbUser;
        ALTER DEFAULT privileges IN SCHEMA $dbSchema GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO $dbUser;
        GRANT USAGE, SELECT ON ALL sequences IN SCHEMA $dbSchema TO $dbUser;
        ALTER DEFAULT privileges IN SCHEMA $dbSchema GRANT USAGE, SELECT ON sequences TO $dbUser;
        ALTER ROLE $dbUser SET search_path = $dbSchema;
EOSQL