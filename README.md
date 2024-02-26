## Set up
- [Docker](https://docs.docker.com/get-docker/)
- [Docker Compose](https://docs.docker.com/compose/install/)
- [Scala CLI](https://scala-cli.virtuslab.org/)

## Development
- Get the .env file
- Start a local Postgres and Redis server using `make postgres-up`
- (Optional) Start a local [gridoai-ml](https://github.com/gridoai/gridoai-ml) instance for faster and cheaper development
- Run `make dev`

## Deploying
- Set KEY_PATH to the path of your private key file
- Run `make deploy`

## Changing LLM settings
- Start the litellm server using `litellm --config litellm-config.yaml`. This will print in which port the server is running.
- Set the litellm endpoint in the .env file, e.g. `OPENAI_ENDPOINT=http://0.0.0.0:46822`
  
## Testing
- Create a test.env file
  - USE_MOCK_LLM should be set to true
  - Redis and Postgres connection strings should be set to the test containers
  - Embedding api endpoint should be set to a local instance of gridoai-ml
- Start the API server using `make dev`
- Start the test containers using `docker compose up`
- Run `make test` to run the tests
