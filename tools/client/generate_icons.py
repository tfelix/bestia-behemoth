#!/usr/bin/env python3
"""Generate missing item and skill icons with Pixellab, review them, and wire them into the client.

    python3 tools/client/generate_icons.py                  # guided
    python3 tools/client/generate_icons.py items --dry-run  # show prompts, no API call, no cost
    python3 tools/client/generate_icons.py skills --only 5,firebolt
    python3 tools/client/generate_icons.py --probe          # one real call, dumps the raw API reply

Needs `pip install requests pillow` and a Pixellab token in $PIXELLAB_SECRET
(https://pixellab.ai/account). Settings are the constants below.
"""
import argparse
import base64
import csv
import io
import json
import os
import random
import re
import shlex
import shutil
import subprocess
import sys
import textwrap
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urljoin, urlparse

try:
    import requests
    from PIL import Image, ImageDraw, ImageFont
except ImportError as error:
    sys.exit(f"{error}. Install the dependencies with: pip install requests pillow")

REPO_ROOT = Path(__file__).resolve().parents[2]
CLIENT_SRC = REPO_ROOT / "bestia-client" / "src"
ITEM_DB = CLIENT_SRC / "Game" / "Item" / "DB"
SKILL_DB = CLIENT_SRC / "Game" / "Attack" / "DB"
ITEM_CSV = CLIENT_SRC / "Localization" / "items.csv"
SKILL_CSV = CLIENT_SRC / "Localization" / "skills.csv"
STAGING = REPO_ROOT / "assets-raw" / "pixellab"  # gitignored; keeps every candidate and raw API reply

# --- Settings -------------------------------------------------------------------------------------

HOST = "https://api.pixellab.ai"
API = f"{HOST}/v2"
SECRET_ENV = "PIXELLAB_SECRET"

IMAGE_SIZE = 64  # pro-flash: multiple of 4, 16 to 256
VARIANTS = 4  # candidates per review round
NO_BACKGROUND = True
STYLE_IMAGE: Path | None = None  # one approved icon of exactly IMAGE_SIZE; keeps the set consistent
STYLE_OPTIONS = {"color_palette": True, "outline": True, "detail": True, "shading": True}

STYLE_PREFIX = "Pixel art RPG icon, single object, centered, thick dark outline, vibrant colors, no text:"
ITEM_HINTS = {0: "consumable item", 1: "piece of equipment"}  # by .tres `type`; other items get none
SKILL_HINTS = {True: "passive skill", False: "active skill"}  # by .tres `is_passive`
DESCRIPTION_MIN_CHARS = 40
DESCRIPTION_MAX_CHARS = 200

PREVIEW_SCALE = 6  # contact sheet zoom, for viewing only
POLL_INTERVAL_S = 3
JOB_TIMEOUT_S = 180
REQUEST_TIMEOUT_S = 60
MAX_ATTEMPTS = 5
RETRY_BASE_DELAY_S = 2
RETRY_STATUSES = {429, 503, 529}
FATAL_STATUSES = {401, 402}
SPEND_LIMIT_USD = 5.0

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"
PNG_BASE64_PREFIX = base64.b64encode(PNG_MAGIC).decode()[:10]


# --- Client files ---------------------------------------------------------------------------------


@dataclass
class Asset:
    kind: str  # "item" or "skill"
    id: int
    identifier: str
    name: str
    description: str
    tres: Path
    icon: Path | None  # PNG the .tres points to
    wired: bool  # the .tres has an `icon =` line
    hint: str

    @property
    def has_icon(self) -> bool:
        return self.wired and (self.icon is None or self.icon.exists())

    @property
    def default_png(self) -> Path:
        return self.tres.with_suffix(".png")

    @property
    def label(self) -> str:
        return f"{self.kind} {self.id} - {self.name} ({self.identifier})"


ICON_LINE = re.compile(r"^icon[ \t]*=", re.M)
ICON_EXT_REF = re.compile(r'^icon[ \t]*=[ \t]*ExtResource\("([^"]+)"\)', re.M)
EXT_RESOURCE = re.compile(r"^\[ext_resource\s+([^\]\n]*)\]", re.M)
ID_LINE = re.compile(r"^(?:item_id|skill_id)[ \t]*=[ \t]*\d+", re.M)
LOAD_STEPS = re.compile(r"load_steps=(\d+)")


def read_tres(path: Path) -> str:
    return path.read_bytes().decode("utf-8")  # bytes, so line endings survive a rewrite


def int_field(text: str, key: str, default: int | None = None) -> int | None:
    match = re.search(rf"^{key}[ \t]*=[ \t]*(-?\d+)", text, re.M)
    return int(match.group(1)) if match else default


def str_field(text: str, key: str) -> str:
    match = re.search(rf'^{key}[ \t]*=[ \t]*"(.*)"', text, re.M)
    return match.group(1) if match else ""


def bool_field(text: str, key: str) -> bool:
    return re.search(rf"^{key}[ \t]*=[ \t]*true", text, re.M) is not None


def ext_resources(text: str) -> dict[str, dict[str, str]]:
    """The [ext_resource] lines by id. Attribute order differs between stubs and editor-saved files."""
    parsed = (dict(re.findall(r'(\w+)="([^"]*)"', match.group(1))) for match in EXT_RESOURCE.finditer(text))
    return {attributes["id"]: attributes for attributes in parsed if "id" in attributes}


def wired_icon_path(text: str) -> Path | None:
    reference = ICON_EXT_REF.search(text)
    resource_path = ext_resources(text).get(reference.group(1), {}).get("path") if reference else None
    return CLIENT_SRC / resource_path.removeprefix("res://") if resource_path else None


def load_translations(path: Path) -> dict[str, str]:
    with open(path, newline="", encoding="utf-8") as file:
        return {row["keys"]: row.get("en") or "" for row in csv.DictReader(file)}


def make_asset(kind: str, tres: Path, text: str, asset_id: int, name: str, description: str, hint: str) -> Asset:
    identifier = tres.stem.partition("_")[2] or tres.stem
    wired = ICON_LINE.search(text) is not None
    return Asset(kind, asset_id, identifier, name or identifier, description, tres, wired_icon_path(text), wired, hint)


def discover_items() -> list[Asset]:
    translations = load_translations(ITEM_CSV)
    assets = []
    for tres in ITEM_DB.glob("*.tres"):
        text = read_tres(tres)
        item_id = int_field(text, "item_id")
        if item_id is None:
            continue
        name_key = str_field(text, "name_key")
        description = translations.get(str_field(text, "description_key"), "")
        hint = ITEM_HINTS.get(int_field(text, "type", 0), "")
        assets.append(make_asset("item", tres, text, item_id, translations.get(name_key, name_key), description, hint))
    return sorted(assets, key=lambda asset: asset.id)


def discover_skills() -> list[Asset]:
    translations = load_translations(SKILL_CSV)
    assets = []
    for tres in SKILL_DB.glob("*.tres"):
        text = read_tres(tres)
        skill_id = int_field(text, "skill_id")
        if skill_id is None:
            continue
        description = translations.get(str_field(text, "description_key"), "")
        hint = SKILL_HINTS[bool_field(text, "is_passive")]
        assets.append(make_asset("skill", tres, text, skill_id, str_field(text, "name"), description, hint))
    return sorted(assets, key=lambda asset: asset.id)


def select_targets(assets: list[Asset], only: str | None, replace: bool) -> list[Asset]:
    """Naming an asset with --only targets it even when it has an icon; the review is the veto."""
    if only:
        wanted = {token.strip().lower() for token in only.split(",")}
        return [a for a in assets if str(a.id) in wanted or a.identifier.lower() in wanted]
    targets = [asset for asset in assets if replace or not asset.has_icon]
    return sorted(targets, key=lambda asset: asset.has_icon)  # missing first


# --- Prompt ---------------------------------------------------------------------------------------


def short_description(text: str) -> str:
    """The first sentences of the first paragraph. Later text is mostly mechanics, which an image model cannot
    draw, and some of it carries BBCode tables."""
    paragraph = re.split(r"\n\s*\n", text.strip())[0]
    paragraph = " ".join(re.sub(r"\[/?\w+[^\]]*\]|`", "", paragraph).split())
    summary = ""
    for sentence in re.split(r"(?<=[.!?])\s+", paragraph):
        summary = f"{summary} {sentence}".strip()
        if len(summary) >= DESCRIPTION_MIN_CHARS:  # a first sentence like "The capstone of the forge." says little
            break
    if len(summary) <= DESCRIPTION_MAX_CHARS:
        return summary
    return summary[:DESCRIPTION_MAX_CHARS].rsplit(" ", 1)[0] + "..."


def build_prompt(asset: Asset) -> str:
    subject = f"{asset.name} ({asset.hint})" if asset.hint else asset.name
    return " ".join(part for part in (STYLE_PREFIX, f"{subject}.", short_description(asset.description)) if part)


# --- Wiring ---------------------------------------------------------------------------------------


class WiringError(Exception):
    pass


def line_end(text: str, position: int) -> int:
    newline = text.find("\n", position)
    return len(text) if newline < 0 else newline + 1


def wire_icon(text: str, png_resource_path: str) -> str:
    """Add the texture and the `icon` line to a .tres that has none. Targeted edits, never a rewrite."""
    if ICON_LINE.search(text):
        return text
    id_line = ID_LINE.search(text)
    ext_lines = list(EXT_RESOURCE.finditer(text))
    if not id_line or not ext_lines:
        raise WiringError("no item_id/skill_id line or no ext_resource to anchor on")

    existing_ids = set(ext_resources(text))
    number = len(existing_ids) + 1
    while f"{number}_icon" in existing_ids:
        number += 1
    resource_id = f"{number}_icon"
    newline = "\r\n" if "\r\n" in text else "\n"

    icon_at = line_end(text, id_line.end())  # later in the file, so insert it first
    text = text[:icon_at] + f'icon = ExtResource("{resource_id}"){newline}' + text[icon_at:]
    ext_at = line_end(text, ext_lines[-1].end())
    texture = f'[ext_resource type="Texture2D" path="{png_resource_path}" id="{resource_id}"]'
    text = text[:ext_at] + texture + newline + text[ext_at:]

    header, separator, rest = text.partition("\n")  # editor-saved files omit load_steps; never add it
    header = LOAD_STEPS.sub(lambda match: f"load_steps={int(match.group(1)) + 1}", header, count=1)
    return header + separator + rest


def install_icon(asset: Asset, image: Image.Image) -> Path:
    """Write the PNG where the .tres points, or beside it for a new icon, and wire a new one."""
    if asset.wired and asset.icon is None:
        raise WiringError(f"{asset.tres.name} sets icon to something other than a texture file; edit it by hand")
    target = asset.icon or asset.default_png
    wired_text = None
    if not asset.wired:
        resource_path = "res://" + target.relative_to(CLIENT_SRC).as_posix()
        wired_text = wire_icon(read_tres(asset.tres), resource_path)  # before any write, so a refusal leaves no trace
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target, "PNG")
    if wired_text is not None:
        asset.tres.write_bytes(wired_text.encode("utf-8"))
    return target


# --- Pixellab -------------------------------------------------------------------------------------


class PixellabError(Exception):
    def __init__(self, status: int | None, message: str):
        super().__init__(message)
        self.status = status


def raise_for_status(response: requests.Response) -> None:
    if response.ok:
        return
    reasons = {401: f"token rejected, check {SECRET_ENV}", 402: "out of credit"}
    reason = reasons.get(response.status_code, "request failed")
    raise PixellabError(response.status_code, f"HTTP {response.status_code} ({reason}): {response.text[:300]}")


def leaves(node, key=None):
    """Every (key, value) leaf of a JSON tree; list items inherit their list's key."""
    if isinstance(node, dict):
        for child_key, child in node.items():
            yield from leaves(child, child_key)
    elif isinstance(node, list):
        for child in node:
            yield from leaves(child, key)
    else:
        yield key, node


def extract_png(job: dict, download) -> bytes:
    """Find the image in a finished job. The docs do not pin down the reply, so look for a PNG anywhere,
    then for an image_url, then for the download route they name."""
    reply = job.get("last_response") or {}
    for _, value in leaves(reply):
        if isinstance(value, str):
            encoded = value.split(",", 1)[-1] if value.startswith("data:") else value
            if encoded.startswith(PNG_BASE64_PREFIX):
                return base64.b64decode(encoded)
    url = next((v for k, v in leaves(reply) if k == "image_url" and isinstance(v, str)), None)
    data = download(url or f"/mcp/images/{job['id']}/download")
    if not data.startswith(PNG_MAGIC):
        raise PixellabError(None, "the downloaded file is not a PNG")
    return data


def style_payload() -> dict:
    if STYLE_IMAGE is None:
        return {}
    image = Image.open(STYLE_IMAGE).convert("RGBA")
    buffer = io.BytesIO()
    image.save(buffer, "PNG")
    encoded = base64.b64encode(buffer.getvalue()).decode()
    return {
        "style_image": {
            "image": {"type": "base64", "base64": encoded, "format": "png"},
            "size": {"width": image.width, "height": image.height},
        },
        "style_options": STYLE_OPTIONS,
    }


class Pixellab:
    def __init__(self, secret: str):
        self.headers = {"Authorization": f"Bearer {secret}"}
        self.spent_usd = 0.0
        self.stop = threading.Event()
        self._lock = threading.Lock()

    def request(self, method: str, path: str, **kwargs) -> dict:
        for attempt in range(MAX_ATTEMPTS):
            try:
                response = requests.request(
                    method, f"{API}{path}", headers=self.headers, timeout=REQUEST_TIMEOUT_S, **kwargs
                )
            except requests.RequestException as error:
                raise PixellabError(None, f"network error: {error}") from error
            if response.status_code in RETRY_STATUSES and attempt + 1 < MAX_ATTEMPTS:
                self.stop.wait(RETRY_BASE_DELAY_S * 2**attempt)
                continue
            break
        raise_for_status(response)
        return response.json()

    def download(self, url: str) -> bytes:
        url = urljoin(f"{HOST}/", url)
        same_host = urlparse(url).hostname == urlparse(HOST).hostname  # the token goes to Pixellab only
        response = requests.get(url, headers=self.headers if same_host else {}, timeout=REQUEST_TIMEOUT_S)
        raise_for_status(response)
        return response.content

    def run_job(self, prompt: str, seed: int) -> dict:
        body = {
            "description": prompt,
            "image_size": {"width": IMAGE_SIZE, "height": IMAGE_SIZE},
            "no_background": NO_BACKGROUND,
            "seed": seed,
            **style_payload(),
        }
        submitted = self.request("POST", "/create-image-pro-flash", json=body)
        job_id = submitted["background_job_id"]
        job = self._wait_for(job_id)
        job.setdefault("id", job_id)
        self._add_usage(job.get("usage") or submitted.get("usage"))
        (STAGING / "jobs").mkdir(parents=True, exist_ok=True)
        (STAGING / "jobs" / f"{job_id}.json").write_text(json.dumps(job, indent=2))  # a paid result is never lost
        return job

    def _wait_for(self, job_id: str) -> dict:
        deadline = time.monotonic() + JOB_TIMEOUT_S
        while not self.stop.is_set():
            job = self.request("GET", f"/background-jobs/{job_id}")
            status = job.get("status")
            if status == "completed":
                return job
            if status == "failed":
                raise PixellabError(None, f"job failed: {json.dumps(job.get('last_response'))[:300]}")
            if time.monotonic() > deadline:
                self._cancel(job_id)
                raise PixellabError(None, f"job still running after {JOB_TIMEOUT_S}s")
            self.stop.wait(POLL_INTERVAL_S)
        self._cancel(job_id)  # the run was abandoned: stop being charged for this job
        raise PixellabError(None, "cancelled")

    def _add_usage(self, usage: dict | None) -> None:
        if usage and usage.get("type") == "usd":
            with self._lock:
                self.spent_usd += usage.get("usd") or 0.0

    def _cancel(self, job_id: str) -> None:
        try:
            self.request("DELETE", f"/background-jobs/{job_id}")
        except PixellabError:
            pass  # best effort: the job may already be done

    def generate_round(self, prompt: str) -> list[Image.Image]:
        """Run VARIANTS jobs side by side. A failed variant is reported and skipped; 401/402 stop the run."""
        self.stop.clear()
        seeds = random.sample(range(2**31), VARIANTS)
        images = []
        with ThreadPoolExecutor(max_workers=VARIANTS) as pool:
            variants = {pool.submit(self._generate_one, prompt, seed): number for number, seed in enumerate(seeds, 1)}
            try:
                for future in as_completed(variants):  # in completion order, so a fatal error is seen at once
                    try:
                        images.append(future.result())
                    except PixellabError as error:
                        if error.status in FATAL_STATUSES:
                            raise
                        print(f"  Variant {variants[future]} failed: {error}")
            except BaseException:  # Ctrl-C or a fatal error: the workers see the flag and cancel their jobs
                self.stop.set()
                raise
        return images

    def _generate_one(self, prompt: str, seed: int) -> Image.Image:
        try:
            job = self.run_job(prompt, seed)
            return Image.open(io.BytesIO(extract_png(job, self.download))).convert("RGBA")
        except (KeyError, ValueError, OSError) as error:  # a reply that is not shaped as expected
            raise PixellabError(None, f"unexpected reply: {error!r}") from error


# --- Review ---------------------------------------------------------------------------------------


class Quit(Exception):
    pass


def ask(question: str, keys: list[str], default: str = "") -> str:
    while True:
        answer = input(f"{question} > ").strip().lower() or default
        if answer in keys:
            return answer
        print("  Not one of the options.")


def edit_text(initial: str) -> str:
    try:
        import readline
    except ImportError:
        return input("New prompt (empty keeps the current one): ").strip() or initial
    readline.set_startup_hook(lambda: readline.insert_text(initial))  # start from the current prompt
    try:
        return input("Prompt> ").strip() or initial
    finally:
        readline.set_startup_hook()


def checkerboard(size: int, cell: int = 24) -> Image.Image:
    board = Image.new("RGB", (size, size), (70, 70, 78))
    draw = ImageDraw.Draw(board)
    for y in range(0, size, cell):
        for x in range(0, size, cell):
            if (x // cell + y // cell) % 2:
                draw.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(95, 95, 104))
    return board


def label_font() -> ImageFont.ImageFont:
    try:
        return ImageFont.truetype("DejaVuSans-Bold.ttf", 28)
    except OSError:
        return ImageFont.load_default()


def contact_sheet(images: list[Image.Image]) -> Image.Image:
    """The candidates side by side on a checkerboard, so transparency shows, enlarged without smoothing."""
    tile, gap = IMAGE_SIZE * PREVIEW_SCALE, 12
    sheet = Image.new("RGB", (len(images) * (tile + gap) + gap, tile + 2 * gap), (40, 40, 48))
    draw, font = ImageDraw.Draw(sheet), label_font()
    for index, image in enumerate(images):
        left = gap + index * (tile + gap)
        enlarged = image.resize((tile, tile), Image.NEAREST)
        sheet.paste(checkerboard(tile), (left, gap))
        sheet.paste(enlarged, (left, gap), enlarged)
        draw.text((left + 8, gap + 4), str(index + 1), font=font, fill="white", stroke_width=3, stroke_fill="black")
    return sheet


def open_viewer(path: Path) -> None:
    viewer = shlex.split(os.environ.get("IMAGE_VIEWER", "xdg-open"))
    has_display = os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY")
    if has_display and shutil.which(viewer[0]):
        # Popen, not run: the viewer must not hold the terminal while we wait for the answer.
        subprocess.Popen([*viewer, str(path)], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                         stderr=subprocess.DEVNULL, start_new_session=True)
    print(f"  Candidates: {path}")


def save_candidates(asset: Asset, images: list[Image.Image]) -> Path:
    folder = STAGING / asset.kind
    folder.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")  # unique per round: viewers do not refresh a reused file
    for number, image in enumerate(images, 1):
        image.save(folder / f"{asset.tres.stem}_{stamp}_{number}.png")
    sheet_path = folder / f"{asset.tres.stem}_{stamp}_sheet.png"
    contact_sheet(images).save(sheet_path)
    return sheet_path


def generate_and_show(asset: Asset, prompt: str, client: Pixellab) -> list[Image.Image]:
    if client.spent_usd >= SPEND_LIMIT_USD:
        print(f"  Spend limit of ${SPEND_LIMIT_USD:.2f} reached.")
        raise Quit
    before = client.spent_usd
    print(f"  Generating {VARIANTS} candidates...")
    images = client.generate_round(prompt)
    print(f"  Cost ${client.spent_usd - before:.3f}, run total ${client.spent_usd:.3f}")
    if images:
        open_viewer(save_candidates(asset, images))
    return images


def review_asset(asset: Asset, prompt: str, client: Pixellab) -> Path | None:
    """Returns the installed PNG, or None when the user skipped."""
    description = short_description(asset.description) or "(no description)"
    print(textwrap.fill(description, 90, initial_indent="  ", subsequent_indent="  "))
    print(f"  Prompt: {prompt}")
    choice = ask("  [Enter] generate, [e]dit prompt, [s]kip, [q]uit", ["", "e", "s", "q"])
    while True:
        if choice == "q":
            raise Quit
        if choice == "s":
            return None
        if choice == "e":
            prompt = edit_text(prompt)
            print(f"  Prompt: {prompt}")
        images = generate_and_show(asset, prompt, client)
        numbers = [str(number) for number in range(1, len(images) + 1)]
        question = f"Use {'/'.join(numbers)}, " if images else "Nothing came back. "
        choice = ask(f"  {question}[r]etry, [e]dit prompt and retry, [s]kip, [q]uit", numbers + ["r", "e", "s", "q"])
        if choice in numbers:
            target = install_icon(asset, images[int(choice) - 1])
            print(f"  Installed {target.relative_to(REPO_ROOT)}")
            return target


# --- Command line ---------------------------------------------------------------------------------


def print_counts(items: list[Asset], skills: list[Asset]) -> None:
    for title, assets in (("Items", items), ("Skills", skills)):
        missing = sum(1 for asset in assets if not asset.has_icon)
        print(f"{title}: {missing} of {len(assets)} have no icon")


def print_balance(client: Pixellab) -> None:
    try:
        print(f"Pixellab balance: {json.dumps(client.request('GET', '/balance').get('credits'))}")
    except PixellabError as error:
        if error.status in FATAL_STATUSES:
            raise
        print(f"(balance not available: {error})")


def shorten(node):
    if isinstance(node, dict):
        return {key: shorten(value) for key, value in node.items()}
    if isinstance(node, list):
        return [shorten(value) for value in node]
    if isinstance(node, str) and len(node) > 120:
        return f"{node[:60]}...<{len(node)} chars>"
    return node


def probe(client: Pixellab) -> None:
    """One real generation, to see what the API really returns. Writes nothing to the client."""
    print_balance(client)
    try:
        print("Capabilities:", json.dumps(client.request("GET", "/pro-flash/capabilities"), indent=2)[:2000])
    except PixellabError as error:
        print(f"(capabilities not available: {error})")
    job = client.run_job("Pixel art RPG icon of a red apple, single object, centered", seed=1)
    print(json.dumps(shorten(job), indent=2))
    png = extract_png(job, client.download)
    STAGING.mkdir(parents=True, exist_ok=True)
    (STAGING / "probe.png").write_bytes(png)
    print(f"Image found and saved to {STAGING / 'probe.png'}. Cost so far ${client.spent_usd:.3f}")


def print_summary(installed: list[Path], skipped: int, spent_usd: float) -> None:
    print(f"\nInstalled {len(installed)}, skipped {skipped}. Spent ${spent_usd:.3f}.")
    if not installed:
        return
    unimported = [path for path in installed if not path.with_name(path.name + ".import").exists()]
    print("Next steps:")
    if unimported:
        print(f"  1. Open the Godot editor once so it creates the .import files ({len(unimported)} are missing).")
    print("  2. Select the new PNGs and set the import options from .claude/skills/skill-system/SKILL.md (Icons).")
    print("  3. Commit the PNGs, .import files and .tres files; run ./gradlew checkItemDb checkSkillDb.")
    print("  4. Decide whether bestia-client/ASSETS.md needs a credit line for the generated art.")


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("kind", nargs="?", choices=["items", "skills"], help="default: ask")
    parser.add_argument("--replace", action="store_true", help="also redo icons that already exist")
    parser.add_argument("--only", help="comma separated ids or identifiers; targets them even if they have an icon")
    parser.add_argument("--dry-run", action="store_true", help="list targets and prompts, call nothing")
    parser.add_argument("--probe", action="store_true", help="make one real call and print the raw reply")
    return parser.parse_args(argv)


def require_client() -> Pixellab:
    secret = os.environ.get(SECRET_ENV)
    if not secret:
        sys.exit(f"Set {SECRET_ENV} to your Pixellab token (https://pixellab.ai/account).")
    return Pixellab(secret)


def main(argv: list[str] | None = None) -> None:
    args = parse_args(argv)
    if not (IMAGE_SIZE % 4 == 0 and 16 <= IMAGE_SIZE <= 256):
        sys.exit("IMAGE_SIZE must be a multiple of 4 between 16 and 256.")
    if args.probe:
        try:
            probe(require_client())
        except PixellabError as error:
            sys.exit(f"Stopped: {error}")
        return

    items, skills = discover_items(), discover_skills()
    print_counts(items, skills)
    if not (args.dry_run or sys.stdin.isatty()):
        sys.exit("The review needs a terminal. Use --dry-run to only list the prompts.")
    kind, replace = args.kind or "both", args.replace
    if args.kind is None and not (args.dry_run or args.only):  # nothing said: guide
        kind = {"i": "items", "s": "skills", "b": "both"}[ask("Generate for [i]tems, [s]kills or [b]oth?", ["i", "s", "b"])]
        replace = replace or ask("Also redo icons that already exist? [y/N]", ["y", "n"], default="n") == "y"
    pool = {"items": items, "skills": skills, "both": items + skills}[kind]
    targets = select_targets(pool, args.only, replace)
    if not targets:
        print("Nothing to do.")
        return

    if args.dry_run:
        for asset in targets:
            print(f"\n{asset.label}{' [has icon]' if asset.has_icon else ''}\n  {build_prompt(asset)}")
        return

    client = require_client()
    installed: list[Path] = []
    skipped = 0
    try:
        print_balance(client)
        for number, asset in enumerate(targets, 1):
            note = " - replaces the existing icon" if asset.has_icon else ""
            print(f"\n[{number}/{len(targets)}] {asset.label}{note}")
            result = review_asset(asset, build_prompt(asset), client)
            if result:
                installed.append(result)
            else:
                skipped += 1
    except (Quit, KeyboardInterrupt, EOFError):
        print()
    except (PixellabError, WiringError) as error:
        print(f"\nStopped: {error}")
    print_summary(installed, skipped, client.spent_usd)


if __name__ == "__main__":
    main()
