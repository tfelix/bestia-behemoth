# Bestia-InMemoryServer

The in-memory database server (Hazelcast) is used for fast and scalable access to saved entities which is needed for operating the game.

## Installation

In order to perform automatic startup of the server systemd start scripts are provided. In order to start the server automatically place the scripts under `/etc/systemd/system` and then do the following:

```
systemctl enable bestia-memdb.service
```

The Bestia  bestia-memdb.jar file must be placed in `/opt/bestia-memdb/` to install it.