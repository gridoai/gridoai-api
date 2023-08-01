ALTER TYPE {schema}.embedding_model RENAME TO {schema}.embedding_model_old;
create type {schema}.embedding_model AS ENUM (
  'TextEmbeddingsAda002',
  'TextEmbeddingsBert002',
  'TextEmbeddingsBertMultilingual002',
  'TextGecko',
  'InstructorLarge',
  'Mocked'
);
ALTER TABLE {schema}.chunks ALTER COLUMN embedding_model TYPE {schema}.embedding_model USING embedding_model::text::{schema}.embedding_model;
DROP TYPE {schema}.embedding_model_old;