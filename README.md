## Set up
- [Docker](https://docs.docker.com/get-docker/)
- [Docker Compose](https://docs.docker.com/compose/install/)
- [Scala CLI](https://scala-cli.virtuslab.org/)

## Development
- Get the .env file
- Start a local Postgres and Redis server using `make postgres-up`
- (Optional) Start a local [gridoai-ml](https://github.com/gridoai/gridoai-ml) instance for faster and cheaper development
- Start the litellm server using `litellm --config litellm-config.yaml --port 8000`
- Run `make dev`

## Deploying
- Set KEY_PATH to the path of your private key file
- Run `make deploy`

## Testing
- Create a test.env file
  - USE_MOCK_LLM should be set to true
  - Redis and Postgres connection strings should be set to the test containers
  - Embedding api endpoint should be set to a local instance of gridoai-ml
- Start the API server using `make dev`
- Start the test containers using `docker compose up`
- Run `make test` to run the tests
