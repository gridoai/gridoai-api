#!/bin/bash
source /home/ubuntu/.bashrc
export JAVA_HOME=/home/ubuntu/.sdkman/candidates/java/current
killall -9 java
while true
do
	if [[ $(curl localhost:8080/health -m 5 -s) == "OK" ]]; then
		echo "API is up."
	else
		echo "API is down. Restarting..."
		killall -9 java
		export $(grep -v '^#' .env | xargs) && ./app &
	fi
	sleep 5
done