# GT30 Pro firmware scene visuals

All values are **decimal** firmware scene bytes (`[0,1,0,scene,0,0,0,0]`),
verified by eye on X6873. Names in parentheses are the Settings
("Mechanical Light Waves") names where they match.

| Scene | Visual |
|------:|--------|
| 0 | off |
| 1 | dim custom glow in RGB bytes (subtle by design) |
| 2 | nothing |
| 3 | flip-to-flash (Flip to flash) |
| 4 | green breathe + double-blink, then off (notification) |
| 5 | rotating red trail, ~15x clockwise (snake) |
| 9 | dim white |
| 10 | music mode (Music), runs until OFF |
| 11 | music mode, single run |
| 12 | nothing |
| 20 | charging mode (Charging): 1 round + 2 fast green blinks |
| 21 | charging remap target (system maps 26→21); visual unconfirmed |
| 22 | breathing green (game launch) |
| 51 | camera shutter, full red, ~4x slow breathe (~2s), fast blink, stop (~3s timer) |
| 52 | same, ~5s timer |
| 53 | same, ~10s timer |
| 54 | alternating red trailer from bottom sides |
| 61 | XArena game-start flash (XArena flash) |
| 62 | red fast blink ~5x, then full bottom-to-top sweep |
| 63 | same as 62 |
| 64 | bright white alternate-side blink |
| 65 | same as 64 |
| 80 | blue dripping fill, purple base, 2x, then 2x full breathing blink |
| 81 | dripping unlimited, no final blink |
| 82 | same as 81 |
| 83 | the 80-blink, infinite until OFF |
| 91 | party breathe (Party mode) |
| 92 | party meteor (Party mode) |
| 93 | party rhythm (Party mode) |

Notes:

- There is no solid-bright scene; the brightest white (64/65) blinks.
  ON in the app maps to 64.
- Scene 1 is the only RGB-honoring scene and stays dim; it backs Custom Color.
- Scenes 2 and 12 are confirmed no-ops. Unlisted numbers were not tested;
  the system switch only maps the inputs in `docs/PROTOCOL.md`.
