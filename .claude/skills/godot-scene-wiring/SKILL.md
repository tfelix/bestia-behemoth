---
name: godot-scene-wiring
description: How Godot client (bestia-client) nodes find each other without globals - the ladder from an editor-wired node in a .tscn, through a scene-wide service found by group, to static helpers, with an autoload only as the last resort. Read this BEFORE adding an autoload, a "Manager", a singleton, a get_instance(), or any node other code has to reach, and before putting a script into `Manager/`. Triggers on: autoload, singleton, global, Manager, get_instance, service locator, @export node, node_paths, unique name, share a node, scene-wide, where does this node live, add_child in code.
---

# Wiring nodes in the Godot client

Globals are kept to an absolute minimum. An autoload lives as long as the process, so everything it holds
outlives the world, the master and the menus - and every caller is coupled to it by name. Take the **first**
rung below that works, and say in the script's doc which rung it is on when that is not obvious.

## 1. A node in the scene, wired in the editor

Put the node into the `.tscn` that owns its lifetime and hand it to its users as a typed export:

```gdscript
@export var notebook: Notebook
```

assigned in the scene with `node_paths` (see `SpringArmCamera.tscn`, `camera_follow.gd` `spring_arm_position`,
or `UI.tscn` `MapMarks`/`MapOverlay` → `Notebook`). Within one scene `%UniqueName` works too. Signals go up,
calls go down. Prefer placing the node in the scene over `add_child` in code: the scene is what the editor
shows, and a hand-edited `.tscn` only needs the script's uid from its `.gd.uid` file.

## 2. A scene-wide service in `Game.tscn`

When the users are spawned at runtime (entities, their visuals and name tags) or sit inside another instanced
scene where an export cannot reach, place one node in `Game.tscn` and let it be found by group:

```gdscript
const _GROUP := "emblem_cache"

static func get_instance() -> EmblemCache:
	return (Engine.get_main_loop() as SceneTree).get_first_node_in_group(_GROUP) as EmblemCache

func _enter_tree() -> void:
	add_to_group(_GROUP)  # in _enter_tree, not _ready: siblings' _ready may already ask
```

Examples: `EntityManager`, `MouseManager`, `EmblemCache`. It dies with the world, so it never needs a reset.
Callers outside the world (menu previews reuse entity visuals) get null and must guard.

## 3. A `class_name` with static functions and constants

Stateless helpers need no node at all: `MouseCursor`, `GuildTagLine`, `DisplaySettings` (applied once from the
main menu at launch, guarded by a `static var`). A dev tool that only walks the current scene, like `RenderDebug`,
is just a node dropped into each scene that wants it.

## 4. An autoload - last resort

Only for something that must outlive scene changes **and** is needed before or between them. The whole list is
`SceneManager` (swaps the scenes), `ConnectionManager` (the connection), `DialogManager` (buffers server dialogs
that arrive before the game UI exists) and `AudioManager` (music across menus and world). Each script's doc states
its reason; a new entry needs one just as good.

**The tell that an autoload is wrong:** a `forget()`, `reset()` or `clear()` called whenever a scene is
entered. That state belongs to the scene - move it to rung 1 or 2 and the reset disappears.

## Where the script goes

Beside what it serves, not in `Manager/`: the notebook next to the map views that use it
(`Game/UI/Map/notebook.gd`), the emblem cache next to the name tag that draws emblems
(`Game/Entity/NameTag/emblem_cache.gd`). `Manager/` is for the few real autoloads.
