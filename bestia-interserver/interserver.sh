#!/bin/sh

DESC="Bestia Interserver"
DAEMONUSER=bestia
PIDFILE=/home/bestia/inter.PID
JAR="/home/bestia/bestia-interserver.jar"

start () {
	echo "Starting interserver..."
	/sbin/start-stop-daemon --start --quiet --user $DAEMONUSER --make-pidfile --pidfile $PIDFILE --background --exec /usr/bin/java -- -Dlog4j.configurationFile=log4j2-prod.xml -jar $JAR

}

stop () {
	echo "Stopping interserver..."
	/sbin/start-stop-daemon --stop --verbose --user $DAEMONUSER --pidfile $PIDFILE --exec /usr/bin/java -- -Dlog4j.configurationFile=log4j2-prod.xml -jar $JAR
	rm $PIDFILE
}

case $1 in
	"start") start;;
	"stop")	stop;;
	*) echo "Usage: interserver.sh start|stop" ;;
esac
