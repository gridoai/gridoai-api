
deploy:
  rm target/scala-3.2.2/api_3-0.1.0-SNAPSHOT.jar
  gcloud functions deploy api \
    --entry-point com.programandonocosmos.ScalaHttpFunction \
    --runtime java11 \
    --trigger-http \
    --allow-unauthenticated \
    --source=target/scala-3.2.2/