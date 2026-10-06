"""Run with: cd tools/client && python3 -m unittest"""
import base64
import io
import json
import os
import signal
import sys
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest import mock

import generate_icons as gi

ITEM_STUB = """[gd_resource type="Resource" script_class="ItemResource" load_steps=2 format=3]

[ext_resource type="Script" path="res://Game/Item/item_resource.gd" id="1_script"]

[resource]
script = ExtResource("1_script")
item_id = 19
name_key = "IRON_SWORD"
description_key = "IRON_SWORD_DESC"
weight = 120
type = 1
level = 20
"""

ITEM_WITH_HYPHEN_PNG = """[gd_resource type="Resource" script_class="ItemResource" load_steps=4 format=3 uid="uid://ctyip7ychsigs"]

[ext_resource type="Texture2D" uid="uid://5o8k1ijvpla1" path="res://Game/Item/DB/3_small-health-potion.png" id="1_cw0r6"]
[ext_resource type="Script" uid="uid://bn4gvq3ayku4r" path="res://Game/Item/item_resource.gd" id="1_otq61"]

[resource]
script = ExtResource("1_otq61")
item_id = 3
icon = ExtResource("1_cw0r6")
name_key = "SMALL_HEALTH_POTION"
description_key = "SMALL_HEALTH_POTION_DESC"
"""

SKILL_STUB = """[gd_resource type="Resource" script_class="AttackResource" load_steps=2 format=3]

[ext_resource type="Script" path="res://Game/Attack/attack_resource.gd" id="1_script"]

[resource]
script = ExtResource("1_script")
skill_id = 2
name = "Divine Protection"
description_key = "SKILL_2_DESC"
is_passive = true
"""

SKILL_EDITOR_SAVED = """[gd_resource type="Resource" script_class="AttackResource" format=3]

[ext_resource type="Script" path="res://Game/Attack/attack_resource.gd" id="1_script"]
[ext_resource type="Texture2D" path="res://Game/Attack/DB/5_firebolt.png" id="1_v44l0"]

[resource]
script = ExtResource("1_script")
skill_id = 5
icon = ExtResource("1_v44l0")
name = "Firebolt"
description_key = "SKILL_5_DESC"
"""

SKILL_EDITOR_SAVED_NO_ICON = """[gd_resource type="Resource" script_class="AttackResource" format=3]

[ext_resource type="Script" path="res://Game/Attack/attack_resource.gd" id="1_script"]

[resource]
script = ExtResource("1_script")
skill_id = 6
name = "Blessing"
"""

ITEMS_CSV = '''keys,en
SMALL_HEALTH_POTION,Small Health Potion
SMALL_HEALTH_POTION_DESC,"A small flask of red liquid.

Restores health."
IRON_SWORD,Iron Sword
IRON_SWORD_DESC,"A straight iron blade, plain and heavier than it looks.

Nothing about it is clever."
'''

SKILLS_CSV = '''keys,en
SKILL_2_DESC,A passive ward that shields the master from harm.
SKILL_5_DESC,"Hurls a bolt of searing fire at a single enemy.

Must be channelled."
'''


def png_bytes(color=(255, 0, 0, 255), size=4) -> bytes:
    buffer = io.BytesIO()
    gi.Image.new("RGBA", (size, size), color).save(buffer, "PNG")
    return buffer.getvalue()


class ClientTreeCase(unittest.TestCase):
    """A throwaway copy of the client layout, with the module's paths pointed at it."""

    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        src = self.root / "bestia-client" / "src"
        self.item_db = src / "Game" / "Item" / "DB"
        self.skill_db = src / "Game" / "Attack" / "DB"
        for folder in (self.item_db, self.skill_db, src / "Localization"):
            folder.mkdir(parents=True)
        (self.item_db / "19_iron_sword.tres").write_text(ITEM_STUB)
        (self.item_db / "3_small_health_potion.tres").write_text(ITEM_WITH_HYPHEN_PNG)
        (self.item_db / "3_small-health-potion.png").write_bytes(png_bytes())
        (self.skill_db / "2_divine_protection.tres").write_text(SKILL_STUB)
        (self.skill_db / "5_firebolt.tres").write_text(SKILL_EDITOR_SAVED)
        (self.skill_db / "5_firebolt.png").write_bytes(png_bytes())
        (src / "Localization" / "items.csv").write_text(ITEMS_CSV)
        (src / "Localization" / "skills.csv").write_text(SKILLS_CSV)
        patcher = mock.patch.multiple(
            gi,
            REPO_ROOT=self.root,
            CLIENT_SRC=src,
            ITEM_DB=self.item_db,
            SKILL_DB=self.skill_db,
            ITEM_CSV=src / "Localization" / "items.csv",
            SKILL_CSV=src / "Localization" / "skills.csv",
            STAGING=self.root / "assets-raw" / "pixellab",
        )
        patcher.start()
        self.addCleanup(patcher.stop)
        self.out = io.StringIO()
        out_patcher = mock.patch("sys.stdout", self.out)
        out_patcher.start()
        self.addCleanup(out_patcher.stop)

    def asset(self, asset_id):
        return {a.id: a for a in gi.discover_items()}[asset_id]


class WireIconTest(unittest.TestCase):
    def test_adds_the_texture_and_the_icon_line(self):
        wired = gi.wire_icon(ITEM_STUB, "res://Game/Item/DB/19_iron_sword.png")
        expected = ITEM_STUB.replace("load_steps=2", "load_steps=3").replace(
            'id="1_script"]\n',
            'id="1_script"]\n[ext_resource type="Texture2D" path="res://Game/Item/DB/19_iron_sword.png" id="2_icon"]\n',
        ).replace("item_id = 19\n", 'item_id = 19\nicon = ExtResource("2_icon")\n')
        self.assertEqual(expected, wired)

    def test_a_file_without_load_steps_does_not_get_one(self):
        wired = gi.wire_icon(SKILL_EDITOR_SAVED_NO_ICON, "res://Game/Attack/DB/6_blessing.png")
        self.assertNotIn("load_steps", wired)
        self.assertIn('icon = ExtResource("2_icon")', wired)

    def test_a_file_that_has_an_icon_is_unchanged(self):
        self.assertEqual(ITEM_WITH_HYPHEN_PNG, gi.wire_icon(ITEM_WITH_HYPHEN_PNG, "res://other.png"))

    def test_a_missing_anchor_is_refused(self):
        with self.assertRaises(gi.WiringError):
            gi.wire_icon(ITEM_STUB.replace("item_id = 19\n", ""), "res://a.png")
        with self.assertRaises(gi.WiringError):
            gi.wire_icon(ITEM_STUB.replace('[ext_resource type="Script" path="res://Game/Item/item_resource.gd" id="1_script"]\n', ""), "res://a.png")

    def test_a_taken_resource_id_is_skipped(self):
        taken = ITEM_STUB.replace(
            'id="1_script"]\n', 'id="1_script"]\n[ext_resource type="PackedScene" path="res://a.tscn" id="3_icon"]\n'
        )
        self.assertIn('id="4_icon"', gi.wire_icon(taken, "res://a.png"))

    def test_windows_line_endings_survive(self):
        wired = gi.wire_icon(ITEM_STUB.replace("\n", "\r\n"), "res://a.png")
        self.assertNotIn("\n", wired.replace("\r\n", ""))
        self.assertIn('icon = ExtResource("2_icon")\r\n', wired)


class DiscoveryTest(ClientTreeCase):
    def test_items_are_read_from_the_tres_and_the_csv(self):
        items = {item.id: item for item in gi.discover_items()}
        sword = items[19]
        self.assertEqual(("iron_sword", "Iron Sword", "piece of equipment"), (sword.identifier, sword.name, sword.hint))
        self.assertIn("plain and heavier", sword.description)
        self.assertFalse(sword.has_icon)

    def test_the_icon_path_comes_from_the_tres_not_from_its_name(self):
        potion = {item.id: item for item in gi.discover_items()}[3]
        self.assertEqual("3_small-health-potion.png", potion.icon.name)
        self.assertTrue(potion.has_icon)

    def test_a_wired_icon_whose_png_is_gone_counts_as_missing(self):
        (self.item_db / "3_small-health-potion.png").unlink()
        self.assertFalse({item.id: item for item in gi.discover_items()}[3].has_icon)

    def test_skills_read_name_and_passive_flag(self):
        skills = {skill.id: skill for skill in gi.discover_skills()}
        self.assertEqual(("Divine Protection", "passive skill"), (skills[2].name, skills[2].hint))
        self.assertEqual("active skill", skills[5].hint)
        self.assertEqual([False, True], [skills[2].has_icon, skills[5].has_icon])

    def test_targets_are_the_missing_icons_unless_asked_otherwise(self):
        items = gi.discover_items()
        self.assertEqual([19], [item.id for item in gi.select_targets(items, None, replace=False)])
        self.assertEqual([19, 3], [item.id for item in gi.select_targets(items, None, replace=True)])  # missing first
        self.assertEqual([3], [item.id for item in gi.select_targets(items, "small_health_potion", replace=False)])
        self.assertEqual([3, 19], [item.id for item in gi.select_targets(items, "19, 3", replace=False)])


class PromptTest(unittest.TestCase):
    def asset(self, description, hint="piece of equipment"):
        return gi.Asset("item", 1, "x", "Iron Sword", description, Path("x.tres"), None, False, hint)

    def test_prompt_names_the_asset_and_leads_with_the_style(self):
        prompt = gi.build_prompt(self.asset("A straight iron blade, plain and heavier than it looks."))
        self.assertTrue(prompt.startswith(gi.STYLE_PREFIX))
        self.assertIn("Iron Sword (piece of equipment).", prompt)

    def test_no_hint_means_no_brackets(self):
        self.assertIn("Iron Sword.", gi.build_prompt(self.asset("A blade.", hint="")))

    def test_only_the_first_paragraph_and_no_bbcode_backticks_or_line_breaks(self):
        text = "Hurls a [b]bolt[/b] of `fire`\nat one enemy.\n\nCooldown 3 minutes."
        self.assertEqual("Hurls a bolt of fire at one enemy.", gi.short_description(text))

    def test_a_very_short_first_sentence_gets_the_next_one(self):
        self.assertEqual(
            "The capstone of the forge. It makes anything you can name.",
            gi.short_description("The capstone of the forge. It makes anything you can name. Costs gold."),
        )

    def test_long_text_is_cut_on_a_word(self):
        summary = gi.short_description("word " * 100)
        self.assertLessEqual(len(summary), gi.DESCRIPTION_MAX_CHARS + 3)
        self.assertTrue(summary.endswith("word..."))


class ExtractPngTest(unittest.TestCase):
    PNG = png_bytes()

    def no_download(self, url):
        raise AssertionError(f"unexpected download of {url}")

    def test_finds_a_png_anywhere_in_the_reply(self):
        encoded = base64.b64encode(self.PNG).decode()
        job = {"id": "j", "last_response": {"images": [{"name": "x", "image": {"base64": encoded}}]}}
        self.assertEqual(self.PNG, gi.extract_png(job, self.no_download))

    def test_accepts_a_data_uri(self):
        encoded = "data:image/png;base64," + base64.b64encode(self.PNG).decode()
        self.assertEqual(self.PNG, gi.extract_png({"id": "j", "last_response": {"image": encoded}}, self.no_download))

    def test_follows_an_image_url(self):
        job = {"id": "j", "last_response": {"image_url": "/files/a.png", "source_image_id": "abc"}}
        self.assertEqual(self.PNG, gi.extract_png(job, lambda url: self.PNG if url == "/files/a.png" else b""))

    def test_falls_back_to_the_documented_download_route(self):
        requested = []
        gi.extract_png({"id": "job-7", "last_response": {}}, lambda url: requested.append(url) or self.PNG)
        self.assertEqual(["/mcp/images/job-7/download"], requested)

    def test_a_download_that_is_not_a_png_is_an_error(self):
        with self.assertRaises(gi.PixellabError):
            gi.extract_png({"id": "j", "last_response": {}}, lambda url: b"<html>")


class InstallTest(ClientTreeCase):
    def test_a_new_icon_is_written_beside_the_tres_and_wired(self):
        image = gi.Image.new("RGBA", (4, 4), (1, 2, 3, 255))
        target = gi.install_icon(self.asset(19), image)
        self.assertEqual(self.item_db / "19_iron_sword.png", target)
        wired = self.asset(19)
        self.assertTrue(wired.has_icon)
        self.assertEqual(target, wired.icon)
        self.assertEqual((1, 2, 3, 255), gi.Image.open(target).getpixel((0, 0)))

    def test_a_replace_overwrites_the_png_the_tres_points_to_and_leaves_the_tres_alone(self):
        tres_before = (self.item_db / "3_small_health_potion.tres").read_bytes()
        target = gi.install_icon(self.asset(3), gi.Image.new("RGBA", (4, 4), (9, 9, 9, 255)))
        self.assertEqual(self.item_db / "3_small-health-potion.png", target)
        self.assertEqual(tres_before, (self.item_db / "3_small_health_potion.tres").read_bytes())
        self.assertEqual((9, 9, 9, 255), gi.Image.open(target).getpixel((0, 0)))

    def test_a_refused_wiring_writes_nothing(self):
        (self.item_db / "19_iron_sword.tres").write_text(ITEM_STUB.replace("item_id = 19\n", ""))
        # no item_id line: the asset is not discovered, so build it by hand
        asset = gi.Asset("item", 19, "iron_sword", "Iron Sword", "", self.item_db / "19_iron_sword.tres", None, False, "")
        with self.assertRaises(gi.WiringError):
            gi.install_icon(asset, gi.Image.new("RGBA", (4, 4)))
        self.assertFalse((self.item_db / "19_iron_sword.png").exists())

    def test_an_icon_that_is_not_a_texture_file_is_refused(self):
        asset = gi.Asset("item", 1, "x", "X", "", self.item_db / "x.tres", None, True, "")
        with self.assertRaises(gi.WiringError):
            gi.install_icon(asset, gi.Image.new("RGBA", (4, 4)))


class FakeClient:
    def __init__(self, rounds):
        self.rounds, self.prompts, self.spent_usd = list(rounds), [], 0.0

    def generate_round(self, prompt):
        self.prompts.append(prompt)
        return self.rounds.pop(0)


class ReviewFlowTest(ClientTreeCase):
    def setUp(self):
        super().setUp()
        for patcher in (mock.patch.object(gi, "open_viewer"), mock.patch.object(gi, "VARIANTS", 2)):
            patcher.start()
            self.addCleanup(patcher.stop)

    def candidates(self, *colors):
        return [gi.Image.new("RGBA", (gi.IMAGE_SIZE, gi.IMAGE_SIZE), color) for color in colors]

    def review(self, answers, rounds, edited_prompt="edited"):
        client = FakeClient(rounds)
        with mock.patch("builtins.input", side_effect=answers), mock.patch.object(gi, "edit_text", return_value=edited_prompt):
            result = gi.review_asset(self.asset(19), "first prompt", client)
        return result, client

    def test_picking_a_candidate_installs_that_one(self):
        result, client = self.review(["", "2"], [self.candidates((255, 0, 0, 255), (0, 0, 255, 255))])
        self.assertEqual((0, 0, 255, 255), gi.Image.open(result).getpixel((0, 0)))
        self.assertEqual(["first prompt"], client.prompts)
        self.assertTrue(list((self.root / "assets-raw" / "pixellab" / "item").glob("19_iron_sword_*_sheet.png")))

    def test_skipping_installs_nothing(self):
        result, client = self.review(["s"], [])
        self.assertIsNone(result)
        self.assertEqual([], client.prompts)
        self.assertFalse((self.item_db / "19_iron_sword.png").exists())

    def test_retry_generates_again_with_the_same_prompt(self):
        rounds = [self.candidates((1, 1, 1, 255), (2, 2, 2, 255)), self.candidates((3, 3, 3, 255), (4, 4, 4, 255))]
        result, client = self.review(["", "r", "1"], rounds)
        self.assertEqual(["first prompt", "first prompt"], client.prompts)
        self.assertEqual((3, 3, 3, 255), gi.Image.open(result).getpixel((0, 0)))

    def test_editing_the_prompt_generates_again_with_the_new_one(self):
        rounds = [self.candidates((1, 1, 1, 255)), self.candidates((5, 5, 5, 255))]
        result, client = self.review(["", "e", "1"], rounds, edited_prompt="a better prompt")
        self.assertEqual(["first prompt", "a better prompt"], client.prompts)

    def test_an_empty_round_offers_a_retry(self):
        result, client = self.review(["", "r", "1"], [[], self.candidates((7, 7, 7, 255))])
        self.assertEqual(2, len(client.prompts))
        self.assertIsNotNone(result)

    def test_quitting_stops_the_run(self):
        with self.assertRaises(gi.Quit):
            self.review(["q"], [])

    def test_the_spend_limit_stops_before_another_round(self):
        client = FakeClient([])
        client.spent_usd = gi.SPEND_LIMIT_USD
        with mock.patch("builtins.input", side_effect=[""]), self.assertRaises(gi.Quit):
            gi.review_asset(self.asset(19), "p", client)


class FakeHandler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, status, body):
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def authorized(self):
        return self.headers.get("Authorization") == "Bearer test-token"

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        if not self.authorized():
            return self.reply(401, {"detail": "Invalid API token"})
        server = self.server
        with server.lock:
            server.submissions.append(body)
            status = server.submit_statuses.pop(0) if server.submit_statuses else 202
            server.job_counter += 1
            job_id = f"job{server.job_counter}"
        if status != 202:
            return self.reply(status, {"detail": "refused"})
        self.reply(202, {"background_job_id": job_id, "status": "processing", "usage": {"type": "usd", "usd": 0.05}})

    def do_GET(self):
        server = self.server
        if self.path.startswith("/v2/background-jobs/"):
            job_id = self.path.rsplit("/", 1)[1]
            with server.lock:
                server.polls[job_id] = server.polls.get(job_id, 0) + 1
                polls = server.polls[job_id]
            job = {"id": job_id, "created_at": "now", "status": "processing"}
            if not server.hold and polls >= 2:
                if job_id in server.failing:
                    job.update(status="failed", last_response={"error": "boom"})
                else:
                    job.update(status="completed", usage={"type": "usd", "usd": 0.02},
                               last_response={"images": [{"type": "base64", "base64": server.png_b64}]})
            return self.reply(200, job)
        self.reply(404, {"detail": "not found"})

    def do_DELETE(self):
        with self.server.lock:
            self.server.deleted.append(self.path.rsplit("/", 1)[1])
        self.reply(200, {"id": "x", "status": "failed", "message": "cancelled"})


class PixellabApiTest(ClientTreeCase):
    """The client against a local fake of the Pixellab API."""

    def setUp(self):
        super().setUp()
        server = ThreadingHTTPServer(("127.0.0.1", 0), FakeHandler)
        server.lock = threading.Lock()
        server.submissions, server.deleted, server.polls = [], [], {}
        server.submit_statuses, server.failing, server.hold, server.job_counter = [], set(), False, 0
        server.png_b64 = base64.b64encode(png_bytes(size=gi.IMAGE_SIZE)).decode()
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        self.server = server
        base = f"http://127.0.0.1:{server.server_port}"
        patcher = mock.patch.multiple(gi, HOST=base, API=f"{base}/v2", POLL_INTERVAL_S=0.01, RETRY_BASE_DELAY_S=0.01)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.client = gi.Pixellab("test-token")

    def test_a_round_returns_one_image_per_variant(self):
        images = self.client.generate_round("a prompt")
        self.assertEqual(gi.VARIANTS, len(images))
        for body in self.server.submissions:
            self.assertEqual("a prompt", body["description"])
            self.assertEqual({"width": gi.IMAGE_SIZE, "height": gi.IMAGE_SIZE}, body["image_size"])
            self.assertIs(gi.NO_BACKGROUND, body["no_background"])
        self.assertEqual(gi.VARIANTS, len({body["seed"] for body in self.server.submissions}))
        self.assertEqual(gi.VARIANTS, len(list((gi.STAGING / "jobs").glob("*.json"))))
        self.assertAlmostEqual(gi.VARIANTS * 0.02, self.client.spent_usd)

    def test_a_rate_limited_submit_is_retried(self):
        self.server.submit_statuses = [429]
        with mock.patch.object(gi, "VARIANTS", 1):
            self.assertEqual(1, len(self.client.generate_round("p")))
        self.assertEqual(2, len(self.server.submissions))

    def test_a_failed_variant_is_reported_and_the_others_are_kept(self):
        self.server.failing = {"job2"}
        self.assertEqual(gi.VARIANTS - 1, len(self.client.generate_round("p")))
        self.assertIn("failed", self.out.getvalue())

    def test_a_rejected_token_is_fatal(self):
        with self.assertRaises(gi.PixellabError) as caught:
            gi.Pixellab("wrong").generate_round("p")
        self.assertEqual(401, caught.exception.status)

    def test_running_out_of_credit_cancels_the_other_jobs(self):
        self.server.hold = True  # the other jobs would run forever
        self.server.submit_statuses = [202, 202, 202, 402]
        with self.assertRaises(gi.PixellabError) as caught:
            self.client.generate_round("p")
        self.assertEqual(402, caught.exception.status)
        self.assertEqual(["job1", "job2", "job3"], sorted(self.server.deleted))

    def test_ctrl_c_cancels_every_open_job(self):
        self.server.hold = True

        def interrupt_once_all_jobs_run():
            deadline = time.monotonic() + 5
            while len(self.server.polls) < gi.VARIANTS and time.monotonic() < deadline:
                time.sleep(0.01)
            os.kill(os.getpid(), signal.SIGINT)

        threading.Thread(target=interrupt_once_all_jobs_run, daemon=True).start()
        with self.assertRaises(KeyboardInterrupt):
            self.client.generate_round("p")
        self.assertEqual(gi.VARIANTS, len(self.server.deleted))

    def test_the_token_goes_to_pixellab_only(self):
        with mock.patch.object(gi.requests, "get", return_value=mock.Mock(ok=True, content=b"x")) as get:
            self.client.download("/files/a.png")
            self.client.download("https://cdn.example.com/a.png")
        own, foreign = (call.kwargs["headers"] for call in get.call_args_list)
        self.assertEqual({"Authorization": "Bearer test-token"}, own)
        self.assertEqual({}, foreign)


class MainTest(ClientTreeCase):
    def test_the_guided_run_asks_what_to_do_then_installs_the_pick(self):
        client = FakeClient([[gi.Image.new("RGBA", (gi.IMAGE_SIZE, gi.IMAGE_SIZE), (8, 8, 8, 255))]])
        client.request = lambda method, path: {"credits": {"usd": 1.0}}
        with mock.patch.object(gi, "require_client", return_value=client), \
                mock.patch.object(gi, "open_viewer"), \
                mock.patch.object(gi.sys.stdin, "isatty", return_value=True), \
                mock.patch("builtins.input", side_effect=["i", "n", "", "1"]):  # items, no replace, generate, use 1
            gi.main([])
        self.assertTrue((self.item_db / "19_iron_sword.png").exists())
        self.assertIn("Installed 1, skipped 0", self.out.getvalue())
        self.assertIn("Open the Godot editor", self.out.getvalue())

    def test_dry_run_lists_the_missing_icons_and_calls_nothing(self):
        gi.main(["items", "--dry-run"])
        output = self.out.getvalue()
        self.assertIn("Items: 1 of 2 have no icon", output)
        self.assertIn("item 19 - Iron Sword (iron_sword)", output)
        self.assertNotIn("Small Health Potion", output)


class ContactSheetTest(unittest.TestCase):
    def test_sheet_fits_all_candidates(self):
        images = [gi.Image.new("RGBA", (gi.IMAGE_SIZE, gi.IMAGE_SIZE)) for _ in range(3)]
        tile, gap = gi.IMAGE_SIZE * gi.PREVIEW_SCALE, 12
        self.assertEqual((3 * (tile + gap) + gap, tile + 2 * gap), gi.contact_sheet(images).size)


if __name__ == "__main__":
    unittest.main()
