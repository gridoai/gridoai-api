#!/bin/bash
source /home/ubuntu/.bashrc
export JAVA_OPTS="-Xms512m -Xmx20g -Dcom.sun.management.jmxremote.port=9010 -Dcom.sun.management.jmxremote.rmi.port=9010 -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=api.gridoai.com"

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