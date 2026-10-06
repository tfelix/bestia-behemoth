# tools

Helper scripts for development. Client helpers live in `client/`. Backend helpers can go next to them.

## client/generate_icons.py

Generates missing item and skill icons with [Pixellab](https://www.pixellab.ai/) (model: pro-flash).
You review every result before it is used.

### Setup

```bash
pip install requests pillow
export PIXELLAB_SECRET=<your token from https://pixellab.ai/account>
```

### Usage

```bash
python3 tools/client/generate_icons.py                      # guided: asks what to do
python3 tools/client/generate_icons.py items --dry-run      # show targets and prompts, no API call
python3 tools/client/generate_icons.py skills --replace     # also redo skills that already have an icon
python3 tools/client/generate_icons.py --only 19,firebolt   # only these ids or identifiers (replaces their icon)
python3 tools/client/generate_icons.py --probe              # one real call (about $0.024), prints the raw reply
```

Run `--probe` first on a new machine or after Pixellab changes. It shows whether the script still finds the image in the reply.

### What happens for each icon

1. The prompt is built from the name and description. You can edit it.
2. Four candidates are generated and shown as one numbered sheet in your image viewer (`xdg-open`, or set `IMAGE_VIEWER`).
3. You pick `1`-`4`, or retry, edit the prompt, skip or quit. Nothing is written before you pick.
4. The PNG is saved next to the item or skill `.tres`, and the `icon` line is added to it.

A run can be stopped at any time. The next run starts again from the icons that are still missing.

Every candidate and raw API reply is kept in `assets-raw/pixellab/` (not committed).

### After a run

1. Open the Godot editor once. It creates the `.png.import` files.
2. Set the import options for the new PNGs (see "Icons" in `.claude/skills/skill-system/SKILL.md`).
3. Commit the PNGs, `.import` files and `.tres` files. Run `./gradlew checkItemDb checkSkillDb`.
4. Decide if `bestia-client/ASSETS.md` needs a credit line for generated art.

### Settings

Constants at the top of the script: `IMAGE_SIZE`, `VARIANTS`, `STYLE_PREFIX`, `STYLE_IMAGE`, `SPEND_LIMIT_USD`.

A round of 4 candidates costs about $0.10. The run stops when `SPEND_LIMIT_USD` is reached, so raise it for a big batch.

### Tests

```bash
cd tools/client && python3 -m unittest
```
