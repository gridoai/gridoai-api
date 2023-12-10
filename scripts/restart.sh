source /home/ubuntu/.bashrc
killall -9 java
export JAVA_HOME=/home/ubuntu/.sdkman/candidates/java/current
export $(grep -v '^#' .env | xargs) && ./app &