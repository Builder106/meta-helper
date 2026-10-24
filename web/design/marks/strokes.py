"""Seeded marker-stroke geometry for the Glaxnimate drawings.

Prints one JSON document per mark: {"name": {"width", "height", "elements"}}. Each element
list goes to glaxnimate_create_svg unchanged; the SVG it returns is saved as <name>.svg.
Marker strokes wobble a little and overshoot their corners, the way a dry-erase pen does.
"""
import json
import math
import random

R = random.Random(1217)
INK, BLUE, RED, MUTED = "#1B1E24", "#2246B8", "#C0272D", "#585E65"


def f(v):
    return f"{v:.1f}".rstrip("0").rstrip(".")


def smooth(pts, closed=False):
    """Catmull-Rom through pts, as cubic Bezier path data."""
    if closed:
        pts = pts + pts[:3]
    d = f"M{f(pts[0][0])} {f(pts[0][1])}"
    for i in range(len(pts) - 1):
        p0 = pts[i - 1] if i else pts[0]
        p1, p2 = pts[i], pts[i + 1]
        p3 = pts[i + 2] if i + 2 < len(pts) else p2
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += f" C{f(c1[0])} {f(c1[1])} {f(c2[0])} {f(c2[1])} {f(p2[0])} {f(p2[1])}"
    return d


def wobble_line(a, b, amp=1.6, steps=5):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        x = a[0] + (b[0] - a[0]) * t
        y = a[1] + (b[1] - a[1]) * t
        nx, ny = -(b[1] - a[1]), b[0] - a[0]
        n = math.hypot(nx, ny) or 1
        k = math.sin(t * math.pi) * R.uniform(-amp, amp)
        pts.append((x + nx / n * k, y + ny / n * k))
    return pts


def rough_box(x0, y0, x1, y1):
    """Two strokes, the way people draw a box: top+right, then left+bottom, corners overshooting."""
    o = lambda: R.uniform(3, 9)
    j = lambda: R.uniform(-2.5, 2.5)
    s1 = wobble_line((x0 - o(), y0 + j()), (x1 + j(), y0 + j())) + wobble_line((x1 + j(), y0 - o()), (x1 + j(), y1 + o()))[1:]
    s2 = wobble_line((x0 + j(), y0 - o() * 0.4), (x0 + j(), y1 + j())) + wobble_line((x0 - o() * 0.5, y1 + j()), (x1 + o(), y1 + j()))[1:]
    return smooth(s1), smooth(s2)


def arrow(a, b, bend=0.0, head=16):
    mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
    nx, ny = -(b[1] - a[1]), b[0] - a[0]
    n = math.hypot(nx, ny) or 1
    c = (mid[0] + nx / n * bend, mid[1] + ny / n * bend)
    shaft = smooth([a, (a[0] * 0.5 + c[0] * 0.5, a[1] * 0.5 + c[1] * 0.5), c, (c[0] * 0.5 + b[0] * 0.5, c[1] * 0.5 + b[1] * 0.5), b])
    ang = math.atan2(b[1] - c[1], b[0] - c[0])
    l = (b[0] - head * math.cos(ang - 0.5) + R.uniform(-1, 1), b[1] - head * math.sin(ang - 0.5))
    r = (b[0] - head * math.cos(ang + 0.5), b[1] - head * math.sin(ang + 0.5) + R.uniform(-1, 1))
    tip = smooth([l, b, r])
    return shaft, tip


def loop(cx, cy, rx, ry, turns=1.12, tilt=-0.08):
    pts = []
    n = 34
    for i in range(int(n * turns) + 1):
        t = i / n * 2 * math.pi + 2.2
        k = 1 + R.uniform(-0.035, 0.035) + 0.05 * (i / n)
        x, y = rx * k * math.cos(t), ry * k * math.sin(t)
        pts.append((cx + x * math.cos(tilt) - y * math.sin(tilt), cy + x * math.sin(tilt) + y * math.cos(tilt)))
    return smooth(pts)


def stroke(id_, d, color, width):
    return {"type": "path", "id": id_, "attributes": {"d": d, "fill": "none", "stroke": color, "stroke-width": width,
            "stroke-linecap": "round", "stroke-linejoin": "round"}}


def text(id_, x, y, body, size, color, weight=600, anchor="middle"):
    return {"type": "text", "id": id_, "text": body, "attributes": {"x": x, "y": y, "fill": color, "font-size": size,
            "font-family": "Shantell Sans", "font-weight": weight, "text-anchor": anchor}}


marks = {}

# Pipeline: the loop from the glasses to the server and back to your ears.
els = []
boxes = [
    ("glasses", 20, 40, 210, 130, "glasses", "takes the photo"),
    ("phone", 300, 40, 470, 130, "phone", "spots it in the gallery"),
    ("server", 560, 40, 740, 130, "server", "POST /process-image"),
    ("gemini", 830, 40, 1020, 130, "Gemini", "reads it, explains it"),
    ("azure", 780, 270, 1020, 360, "Azure Speech", "says it as an MP3"),
    ("ffmpeg", 520, 270, 690, 360, "ffmpeg", "turns it down"),
    ("ears", 20, 270, 250, 360, "your ears", "open-ear speakers"),
]
for key, x0, y0, x1, y1, label, note in boxes:
    a, b = rough_box(x0, y0, x1, y1)
    els.append(stroke(f"box-{key}-a", a, BLUE, 4.5))
    els.append(stroke(f"box-{key}-b", b, BLUE, 4.5))
    els.append(text(f"label-{key}", (x0 + x1) / 2, (y0 + y1) / 2 + 11, label, 32, INK, 700))
    els.append(text(f"note-{key}", (x0 + x1) / 2, y1 + 38, note, 21, MUTED, 500))
links = [((222, 82), (288, 84), 4), ((482, 84), (548, 82), -4), ((752, 82), (818, 85), 4),
         ((930, 182), (918, 258), 10), ((768, 318), (702, 314), -5), ((508, 314), (264, 318), 14)]
for i, (a, b, bend) in enumerate(links):
    shaft, tip = arrow(a, b, bend)
    els.append(stroke(f"arrow-{i}", shaft, BLUE, 4))
    els.append(stroke(f"arrow-{i}-tip", tip, BLUE, 4))
marks["pipeline"] = {"width": 1040, "height": 420, "elements": els}

# Red marker: the bug circle on sum.c, its arrow and the margin note.
els = [stroke("bug-loop", loop(78, 40, 66, 30), RED, 5)]
shaft, tip = arrow((150, 70), (230, 132), -18, 14)
els += [stroke("bug-arrow", shaft, RED, 4.5), stroke("bug-arrow-tip", tip, RED, 4.5)]
els.append(text("bug-note", 244, 156, "one step too far", 30, RED, 650, "start"))
marks["bug"] = {"width": 520, "height": 190, "elements": els}

# Hero underline: one fast pass of the pen, flicking up at the end.
pts = [(6, 24), (120, 18), (260, 21), (420, 15), (560, 19), (690, 11), (720, 4)]
marks["underline"] = {"width": 730, "height": 34, "elements": [stroke("underline-pass", smooth([(x + R.uniform(-2, 2), y + R.uniform(-1.5, 1.5)) for x, y in pts]), BLUE, 7)]}

# Start-here arrow from the hero to sum.c.
shaft, tip = arrow((10, 90), (230, 18), 40, 20)
marks["start"] = {"width": 250, "height": 110, "elements": [stroke("start-shaft", shaft, INK, 4.5), stroke("start-tip", tip, INK, 4.5)]}

# Glasses callouts: arrows from the labels down to the pair lying in the tray.
els = []
for i, (a, b, bend) in enumerate([((80, 30), (150, 250), 30), ((420, 30), (300, 250), -26)]):
    shaft, tip = arrow(a, b, bend, 18)
    els += [stroke(f"callout-{i}", shaft, BLUE, 4.5), stroke(f"callout-{i}-tip", tip, BLUE, 4.5)]
marks["callouts"] = {"width": 500, "height": 270, "elements": els}

# Tick marks for the lab notes.
els = []
for i in range(4):
    y = 18 + i * 64
    els.append(stroke(f"tick-{i}", smooth([(4, y + 10), (12, y + 20), (30, y - 4)]), INK, 4.5))
marks["ticks"] = {"width": 36, "height": 260, "elements": els}

# Viewfinder corner (the reticle repeats it four times, rotated).
marks["reticle"] = {"width": 40, "height": 40, "elements": [{"type": "path", "id": "reticle-corner", "attributes": {
    "d": "M3 17 V8 C3 5.2 5.2 3 8 3 H17", "fill": "none", "stroke": "#0F1214", "stroke-width": 3, "stroke-linecap": "square"}}]}

# Waveform for Listen: seven bars, the middle tallest.
els = []
for i, h in enumerate([6, 12, 18, 24, 18, 12, 6]):
    els.append({"type": "rect", "id": f"bar-{i}", "attributes": {"x": 1 + i * 4, "y": 14 - h / 2, "width": 2, "height": h, "rx": 1, "fill": "#E8ECEE"}})
marks["waveform"] = {"width": 30, "height": 28, "elements": els}

# Favicon: the glasses in black marker on board white.
els = [{"type": "rect", "id": "tile", "attributes": {"x": 0, "y": 0, "width": 64, "height": 64, "fill": "#ECEFF0"}},
       stroke("lens-left", loop(20, 33, 11.5, 9.5, 1.06, 0.03), INK, 4.6),
       stroke("lens-right", loop(44, 33, 11.5, 9.5, 1.06, -0.03), INK, 4.6),
       stroke("bridge", smooth([(29, 30), (32, 27.5), (35, 30)]), INK, 3.8),
       stroke("arm-left", smooth([(8.5, 30), (4, 27.5)]), INK, 3.8),
       stroke("arm-right", smooth([(55.5, 30), (60, 27.5)]), INK, 3.8),
       {"type": "circle", "id": "camera", "attributes": {"cx": 11.5, "cy": 25.5, "r": 2.2, "fill": RED}}]
marks["favicon"] = {"width": 64, "height": 64, "elements": els}

# Eraser swipes for the 404 corner.
els = []
for i in range(5):
    y = 40 + i * 46 + R.uniform(-6, 6)
    pts = [(10 + R.uniform(0, 20), y), (200, y - 14 + R.uniform(-5, 5)), (420, y + 6), (600 - R.uniform(0, 40), y - 10)]
    els.append({"type": "path", "id": f"swipe-{i}", "attributes": {"d": smooth(pts), "fill": "none", "stroke": "#C9D0D4",
                "stroke-width": 44, "stroke-linecap": "round", "opacity": 0.55}})
marks["erased"] = {"width": 620, "height": 300, "elements": els}

print(json.dumps(marks))
