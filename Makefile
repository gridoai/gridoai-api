
deploy:
  rm target/scala-3.2.2/api_3-0.1.0-SNAPSHOT.jar
  gcloud functions deploy api \
    --entry-point com.programandonocosmos.ScalaHttpFunction \
    --runtime java17 \
    --trigger-http \
    --allow-unauthenticated \
    --memory=256MB \
    --source=target/scala-3.2.2/