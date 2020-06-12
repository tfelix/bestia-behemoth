<p align="center">
  <img width="50%" src="img/logo.png">
</p>

# Bestia - Behemoth Server

[![Build Status](https://travis-ci.org/tfelix/bestia-behemoth.svg)](https://travis-ci.org/tfelix/bestia-behmoth)
[![Codecov](https://codecov.io/github/tfelix/bestia-behemoth/coverage.svg)](https://codecov.io/gh/tfelix/bestia-behemoth)
[![Awesome Kotlin Badge](https://kotlin.link/awesome-kotlin.svg)](https://github.com/KotlinBy/awesome-kotlin)

> Massive scalable game server for the [Bestia MMORPG](https://bestia-game.net) using Akka Actors and written 
> in Kotlin.

The Behemoth server is the backend game server for the Bestia MMORPG. 
It provides all needed business logic in order to play the game and is able to support huge tile maps of several thousands square kilometers and thousands of entities.

A detailed documentation about the game mechanics and server architecture can be found in the official
[documentation](https://docs.bestia-game.net).

The server is open source so bug fixes and feature can be community driven which is a declared goal
of the whole Bestia game project.

## Contributing

Contributions are always welcome! If you want to get into it a lot of code is still untested and might be
a good starting point. If you want to change or work a game related stuff please consult the 
[documentation](https://docs.bestia-game.net) first to stick to the general manifests of this game.
 
 Anyways to start up the develop environment there is a `docker-compose.yml` inside 
the `/bestia-zonserver` folder which will setup all the needed database and server infrastructure.

```
cd bestia-zoneserver
sudo docker-compose up
```

After the server has started you can startup the zoneserver. It will automatically setup all the needed 
database schemas and you can start right ahead.