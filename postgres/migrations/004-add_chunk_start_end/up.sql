alter table {schema}.chunks add column start_pos integer not null default 0;
alter table {schema}.chunks add column end_pos integer not null default 0;