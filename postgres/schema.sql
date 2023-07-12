create extension if not exists "uuid-ossp";
create extension vector;

create type public.embedding_model AS ENUM (
  'text-embeddings-ada-002',
  'text-embeddings-bert-002',
  'text-embeddings-bert-multilingual-002',
  'text-gecko',
  'instructor-large',
  'mocked'
);

create table
  public.documents (
    uid uuid not null default uuid_generate_v4 (),
    name text not null,
    source text not null,
    content text not null,
    token_quantity integer not null,
    organization text null,
    roles text[] not null default '{}'::text[],
    created_at timestamp with time zone not null default now(),
    constraint documents_pkey primary key (uid)
  ) tablespace pg_default;

create table
  public.chunks (
    uid uuid not null default uuid_generate_v4 (),
    document_uid uuid not null,
    document_name text not null,
    document_source text not null,
    content text not null,
    embedding public.vector not null,
    embedding_model public.embedding_model null default 'instructor-large'::embedding_model,
    token_quantity integer not null,
    document_organization text null,
    document_roles text[] not null default '{}'::text[],
    created_at timestamp with time zone not null default now(),
    constraint chunks_pkey primary key (uid)
  ) tablespace pg_default;