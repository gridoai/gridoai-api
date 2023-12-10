#!/bin/bash

killall -9 java
while true
do
	if [[ $(curl localhost:8080/health -m 5 -s) == "OK" ]]; then
		echo "API is up."
	else
		echo "API is down. Restarting..."
		bash /home/ubuntu/restart.sh
	fi
	sleep 5
done