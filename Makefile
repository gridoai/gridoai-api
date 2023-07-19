postgres-up:
	@cd postgres; cat migrations/*/up.sql >> schema.sql; docker compose up