"""Builds the swappable master parts from the KayKit Adventurers 2.0 pack.

    blender -b --factory-startup --python tools/client/build_master_parts.py -- <path to KayKit_Adventurers_2.0_FREE>

Writes bestia-client/src/Game/Master/Parts/master_parts.glb: the pack's rig, which matches the client's Mage rig
bone for bone, with one skinned mesh per part:
- Head (the bald base), Face_1..Face_5 and Hair_1..Hair_4 (bald has no Hair part)
"""
import math
import sys
from pathlib import Path

import bmesh
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

REPO_ROOT = Path(__file__).resolve().parents[2]
OUTPUT = REPO_ROOT / "bestia-client" / "src" / "Game" / "Master" / "Parts" / "master_parts.glb"
RIG_SOURCE = Path("Animations") / "gltf" / "Rig_Medium" / "Rig_Medium_General.glb"

# Proto enum order: FACE_1 is index 0 and so on. Appending keeps stored masters on their part.
FACE_SOURCES = ["Knight", "Mage", "Ranger", "Barbarian", "Rogue"]
HAIR_SOURCES = ["Knight", "Ranger", "Mage", "Rogue"]

# Mage and Rogue share this face shell, and the other hairstyles were modelled around a near copy of it.
SLIM_FACE_SOURCE = "Mage"
# The only head in the pack with a closed skull.
CLOSED_SKULL_SOURCE = "Barbarian"

# KayKit textures are an 8 x 4 grid of vertical gradients. Shading lives only in the vertical position.
PALETTE_COLUMNS = 8
PALETTE_ROWS = 4
ATLAS_CELL_WIDTH = 32
ATLAS_HEIGHT = 256

SKIN_RGB = (0.96, 0.73, 0.57)

HAIR, SKIN, FIXED = "hair", "skin", "fixed"



# --- reading the pack ------------------------------------------------------------------------------------


def load_head_parts(pack: Path, character: str):
    """Imports a character and returns its head split into loose parts, each tagged with a 'kind'."""
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(pack / "Characters" / "gltf" / f"{character}.glb"))
    imported = set(bpy.data.objects) - before

    head = next(o for o in imported if o.name.endswith("_Head"))
    for obj in imported:
        if obj is not head and obj.type == "MESH":
            bpy.data.objects.remove(obj)

    bpy.ops.object.select_all(action="DESELECT")
    bpy.context.view_layer.objects.active = head
    head.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.mesh.separate(type="LOOSE")
    bpy.ops.object.mode_set(mode="OBJECT")

    parts = [o for o in bpy.data.objects if o.name.startswith(head.name)]
    texture = source_texture(parts[0])
    for part in parts:
        part["kind"] = part_kind(part, texture)
        part["texture"] = texture.name
    return parts


def source_texture(obj):
    nodes = obj.material_slots[0].material.node_tree.nodes
    return next(n for n in nodes if n.type == "TEX_IMAGE").image


def palette_cell(u: float, v: float):
    return (min(PALETTE_COLUMNS - 1, int(u * PALETTE_COLUMNS)), min(PALETTE_ROWS - 1, int(v * PALETTE_ROWS)))


_pixel_cache = {}


def pixels_of(image):
    """Reading image.pixels copies the whole image, so each source texture is read once."""
    if image.name not in _pixel_cache:
        _pixel_cache[image.name] = image.pixels[:]
    return _pixel_cache[image.name]


def cell_mean_colour(image, cell):
    column, row = cell
    width, height = image.size
    cell_width, cell_height = width // PALETTE_COLUMNS, height // PALETTE_ROWS
    pixels = pixels_of(image)
    total = [0.0, 0.0, 0.0]
    samples = 0
    for y in range(row * cell_height, (row + 1) * cell_height, 8):
        for x in range(column * cell_width, (column + 1) * cell_width, 8):
            i = (y * width + x) * 4
            for c in range(3):
                total[c] += pixels[i + c]
            samples += 1
    return tuple(t / samples for t in total)


def face_cell(mesh, polygon):
    uv_layer = mesh.uv_layers.active.data
    uvs = [uv_layer[i].uv for i in polygon.loop_indices]
    return palette_cell(sum(uv.x for uv in uvs) / len(uvs), sum(uv.y for uv in uvs) / len(uvs))


def dominant_colour(obj, image):
    cells = {}
    for polygon in obj.data.polygons:
        cell = face_cell(obj.data, polygon)
        cells[cell] = cells.get(cell, 0) + 1
    return cell_mean_colour(image, max(cells, key=cells.get))


def is_skin(rgb):
    return all(abs(a - b) < tolerance for a, b, tolerance in zip(rgb, SKIN_RGB, (0.06, 0.08, 0.1)))


def part_kind(obj, image):
    """Sorts a loose part by its colour and where it sits on the head. KayKit heads share one layout."""
    points = [obj.matrix_world @ v.co for v in obj.data.vertices]
    x0, x1 = min(p.x for p in points), max(p.x for p in points)
    y0, y1 = min(p.y for p in points), max(p.y for p in points)
    z0, z1 = min(p.z for p in points), max(p.z for p in points)
    vertex_count = len(points)
    on_face_front = y1 < -0.2

    if is_skin(dominant_colour(obj, image)):
        if x0 > 0.3 or x1 < -0.3:
            return "ear"
        if x0 < -0.3 and x1 > 0.3:
            return "skull"
        return "nose_mouth" if on_face_front else "skin_other"
    if on_face_front and z0 > 1.5 and z1 < 1.71 and vertex_count <= 60:
        return "eye"
    if on_face_front and z0 > 1.64 and z1 < 1.86 and (z1 - z0) < 0.2 and vertex_count <= 80:
        return "brow"
    if z1 < 1.56 and y0 < -0.4 and abs((x0 + x1) / 2) < 0.25:
        return "facial_hair"
    return "hair"


def of_kind(parts, *kinds):
    return [p for p in parts if p["kind"] in kinds]


# --- building the parts ----------------------------------------------------------------------------------


def smoothstep(t: float) -> float:
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def build_bald_skull(closed_skull, slim_face, tuck=0.012, crown_inset=0.07):
    """The slim face shell in front, the closed skull behind it for the back and crown.

    The closed skull alone is too wide at the jaw: long hair, modelled around the slim face, shows its
    inside through the cheeks. So the skull is fitted onto the slim face and tucked just behind it, and the
    exact slim face stays the visible front. The crown is pulled in so that no hairstyle lets it poke out.
    """
    bm = bmesh.new()
    bm.from_mesh(slim_face.data)
    bm.transform(slim_face.matrix_world)
    slim_surface = BVHTree.FromBMesh(bm)

    skull = duplicate(closed_skull)
    head_centre = Vector((0.0, 0.0, 1.62))
    for vertex in skull.data.vertices:
        p = vertex.co
        face_weight = (1 - smoothstep((p.y - 0.2) / 0.2)) * (1 - smoothstep((p.z - 1.8) / 0.15))
        nearest, normal, _, _ = slim_surface.find_nearest(p)
        p = p.lerp(nearest - normal * tuck, face_weight)
        crown_weight = min(1.0, smoothstep((p.z - 1.75) / 0.2) + smoothstep((p.y - 0.05) / 0.25)) * (1 - face_weight)
        vertex.co = p - (p - head_centre).normalized() * crown_inset * crown_weight
    return [skull, duplicate(slim_face)]


def duplicate(obj):
    """A standalone copy in model space, so parts from different imports can be joined."""
    copy = obj.copy()
    copy.data = obj.data.copy()
    copy.data.transform(obj.matrix_world)
    copy.parent = None
    copy.matrix_world.identity()
    copy.modifiers.clear()
    copy.vertex_groups.clear()
    for key in ("kind", "texture"):
        if key in obj:
            copy[key] = obj[key]
    bpy.context.collection.objects.link(copy)
    return copy


def join(objects, name=None):
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = objects[0]
    bpy.ops.object.join()
    joined = bpy.context.view_layer.objects.active
    if name:
        joined.name = name
        joined.data.name = name
    return joined


# --- skinning --------------------------------------------------------------------------------------------


def rig_to_head(obj, rig):
    group = obj.vertex_groups.new(name="head")
    group.add(range(len(obj.data.vertices)), 1.0, "REPLACE")
    attach(obj, rig)


def attach(obj, rig):
    obj.parent = rig
    obj.matrix_parent_inverse = rig.matrix_world.inverted()
    obj.modifiers.new("rig", "ARMATURE").object = rig


# --- texture atlas ---------------------------------------------------------------------------------------


class Atlas:
    """Hair and skin get one neutral gradient each, tinted by the client. Everything else keeps its colour.

    Each atlas cell is a whole KayKit cell squeezed to a narrow column. Every UV is moved to the column's
    centre: the gradient only runs vertically, and the centre keeps mipmaps from bleeding neighbours in.
    """

    def __init__(self):
        self.cells = []  # a function per cell that returns its gradient
        self.index = {}

    def cell_for(self, role, image, cell):
        """The hair or skin cell is created from the first part that asks for it."""
        key = role if role in (HAIR, SKIN) else (image.name, cell)
        if key not in self.index:
            neutral = role != FIXED
            self.index[key] = len(self.cells)
            self.cells.append(lambda: neutralised(vertical_gradient(image, cell)) if neutral else vertical_gradient(image, cell))
        return self.index[key]

    def finish_uvs(self, objects):
        """UVs hold the bare cell index until every cell is known, then move to that cell's centre."""
        for obj in objects:
            for uv in obj.data.uv_layers.active.data:
                uv.uv.x = (round(uv.uv.x) + 0.5) / len(self.cells)

    def bake(self, name):
        width = ATLAS_CELL_WIDTH * len(self.cells)
        atlas = bpy.data.images.new(name, width, ATLAS_HEIGHT, alpha=False)
        pixels = [1.0] * (width * ATLAS_HEIGHT * 4)
        for index, make_gradient in enumerate(self.cells):
            gradient = make_gradient()
            for y in range(ATLAS_HEIGHT):
                for x in range(index * ATLAS_CELL_WIDTH, (index + 1) * ATLAS_CELL_WIDTH):
                    i = (y * width + x) * 4
                    pixels[i:i + 3] = gradient[y]
        atlas.pixels[:] = pixels
        atlas.pack()
        return atlas


def vertical_gradient(image, cell):
    """The cell's colour down its centre line, resampled to the atlas height."""
    column, row = cell
    width, height = image.size
    cell_width, cell_height = width // PALETTE_COLUMNS, height // PALETTE_ROWS
    x = column * cell_width + cell_width // 3
    pixels = pixels_of(image)
    gradient = []
    for y in range(ATLAS_HEIGHT):
        source_y = row * cell_height + int(y * cell_height / ATLAS_HEIGHT)
        i = (source_y * width + x) * 4
        gradient.append(tuple(pixels[i:i + 3]))
    return gradient


def neutralised(gradient):
    """Brightness only, scaled so the lightest shade is white: the tint is then the lightest shade."""
    luminance = [0.2126 * r + 0.7152 * g + 0.0722 * b for r, g, b in gradient]
    brightest = max(luminance)
    return [(l / brightest,) * 3 for l in luminance]


# --- roles and materials ---------------------------------------------------------------------------------


def face_role(part_kind_, cell_colour, hair_colour):
    if part_kind_ in ("skull", "ear", "nose_mouth", "skin_other"):
        return SKIN
    if part_kind_ == "eye":
        return FIXED
    # Hair can carry trinkets, such as the Rogue's earring. They keep their own colour.
    distance = math.dist(cell_colour, hair_colour)
    return HAIR if distance < 0.15 else FIXED


def hair_reference_colour(parts):
    hairy = of_kind(parts, "hair", "facial_hair") or of_kind(parts, "brow")
    largest = max(hairy, key=lambda p: len(p.data.vertices))
    return dominant_colour(largest, bpy.data.images[largest["texture"]])


def assign_roles(obj, atlas, materials, hair_colour):
    """Gives every face a hair, skin or fixed material and moves its UVs into the atlas.

    Runs per loose part, before parts are joined, because the role depends on the part's kind.
    """
    image = bpy.data.images[obj["texture"]]
    obj.data.materials.clear()
    for role in (HAIR, SKIN, FIXED):
        obj.data.materials.append(materials[role])
    slot = {HAIR: 0, SKIN: 1, FIXED: 2}

    uv_layer = obj.data.uv_layers.active.data
    for polygon in obj.data.polygons:
        cell = face_cell(obj.data, polygon)
        role = face_role(obj["kind"], cell_mean_colour(image, cell), hair_colour)
        polygon.material_index = slot[role]
        atlas_index = atlas.cell_for(role, image, cell)
        for loop in polygon.loop_indices:
            local_v = uv_layer[loop].uv.y * PALETTE_ROWS - cell[1]
            uv_layer[loop].uv = (atlas_index, max(0.0, min(1.0, local_v)))


def make_material(name):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    nodes = material.node_tree.nodes
    shader = nodes["Principled BSDF"]
    shader.inputs["Roughness"].default_value = 0.5  # KayKit's value
    shader.inputs["Metallic"].default_value = 0.0
    texture = nodes.new("ShaderNodeTexImage")
    material.node_tree.links.new(texture.outputs["Color"], shader.inputs["Base Color"])
    return material


# --- main ------------------------------------------------------------------------------------------------


def main(pack: Path):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(pack / RIG_SOURCE))
    rig = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    rig.animation_data_clear()

    characters = sorted(set(FACE_SOURCES + HAIR_SOURCES + [SLIM_FACE_SOURCE, CLOSED_SKULL_SOURCE]))
    heads = {c: load_head_parts(pack, c) for c in characters}
    hair_colours = {c: hair_reference_colour(parts) for c, parts in heads.items()}

    atlas = Atlas()
    materials = {role: make_material(f"master_{role}") for role in (HAIR, SKIN, FIXED)}

    def prepared(copies, character):
        for copy in copies:
            assign_roles(copy, atlas, materials, hair_colours[character])
        return copies

    def parts_of(character, *kinds):
        return prepared([duplicate(p) for p in of_kind(heads[character], *kinds)], character)

    closed_skull = of_kind(heads[CLOSED_SKULL_SOURCE], "skull")[0]
    slim_face = of_kind(heads[SLIM_FACE_SOURCE], "skull")[0]
    skull = prepared(build_bald_skull(closed_skull, slim_face), SLIM_FACE_SOURCE)
    head_parts = [join(skull + parts_of(SLIM_FACE_SOURCE, "ear"), "Head")]
    for number, character in enumerate(FACE_SOURCES, start=1):
        head_parts.append(join(parts_of(character, "eye", "brow", "nose_mouth", "facial_hair"), f"Face_{number}"))
    for number, character in enumerate(HAIR_SOURCES, start=1):
        head_parts.append(join(parts_of(character, "hair"), f"Hair_{number}"))

    for obj in head_parts:
        rig_to_head(obj, rig)

    kept = set(head_parts + [rig])
    for obj in list(bpy.data.objects):
        if obj not in kept:
            bpy.data.objects.remove(obj)

    atlas.finish_uvs(head_parts)
    baked = atlas.bake("master_parts")
    for material in materials.values():
        material.node_tree.nodes["Image Texture"].image = baked

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.object.select_all(action="DESELECT")
    for obj in kept:
        obj.select_set(True)
    bpy.ops.export_scene.gltf(
        filepath=str(OUTPUT),
        export_format="GLB",
        use_selection=True,
        export_animations=False,
        export_skins=True,
        export_morph=False,
    )
    print(f"Wrote {OUTPUT} with {len(kept) - 1} parts and {len(atlas.cells)} atlas cells")


if __name__ == "__main__":
    main(Path(sys.argv[sys.argv.index("--") + 1]).expanduser())
