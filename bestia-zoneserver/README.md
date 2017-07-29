# Bestia-Zoneserver

The zoneserver is the central game server for the Bestia game. It provides all needed buisness logic in order to play the game.

## Installation

In order to perform automatic startup of the server systemd start scripts are provided. In order to start the server automatically place the scripts under `/etc/systemd/system` and then do the following:

```
systemctl enable bestia-zone.service
```

The Bestia  bestia-zoneserver.jar file must be placed in `/opt/bestia-zone/` to install it.