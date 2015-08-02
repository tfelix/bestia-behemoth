#!/bin/sh

DESC="Bestia Zoneserver"
DAEMONUSER=bestia
PIDFILE=/home/bestia/zone.PID
DEAMON="java -jar bestia-zoneserver.jar"
DAEMON_ARGS="-Dlog4j.configuration=log4j-prod.xml"

/sbin/start-stop-daemon --start --quiet --chuid $DAEMONUSER    \
 --make-pidfile --pidfile $PIDFILE --background       \
 --startas /bin/bash -- -c "exec $DAEMON $DAEMON_ARGS"

