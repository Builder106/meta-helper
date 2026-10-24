# MetaHelper site: design record

## Phase 0: brief

- **Subject:** MetaHelper, an open-source audio programming assistant for Meta Ray-Ban
  glasses. The wearer photographs code on a screen, whiteboard, slide or printed page; the
  backend asks Gemini for a word-for-word read-out followed by an explanation, Azure Speech
  turns it into an MP3, ffmpeg lowers the volume, and the glasses' open-ear speakers play it.
  A double-tap on the arm replays the answer.
- **Audience:** developers and students who are blind or have low vision (the prompt names
  them), hands-busy developers, and contributors who might self-host or extend it.
- **Job:** make the idea land in seconds, show how it works honestly, and send people to the
  source and the self-hosting guide.
- **Assets:** README, JOURNAL.md, the Gemini prompt in `VisionService.java`, deployment docs.
  The existing indigo app-tile logo stays in the README; the site draws its own mark.
- **Assumptions (placeholder: true in `src/data/site.ts`):**
  - The production URL (`https://metahelper.yinkavaughan.me`) is a guess until the owner
    picks a domain.
  - The code example follows the two-section format in the backend prompt. The other panels
    use the same presentation for the site. All wording was written for the site and is not
    recorded model output. The page says so.
  - "Listen" uses the visitor's browser voice. The backend currently defaults to Azure's
    `en-US-AriaNeural`. A recorded MP3 from the real backend would replace it.
  - The glasses in the renders are a generic pair of camera glasses modelled in Blender, not
    Meta's product.

## Phase 1: concept

| | A. Through the lens (hardware) | B. The whiteboard (how it is used) | C. One answer (formal: the MP3) |
|---|---|---|---|
| Idea | The site is the view through the glasses: two lens apertures, a heads-up world | The site is one whiteboard covered in code; you look at a panel and take its photo, and MetaHelper reads it to you | The site is a single spoken answer: one audio timeline you scrub, with the transcript lighting word by word |
| Anchor | the product runs on camera glasses | the prompt is written for "a screen, whiteboard, slide, or printed page" and answers in two layers, read-out then explanation | `POST /process-image` returns one `audio/mpeg` file |
| Structure | the viewport is the lens; content floats in it | one whiteboard you look across; each panel is a unit; capturing it opens its answer | a timeline; chapters are timestamps; the playhead is the nav |
| Signature | lens tint and focus pull per section | capture: the reticle locks, the shutter flashes, the photo lifts off and the answer is spoken out | scrubbing the waveform plays the site |
| O/F/Fe/P/S | 3 / 4 / 3 / 3 / 4 (and close to a viewport-as-frame idea) | 4 / 5 / 4 / 2 / 5 | 4 / 4 / 4 / 2 / 4 |

**Pick B.** It is what MetaHelper is for: something written on a board you cannot read, and a
voice that reads it to you. It decides the structure (the site is the board), the movement
(you look across it), the unit (a panel, opened by photographing it), the type (a hand and a
voice), and the escape hatch, which is the transcript: the plain-text version is the
product's own idea. Steal from C for the 404: a wiped corner of the board, answered in
MetaHelper's own "reframe and retake the photo" voice.

```
Idea:        A whiteboard full of code you can't read; look at a panel, take its photo, and MetaHelper reads it to you.
Metaphor:    a lecture-room whiteboard seen through camera glasses
Signature:   capture: reticle locks on the panel, shutter flash, the photo lifts into the answer sheet,
             and the answer appears word by word in two layers (read-out, then explanation)
Emotion:     a room you walk into mid-lecture: dense, handwritten, then suddenly legible
Motion verb: written, then read out. Strokes draw on like marker; answers arrive word by word at speaking pace
Screenshot:  the board with sum.c circled in red marker, the reticle locked on it, and the dark answer sheet reading it out
Structure:   the site is one whiteboard (desktop: a board larger than the screen you look across;
             phone: a tall board you scroll down). Movement: drag, wheel, arrow keys, or Tab across
             panels; the reticle is your gaze. Unit: a panel; capturing it opens its answer at its own URL.
Escape hatch: /transcript, every panel's read-out and explanation as plain text with real links
```

## Phase 2: art direction

### Palette (from the room)

| Role | Hex | Source |
|---|---|---|
| `board` (ground) | `#ECEFF0` | a cool melamine whiteboard under fluorescent light |
| `marker` (ink) | `#1B1E24` | black dry-erase marker |
| `blue` | `#2246B8` | blue dry-erase marker, for diagrams and arrows |
| `red` (accent) | `#C0272D` | red marker, used only to mark a bug or a correction |
| `lens` (curtain, answer sheet) | `#0F1214` | the smoked lens tint; the answer happens "in your ear", off the board |
| `voice` / `hush` | `#E8ECEE` / `#9BA4A9` | the answer's text on the lens, and its quieter labels |
| `muted` (on board) | `#585E65` | dried-out marker, for small notes |

Pure white appears only as the shutter flash.

### Type: a hand and a voice

- **Hand:** Shantell Sans (Google, variable: weight, informal and bounce axes). Everything
  written on the board, including the code, because whiteboard code is handwritten. Weight
  500 to 700, informal 100 on titles.
- **Voice:** Switzer (Fontshare). The answer sheet, the corner UI and every label. Neutral and
  synthetic on purpose: it is the speech, not the hand.
- Display hand on the board: hero 132 px board units, panel titles 44, code 26 to 30. Voice:
  answer body 18 px / 1.55, labels 13 px tracked +0.04em uppercase (only the two real section
  labels from the prompt, VERBATIM READ-OUT and EXPLANATION).

### Grid and rhythm

- The board is a 2800 x 1600 unit plane (1 unit = 1 mm of the Blender board). Panels sit where
  people write on a real board: dense in the middle, looser at the edges, at small angles
  (never more than 2 degrees).
- The answer sheet is on a 6-column grid inside 44% of the viewport (desktop), full width on
  phones, with a 38rem measure.

### Shape

Radius 0 on the board (marker has no radius). The lens sheet has a 0 radius too: it is a
cut, not a card. Buttons are text with drawn underlines, not pills.

### Motion language

- Engine eases only (`expo.out`, `expo.inOut`, `power3.out`).
- Writing: `stroke-dashoffset` draws on in 0.6 to 1.2 s (`power2.inOut`); titles reveal left to
  right with a clip, like a hand moving.
- Speaking: words fade and rise 6 px with a 28 ms stagger, about speaking pace.
- Capture: reticle snap 0.22 s, flash 0.06 s up and 0.35 s down, photo lift 0.7 s, sheet in
  0.8 s.
- Looking: the camera glides to a panel in 1.0 s (`expo.inOut`); the reticle follows the
  pointer with lag, like a head turning.
- Idle: the reticle drifts by about 3 px on a slow sine (head sway) and the board drifts by
  2 px. Off under reduced motion.

### Structure plan

```
DESKTOP (viewport is a window onto the board; the board pans under it)
+-----------------------------------------------------------------------------------+
| MetaHelper                                                Transcript   Source   |  fixed corners (Switzer)
|                                                                                   |
|    [pipeline: boxes + arrows]          [HERO: "Hear the code    [sum.c  circled]  |
|                                          in front of you."]                       |
|    [who it's for]           [lab notes]                    [run your own]  [src]  |
|                                                                                   |
| [erased corner]            ======= tray: markers, eraser, glasses =======         |
| Looking at: sum.c                                  Drag or scroll to look around  |
+-----------------------------------------------------------------------------------+
    reticle (four corner brackets) follows the pointer; locks onto a panel on hover/focus
    click / Enter = capture -> URL /sum

UNIT OPEN (/sum, /pipeline, /glasses, /who, /notes, /run, /source)
+--------------------------------------------+--------------------------------------+
| board, panned so the unit sits at 28% x    |  LENS SHEET (44%)                    |
| reticle locked on it, photo taken          |  [photo of the panel]  Captured      |
|                                            |  h1  sum.c                           |
|                                            |  VERBATIM READ-OUT ...               |
|                                            |  EXPLANATION ...                     |
|                                            |  Listen   Replay   Back to the board |
|                                            |  Next panel: The pipeline            |
+--------------------------------------------+--------------------------------------+
    Esc or "Back to the board" closes (history.back when we came from the board)

PHONE (390)
+------------------+
| MetaHelper  Tr.  |
| [HERO]           |  a tall board you scroll; panels in reading order, offset left/right
| [sum.c]          |  the reticle sits fixed at mid-screen: the panel under it is "in view"
| [pipeline]       |  tap = capture; the lens sheet slides up full screen
| [glasses + tray] |
| [who] [notes]    |
| [run] [source]   |
| tray render      |
+------------------+

/transcript: the lens sheet at full width over the board, every panel's read-out and
explanation in order, each title a link to its unit.
/404: the camera pans to the erased corner; the sheet says it can't read it and won't guess.
```

### Genericness test

- Skeleton: no navbar, hero, stacked sections or footer. The page is a board; the corners
  carry the brand, the escape hatch and the gaze label. Pass.
- Hero: a sentence in marker on a board, not a centered headline over a gradient. Pass.
- Content units: handwritten panels, not cards. Changed the first idea (a row of "how it
  works" steps) into one hand-drawn diagram panel.
- Hovers: the reticle locking on is the primary treatment, from the concept, not a lift or
  scale. Pass.
- Palette: cool board white and lens black with marker colors. Not cream, not acid green.
- Labels: the only uppercase labels are the prompt's real section names.

## Assets

| Asset | Route | Use |
|---|---|---|
| `board.webp` (2800 x 1600, plus 1400 w) | Blender (Cycles, Metal) `design/blender/board.py` | the whiteboard: melamine, aluminium frame, tray with three markers, an eraser and the glasses |
| `tray-phone.webp` | Blender, second camera on the tray | the end of the phone board |
| `glasses.webp` | Blender, close three-quarter view | the glasses unit's photo |
| reticle, pipeline diagram, red annotations, underlines, waveform, favicon | Glaxnimate `design/marks/` | drawn marker marks, animated by GSAP |

No 3D model in the browser: the glasses read well as a still, and a live model would be an
accessory.

## Decisions

- Stack: Astro (static, motion is the product, about 75 KB of JS). No ScrollSmoother: the
  desktop board is a camera, not a scrolling page, and phones scroll natively.
- The board is `transition:persist`, so it never reloads between units; only the sheet
  swaps.
