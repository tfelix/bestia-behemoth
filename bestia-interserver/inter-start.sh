#!/bin/sh

DESC="Bestia Interserver"
DAEMONUSER=bestia
PIDFILE=/home/bestia/inter.PID
JAR="/home/bestia/bestia-interserver.jar"

/sbin/start-stop-daemon --start --quiet --user $DAEMONUSER --make-pidfile --pidfile $PIDFILE --background --exec /usr/bin/java -- -Dlog4j.configurationFile=log4j2-prod.xml -jar $JAR

#/sbin/start-stop-daemon --stop --quiet --user $DAEMONUSER --make-pidfile --pidfile $PIDFILE --background --exec /usr/bin/java -- -Dlog4j.configurationFile=log4j2-prod.xml -jar $JAR