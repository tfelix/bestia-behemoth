# Bestia Companion

The companion web app: an installable PWA for looking in on your Masters and Bestia while you are away from the
game. It talks JSON to `api-service` (`/v1/companion/...`) and signs in through `login-server`
(`/api/v1/auth/companion/...`). Design: bestia-docs `mechanics/companion-app.md` and `server/api-service.md`.

## Running it

Start the servers first, zone before api-service (the zone's compose file brings up Valkey):

```
./gradlew :zone-server:bootRun
./gradlew :login-server:bootRun
./gradlew :api-service:bootRun
```

The game client needs the api-service as well: its map comes from there, not from the zone.

Then the app on http://localhost:5173, whose dev server proxies `/api/v1/auth` and `/v1/companion` to the servers:

```
npm install
npm run dev
```

Signing in uses a passkey on the login server's page, as in the game. Notifications need a real browser that
allows them; on iPhone and iPad only once the app is on the Home Screen.

## Checks

`npm run check` (svelte-check) and `npm test` (vitest), or `./gradlew companionCheck` from the repository root.

`src/lib/api/schema.d.ts` is generated from the api-service's OpenAPI document. After changing a REST DTO:

```
./gradlew :api-service:test --tests net.bestia.api.OpenApiDocumentTest
npm run gen:api
```

## Names

Item and bestia names come from the game client's own tables (`bestia-client/src/Localization/items.csv` and
`bestias.csv`), read at build time by the `bestia-names` plugin in `vite.config.ts`. The server only sends
identifiers.

## Assets

- **Open Sans SemiBold** (`static/fonts/`), Steve Matteson, Apache-2.0 - copied from the game client.
- **Tabler Icons** (`src/lib/icons/`), tabler.io, MIT - the game client's set.
- **Cinzel** (`@fontsource/cinzel`), The Cinzel Project Authors, SIL Open Font License 1.1.
- **Leaflet** (`leaflet`), BSD-2-Clause.
- The app icons in `static/icons/` are the repository's own `icon.png`, resized.
