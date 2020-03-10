CREATE SCHEMA "myschema";
GRANT USAGE, CREATE ON SCHEMA "myschema" TO "myuser";
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "myschema" TO "myuser";
ALTER DEFAULT privileges IN SCHEMA "myschema" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "myuser";
GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "myschema" TO "myuser";
ALTER DEFAULT privileges IN SCHEMA "myschema" GRANT USAGE, SELECT ON sequences TO "myuser";
ALTER ROLE "myuser" SET search_path = "myschema";
