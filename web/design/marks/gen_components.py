"""Inline the Glaxnimate SVGs in design/marks/ as Astro components in src/components/marks/.

Run from web/: python3 -I design/marks/gen_components.py
Pen colors become token classes (CSS custom properties do not work in SVG presentation
attributes), strokes get pathLength="1" and data-draw for GSAP's draw-on, and every mark is
decorative: the words it carries are also in the panel text, the sheet and the transcript.
"""
import pathlib
import re

SRC = pathlib.Path("design/marks")
OUT = pathlib.Path("src/components/marks")
OUT.mkdir(parents=True, exist_ok=True)

STROKE = {"#2246B8": "s-blue", "#1B1E24": "s-ink", "#C0272D": "s-red", "#0F1214": "s-lens", "#C9D0D4": "s-wipe"}
FILL = {"#1B1E24": "f-ink", "#585E65": "f-muted", "#C0272D": "f-red", "#E8ECEE": "f-voice", "#ECEFF0": "f-board"}
NAMES = {"bug": "Bug", "underline": "Underline", "start": "Start", "callouts": "Callouts", "ticks": "Ticks",
         "reticle": "Reticle", "waveform": "Waveform", "erased": "Erased", "pipeline": "Pipeline"}
NO_DRAW = {"reticle", "waveform"}


def convert(name, svg):
    svg = svg.strip()
    svg = re.sub(r' xmlns="[^"]+"', "", svg)
    svg = re.sub(r' id="[^"]+"', "", svg)  # marks render more than once per page
    svg = re.sub(r' width="\d+" height="\d+"', "", svg, count=1)
    svg = svg.replace("<svg ", '<svg class:list={["mark", `mark--' + name + '`, className]} aria-hidden="true" focusable="false" ', 1)

    def stroke(m):
        cls = STROKE[m.group(1)]
        extra = "" if name in NO_DRAW else ' pathLength="1" data-draw'
        return f'class="{cls}"{extra}'

    svg = re.sub(r'stroke="(#[0-9A-F]{6})"', stroke, svg)
    svg = re.sub(r'fill="(#[0-9A-F]{6})"', lambda m: f'class="{FILL[m.group(1)]}"', svg)
    svg = svg.replace(' font-family="Shantell Sans"', "")
    # An element can now carry two class attributes (fill and stroke); merge them.
    svg = re.sub(r'class="([^"]+)"([^>]*?) class="([^"]+)"', r'class="\1 \3"\2', svg)
    return svg


for svg_path in sorted(SRC.glob("*.svg")):
    key = svg_path.stem
    if key not in NAMES:
        continue
    body = convert(key, svg_path.read_text())
    comp = (
        "---\n"
        f"// Generated from design/marks/{svg_path.name} (drawn with Glaxnimate) by gen_components.py.\n"
        "interface Props { class?: string }\n"
        "const { class: className } = Astro.props;\n"
        "---\n"
        f"{body}\n"
    )
    (OUT / f"{NAMES[key]}.astro").write_text(comp)
    print("wrote", NAMES[key])
