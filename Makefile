include .env
export

postgres-up:
	@cd postgres; cat migrations/*/up.sql > schema.sql; sed -i '' 's/{schema}/public/g' schema.sql; docker compose up

submit-img:
	@gcloud builds submit --tag gcr.io/lucid-arch-387422/gridoai-api --project lucid-arch-387422

deploy: 
	@gcloud run deploy gridoai-api --image gcr.io/lucid-arch-387422/gridoai-api --platform managed --region=southamerica-east1 --allow-unauthenticated --memory=512Mi --project lucid-arch-387422

pkg:
	@scala-cli --power package . -o ./deployment/app  -f

redeploy: makejar submit-img deploy

start: 
	./deployment/app

pushpkg:
	@echo "Running pushpkg"
	scp -i $(KEY_PATH) ./deployment/app ubuntu@api.gridoai.com:/home/ubuntu/app

restart-remote-app:
	ssh -i $(KEY_PATH) ubuntu@api.gridoai.com "nohup bash /home/ubuntu/restart.sh >> /home/ubuntu/nohup.out &" &

deploy: pkg pushpkg restart-remote-app
	
dev:
	@scala-cli --revolver .