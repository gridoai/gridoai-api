postgres-up:
	@cd postgres; cat migrations/*/up.sql > schema.sql; docker compose up

submit-img:
	@gcloud builds submit --tag gcr.io/lucid-arch-387422/gridoai-api --project lucid-arch-387422

deploy: 
	@gcloud run deploy gridoai-api --image gcr.io/lucid-arch-387422/gridoai-api --platform managed --region=southamerica-east1 --allow-unauthenticated --memory=512Mi --project lucid-arch-387422

makejar:
	@sbt assembly; cp ./target/scala-3.3.0/API-assembly-0.1.0-SNAPSHOT.jar ./deployment/app.jar 

redeploy: makejar submit-img deploy

start: 
	export $(grep -v '^#' .env | xargs) && java -jar target/scala-3.3.0/API-assembly-0.1.0-SNAPSHOT.jar

