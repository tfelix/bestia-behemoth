#!/bin/sh

DESC="Bestia Websocketserver"
DAEMONUSER=bestia
PIDFILE=/home/bestia/socket.PID
JAR="/home/bestia/bestia-webserver.jar"
CONF_FILE="/home/bestia/zone-config.properties"

start () {
	echo "Starting $DESC ..."
	/sbin/start-stop-daemon --start --quiet --user $DAEMONUSER --make-pidfile --pidfile $PIDFILE --background --exec /usr/bin/java -- -Dlog4j.configurationFile=log4j2-prod.xml -jar $JAR --configFile=$CONF_FILE
}

stop () {
	echo "Starting $DESC ..."
	/sbin/start-stop-daemon --stop --quiet --user $DAEMONUSER --pidfile $PIDFILE --exec /usr/bin/java -- -Dlog4j.configurationFile=log4j2-prod.xml -jar $JAR --configFile=$CONF_FILE
	rm $PIDFILE
}

case $1 in
	"start") start;;
	"stop")	stop;;
	*) echo "Usage: webserver.sh start|stop" ;;
esac
