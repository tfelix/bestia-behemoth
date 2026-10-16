"""Builds the swappable master parts from the KayKit Adventurers 2.0 pack.

    blender -b --factory-startup --python tools/client/build_master_parts.py -- <path to KayKit_Adventurers_2.0_FREE>

Writes bestia-client/src/Game/Master/Parts/master_parts.glb: the pack's rig, which matches the client's Mage rig
bone for bone, with one skinned mesh per part:
- Head (the bald base), Face_1..Face_10 (6 to 10 are feminine) with FacialHair_<n> where a face has a beard,
  and Hair_1..Hair_4 (bald has no Hair part)
- worn gear, named Gear_<Item>, such as Gear_IronHelmet
- the base body in underwear: Body, ArmLeft, ArmRight, LegLeft, LegRight, and the same with _F for the female
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
# Holds the rig and the Mannequin, the plain body every KayKit outfit is modelled over.
RIG_SOURCE = Path("Animations") / "gltf" / "Rig_Medium" / "Rig_Medium_General.glb"

# Proto enum order: FACE_1 is index 0 and so on. Appending keeps stored masters on their part.
FACE_SOURCES = ["Knight", "Mage", "Ranger", "Barbarian", "Rogue"]
HAIR_SOURCES = ["Knight", "Ranger", "Mage", "Rogue"]

# Worn gear: part name -> (character, the character's meshes that make it up). An item's client resource
# names the part it shows when worn.
GEAR_SOURCES = {
    "Gear_IronHelmet": ("Knight", ["Knight_Helmet", "Knight_HelmetVisor"]),
}

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

# Underwear on the base body, in model space metres. The shirt has short sleeves to cover the shoulder seam.
WAISTBAND_TOP = 0.665
WAISTBAND_BOTTOM = 0.625
NECKLINE = 1.235
SLEEVE_END = 0.34
BRIEFS_LEG_OPENING = 0.40
UNDERWEAR_GRADIENTS = {
    "shirt": ((1.0, 1.0, 1.0), (0.84, 0.85, 0.88)),
    "briefs": ((0.86, 0.87, 0.9), (0.7, 0.72, 0.76)),
    "waistband": ((0.62, 0.64, 0.7), (0.48, 0.5, 0.56)),
}
FEMALE_SUFFIX = "_F"


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


def load_gear(pack: Path, character: str, mesh_names):
    """Imports a character and returns copies of the named meshes, which keep their own colours."""
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(pack / "Characters" / "gltf" / f"{character}.glb"))
    imported = {o.name: o for o in set(bpy.data.objects) - before}
    pieces = []
    for name in mesh_names:
        piece = duplicate(imported[name])
        piece["texture"] = source_texture(imported[name]).name
        piece["kind"] = "gear"
        pieces.append(piece)
    for obj in imported.values():
        bpy.data.objects.remove(obj)
    return pieces


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


# --- feminine faces --------------------------------------------------------------------------------------


class FaceRecipe:
    """A face put together from other faces' eyes, brows and nose, plus the touches that soften it."""

    def __init__(self, eyes, brows, nose, eye_scale=1.0, thin_brows=False, lips=False, freckles=False):
        self.eyes, self.brows, self.nose = eyes, brows, nose
        self.eye_scale, self.thin_brows = eye_scale, thin_brows
        self.lips, self.freckles = lips, freckles


# FACE_6 to FACE_10. Only the Mage's and the Rogue's eyes have lashes, so every feminine face uses one of them.
FEMININE_FACES = [
    FaceRecipe(eyes="Rogue", brows="Rogue", nose="Rogue", lips=True),
    FaceRecipe(eyes="Mage", brows="Rogue", nose="Mage"),
    FaceRecipe(eyes="Rogue", brows="Knight", nose="Mage", eye_scale=1.15, thin_brows=True, lips=True),
    FaceRecipe(eyes="Mage", brows="Ranger", nose="Rogue", thin_brows=True),
    FaceRecipe(eyes="Rogue", brows="Rogue", nose="Rogue", freckles=True),
]
LIPS_GRADIENT = ((0.88, 0.62, 0.62), (0.76, 0.5, 0.52))
FRECKLE_GRADIENT = ((0.74, 0.55, 0.48), (0.68, 0.5, 0.44))
# Left cheek, mirrored for the right.
FRECKLE_SPOTS = [(0.16, 1.47), (0.2, 1.49), (0.185, 1.515), (0.225, 1.465)]
MOUTH_HEIGHT = 1.415


def feminine_face(recipe, parts_of, slim_face, atlas, materials):
    eyes = parts_of(recipe.eyes, "eye")
    brows = parts_of(recipe.brows, "brow")
    parts = eyes + brows + parts_of(recipe.nose, "nose_mouth")
    for eye in eyes:
        scale_about(eye, eye_centre(eyes, eye), recipe.eye_scale)
    if recipe.thin_brows:
        for brow in brows:
            thin(brow)
    surface = face_surface(slim_face)
    if recipe.lips:
        parts.append(skin_dot(surface, (0.0, MOUTH_HEIGHT), (0.065, 0.012, 0.015), "lips", LIPS_GRADIENT, atlas, materials))
    if recipe.freckles:
        for x, z in FRECKLE_SPOTS:
            for side in (1, -1):
                parts.append(skin_dot(surface, (side * x, z), (0.009, 0.003, 0.009), "freckle", FRECKLE_GRADIENT, atlas, materials))
    return parts


def centroid(obj):
    return sum((v.co for v in obj.data.vertices), Vector()) / len(obj.data.vertices)


def eye_centre(eyes, eye):
    """An eye and its highlight scale about the same point, so the highlight stays on the eye."""
    side = [e for e in eyes if (centroid(e).x > 0) == (centroid(eye).x > 0)]
    return centroid(max(side, key=lambda e: len(e.data.vertices)))


def scale_about(obj, centre, factor):
    for vertex in obj.data.vertices:
        vertex.co = centre + (vertex.co - centre) * factor


def thin(brow, height=0.55, width=0.92, lift=0.012):
    middle = centroid(brow)
    for vertex in brow.data.vertices:
        offset = vertex.co - middle
        vertex.co = middle + Vector((offset.x * width, offset.y, offset.z * height + lift))


def face_surface(slim_face):
    bm = bmesh.new()
    bm.from_mesh(slim_face.data)
    bm.transform(slim_face.matrix_world)
    surface = BVHTree.FromBMesh(bm)
    bm.free()
    return surface


def skin_dot(surface, position, size, name, gradient, atlas, materials):
    """A small lens on the face, such as lips or a freckle. It is skin-tinted, so it suits every skin colour."""
    x, z = position
    hit, _, _, _ = surface.ray_cast(Vector((x, -1, z)), Vector((0, 1, 0)))
    if hit is None:
        # The shell is open at the mouth, where each face brings its own; the shell's depth around it serves.
        nearest, _, _, _ = surface.find_nearest(Vector((x, -0.6, z)))
        hit = Vector((x, nearest.y, z))
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=12, v_segments=6, radius=1.0)
    bmesh.ops.scale(bm, vec=Vector(size), verts=bm.verts)
    bmesh.ops.translate(bm, vec=hit + Vector((0, size[1] * 0.3, 0)), verts=bm.verts)
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    dot = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(dot)
    dot.data.materials.append(materials[SKIN])
    cell = atlas.synthetic(name, gradient)
    for uv in dot.data.uv_layers.new(name="UVMap").data:
        uv.uv = (cell, 0.6)
    for polygon in dot.data.polygons:
        polygon.use_smooth = True
    return dot


# --- the base body ---------------------------------------------------------------------------------------


AXES = {"x": 0, "y": 1, "z": 2}


class BodyPart:
    """A part of the base body: lofted along one axis through cross-sections of a Mannequin part.

    The Mannequin has the right volume but a plated surface. Fitting a rounded section to each slice and
    smoothing the profile keeps the volume and drops the plates.
    """

    def __init__(self, name, source, axis, start, end, rings, sides, smoothing, roundness=2.6, cap_start=0.0, cap_end=0.0, reshape=None):
        self.name, self.source, self.axis = name, source, axis
        self.start, self.end, self.rings, self.sides = start, end, rings, sides
        self.smoothing, self.roundness = smoothing, roundness
        self.cap_start, self.cap_end = cap_start, cap_end
        self.reshape = reshape

    def feminine(self):
        reshape = {"Body": feminine_torso, "Arm": slimmer(0.9), "Leg": slimmer_above_feet(0.92)}
        kind = next(k for k in reshape if self.name.startswith(k))
        return BodyPart(self.name + FEMALE_SUFFIX, self.source, self.axis, self.start, self.end, self.rings, self.sides,
                        self.smoothing, self.roundness, self.cap_start, self.cap_end, reshape[kind])


BODY_PARTS = [
    BodyPart("Body", "Mannequin_Body", "z", 0.40, 1.20, 22, 16, 3, cap_start=0.03, cap_end=0.07),
    BodyPart("ArmLeft", "Mannequin_ArmLeft", "x", 0.10, 0.94, 24, 12, 1, cap_start=0.02, cap_end=0.05),
    BodyPart("ArmRight", "Mannequin_ArmRight", "x", -0.10, -0.94, 24, 12, 1, cap_start=0.02, cap_end=0.05),
    BodyPart("LegLeft", "Mannequin_LegLeft", "z", 0.015, 0.50, 20, 12, 2, roundness=2.4, cap_start=0.012, cap_end=0.03),
    BodyPart("LegRight", "Mannequin_LegRight", "z", 0.015, 0.50, 20, 12, 2, roundness=2.4, cap_start=0.012, cap_end=0.03),
]


def bump(level, centre, width):
    return math.exp(-((level - centre) / width) ** 2)


def feminine_torso(section):
    """Narrower shoulders and waist, wider hips, a flatter belly and a bust pushed forward (the front is -y)."""
    level, x_min, x_max, y_min, y_max = section
    width = 1 - 0.12 * bump(level, 1.16, 0.08) - 0.26 * bump(level, 0.8, 0.14) + 0.1 * bump(level, 0.5, 0.1)
    x_mid = (x_min + x_max) / 2
    belly = 0.04 * bump(level, 0.78, 0.12)
    bust = 0.085 * bump(level, 1.04, 0.085)
    return [level, x_mid + (x_min - x_mid) * width, x_mid + (x_max - x_mid) * width,
            y_min + belly - bust, y_max - belly / 2]


def slimmer(factor):
    def reshape(section):
        level, first_min, first_max, second_min, second_max = section
        first_mid, second_mid = (first_min + first_max) / 2, (second_min + second_max) / 2
        return [level, first_mid + (first_min - first_mid) * factor, first_mid + (first_max - first_mid) * factor,
                second_mid + (second_min - second_mid) * factor, second_mid + (second_max - second_mid) * factor]
    return reshape


def slimmer_above_feet(factor, ankle=0.16):
    """The feet keep their size, so both bodies stand the same."""
    def reshape(section):
        blend = smoothstep((section[0] - ankle + 0.04) / 0.08)
        return slimmer(1 - (1 - factor) * blend)(section)
    return reshape


def watertight(obj, voxel_size=0.02):
    """The plates are separate shells with gaps between them; slicing needs one closed surface."""
    copy = duplicate(obj)
    remesh = copy.modifiers.new("remesh", "REMESH")
    remesh.mode = "VOXEL"
    remesh.voxel_size = voxel_size
    bpy.context.view_layer.objects.active = copy
    bpy.ops.object.modifier_apply(modifier=remesh.name)
    return copy


def cross_sections(obj, part):
    """Per level along the axis: the section's extent on the two other axes, each side measured apart."""
    axis = AXES[part.axis]
    first, second = [i for i in range(3) if i != axis]
    normal = Vector((0, 0, 0))
    normal[axis] = 1
    sections = []
    for ring in range(part.rings):
        level = part.start + (part.end - part.start) * ring / (part.rings - 1)
        bm = bmesh.new()
        bm.from_mesh(obj.data)
        origin = Vector((0, 0, 0))
        origin[axis] = level
        cut = bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=origin, plane_no=normal)
        points = [g.co.copy() for g in cut["geom_cut"] if isinstance(g, bmesh.types.BMVert)]
        bm.free()
        sections.append([level, min(p[first] for p in points), max(p[first] for p in points),
                         min(p[second] for p in points), max(p[second] for p in points)])
    sections = smoothed(sections, part.smoothing)
    if part.reshape:
        sections = [part.reshape(section) for section in sections]
    return sections, axis, first, second


def smoothed(sections, passes):
    """Irons out the plates' ridges. The end sections stay, so the part keeps its length."""
    for _ in range(passes):
        sections = [sections[0]] + [
            [here[0]] + [(before[k] + 2 * here[k] + after[k]) / 4 for k in range(1, 5)]
            for before, here, after in zip(sections, sections[1:], sections[2:])
        ] + [sections[-1]]
    return sections


def loft(part, sections, axis, first, second):
    bm = bmesh.new()
    rings = [section_ring(bm, part, section, axis, first, second) for section in sections]
    for lower, upper in zip(rings, rings[1:]):
        bridge(bm, lower, upper)
    toward_start = -1 if part.end > part.start else 1
    dome(bm, rings[0], axis, part.cap_start * toward_start)
    dome(bm, rings[-1], axis, -part.cap_end * toward_start)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    mesh = bpy.data.meshes.new(part.name)
    bm.to_mesh(mesh)
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    obj = bpy.data.objects.new(part.name, mesh)
    bpy.context.collection.objects.link(obj)
    return obj


def section_ring(bm, part, section, axis, first, second):
    """A rounded rectangle (superellipse) through the section's four extents."""
    level, first_min, first_max, second_min, second_max = section
    first_mid, second_mid = (first_min + first_max) / 2, (second_min + second_max) / 2
    ring = []
    for side in range(part.sides):
        angle = 2 * math.pi * side / part.sides
        c, s = math.cos(angle), math.sin(angle)
        co = Vector((0, 0, 0))
        co[axis] = level
        co[first] = first_mid + math.copysign(abs(c) ** (2 / part.roundness), c) * ((first_max - first_mid) if c > 0 else (first_mid - first_min))
        co[second] = second_mid + math.copysign(abs(s) ** (2 / part.roundness), s) * ((second_max - second_mid) if s > 0 else (second_mid - second_min))
        ring.append(bm.verts.new(co))
    return ring


def bridge(bm, lower, upper):
    sides = len(lower)
    for side in range(sides):
        bm.faces.new([lower[side], lower[(side + 1) % sides], upper[(side + 1) % sides], upper[side]])


def dome(bm, ring, axis, depth, steps=3):
    """Closes an open end with a rounded cap, so hands, feet and shoulders are not cut off flat."""
    centre = sum((v.co for v in ring), Vector()) / len(ring)
    previous = ring
    for step in range(1, steps):
        angle = math.pi / 2 * step / steps
        next_ring = []
        for vertex in ring:
            co = centre + (vertex.co - centre) * math.cos(angle)
            co[axis] += depth * math.sin(angle)
            next_ring.append(bm.verts.new(co))
        bridge(bm, previous, next_ring)
        previous = next_ring
    tip = centre.copy()
    tip[axis] += depth
    tip_vertex = bm.verts.new(tip)
    for side in range(len(previous)):
        bm.faces.new([previous[side], previous[(side + 1) % len(previous)], tip_vertex])


def build_body_part(part, sources):
    sections, axis, first, second = cross_sections(watertight(sources[part.source]), part)
    return loft(part, sections, axis, first, second)


def underwear_region(part_name, centre):
    """Which piece of underwear, if any, covers a face of the base body."""
    if part_name.startswith("Body"):
        if centre.z > NECKLINE:
            return None
        if centre.z > WAISTBAND_TOP:
            return "shirt"
        return "waistband" if centre.z > WAISTBAND_BOTTOM else "briefs"
    if part_name.startswith("Arm"):
        return "shirt" if abs(centre.x) < SLEEVE_END else None
    return "briefs" if centre.z > BRIEFS_LEG_OPENING else None


def paint_body_part(obj, atlas, materials):
    """Skin or underwear per face. The gradient runs from the part's top (light) to its bottom (dark)."""
    obj.data.materials.clear()
    obj.data.materials.append(materials[SKIN])
    obj.data.materials.append(materials[FIXED])
    z_values = [v.co.z for v in obj.data.vertices]
    bottom, top = min(z_values), max(z_values)
    uv_layer = obj.data.uv_layers.new(name="UVMap").data
    for polygon in obj.data.polygons:
        region = underwear_region(obj.name, polygon.center)
        polygon.material_index = 0 if region is None else 1
        atlas_index = atlas.cell_for(SKIN, None, None) if region is None else atlas.synthetic(region, UNDERWEAR_GRADIENTS[region])
        for loop, vertex in zip(polygon.loop_indices, polygon.vertices):
            shade = 0.3 + 0.65 * (obj.data.vertices[vertex].co.z - bottom) / max(top - bottom, 1e-6)
            uv_layer[loop].uv = (atlas_index, shade)


# --- skinning --------------------------------------------------------------------------------------------


def rig_to_head(obj, rig):
    group = obj.vertex_groups.new(name="head")
    group.add(range(len(obj.data.vertices)), 1.0, "REPLACE")
    attach(obj, rig)


def rig_like(obj, source, rig):
    """Copies the Mannequin part's weights onto the lofted part, which sits where the Mannequin's does."""
    transfer = obj.modifiers.new("weights", "DATA_TRANSFER")
    transfer.object = source
    transfer.use_vert_data = True
    transfer.data_types_verts = {"VGROUP_WEIGHTS"}
    transfer.vert_mapping = "POLYINTERP_NEAREST"
    bpy.ops.object.select_all(action="DESELECT")
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.datalayout_transfer(modifier=transfer.name)
    bpy.ops.object.modifier_apply(modifier=transfer.name)
    # The Mannequin's plates are weighted one bone each, so weights step at every plate edge and the
    # lofted surface creases there when posed.
    smooth_weights(obj, passes=6)
    bpy.ops.object.vertex_group_limit_total(limit=4)
    bpy.ops.object.vertex_group_normalize_all(lock_active=False)
    attach(obj, rig)


def smooth_weights(obj, passes):
    """Averages each vertex's weights with its neighbours', half and half."""
    groups = range(len(obj.vertex_groups))
    weights = [[0.0] * len(obj.vertex_groups) for _ in obj.data.vertices]
    for vertex in obj.data.vertices:
        for element in vertex.groups:
            weights[vertex.index][element.group] = element.weight
    neighbours = [[] for _ in obj.data.vertices]
    for edge in obj.data.edges:
        a, b = edge.vertices
        neighbours[a].append(b)
        neighbours[b].append(a)
    for _ in range(passes):
        weights = [
            [0.5 * own[g] + 0.5 * sum(weights[n][g] for n in near) / len(near) for g in groups] if near else own
            for own, near in zip(weights, neighbours)
        ]
    for group in obj.vertex_groups:
        for index, row in enumerate(weights):
            group.add([index], row[group.index], "REPLACE")


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

    def synthetic(self, key, top_and_bottom):
        """A cell for a colour the pack does not have, such as the underwear."""
        if key not in self.index:
            top, bottom = top_and_bottom
            self.index[key] = len(self.cells)
            self.cells.append(lambda: [tuple(b + (t - b) * y / (ATLAS_HEIGHT - 1) for t, b in zip(top, bottom)) for y in range(ATLAS_HEIGHT)])
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
    if part_kind_ in ("eye", "gear"):
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
    mannequin = {o.name: o for o in bpy.data.objects if o.type == "MESH"}

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
        head_parts.append(join(parts_of(character, "eye", "brow", "nose_mouth"), f"Face_{number}"))
        # Its own part, so a helmet can hide it together with the hair.
        if of_kind(heads[character], "facial_hair"):
            head_parts.append(join(parts_of(character, "facial_hair"), f"FacialHair_{number}"))
    for number, recipe in enumerate(FEMININE_FACES, start=len(FACE_SOURCES) + 1):
        head_parts.append(join(feminine_face(recipe, parts_of, slim_face, atlas, materials), f"Face_{number}"))
    for number, character in enumerate(HAIR_SOURCES, start=1):
        head_parts.append(join(parts_of(character, "hair"), f"Hair_{number}"))
    for part_name, (character, mesh_names) in GEAR_SOURCES.items():
        head_parts.append(join(prepared(load_gear(pack, character, mesh_names), character), part_name))

    all_body_parts = BODY_PARTS + [part.feminine() for part in BODY_PARTS]
    body_parts = [build_body_part(part, mannequin) for part in all_body_parts]
    for obj in body_parts:
        paint_body_part(obj, atlas, materials)

    for obj in head_parts:
        rig_to_head(obj, rig)
    for obj, part in zip(body_parts, all_body_parts):
        rig_like(obj, mannequin[part.source], rig)

    kept = set(head_parts + body_parts + [rig])
    for obj in list(bpy.data.objects):
        if obj not in kept:
            bpy.data.objects.remove(obj)

    atlas.finish_uvs(head_parts + body_parts)
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
