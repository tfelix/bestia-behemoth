<p align="center">
  <img width="50%" src="img/logo.png">
</p>

# Bestia - Game Client

This is the client implementation of the [Bestia Game](https://bestia-game.net).

Currently its meant as a development platform and as a small introduction game, thus currently no connection to the server
is established and it is solely a single user game. Later the server support will be added. The game is under active development
but I can only do so much. If you want to help in coding feel free to fork this repo and open some pull requests. :)

Documentation and Game Design goals can be found here in the [Bestia Developers Documentation](https://docs.bestia-game.net/).

You can also get in touch via [Discord](https://discord.gg/zZW8M2S).

## Development

The Bestia Game Client is built with the stock [Godot Engine](https://godotengine.org) (.NET/Mono
build, currently targeting 4.7) — no custom engine build or C++ modules are required anymore.
Entity synchronization with the server and voxel terrain streaming/meshing are both implemented
as regular C# code inside the project (`src/Bnet/` and `src/Game/World/` respectively) rather than
as engine modules; there is no dependency on the third-party `godot_voxel` GDExtension.

To get set up:

1. Install the [Godot 4.7 .NET/Mono build](https://godotengine.org/download) and the .NET SDK
   version it requires.
2. Open `bestia-client/src/project.godot` in the editor (or `bestia-client/src/Bestia Behemoth
   Client.sln` in your C# IDE) — that's it, no engine compilation step needed.

For more on how the client is structured internally, see the
[Client docs](https://docs.bestia-game.net/docs/client/overview).

## Contributing

* Found a bug? Report it on [GitHub Issues](https://github.com/tfelix/bestia-client/issues)
* Before submitting a Pull Request please make sure you follow the [Godot best practices](https://docs.godotengine.org/en/3.1/getting_started/workflow/best_practices/)
* If you want to extend the client please read the [Bestia Developers Documentation](https://docs.bestia-game.net/) first

Have an idea which would benefit the game and want to chat about it? Cool, tell us via our [Discord Server](https://discord.gg/zZW8M2S)!

## Art Assets

The licences of all artwork content used in this project can be found in the [ASSETS.md](ASSETS.md) file.
Great care was taken to only include artworks and assets with permissive licences. If you think there is a problem please
open an issue about it.

Without this free artwork this game would not have been possible. Thanks to all artists! Please check out their great work!

## Created By

Created with :heart: by [Thomas Felix](https://tfelix.de).

The idea of this game is in my mind since 2006. It was originaly planned as a little browsergame meant to be played
by my highschool class. Sadly it never came to a state which I was satisfied with. So the idea got dragged along and
along and slowly evolved into this game.

The first Alpha version screenshots of the Browser Game version looked like this and came from the release around 2009:

![Bestia Overview Screen](https://bestia-game.net/user/data/images/github/bestia_overview.jpg "Bestia Overview Screen")
![Inventory](https://bestia-game.net/user/data/images/github/inventory.jpg "Bestia Inventory")
![Moving Bestia in the overworld](https://bestia-game.net/user/data/images/github/move.jpg "Moving Bestia in Overview World")

Since then there was pause in development until I dreamed about new game mechanics and was complelty fed up with todays MMORPGs. The idea of Bestia was renewed and development began to speed up again in 2016.
