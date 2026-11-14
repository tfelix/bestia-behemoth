# Contributing to Bestia Behemoth

Thank you for helping. This file covers what you need to open a good pull request. The game design and server
architecture are documented at [docs.bestia-game.net](https://docs.bestia-game.net); read the design guidelines there
before changing gameplay.

## Commit messages

Commits use [Conventional Commit](https://www.conventionalcommits.org) prefixes: `feat`, `fix`, `perf`, `refactor`,
`docs`, `test`, `build`, `ci`, `chore`, `style` or `revert`, optionally with a scope and a `!` for breaking changes,
for example `fix(zone): stop dropping ticks under load`.

Release Please builds the changelog from these prefixes, and CI rejects a pull request that has a commit without one.

## Running tests

`./gradlew :zone-server:test` is the default while working; it takes about 3 minutes.

| Change touches | Run |
|---|---|
| `worldgen/`, a world generation stage or its configuration | `./gradlew :worldgen:test` (about 5 minutes, uses most of the machine; run it on its own) |
| `bus/` or `api-server/` | `./gradlew :bus:test` / `:api-server:test` (need Docker) |
| `companion-app/` | `./gradlew companionCheck` (needs Node 22+) |

After changing a REST DTO, regenerate the app's types: run
`./gradlew :api-server:test --tests net.bestia.api.OpenApiDocumentTest`, then `npm run gen:api` in `companion-app/`.

Before handing work over, run the whole suite once; `./gradlew test` includes the full worldgen suite.

## Protobuf messages

After editing a `.proto` file, regenerate the client's C# classes by running `gen-protobuf.bat` in
`bnet-messages/`, and commit the generated files together with the change.

## Third-party assets

Anything you add that you did not make yourself (models, textures, fonts, sounds, shaders) needs its source and
licence recorded in `bestia-client/src/CREDITS.txt` and, where it applies, in [THIRD-PARTY.md](THIRD-PARTY.md).

## License

By contributing you agree that your work is licensed under the [GNU General Public License v3.0](LICENSE).
