"""Cuts each tree model of the client down to the stump a removed tree leaves behind.

    blender -b --factory-startup --python tools/client/build_tree_stumps.py

Writes Stump_<Tree>.gltf and .bin next to each tree in bestia-client/src/Game/Terrain/Trees/, plus
Stump_EndGrain.png, the cut face all stumps share. A stump keeps its tree's model space, so the client scales
it with the tree's natural height and it matches the trunk that stood there.
"""
import math
import shutil
import tempfile
from pathlib import Path

import bmesh
import bpy
import numpy as np
from mathutils import Vector

REPO_ROOT = Path(__file__).resolve().parents[2]
TREES = REPO_ROOT / "bestia-client" / "src" / "Game" / "Terrain" / "Trees"
END_GRAIN = "Stump_EndGrain.png"

# Model-space cut height per tree, in metres. Low enough to stay under the lowest branch, high enough to keep
# the root flare.
CUT_HEIGHTS = {
    "CommonTree_1": 0.45,
    "CommonTree_2": 0.45,
    "CommonTree_3": 0.45,
    "CommonTree_4": 0.45,
    "CommonTree_5": 0.45,
    "Pine_1": 0.45,
    "Pine_2": 0.45,
    "Pine_3": 0.45,
    "Pine_4": 0.45,
    "Pine_5": 0.45,
}

# A saw cut is never quite level.
CUT_TILT_DEGREES = 6.0


def paint_end_grain(path, size=512, seed=3):
    """Rings around a pith, a pale sapwood band and a bark rim. UV radius 1 is the bark outline."""
    rng = np.random.default_rng(seed)
    ys, xs = np.mgrid[0:size, 0:size]
    u = (xs + 0.5) / size * 2 - 1
    v = (ys + 0.5) / size * 2 - 1
    r = np.sqrt(u * u + v * v)
    theta = np.arctan2(v, u)

    # Rings are not perfect circles, and the outer years wander most.
    wobble = sum(rng.uniform(-1, 1) / k * np.sin(2 * k * theta + rng.uniform(0, 2 * math.pi)) for k in range(1, 5))
    rr = r + 0.035 * wobble * r

    heart = np.array([0.80, 0.58, 0.36])
    sap = np.array([0.93, 0.78, 0.55])
    ring_line = np.array([0.62, 0.42, 0.24])
    bark = np.array([0.34, 0.22, 0.14])

    sapwood = np.clip((rr - 0.45) / 0.35, 0, 1)[..., None]
    colour = heart * (1 - sapwood) + sap * sapwood

    phase = rr / 0.86 * 7
    line = np.clip(1 - np.abs(phase - np.floor(phase) - 0.5) / 0.09, 0, 1) ** 1.5 * (rr < 0.84)
    colour = colour * (1 - 0.65 * line[..., None]) + ring_line * 0.65 * line[..., None]

    # Two drying cracks from the pith, the way a cut log checks.
    for angle, length in ((0.7, 0.55), (3.6, 0.38)):
        off_axis = np.abs(np.sin(theta - angle)) * r
        crack = (off_axis < 0.012 * (1 - r / length)) & (np.cos(theta - angle) > 0) & (r < length)
        colour[crack] = ring_line * 0.7

    colour[r < 0.035] = ring_line * 0.85

    rim = np.clip((r - 0.90) / 0.04, 0, 1)[..., None]
    colour = colour * (1 - rim) + bark * rim

    # A byte image keeps its pixels in sRGB, bottom row first.
    srgb = np.clip(colour, 0, 1)[::-1]
    rgba = np.concatenate([srgb, np.ones((size, size, 1))], axis=2)

    image = bpy.data.images.new("Stump_EndGrain", size, size)
    image.pixels.foreach_set(rgba.astype(np.float32).ravel())
    image.filepath_raw = str(path)
    image.file_format = "PNG"
    image.save()
    return image


def end_grain_material(image):
    material = bpy.data.materials.new("Stump_EndGrain")
    material.use_nodes = True
    nodes = material.node_tree.nodes
    shader = nodes["Principled BSDF"]
    shader.inputs["Roughness"].default_value = 0.85
    texture = nodes.new("ShaderNodeTexImage")
    texture.image = image
    material.node_tree.links.new(texture.outputs["Color"], shader.inputs["Base Color"])
    return material


def import_tree(name):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(TREES / f"{name}.gltf"))
    return next(o for o in bpy.data.objects if o not in before and o.type == "MESH")


def cut_plane(height):
    tilt = math.radians(CUT_TILT_DEGREES)
    return Vector((0.0, 0.0, height)), Vector((math.sin(tilt), 0.0, math.cos(tilt)))


def cut_to_stump(tree, height, end_grain):
    mesh = tree.data
    bark_index = next(i for i, m in enumerate(mesh.materials) if "Bark" in m.name)

    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if f.material_index != bark_index], context="FACES")
    # The glTF splits vertices at every UV seam; a cut through split vertices leaves no closed rim to cap.
    bmesh.ops.remove_doubles(bm, verts=bm.verts[:], dist=1e-4)

    origin, normal = cut_plane(height)
    bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], dist=1e-4,
                           plane_co=origin, plane_no=normal, clear_outer=True)
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_faces], context="VERTS")

    rim = [e for e in bm.edges
           if e.is_boundary and all(abs((v.co - origin).dot(normal)) < 1e-3 for v in e.verts)]
    caps = bmesh.ops.holes_fill(bm, edges=rim, sides=0)["faces"]
    rim_sizes = [len(cap.verts) for cap in caps]
    for e in rim:
        e.smooth = False

    bark = mesh.materials[bark_index]
    mesh.materials.clear()
    mesh.materials.append(bark)
    mesh.materials.append(end_grain)
    for face in bm.faces:
        face.material_index = 0
        face.smooth = True
    paint_rings(bm, caps, normal)

    bm.to_mesh(mesh)
    bm.free()
    if mesh.has_custom_normals:
        bpy.context.view_layer.objects.active = tree
        bpy.ops.mesh.customdata_custom_splitnormals_clear()
    return rim_sizes


def paint_rings(bm, caps, normal):
    """Gives each cap a centre vertex and maps its rim to the unit circle, so the rings follow the outline."""
    uv = bm.loops.layers.uv.active
    colour = bm.loops.layers.color.active
    side = normal.cross(Vector((0, 1, 0))).normalized()
    up = normal.cross(side).normalized()

    for cap in caps:
        centre = cap.calc_center_median()
        for triangle in bmesh.ops.poke(bm, faces=[cap], center_mode="MEAN")["faces"]:
            triangle.material_index = 1
            triangle.smooth = False
            for loop in triangle.loops:
                offset = loop.vert.co - centre
                if offset.length < 1e-6:
                    loop[uv].uv = (0.5, 0.5)
                else:
                    angle = math.atan2(offset.dot(up), offset.dot(side))
                    loop[uv].uv = (0.5 + 0.5 * math.cos(angle), 0.5 + 0.5 * math.sin(angle))
                # White, because the bark's vertex colours tint whatever material draws them.
                if colour:
                    loop[colour] = (1.0, 1.0, 1.0, 1.0)


def export(stump, name, scratch):
    bpy.ops.object.select_all(action="DESELECT")
    stump.select_set(True)
    gltf = Path(scratch) / f"{name}.gltf"
    bpy.ops.export_scene.gltf(filepath=str(gltf), use_selection=True, export_format="GLTF_SEPARATE")
    # The exporter also re-encodes every texture it touches; the trees' own PNGs stay as they are.
    for part in (gltf, gltf.with_suffix(".bin")):
        shutil.copyfile(part, TREES / part.name)


def main():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    end_grain = end_grain_material(paint_end_grain(TREES / END_GRAIN))

    with tempfile.TemporaryDirectory() as scratch:
        for tree_name, height in CUT_HEIGHTS.items():
            tree = import_tree(tree_name)
            stump_name = f"Stump_{tree_name}"
            loops = cut_to_stump(tree, height, end_grain)
            tree.name = stump_name
            tree.data.name = stump_name
            export(tree, stump_name, scratch)
            print(f"{stump_name}: cut at {height} m, cap rims {loops}")
            forget_tree(tree, keep=end_grain)


def forget_tree(tree, keep):
    """Drops everything one import brought in, so the next tree's materials and images keep their own names.

    The exporter names each texture after its image, and a second import would be `Bark_NormalTree.001`.
    """
    mesh = tree.data
    bpy.data.objects.remove(tree)
    bpy.data.meshes.remove(mesh)
    for material in [m for m in bpy.data.materials if m != keep]:
        bpy.data.materials.remove(material)
    for image in [i for i in bpy.data.images if i not in (keep.node_tree.nodes["Image Texture"].image,)]:
        bpy.data.images.remove(image)


main()
