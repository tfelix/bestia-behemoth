---
name: attribution
description: Add a Title/Author/Source/License (TASL) attribution entry to bestia-client/src/CREDITS.txt for a third-party asset, given its source URL. Use when the user runs "/attribution <url>" or asks to credit an asset, shader, texture, model, font, or sound used in the Godot client. Triggers on: /attribution, CREDITS.txt, TASL, attribution, credit this asset, add credit.
---

# Add an Asset Attribution

Given a source URL (e.g. an itch.io, OpenGameArt, Kenney, or similar asset page), fetch
the page and add a TASL attribution block to `bestia-client/src/CREDITS.txt`, per the
Creative Commons recommended attribution practices
(https://wiki.creativecommons.org/wiki/Recommended_practices_for_attribution).

## How to run

1. Fetch the page at the given URL.
2. Extract:
   - **Title** — the asset's name
   - **Author** — the creator's name/handle
   - **Source** — the URL itself
   - **License** — the license it's released under (MIT, CC0, CC-BY, etc.)
3. Append a new block to `bestia-client/src/CREDITS.txt`, separated from the previous
   entry by a blank line:

   ```
   Title: <title>
   Author: <author>
   Source: <url>
   License: <license>
   ```

## Important notes

- **Many asset sites (itch.io in particular) block automated fetches with a 403**, even
  with browser-like headers. Do not route around this with scraping proxies (e.g.
  `r.jina.ai`) or alternate mirrors — fetch the URL directly, and if that fails, ask the
  user for the missing fields instead of working around the block.
- Never fabricate a license or author — if a field can't be confirmed, ask rather than
  guess.
- Keep one TASL block per asset; don't merge multiple assets into a single block.
