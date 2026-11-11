-- Runs once, on first start of an empty data volume (docker-entrypoint-initdb.d).
-- The database and user are created by the image from POSTGRES_DB / POSTGRES_USER.
-- The schema itself is in 02-schema.sql.
ALTER DATABASE housecleaning SET timezone TO 'UTC';
