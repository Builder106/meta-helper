"""Whiteboard, marker tray and camera glasses for the MetaHelper site.

Run headless on a build host (Cycles on CPU; Metal when available):
    blender -b --factory-startup -P board.py -- <out_dir> [board|tray|glasses|all] [samples]

Scale: 1 Blender metre = 1000 board units, so the 2800 x 1600 px board render maps one pixel
to one board unit. The site positions panels in those units.
"""
import json
import math
import sys

import bmesh
import bpy
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[0] if argv else "/tmp"
WHICH = argv[1] if len(argv) > 1 else "all"
SAMPLES = int(argv[2]) if len(argv) > 2 else 160
LIGHT = float(argv[3]) if len(argv) > 3 else 1.0
SCALE = int(argv[4]) if len(argv) > 4 else 100

BOARD_W, BOARD_H = 2.8, 1.6


def srgb(h):
    h = h.lstrip("#")
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    lin = [x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return (*lin, 1.0)


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    if sys.platform == "darwin":
        prefs = bpy.context.preferences.addons["cycles"].preferences
        prefs.compute_device_type = "METAL"
        prefs.get_devices()
        for d in prefs.devices:
            d.use = d.type == "METAL"
        scene.cycles.device = "GPU"
    else:
        scene.cycles.device = "CPU"
    scene.cycles.samples = SAMPLES
    scene.cycles.use_denoising = True
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.render.image_settings.file_format = "WEBP"
    scene.render.image_settings.quality = 84
    scene.render.resolution_percentage = SCALE
    world = bpy.data.worlds.new("room")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = srgb("#C9CED1")
    world.node_tree.nodes["Background"].inputs[1].default_value = 0.55
    scene.world = world
    return scene


def material(name, color, rough=0.4, metal=0.0, coat=0.0, alpha=1.0, transmission=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    p = m.node_tree.nodes["Principled BSDF"]
    p.inputs["Base Color"].default_value = srgb(color)
    p.inputs["Roughness"].default_value = rough
    p.inputs["Metallic"].default_value = metal
    p.inputs["Coat Weight"].default_value = coat
    p.inputs["Transmission Weight"].default_value = transmission
    p.inputs["Alpha"].default_value = alpha
    return m


def board_material():
    m = bpy.data.materials.new("melamine")
    m.use_nodes = True
    nt = m.node_tree
    p = nt.nodes["Principled BSDF"]
    p.inputs["Roughness"].default_value = 0.22
    p.inputs["Coat Weight"].default_value = 0.25
    # Ghosting: dry-erase boards keep faint grey clouds where ink was wiped.
    coord = nt.nodes.new("ShaderNodeTexCoord")
    noise = nt.nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = 2.2
    noise.inputs["Detail"].default_value = 9.0
    noise.inputs["Roughness"].default_value = 0.62
    streak = nt.nodes.new("ShaderNodeTexWave")
    streak.wave_type = "BANDS"
    streak.inputs["Scale"].default_value = 1.6
    streak.inputs["Distortion"].default_value = 9.0
    streak.inputs["Detail"].default_value = 4.0
    mix_in = nt.nodes.new("ShaderNodeMath")
    mix_in.operation = "MULTIPLY"
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.3
    ramp.color_ramp.elements[0].color = srgb("#EEF1F2")
    ramp.color_ramp.elements[1].position = 0.9
    ramp.color_ramp.elements[1].color = srgb("#E3E8EA")
    nt.links.new(coord.outputs["Object"], noise.inputs["Vector"])
    nt.links.new(coord.outputs["Object"], streak.inputs["Vector"])
    nt.links.new(noise.outputs["Fac"], mix_in.inputs[0])
    nt.links.new(streak.outputs["Fac"], mix_in.inputs[1])
    nt.links.new(mix_in.outputs[0], ramp.inputs["Fac"])
    nt.links.new(ramp.outputs["Color"], p.inputs["Base Color"])
    return m


def box(name, size, loc, mat, bevel=0.0, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.scale = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    o.data.materials.append(mat)
    if bevel:
        b = o.modifiers.new("bevel", "BEVEL")
        b.width = bevel
        b.segments = 3
        b.limit_method = "ANGLE"
    bpy.ops.object.shade_smooth()
    return o


def cylinder(name, r, depth, loc, mat, rot=(0, 0, 0), verts=48, bevel=0.0):
    bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=depth, location=loc, rotation=rot, vertices=verts)
    o = bpy.context.active_object
    o.name = name
    o.data.materials.append(mat)
    if bevel:
        b = o.modifiers.new("bevel", "BEVEL")
        b.width = bevel
        b.segments = 3
        b.limit_method = "ANGLE"
    bpy.ops.object.shade_smooth()
    return o


def build_board():
    alu = material("aluminium", "#B9BFC4", rough=0.32, metal=1.0)
    cap = material("corner-cap", "#4E5257", rough=0.5)
    wall = material("wall", "#D3D7D9", rough=0.9)

    bpy.ops.mesh.primitive_plane_add(size=1, location=(BOARD_W / 2, 0.03, BOARD_H / 2), rotation=(math.pi / 2, 0, 0))
    w = bpy.context.active_object
    w.scale = (BOARD_W * 2, BOARD_H * 2, 1)
    w.data.materials.append(wall)

    x0, x1, z0, z1 = 0.035, BOARD_W - 0.035, 0.15, BOARD_H - 0.035
    bpy.ops.mesh.primitive_plane_add(size=1, location=((x0 + x1) / 2, 0.0, (z0 + z1) / 2), rotation=(math.pi / 2, 0, 0))
    s = bpy.context.active_object
    s.name = "surface"
    s.scale = (x1 - x0, z1 - z0, 1)
    s.data.materials.append(board_material())

    t = 0.024
    box("frame-top", (x1 - x0 + 2 * t, 0.022, t), ((x0 + x1) / 2, -0.008, z1 + t / 2), alu, 0.004)
    box("frame-bottom", (x1 - x0 + 2 * t, 0.022, t), ((x0 + x1) / 2, -0.008, z0 - t / 2), alu, 0.004)
    box("frame-left", (t, 0.022, z1 - z0), (x0 - t / 2, -0.008, (z0 + z1) / 2), alu, 0.004)
    box("frame-right", (t, 0.022, z1 - z0), (x1 + t / 2, -0.008, (z0 + z1) / 2), alu, 0.004)
    for cx in (x0 - t / 2, x1 + t / 2):
        for cz in (z0 - t / 2, z1 + t / 2):
            box("corner", (0.036, 0.03, 0.036), (cx, -0.01, cz), cap, 0.008)

    # Marker tray: a floor plate and a low front lip under the bottom rail.
    tx0, tx1 = 0.32, BOARD_W - 0.32
    ty = -0.045
    box("tray-floor", (tx1 - tx0, 0.075, 0.004), ((tx0 + tx1) / 2, ty, z0 - t - 0.002), alu, 0.0015)
    box("tray-lip", (tx1 - tx0, 0.004, 0.016), ((tx0 + tx1) / 2, ty - 0.036, z0 - t + 0.004), alu, 0.0015)
    for ex in (tx0, tx1):
        box("tray-end", (0.012, 0.08, 0.026), (ex, ty, z0 - t + 0.008), cap, 0.003)
    return {"surface": [x0, x1, z0, z1], "tray": [tx0, tx1], "tray_floor_z": z0 - t}


def build_marker(name, color, x, floor_z, y=-0.05, roll=0.0):
    body = material(f"{name}-body", "#F2F3F3", rough=0.35)
    ink = material(f"{name}-cap", color, rough=0.3, coat=0.4)
    r = 0.0095
    z = floor_z + r
    rot = (0, math.pi / 2, roll)
    cylinder(f"{name}-barrel", r, 0.105, (x, y, z), body, rot, bevel=0.002)
    cylinder(f"{name}-band", r * 1.01, 0.03, (x + 0.01, y, z), ink, rot)
    cylinder(f"{name}-cap", r * 1.08, 0.04, (x + 0.07, y, z), ink, rot, bevel=0.003)
    cylinder(f"{name}-end", r * 0.8, 0.006, (x - 0.054, y, z), ink, rot, bevel=0.0015)
    box(f"{name}-clip", (0.03, 0.003, 0.004), (x + 0.075, y - r * 1.05, z + 0.002), ink, 0.001)


def build_eraser(x, floor_z, y=-0.046):
    top = material("eraser-top", "#24272B", rough=0.45)
    felt = material("eraser-felt", "#8C9094", rough=0.95)
    box("eraser-felt", (0.135, 0.05, 0.012), (x, y, floor_z + 0.006), felt, 0.003)
    box("eraser-top", (0.13, 0.046, 0.026), (x, y, floor_z + 0.025), top, 0.008)


def squircle(a, b, n=3.0, flare=0.16, side=1, count=96):
    pts = []
    for i in range(count):
        t = 2 * math.pi * i / count
        c, s = math.cos(t), math.sin(t)
        x = a * math.copysign(abs(c) ** (2 / n), c)
        y = b * math.copysign(abs(s) ** (2 / n), s)
        x *= 1 + flare * (y / b)  # wayfarer: wider at the brow
        if y < 0 and x * side > 0:
            x *= 1 - 0.14 * (-y / b)  # and tapered at the outer cheek
        pts.append((x, y))
    return pts


def ring_mesh(name, outer, inner, depth, mat):
    bm = bmesh.new()
    vo = [bm.verts.new((x, 0, y)) for x, y in outer]
    vi = [bm.verts.new((x, 0, y)) for x, y in inner]
    n = len(outer)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((vo[i], vo[j], vi[j], vi[i]))
    geom = bmesh.ops.extrude_face_region(bm, geom=list(bm.faces))
    moved = [e for e in geom["geom"] if isinstance(e, bmesh.types.BMVert)]
    bmesh.ops.translate(bm, verts=moved, vec=(0, depth, 0))
    bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(o)
    o.data.materials.append(mat)
    b = o.modifiers.new("bevel", "BEVEL")
    b.width = 0.0016
    b.segments = 4
    b.limit_method = "ANGLE"
    for p in o.data.polygons:
        p.use_smooth = True
    return o


def plate_mesh(name, outline, depth, mat):
    bm = bmesh.new()
    vs = [bm.verts.new((x, 0, y)) for x, y in outline]
    f = bm.faces.new(vs)
    geom = bmesh.ops.extrude_face_region(bm, geom=[f])
    moved = [e for e in geom["geom"] if isinstance(e, bmesh.types.BMVert)]
    bmesh.ops.translate(bm, verts=moved, vec=(0, depth, 0))
    bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(o)
    o.data.materials.append(mat)
    return o


def build_glasses(open_arms=True):
    """A generic pair of camera glasses. Origin: centre of the front, at the lens plane."""
    acetate = material("acetate", "#0A0B0D", rough=0.09, coat=1.0)
    lens = material("lens", "#1E2427", rough=0.04, coat=1.0, transmission=0.35)
    glass = material("camera-glass", "#050607", rough=0.02, coat=1.0)
    ring = material("camera-ring", "#3A3E43", rough=0.25, metal=1.0)
    led = material("led", "#C7CCD0", rough=0.2)
    grille = material("grille", "#2B2F33", rough=0.8)

    root = bpy.data.objects.new("glasses", None)
    bpy.context.collection.objects.link(root)
    parts = []
    a, b = 0.026, 0.0205
    for side in (-1, 1):
        cx = side * 0.0355
        inner = [(x + cx, y) for x, y in squircle(a, b, side=side)]
        outer = [(x + cx, y + 0.0022) for x, y in squircle(a + 0.0066, b + 0.0074, flare=0.14, side=side)]
        parts.append(ring_mesh(f"rim-{side}", outer, inner, 0.0062, acetate))
        lp = plate_mesh(f"lens-{side}", [(x + cx, y) for x, y in squircle(a + 0.001, b + 0.001, side=side)], 0.0016, lens)
        lp.location.y = 0.002
        parts.append(lp)
    # Brow bar and bridge keep the front reading as one piece of acetate.
    parts.append(box("bridge", (0.02, 0.0062, 0.0085), (0, 0.0031, 0.0125), acetate, 0.0022))
    for side in (-1, 1):
        parts.append(box(f"endpiece-{side}", (0.01, 0.009, 0.016), (side * 0.0705, 0.004, 0.012), acetate, 0.0022))

    # Camera at the viewer's left brow corner, capture LED at the right.
    cam_x, cam_z = -0.064, 0.0175
    parts.append(cylinder("camera-ring", 0.0036, 0.0016, (cam_x, -0.0006, cam_z), ring, (math.pi / 2, 0, 0)))
    parts.append(cylinder("camera-glass", 0.0026, 0.0018, (cam_x, -0.0010, cam_z), glass, (math.pi / 2, 0, 0)))
    parts.append(cylinder("led", 0.0013, 0.0014, (-cam_x, -0.0006, cam_z), led, (math.pi / 2, 0, 0)))

    arm_len = 0.142
    for side in (-1, 1):
        hinge = Vector((side * 0.0715, 0.009, 0.012))
        arm = box(f"arm-{side}", (0.0045, arm_len, 0.0125), (0, arm_len / 2, 0), acetate, 0.0016)
        tip = box(f"tip-{side}", (0.0042, 0.04, 0.009), (0, arm_len + 0.012, -0.012), acetate, 0.0016, rot=(math.radians(-38), 0, 0))
        spk = box(f"speaker-{side}", (0.0004, 0.014, 0.0026), (side * 0.00215, 0.03, -0.0038), grille)
        pad = box(f"touchpad-{side}", (0.0003, 0.03, 0.0058), (side * 0.0021, 0.064, 0.0012), grille)
        pivot = bpy.data.objects.new(f"hinge-{side}", None)
        bpy.context.collection.objects.link(pivot)
        pivot.location = hinge
        for o in (arm, tip, spk, pad):
            o.parent = pivot
        if open_arms:
            pivot.rotation_euler = (0, 0, side * math.radians(-4))
        else:
            pivot.rotation_euler = (0, 0, side * math.radians(88 if side < 0 else 92))
            pivot.location.y += 0.003 if side < 0 else 0.011
        pivot.parent = root
    for o in parts:
        o.parent = root
    return root


def ortho_camera(name, center, width, res, tilt_deg=8.0):
    cam = bpy.data.cameras.new(name)
    cam.type = "ORTHO"
    cam.ortho_scale = width
    cam.clip_end = 20
    o = bpy.data.objects.new(name, cam)
    bpy.context.collection.objects.link(o)
    t = math.radians(tilt_deg)
    d = 4.0
    o.location = (center[0], -d * math.cos(t), center[1] + d * math.sin(t))
    o.rotation_euler = (math.radians(90 - tilt_deg), 0, 0)
    scene = bpy.context.scene
    scene.camera = o
    scene.render.resolution_x, scene.render.resolution_y = res
    return o


def area(name, loc, rot, size, power, size_y=None):
    lamp = bpy.data.lights.new(name, "AREA")
    lamp.shape = "RECTANGLE"
    lamp.size = size
    lamp.size_y = size_y or size
    lamp.energy = power
    o = bpy.data.objects.new(name, lamp)
    o.location = loc
    o.rotation_euler = rot
    bpy.context.collection.objects.link(o)
    return o


def project(cam, p):
    from bpy_extras.object_utils import world_to_camera_view
    scene = bpy.context.scene
    v = world_to_camera_view(scene, cam, Vector(p))
    return [round(v.x * scene.render.resolution_x, 1), round((1 - v.y) * scene.render.resolution_y, 1)]


def room_lights():
    area("ceiling", (BOARD_W / 2, -1.6, 2.6), (math.radians(38), 0, 0), 3.2, LIGHT * 120, 0.6)
    area("window", (-0.6, -1.0, 1.4), (math.radians(80), 0, math.radians(-62)), 1.4, LIGHT * 40, None)
    area("fill", (BOARD_W / 2, -2.4, 0.3), (math.radians(100), 0, 0), 3.0, LIGHT * 18, 0.8)


def scene_board():
    reset()
    info = build_board()
    floor_z = info["tray_floor_z"]
    build_marker("marker-black", "#1B1E24", 0.6, floor_z, roll=0.3)
    build_marker("marker-blue", "#2246B8", 0.76, floor_z, y=-0.042, roll=1.2)
    build_eraser(1.0, floor_z)
    build_marker("marker-red", "#C0272D", 2.12, floor_z, y=-0.048, roll=2.0)
    g = build_glasses(open_arms=False)
    g.location = (1.42, -0.068, floor_z + 0.024)
    g.rotation_euler = (math.radians(-10), 0, math.radians(4))
    room_lights()
    cam = ortho_camera("cam-board", (BOARD_W / 2, BOARD_H / 2), BOARD_W, (2800, 1600))
    bpy.context.view_layer.update()
    x0, x1, z0, z1 = info["surface"]
    cam_world = bpy.data.objects["camera-glass"].matrix_world.translation
    points = {
        "surface": {"tl": project(cam, (x0, 0, z1)), "br": project(cam, (x1, 0, z0))},
        "tray": {"l": project(cam, (info["tray"][0], -0.08, floor_z)), "r": project(cam, (info["tray"][1], -0.08, floor_z))},
        "glasses": {
            "center": project(cam, g.matrix_world.translation),
            "camera": project(cam, cam_world),
            "led": project(cam, bpy.data.objects["led"].matrix_world.translation),
        },
        "markers": {k: project(cam, bpy.data.objects[f"{k}-cap"].matrix_world.translation) for k in ("marker-black", "marker-blue", "marker-red")},
        "eraser": project(cam, bpy.data.objects["eraser-top"].matrix_world.translation),
    }
    bpy.context.scene.render.filepath = f"{OUT}/board.webp"
    bpy.ops.render.render(write_still=True)
    with open(f"{OUT}/board-points.json", "w") as fh:
        json.dump(points, fh, indent=2)
    # A tight crop of the tray for the end of the phone board.
    ortho_camera("cam-tray", (1.02, info["tray_floor_z"] + 0.07), 1.08, (1300, 480), tilt_deg=14)
    bpy.context.scene.render.filepath = f"{OUT}/tray-phone.webp"
    bpy.ops.render.render(write_still=True)
    return points


def scene_glasses():
    """Open glasses on a desk in front of the board, the board soft behind them."""
    reset()
    scene = bpy.context.scene
    build_board()
    desk = material("desk", "#BFC4C7", rough=0.55)
    box("desk", (2.4, 0.9, 0.03), (1.4, -0.75, 0.885), desk, 0.004)
    g = build_glasses(open_arms=True)
    g.location = (1.38, -0.42, 0.9 + 0.0285)
    g.rotation_euler = (0, 0, math.radians(18))
    room_lights()
    area("key", (1.0, -0.95, 1.35), (math.radians(50), 0, math.radians(-30)), 0.9, LIGHT * 12)
    strip = area("strip", (1.25, -0.95, 1.3), (0, 0, 0), 1.4, LIGHT * 26, 0.06)
    strip.rotation_euler = (Vector((1.38, -0.42, 0.93)) - strip.location).to_track_quat("-Z", "Y").to_euler()
    cam = bpy.data.cameras.new("cam-glasses")
    cam.lens = 70
    cam.dof.use_dof = True
    cam.dof.aperture_fstop = 4.0
    o = bpy.data.objects.new("cam-glasses", cam)
    scene.collection.objects.link(o)
    target = Vector((1.385, -0.39, 0.925))
    o.location = target + Vector((-0.2, -0.42, 0.13))
    o.rotation_euler = (target - o.location).to_track_quat("-Z", "Y").to_euler()
    cam.dof.focus_distance = (Vector((1.37, -0.43, 0.93)) - o.location).length
    scene.camera = o
    scene.render.resolution_x, scene.render.resolution_y = 1600, 1000
    bpy.context.view_layer.update()
    names = ("camera-glass", "led", "touchpad-1", "speaker-1", "touchpad--1", "speaker--1")
    points = {k: project(o, bpy.data.objects[k].matrix_world.translation) for k in names}
    scene.render.filepath = f"{OUT}/glasses.webp"
    bpy.ops.render.render(write_still=True)
    with open(f"{OUT}/glasses-points.json", "w") as fh:
        json.dump(points, fh, indent=2)
    bpy.ops.wm.save_as_mainfile(filepath=f"{OUT}/glasses.blend")


if WHICH in ("board", "all"):
    scene_board()
    bpy.ops.wm.save_as_mainfile(filepath=f"{OUT}/board.blend")
if WHICH in ("glasses", "all"):
    scene_glasses()
