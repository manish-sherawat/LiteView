---
name: liquid-glass
description: Implement Apple-style "Liquid Glass" (translucent, edge-refracting, light-reactive, spring-animated, fluid-morphing glass UI) in any app, website or desktop software. Covers web (HTML/CSS/SVG/JS, React, Tailwind), native Apple (SwiftUI, UIKit, AppKit), Android (Jetpack Compose), Flutter, React Native/Expo, Electron/Tauri/Windows. Includes the material layer stack, refraction, specular edges, shapes and concentric corners, design tokens, component specs (buttons, tab bars, toolbars, sheets, menus, sliders, toggles, toasts), spring physics, press/drag/morph/scroll animations, accessibility fallbacks, performance limits, and a QA checklist. Use this skill whenever the user mentions liquid glass, glassmorphism, frosted glass, iOS 26 / macOS 26 style, Apple-style translucent UI, backdrop blur, refraction, glass buttons / navbars / tab bars / sheets / cards, or asks to make a UI "look like Apple's new design", even if they never say "Liquid Glass".
---

# Liquid Glass: Implementation Skill

Version note: Liquid Glass is Apple's design language introduced at WWDC25 (iOS 26, iPadOS 26, macOS 26 "Tahoe", watchOS 26, tvOS 26, visionOS 26). This file teaches you to build the same look and feel on **any** platform, using native APIs where they exist and a faithful recreation where they do not.

Items marked **(verify)** depend on library or OS versions that change quickly. Check the current official documentation before relying on them.

---

## TABLE OF CONTENTS

0. Read this first (rules, router, output contract, glossary)
1. Mental model: what Liquid Glass physically is
2. Design rules (where glass is allowed, tint, legibility, grouping)
3. Design tokens (all numbers in one place)
4. Geometry: shapes, corners, edges, spacing
5. Web Tier 1: the CSS material (works in every browser)
6. Web Tier 2: edge refraction (Chromium, SVG displacement)
7. Light, specular highlight and sheen
8. Motion, physics and transitions (springs, press, drag, morph, scroll, sheets)
9. Component specifications
10. Web: JS module, React component, Tailwind notes
11. Native Apple (SwiftUI, UIKit, AppKit)
12. Android, Flutter, React Native, Electron, Tauri, Windows
13. Accessibility (mandatory)
14. Performance and browser compatibility
15. Anti-patterns (never do these)
16. Agent workflow and QA checklist
17. Troubleshooting table
18. Cheat sheet

---

## 0. READ THIS FIRST

### 0.1 How to use this file

1. Read sections 0 to 4 completely. They apply to every platform.
2. Jump to **only** the platform section you need (5 to 7 and 10 for web, 11 for Apple native, 12 for the rest).
3. Always read section 8 (motion), section 9 (components), section 13 (accessibility) and section 16 (workflow).
4. Do not invent values. Use the numbers in section 3. If a number is missing, pick the closest size class (small, medium, large) and say so in your summary.
5. If the user's framework is not listed, rebuild the 7 layers from section 1 using that framework's primitives. The look comes from the layers, not from a specific API.

### 0.2 The 12 golden rules

1. **Glass is the control layer, not the content layer.** Use it for things that float above content: toolbars, tab bars, floating buttons, sheets, menus, popovers, toasts, sidebars. NEVER use it for list rows, article text blocks, table cells or full page backgrounds.
2. **Never stack glass on glass.** A glass element inside another glass element looks muddy. Group related controls inside ONE glass container instead.
3. **Content sits above the glass and is never blurred.** Labels and icons are crisp, high contrast, slightly heavier weight.
4. **An edge is mandatory.** Blur alone is "frosted glass", not Liquid Glass. You need the specular rim, inner highlights and soft outer shadow (section 5).
5. **Controls are capsules or circles.** Large surfaces are rounded rectangles with **concentric** corners (section 4).
6. **Respond on pointer-down, instantly.** Pressed glass swells (inflates) with a spring. Never wait for pointer-up to show feedback.
7. **All motion is spring-based and interruptible.** The user can grab anything mid-animation. Start every new animation from the element's current on-screen value.
8. **Animate `transform`/`scale`/`translate`/`opacity` only.** Never animate blur radius continuously on a large surface. Never animate layout of large areas.
9. **Always ship fallbacks:** no `backdrop-filter`, reduced transparency, increased contrast, forced colors, reduced motion (section 13). If you skip these the task is not done.
10. **Budget glass.** Show at most about 6 glass surfaces at once on web/mobile, and apply refraction (section 6) to at most 3 small ones.
11. **Tint sparingly.** Only the primary action (one per screen region) gets a tint. Everything else is neutral.
12. **Test on three backgrounds** (busy photo, plain white, plain black) before saying you are done.

### 0.3 Platform router

| Target | Use | Go to |
|---|---|---|
| Website, PWA, web app (any framework) | CSS material + optional SVG refraction + JS springs | 5, 6, 7, 8, 10 |
| React / Next / Vue / Svelte | Same as web, wrapped in a component | 10 |
| iOS / iPadOS / macOS app, SDK 26+ | Native `glassEffect` and friends | 11 |
| iOS / macOS app that must also run older OS | Native + material fallback | 11.5 |
| Android (Compose) | Backdrop blur library + hand-drawn rim + springs | 12.1 |
| Flutter | `BackdropFilter` + custom painter rim + spring simulation | 12.2 |
| React Native / Expo | `expo-glass-effect` (iOS 26) + blur fallback **(verify)** | 12.3 |
| Electron / Tauri / Windows / Linux desktop | Window material API + web tier inside the webview | 12.4 |
| Game engine / custom renderer | Port the refraction math from section 6 into a shader | 6.5, 12.5 |

### 0.4 Output contract (what you must deliver)

When you finish, your final message to the user must contain:

1. A list of files created or changed.
2. Which tier you implemented (web Tier 1, Tier 1 + Tier 2, native, etc.).
3. Which fallbacks are active and how to test them.
4. Anything you could not verify (for example, you could not run Safari).
5. How to tweak the look (name the 4 tokens: `--lg-blur`, `--lg-tint`, `--lg-rim`, `--lg-r`).

### 0.5 Glossary

| Term | Meaning |
|---|---|
| Backdrop | Whatever is rendered behind the glass. |
| Lensing / refraction | Backdrop content bends near the glass edge, like looking through a thick curved lens. |
| Bezel | The edge band (about 10 to 30 px wide) where bending happens. |
| Rim | The thin bright line along the edge (specular highlight). |
| Specular | Bright reflection of a light source on a shiny surface. |
| Sheen | Soft gradient of light across the glass body. |
| Vibrancy | Making text and icons stay readable on top of changing backdrops. |
| Concentric | Nested shapes whose corner centers coincide (inner radius = outer radius minus inset). |
| Materialize | Glass appearing like a real material arriving (blur + scale + fade), not a plain fade. |
| Morph | One glass shape smoothly turning into another (button into menu). |
| Detent | A resting height of a sheet (medium, large). |
| Spring | Physics-based animation described by response (speed) and damping ratio (bounce). |
| Scroll edge effect | Soft blur/fade where scrolling content meets floating glass chrome. |

---

## 1. MENTAL MODEL: WHAT LIQUID GLASS PHYSICALLY IS

Think of a thick, clear, slightly frosted piece of curved glass floating above the interface, lit by a light from the top-left. It bends what is behind it at the edges, catches light on its rim, and behaves like a liquid when touched or moved.

### 1.1 The 8-layer stack (build every layer, bottom to top)

```
7  CONTENT          labels, icons, images         crisp, vibrant, NOT blurred
6  OUTER SHADOW     ambient + contact shadow      lifts the glass off the page
5  SPECULAR RIM     1 to 1.5 px gradient border   bright where light hits, fades in the middle
4  INNER SHADING    inset highlights/shadows      gives thickness and volume
3  SHEEN + TINT     low-alpha fill + light ramp   body color, soft gradient across the surface
2  BLUR+SATURATE    small blur (8 to 24 px)       "frost"; saturation 160 to 200% keeps colors alive
1  REFRACTION       edge displacement (lensing)   content bends at the bezel
0  BACKDROP         what is behind the glass
```

Layers 1 and 5 are what separate Liquid Glass from old "glassmorphism". If you only did layers 2, 3 and 6 you built glassmorphism. Build all of them, or at minimum 2 to 6 plus the fallback for layer 1.

### 1.2 Optical physics you are imitating

- **Refraction (Snell's law):** a convex glass edge bends light. On screen: displace backdrop pixels near the edge, strongest at the very edge, zero in the flat center.
- **Fresnel effect:** surfaces reflect more light at grazing angles. On screen: the rim is brightest at the extreme edge and fades inward.
- **Specular highlight:** two lobes on the rim, a strong one facing the light and a weaker one on the opposite side, with a dark or transparent middle.
- **Dispersion (optional, subtle):** red/green/blue bend slightly differently at the edge, causing a faint color fringe. Never exaggerate.
- **Thickness:** inner highlights on the light side and inner shadows on the far side make the pane look thick.

### 1.3 Mechanical physics you are imitating

- **Springs:** every position, size and scale change uses a spring (section 8).
- **Inertia and momentum:** a flicked element continues and decelerates; you project its landing point.
- **Surface tension / liquid:** when glass elements merge or split, shapes flow into each other (morph), they do not cross-fade.
- **Squash and stretch:** a fast-moving lens elongates slightly along its motion, volume preserved.
- **Rubber band:** at a boundary, resistance increases smoothly. No hard stops.
- **Inflate on press:** pressing glass makes it swell, like pressing a drop of water.

### 1.4 Four behaviors that define the system

1. **Lensing:** bends and concentrates light instead of just scattering it (layers 1 and 2).
2. **Materialization:** appears and disappears by modulating blur, scale and light (section 8.8).
3. **Fluidity:** flexes, morphs and responds to touch with gel-like feel (sections 8.6 to 8.9).
4. **Adaptivity:** shadows and tint adapt to what is behind; it flips between light and dark appearance depending on the content below and the system theme (section 7.4).

### 1.5 Two variants

| | **Regular** (default) | **Clear** |
|---|---|---|
| Use for | Almost everything: bars, buttons, sheets, menus | Controls floating over rich media (photo, video, map) |
| Blur | Medium to strong | Low |
| Tint alpha | 0.30 to 0.60 | 0.08 to 0.16 |
| Legibility | Self-sufficient | Needs a dimming layer behind it (about 20 to 35% black) and bold content |
| Adaptive light/dark | Yes | Limited |

Rule: choose **Regular** unless the backdrop is a vivid media area AND you are willing to add the dimming layer. Never use Clear over text or plain UI.

---

## 2. DESIGN RULES

### 2.1 Where glass is allowed

| Allowed | Not allowed |
|---|---|
| Navigation bar, toolbar, tab bar, bottom bar | Table or list rows, cells |
| Floating action buttons, icon buttons, segmented controls | Body text containers, article cards |
| Sheets, popovers, menus, context menus, alerts, toasts | Entire screen backgrounds |
| Sidebars (floating inset panel), docks | Glass inside glass |
| Slider/toggle knobs, selection lenses | Data tables, charts, forms fields (use normal fills) |
| Overlays on media (video controls, map controls) | Anything that must be read for long periods |

If the user asks for "glass cards everywhere", do this instead: keep content cards as normal surfaces, and make only the floating chrome (nav, tab bar, FAB, menus) glass. Explain why in one sentence.

### 2.2 Hierarchy and layering

- Layer order from back to front: **content, glass chrome, content shown inside glass, modal scrim, modal glass.**
- A modal sheet may sit over glass chrome. When it does, dim the parent chrome progressively (scrim 0 to about 0.25 alpha) and push the background back (scale 0.96 to 0.98, optional).
- Glass elements that belong together live in ONE container with one shared backdrop sample (section 4.5).

### 2.3 Tinting

- Neutral glass is the default. Tint = low-alpha accent color mixed into the body (alpha 0.45 to 0.65) and **white or very dark label**.
- One tinted element per region (for example, one "Done" or "Add" button). Tinting many elements destroys hierarchy.
- Never tint to decorate. Tint to say "this is the main action" or "this is selected".
- Keep tinted label contrast at least 4.5:1 (check with the real tint over a busy backdrop).

### 2.4 Legibility (non-negotiable)

- Text on glass: weight 500 or 600 (not 300 or 400), size at least 15 px/pt for primary labels, letter-spacing about +0.005em for small text.
- Use two label colors only: primary and secondary (section 3). Never use mid-gray text on glass.
- Contrast: 4.5:1 minimum for body labels, 3:1 for large text and icons (WCAG). Measure against the **worst plausible backdrop**.
- If a label is hard to read, increase tint alpha (+0.10 steps) or thicken blur. Do not add a heavy text shadow. A very soft shadow `0 1px 2px rgba(0,0,0,.15)` is the maximum.
- Over images: add a scroll edge effect (section 8.10) or a gradient scrim under the glass.

### 2.5 Content first

- Glass should shrink or get out of the way while the user focuses on content (tab bar minimizes on scroll down, toolbars collapse).
- Prefer the **smallest** glass that does the job. A single floating icon button beats a full-width bar.
- Let content extend **under** the glass (edge to edge). Do not reserve opaque strips for bars.

### 2.6 Consistency

- Same shape for same role everywhere (all icon buttons are circles of the same size; all pill buttons are capsules).
- Same press scale, same springs, same rim for the same size class.
- Do not mix Liquid Glass with old-style flat opaque bars in the same screen region.

---

## 3. DESIGN TOKENS (SINGLE SOURCE OF TRUTH)

### 3.1 CSS custom properties (copy into global CSS; port names to other platforms)

```css
:root {
  color-scheme: light dark;

  /* ----- material (light appearance) ----- */
  --lg-blur: 12px;                 /* small/medium surfaces */
  --lg-blur-thick: 24px;           /* large surfaces: sheets, sidebars, popovers */
  --lg-sat: 190%;
  --lg-bright: 1.05;
  --lg-tint: rgba(255,255,255,.36);        /* Regular */
  --lg-tint-clear: rgba(255,255,255,.12);  /* Clear */
  --lg-sheen: rgba(255,255,255,.22);       /* top-left light ramp */
  --lg-rim: rgba(255,255,255,.95);         /* strong rim lobe */
  --lg-rim-soft: rgba(255,255,255,.55);    /* weak rim lobe + hairline */
  --lg-edge-dark: rgba(0,0,0,.06);         /* 0.5px outer definition on light backdrops */
  --lg-solid: #ffffff;                     /* fallback surface (no transparency) */
  --lg-shadow-ambient: 0 12px 34px rgba(0,0,0,.16);
  --lg-shadow-contact: 0 2px 8px rgba(0,0,0,.08);
  --lg-label: #111114;
  --lg-label-2: rgba(60,60,67,.72);
  --lg-lens: rgba(118,118,128,.16);        /* selection lens inside a bar */
  --lg-accent: #007aff;
  --lg-angle: 135deg;                      /* light direction, updated by JS */

  /* ----- geometry ----- */
  --lg-r-capsule: 999px;
  --lg-r-sm: 14px;
  --lg-r-md: 20px;
  --lg-r-lg: 28px;
  --lg-r-xl: 36px;
  --lg-edge: 1.2px;                        /* rim thickness */
  --lg-gap: 10px;                          /* gap between separate glass controls */
  --lg-inset: 4px;                         /* padding between container edge and inner lens */

  /* ----- motion (response seconds / damping ratio) ----- */
  --lg-ease-out: cubic-bezier(.2,.8,.2,1); /* only for non-spring fallbacks */
  --lg-t-micro: 120ms;                     /* hover/opacity */
  --lg-t-std: 300ms;
  --lg-t-large: 450ms;
}

/* dark appearance: apply BOTH selectors (system preference + manual data-theme override) */
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --lg-tint: rgba(54,54,58,.40);
    --lg-tint-clear: rgba(255,255,255,.08);
    --lg-sheen: rgba(255,255,255,.10);
    --lg-rim: rgba(255,255,255,.42);
    --lg-rim-soft: rgba(255,255,255,.22);
    --lg-edge-dark: rgba(0,0,0,.35);
    --lg-solid: #1c1c1e;
    --lg-shadow-ambient: 0 14px 38px rgba(0,0,0,.5);
    --lg-shadow-contact: 0 2px 8px rgba(0,0,0,.35);
    --lg-label: #ffffff;
    --lg-label-2: rgba(235,235,245,.72);
    --lg-lens: rgba(255,255,255,.16);
    --lg-accent: #0a84ff;
  }
}
:root[data-theme="dark"] {
  --lg-tint: rgba(54,54,58,.40);
  --lg-tint-clear: rgba(255,255,255,.08);
  --lg-sheen: rgba(255,255,255,.10);
  --lg-rim: rgba(255,255,255,.42);
  --lg-rim-soft: rgba(255,255,255,.22);
  --lg-edge-dark: rgba(0,0,0,.35);
  --lg-solid: #1c1c1e;
  --lg-shadow-ambient: 0 14px 38px rgba(0,0,0,.5);
  --lg-shadow-contact: 0 2px 8px rgba(0,0,0,.35);
  --lg-label: #ffffff;
  --lg-label-2: rgba(235,235,245,.72);
  --lg-lens: rgba(255,255,255,.16);
  --lg-accent: #0a84ff;
}
```

### 3.2 Size classes (pick by element size, not by taste)

| Class | Examples | Blur | Tint alpha (light) | Rim thickness | Bezel (refraction band) | Max edge shift | Shadow |
|---|---|---|---|---|---|---|---|
| **Small** (32 to 56 px tall) | icon button, chip, switch knob, slider knob | 8 to 12 px | 0.30 to 0.38 | 1.0 px | 10 to 14 px | 8 to 12 px | ambient 8 to 20 px blur |
| **Medium** (48 to 80 px tall) | tab bar, toolbar pill, search capsule, toast | 12 to 16 px | 0.34 to 0.42 | 1.2 px | 14 to 20 px | 10 to 14 px | ambient 20 to 34 px |
| **Large** (over 120 px) | sheet, popover, menu, sidebar, dialog | 20 to 28 px | 0.45 to 0.60 | 1.2 to 1.5 px | 20 to 32 px | 12 to 18 px | ambient 30 to 60 px |

Reason: bigger surfaces must read as thicker and must stay legible over more varied content, so they get more blur and more tint.

### 3.3 Spring tokens

`response` = seconds to move most of the way (NOT a duration). `damping` = damping ratio (1 = no bounce, below 1 = overshoot).

| Token | response | damping | Use |
|---|---|---|---|
| `snappy` | 0.28 | 1.0 | toggles, small state changes, hover |
| `smooth` (default) | 0.40 | 1.0 | moves, menu open, layout changes |
| `bouncy` | 0.38 | 0.80 | releases after a flick or drag, lens snap, elements that arrive with momentum |
| `inflate` | 0.30 | 0.65 | press swell on glass |
| `sheet` | 0.45 | 0.88 | sheets, large morphs |
| `exit` | 0.22 | 1.0 | dismissals (always faster than entrances) |

Conversion for libraries that want stiffness/damping (mass = 1): `omega = 2*PI/response`, `stiffness = omega^2`, `damping_coefficient = 2 * dampingRatio * omega`.
Examples: response .4, ratio 1 gives stiffness about 246.7, damping about 31.4. Response .3, ratio .65 gives stiffness about 438.6, damping about 27.2.

Rule: use damping 1.0 by default. Use overshoot (below 1.0) **only** when the user's gesture carried momentum (flick, drag release) or the element is a physical "blob" being pressed.

### 3.4 Press and drag constants

| Constant | Value |
|---|---|
| Press scale, small controls | 1.08 to 1.14 |
| Press scale, medium bars | 1.03 to 1.05 (do not swell wide bars much) |
| Press scale, large surfaces | 1.00 to 1.02 |
| Lens inflate while dragging | 1.12 to 1.16 |
| Squash/stretch max | 0.18 (so scaleX up to 1.18, scaleY down to about 0.89) |
| Drag start hysteresis | 6 to 10 px |
| Momentum decel rate | 0.998 (normal), 0.99 (snappier) |
| Rubber-band constant | 0.55 |
| Hover brighten | `brightness(1.04)` over 120 ms |

---

## 4. GEOMETRY: SHAPES, CORNERS, EDGES, SPACING

### 4.1 Shape vocabulary

| Shape | Rule | Use |
|---|---|---|
| **Capsule** | `border-radius = height / 2` (use 999px) | text buttons, tab bars, toolbars, search fields, toasts, segmented controls, selection lens |
| **Circle** | equal width/height, radius 50% | icon buttons, FAB, knobs, avatars on glass |
| **Rounded rectangle** | radius 20 to 36 px | popovers, menus, dialogs, sidebars, sheets, widgets |
| **Squircle (continuous corner)** | superellipse, smoother than a plain arc | preferred for all rounded rectangles when available |

Rule: **controls are capsules/circles, containers are rounded rectangles**. Never use sharp corners. Never use radius under 12 px on a glass element.

### 4.2 Concentric corners (the most common mistake)

When a shape sits inside another shape, the inner radius must be:

```
inner_radius = outer_radius - inset
```

Worked examples:
- Tab bar: height 64, radius 32. Inner selection lens inset 4 px, so lens radius 28 and lens height 56. Correct.
- Popover: radius 28, inner row highlight inset 6 px, so row radius 22.
- Phone sheet near the screen corner: device screen radius about 44 to 55 px, sheet margin 8 px, so sheet radius about 36 to 47 px. Use 36 to 44 for floating bottom sheets on phones.
- If inset is larger than the outer radius, the inner radius is 0 (square): then switch the inner shape to a capsule to avoid awkward corners.

Capsule inside capsule is automatically concentric (both are height/2). Prefer capsule inside capsule.

### 4.3 Continuous corners (squircle)

Plain `border-radius` is a circular arc, with a visible tangent seam. Apple uses continuous corners (superellipse-like, roughly 60% corner smoothing).

- Web progressive enhancement:
  ```css
  .lg-panel { border-radius: var(--lg-r-lg); }
  @supports (corner-shape: squircle) {
    .lg-panel { corner-shape: squircle; border-radius: calc(var(--lg-r-lg) * 1.25); }
  }
  ```
  `corner-shape` is recent **(verify support)**. The plain radius is always the fallback and is acceptable.
- Math, if you must draw it yourself: superellipse `|x/a|^n + |y/b|^n = 1` with n about 4 to 5 for iOS-like corners.
- SwiftUI: `RoundedRectangle(cornerRadius: r, style: .continuous)`. Always pass `.continuous`.
- Flutter: `RoundedSuperellipseBorder` / `ContinuousRectangleBorder` **(verify)**.

### 4.4 Edge anatomy (draw these in order)

1. **Hairline:** `inset 0 0 0 .5px var(--lg-rim-soft)` (a faint bright edge all around).
2. **Light-side inner highlight:** `inset 2px 3px 4px -2px var(--lg-rim)` (bright, top-left).
3. **Shadow-side inner highlight:** `inset -2px -3px 4px -3px var(--lg-rim-soft)` (weaker, bottom-right).
4. **Outer definition line:** `0 0 0 .5px var(--lg-edge-dark)` (helps glass read on light backgrounds where white edges vanish).
5. **Specular rim:** gradient border via mask, 1.2 px, `linear-gradient(angle, rim, transparent 28%, transparent 62%, rim-soft)` (two lobes).
6. **Ambient shadow** (large, soft) and **contact shadow** (small, tight).

Shadow behavior: shadows should be **lighter over plain backgrounds and heavier over busy/text content**. If you cannot detect this, use the token defaults and do not exceed alpha 0.2 for ambient on light mode.

### 4.5 Sizing, spacing and grouping

| Item | Value |
|---|---|
| Min touch target | 44 x 44 pt (iOS), 48 x 48 dp (Android), 44 x 44 css px (web touch), 28 to 32 px tall (desktop pointer UI) |
| Icon button | 44 to 48 px circle, icon 20 to 24 px |
| Capsule button height | 44 px (comfortable), 36 px (compact), 52 px (large) |
| Horizontal padding in capsule | 18 to 22 px (text), 12 to 16 px (icon + text) |
| Tab bar | height 56 to 68 px, floats 8 to 14 px above the bottom safe area, side margin 14 to 20 px, max width about 400 px on phones |
| Toolbar pill | height 44 px, groups separated by 10 to 12 px |
| Distance from screen edge | 12 to 16 px plus safe-area insets |
| Gap between separate glass controls | 8 to 12 px |
| Group related controls | put them in ONE glass capsule with dividers or spacing; do not make 3 separate touching capsules |

**Merge distance rule (native and conceptual):** glass elements closer than the container's `spacing` value visually merge and share one backdrop sample. On web, emulate this by placing them in one container. Never overlap two separate glass elements by a few pixels; either keep a clear gap or group them.

### 4.6 Signed-distance field (SDF) for rounded rectangles (used by refraction and shaders)

```
sd(x, y) for a rounded rect centered at origin, half-size (hw, hh), radius r:
  qx = abs(x) - (hw - r)
  qy = abs(y) - (hh - r)
  sd = length(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - r
negative inside, 0 on the edge, positive outside.
```
Normal at a point = normalized gradient of `sd` (use central differences). Used in section 6.

---

## 5. WEB TIER 1: THE CSS MATERIAL (WORKS EVERYWHERE)

This tier needs no JavaScript and works in every modern browser. It builds layers 2 to 6 of section 1.

### 5.1 Base class (copy exactly)

```css
.lg {
  --lg-r: var(--lg-r-capsule);              /* override per element */
  position: relative;
  isolation: isolate;
  border-radius: var(--lg-r);
  color: var(--lg-label);

  /* layer 3: sheen + tint */
  background:
    linear-gradient(var(--lg-angle), var(--lg-sheen), rgba(255,255,255,0) 55%),
    var(--lg-tint);

  /* layer 2: blur + saturation + brightness (Safari needs the -webkit- prefix) */
  -webkit-backdrop-filter: blur(var(--lg-blur)) saturate(var(--lg-sat)) brightness(var(--lg-bright));
          backdrop-filter: blur(var(--lg-blur)) saturate(var(--lg-sat)) brightness(var(--lg-bright));

  /* layers 4 + 6: inner shading + outer shadows */
  box-shadow:
    inset 0 0 0 .5px var(--lg-rim-soft),
    inset 2px 3px 4px -2px var(--lg-rim),
    inset -2px -3px 4px -3px var(--lg-rim-soft),
    0 0 0 .5px var(--lg-edge-dark),
    var(--lg-shadow-ambient),
    var(--lg-shadow-contact);

  -webkit-tap-highlight-color: transparent;
  user-select: none; -webkit-user-select: none;
}

/* layer 5: specular rim (gradient border drawn with a mask, follows --lg-angle) */
.lg::before {
  content: ""; position: absolute; inset: 0; z-index: 2;
  border-radius: inherit; padding: var(--lg-edge);
  background: linear-gradient(var(--lg-angle), var(--lg-rim), transparent 28%, transparent 62%, var(--lg-rim-soft));
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
          mask-composite: exclude;
  pointer-events: none;
}

/* touch glow: bright spot under the finger, shown only while pressed */
.lg::after {
  content: ""; position: absolute; inset: 0; z-index: 1;
  border-radius: inherit; pointer-events: none; opacity: 0;
  background: radial-gradient(140px 140px at var(--lg-px, 50%) var(--lg-py, 50%), rgba(255,255,255,.55), transparent 70%);
  transition: opacity 180ms ease-out;
}
.lg[data-pressed="true"]::after { opacity: 1; }

/* layer 7: content stays above everything and is never blurred */
.lg > * { position: relative; z-index: 3; }
```

### 5.2 Variants (use classes, do not copy-paste values)

```css
.lg--thick  { --lg-blur: var(--lg-blur-thick); }          /* base class reads --lg-blur, so this is enough */
.lg--clear  { --lg-tint: var(--lg-tint-clear); --lg-blur: 6px; }          /* needs a dimming layer + bold content */
.lg--tint   { --lg-tint: color-mix(in srgb, var(--lg-accent) 58%, transparent); color: #fff; }
.lg--circle { --lg-r: 50%; aspect-ratio: 1; display: grid; place-items: center; }
.lg--sm     { --lg-r: 999px; min-height: 36px; padding: 0 16px; }
.lg--md     { --lg-r: 999px; min-height: 44px; padding: 0 20px; }
.lg--panel  { --lg-r: var(--lg-r-lg); padding: 8px; }                      /* menus, popovers */
.lg--sheet  { --lg-r: var(--lg-r-xl); }
```

### 5.3 Hover and focus (desktop pointers)

```css
@media (hover: hover) {
  .lg[data-lg-interactive]:hover { filter: brightness(1.04); }           /* 120 ms feel; filter is cheap here */
}
.lg:focus-visible { outline: 2px solid var(--lg-accent); outline-offset: 3px; }
```

### 5.4 Fallbacks (mandatory, paste right after the base class)

```css
/* browser has no backdrop-filter: use a solid, slightly translucent surface */
@supports not ((-webkit-backdrop-filter: blur(1px)) or (backdrop-filter: blur(1px))) {
  .lg { background: var(--lg-solid); }
}
/* user asked for less transparency */
@media (prefers-reduced-transparency: reduce) {
  .lg { background: var(--lg-solid); -webkit-backdrop-filter: none; backdrop-filter: none; }
  .lg::after { display: none; }
}
/* user asked for more contrast */
@media (prefers-contrast: more) {
  .lg { background: var(--lg-solid); -webkit-backdrop-filter: none; backdrop-filter: none;
        box-shadow: 0 0 0 1.5px var(--lg-label); }
  .lg::before, .lg::after { display: none; }
}
/* Windows High Contrast / forced colors */
@media (forced-colors: active) {
  .lg { background: Canvas; color: CanvasText; border: 1px solid CanvasText;
        -webkit-backdrop-filter: none; backdrop-filter: none; box-shadow: none; }
  .lg::before, .lg::after { display: none; }
}
```

### 5.5 Backdrop-root gotcha (the #1 cause of "glass shows nothing")

A `backdrop-filter` only sees what is behind it **up to the nearest ancestor that creates a "backdrop root"**. These ancestor properties create one and make the blur show nothing or only the ancestor's own background:

`opacity` below 1, `filter`, `mask` / `mask-image`, `clip-path`, `mix-blend-mode` (not normal), `backdrop-filter`, `will-change` of any of those.

Rules:
- Do not place `.lg` inside an ancestor that has any of the above.
- To fade a glass element in or out, animate **the glass element's own** `opacity` and `scale`, not a wrapper's.
- A fixed tab bar must be a direct child of `<body>` or of a plain layout wrapper with none of these properties.
- Never put `.lg` inside another `.lg` (also a backdrop root).

### 5.6 Page structure for glass to work

```html
<body>
  <main class="content">…scrolling content, images, colors…</main>   <!-- glass needs something to refract -->
  <header class="lg lg--md" style="position:fixed;top:12px;left:50%;translate:-50% 0">…</header>
  <nav class="lg" style="position:fixed;bottom:calc(env(safe-area-inset-bottom) + 12px)">…</nav>
</body>
```

Add `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">` and respect safe areas with `env(safe-area-inset-*)`.

Glass over a flat solid background looks like a plain gray rectangle. When demoing, place content with color (images, gradients, colored cards) behind it. The real app needs this naturally; for an empty page add a subtle colorful gradient or image to the page background.

---

## 6. WEB TIER 2: EDGE REFRACTION (SVG DISPLACEMENT, CHROMIUM)

This tier builds layer 1 (lensing). It is a progressive enhancement. Tier 1 must already work without it.

### 6.1 Support and gating

- `backdrop-filter: url(#svg-filter)` renders in **Chromium browsers** (Chrome, Edge, Opera, Brave, Android Chrome).
- Safari (macOS and ALL iOS browsers, which use WebKit) and Firefox ignore or drop it. Do NOT rely on it there. Keep the Tier 1 look.
- Gate with: Chromium user agent, NOT iOS (`CriOS`, `FxiOS`, `EdgiOS`), no reduced-transparency or high-contrast, and enough CPU (`navigator.hardwareConcurrency > 4`). Allow an explicit override attribute.
- Displacement filters re-run when the backdrop changes (scrolling content under the glass). Apply to **at most 3 small or medium elements**.

### 6.2 How it works

1. Generate a **displacement map** image the size of the element. Pixel color encodes how far and which way to shift: R = x shift, G = y shift, 128 = no shift.
2. Neutral (128,128) in the flat center. Near the edge (inside the bezel), shift **toward the interior along the surface normal**, strongest at the very edge, easing to zero.
3. Put the map in an SVG `<filter>` using `feImage` + `feDisplacementMap`.
4. Reference it first in `backdrop-filter`, then add blur, saturate, brightness.

Math:
- `t = depth_into_bezel / bezel` (0 at the edge, 1 at the inner end of the bezel).
- Profile `k(t) = 1 - sqrt(1 - (1 - t)^2)`. Strong at the edge, drops quickly, zero in the center (a convex lens bezel).
- Normal `n` = normalized gradient of the rounded-rect SDF (section 4.6).
- Pixel color: `R = 128 + 127 * (-n.x * k)`, `G = 128 + 127 * (-n.y * k)` (negative sign = sample from inside).
- `feDisplacementMap scale = 2 * maxShiftPx`, because displacement in px = `scale * (channel - 0.5)`.
- The filter must use `color-interpolation-filters="sRGB"` or the map is color-managed and the shift is wrong.

If the edge looks inverted (content seems pushed outward), flip the sign (`inward = false`).

### 6.3 Complete module (drop into `liquid-glass.js`, see section 10 for the rest)

```js
const NS = 'http://www.w3.org/2000/svg';
const NO_TRANSPARENCY = matchMedia('(prefers-reduced-transparency: reduce), (prefers-contrast: more), (forced-colors: active)');

export const CAN_REFRACT = (() => {
  const ua = navigator.userAgent;
  return /Chrome\//.test(ua) && !/CriOS|FxiOS|EdgiOS/.test(ua) &&
         !NO_TRANSPARENCY.matches && (navigator.hardwareConcurrency || 8) > 4;
})();

let _defs, _uid = 0;
function defs() {
  if (_defs) return _defs;
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('width', '0'); svg.setAttribute('height', '0');
  svg.setAttribute('aria-hidden', 'true'); svg.style.position = 'absolute';
  _defs = document.createElementNS(NS, 'defs');
  svg.appendChild(_defs); document.body.appendChild(svg);
  return _defs;
}

/* Signed distance to a rounded rect centered at the origin. */
const sdRound = (x, y, hw, hh, r) => {
  const qx = Math.abs(x) - (hw - r), qy = Math.abs(y) - (hh - r);
  return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
};

/* Build the displacement map as a PNG data URL. Rendered at reduced resolution for speed. */
export function makeDisplacementMap({ w, h, r, bezel, inward = true }) {
  const s = Math.min(1, 320 / Math.max(w, h));
  const cw = Math.max(2, Math.round(w * s)), ch = Math.max(2, Math.round(h * s));
  const R = Math.min(r, Math.min(w, h) / 2) * s, B = Math.max(1, bezel * s);
  const hw = cw / 2, hh = ch / 2;
  const canvas = document.createElement('canvas'); canvas.width = cw; canvas.height = ch;
  const g = canvas.getContext('2d'), img = g.createImageData(cw, ch), d = img.data;
  const sign = inward ? -1 : 1;
  for (let j = 0; j < ch; j++) for (let i = 0; i < cw; i++) {
    const x = i + .5 - hw, y = j + .5 - hh, dist = sdRound(x, y, hw, hh, R);
    let dx = 0, dy = 0;
    if (dist < 0 && dist > -B) {
      const t = -dist / B, k = 1 - Math.sqrt(1 - (1 - t) * (1 - t));
      const e = .5;
      const nx = sdRound(x + e, y, hw, hh, R) - sdRound(x - e, y, hw, hh, R);
      const ny = sdRound(x, y + e, hw, hh, R) - sdRound(x, y - e, hw, hh, R);
      const n = Math.hypot(nx, ny) || 1;
      dx = sign * nx / n * k; dy = sign * ny / n * k;
    }
    const o = (j * cw + i) * 4;
    d[o] = 128 + 127 * dx; d[o + 1] = 128 + 127 * dy; d[o + 2] = 128; d[o + 3] = 255;
  }
  g.putImageData(img, 0, 0);
  return canvas.toDataURL();
}

/* Attach refraction to one element. Call again when its size changes (ResizeObserver below). */
export function applyRefraction(el) {
  if (!CAN_REFRACT) return;
  const w = el.offsetWidth, h = el.offsetHeight;
  if (!w || !h) return;
  const st = (el._lg ||= { id: 'lg' + (++_uid), w: 0, h: 0 });
  if (st.w === w && st.h === h) return;
  st.w = w; st.h = h;

  const cs = getComputedStyle(el);
  let r = cs.borderTopLeftRadius;
  r = r.endsWith('%') ? parseFloat(r) / 100 * Math.min(w, h) : (parseFloat(r) || 0);
  const bezel = +el.dataset.lgBezel || Math.max(10, Math.min(28, Math.min(w, h) * 0.3));
  const maxShift = +el.dataset.lgShift || 14;                  /* px, see size classes */
  const url = makeDisplacementMap({ w, h, r, bezel });

  let f = document.getElementById(st.id);
  if (!f) {
    f = document.createElementNS(NS, 'filter');
    f.id = st.id;
    f.setAttribute('color-interpolation-filters', 'sRGB');
    f.setAttribute('filterUnits', 'userSpaceOnUse');
    defs().appendChild(f);
    const blur = cs.getPropertyValue('--lg-blur').trim() || '12px';
    const sat = cs.getPropertyValue('--lg-sat').trim() || '190%';
    const br = cs.getPropertyValue('--lg-bright').trim() || '1.05';
    const v = `url(#${st.id}) blur(${Math.min(parseFloat(blur), 4)}px) saturate(${sat}) brightness(${br})`;
    el.style.webkitBackdropFilter = v; el.style.backdropFilter = v;   /* inline wins; media queries guarded by CAN_REFRACT */
  }
  f.setAttribute('x', 0); f.setAttribute('y', 0);
  f.setAttribute('width', w); f.setAttribute('height', h);
  f.innerHTML =
    `<feImage href="${url}" x="0" y="0" width="${w}" height="${h}" preserveAspectRatio="none" result="map"/>` +
    `<feDisplacementMap in="SourceGraphic" in2="map" scale="${maxShift * 2}" xChannelSelector="R" yChannelSelector="G"/>`;
}

export function releaseRefraction(el) {          /* call on unmount to avoid leaking filters */
  if (!el._lg) return;
  ro.unobserve(el);
  document.getElementById(el._lg.id)?.remove();
  el.style.backdropFilter = el.style.webkitBackdropFilter = '';
  delete el._lg;
}

const ro = new ResizeObserver(es => es.forEach(e => applyRefraction(e.target)));
export const observeRefraction = el => ro.observe(el);
```

Note: when refraction is on, blur is capped at 4 px. The displacement is the star; heavy blur hides it.

### 6.4 Optional chromatic dispersion (very subtle, small elements only)

Replace the single `feDisplacementMap` with three, scaled 0.94, 1.0, 1.06, each isolated to one channel with `feColorMatrix`, then recombine:

```xml
<feDisplacementMap in="SourceGraphic" in2="map" scale="26" xChannelSelector="R" yChannelSelector="G" result="dR"/>
<feColorMatrix in="dR" type="matrix" values="1 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0" result="r"/>
<feDisplacementMap in="SourceGraphic" in2="map" scale="28" xChannelSelector="R" yChannelSelector="G" result="dG"/>
<feColorMatrix in="dG" type="matrix" values="0 0 0 0 0  0 1 0 0 0  0 0 0 0 0  0 0 0 1 0" result="g"/>
<feDisplacementMap in="SourceGraphic" in2="map" scale="30" xChannelSelector="R" yChannelSelector="G" result="dB"/>
<feColorMatrix in="dB" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 1 0 0  0 0 0 1 0" result="b"/>
<feBlend in="r" in2="g" mode="screen" result="rg"/>
<feBlend in="rg" in2="b" mode="screen"/>
```

Keep the scale difference to about 2 px. More than that looks like a rendering bug.

### 6.5 Porting the lens to a GPU shader (Flutter, Android AGSL, Unity, WebGL)

Same math, per pixel `p` (origin at element center):

```
d      = sdRoundRect(p, halfSize, radius)           // negative inside
if (d < 0 && d > -bezel):
    t = -d / bezel
    k = 1 - sqrt(1 - (1 - t)^2)
    n = normalize(gradient(sdRoundRect))            // central differences, e = 0.5
    sampleUV = (p - n * k * maxShift) + center      // sample from the interior
else:
    sampleUV = p + center
color = blur(texture(backdrop, sampleUV))
```
Then add tint, rim and shadow as in section 4.4.

---

## 7. LIGHT, SPECULAR HIGHLIGHT AND SHEEN

### 7.1 Light model

- Default light direction: from the **top-left** (`--lg-angle: 135deg` means the gradient runs top-left to bottom-right, so the bright rim lobe is top-left).
- The rim has two lobes: strong (facing the light) and soft (opposite side). The middle is transparent.
- Idle glass is **static**. Do not run looping shimmer animations (vestibular and battery cost).

### 7.2 Pointer-driven light (desktop, fine pointers only)

```js
export function trackLight() {
  if (!matchMedia('(pointer: fine)').matches) return;     // touch devices keep 135deg
  let raf = 0, cur = 135, target = 135;
  const root = document.documentElement;
  const tick = () => {
    const d = ((target - cur + 540) % 360) - 180;          // shortest turn
    cur = (cur + d * 0.12 + 360) % 360;
    root.style.setProperty('--lg-angle', cur.toFixed(1) + 'deg');
    raf = Math.abs(d) > 0.3 ? requestAnimationFrame(tick) : 0;
  };
  addEventListener('pointermove', e => {
    // direction from screen center toward the pointer, converted to CSS gradient angle (+180 so bright lobe faces the pointer)
    target = (Math.atan2(e.clientX - innerWidth / 2, innerHeight / 2 - e.clientY) * 180 / Math.PI + 180 + 360) % 360;
    if (!raf) raf = requestAnimationFrame(tick);
  }, { passive: true });
}
```

Optional on phones: use `deviceorientation` (iOS requires `DeviceOrientationEvent.requestPermission()` from a user gesture). If you do not have permission, keep 135deg. Never block UI waiting for it.

### 7.3 Touch glow

On `pointerdown`, set `--lg-px` / `--lg-py` to the touch point (relative to the element) and set `data-pressed="true"`. The `::after` radial gradient fades in under the finger and fades out on release. This is already implemented in section 10 (`makeInteractive`).

### 7.4 Adaptivity (light/dark flipping)

- Follow the system theme (`prefers-color-scheme`) and any manual override (`data-theme`).
- For glass over a known hero image, set a modifier: `.lg--on-dark` (use dark tint + white label) or `.lg--on-light`. Decide by the dominant luminance of that image (compute on the server or at build time; do not read cross-origin pixels in the browser).
- Smooth theme changes: transition `background-color` and `color` over 350 ms on `body, .lg` (disable under reduced motion). Avoid abrupt brightness jumps.

---

## 8. MOTION, PHYSICS AND TRANSITIONS

### 8.1 Principles

1. **Response first:** feedback on pointer-down, within one frame.
2. **Direct manipulation:** while dragging, the element tracks the finger 1:1 and keeps the grab offset (do not snap its center to the pointer).
3. **Interruptible:** never lock input during a transition. Grabbing a moving element stops its animation and continues from the current value.
4. **Behavior over animation:** the target changes, the spring follows. Do not script fixed-duration keyframes for anything touchable.
5. **Velocity handoff:** at release, the spring starts with the finger's velocity so there is no seam between drag and animation.
6. **Spatial consistency:** things leave the way they came. A menu that grew from a button shrinks back into that button.
7. **Restraint:** motion explains cause and effect. If it does not, remove it.

### 8.2 Spring solver (JS, no dependencies)

```js
const REDUCED_MOTION = matchMedia('(prefers-reduced-motion: reduce)');

export function spring(from, to, { v = 0, response = .4, damping = 1, eps = .05, onUpdate, onDone } = {}) {
  if (REDUCED_MOTION.matches) { onUpdate(to); onDone?.(); return { stop() {} }; }   // jump; use a cross-fade elsewhere
  const w = 2 * Math.PI / response, k = w * w, c = 2 * damping * w;
  let x = from, vel = v, last = performance.now(), live = true;
  const step = t => {
    if (!live) return;
    const dt = Math.min((t - last) / 1000, 1 / 30); last = t;
    const n = Math.max(1, Math.ceil(dt / .004)), h = dt / n;           // sub-step for stability
    for (let i = 0; i < n; i++) { vel += (-k * (x - to) - c * vel) * h; x += vel * h; }
    if (Math.abs(x - to) < eps && Math.abs(vel) < eps * 10) { live = false; onUpdate(to); onDone?.(); return; }
    onUpdate(x); requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
  return { stop() { live = false; } };
}
```

Rules for using it:
- Keep the live value in a variable (`let s = 1`). To interrupt: `anim.stop()`, then `anim = spring(s, newTarget, { v: currentVelocity ... })`.
- For values in the range 0 to 1 (opacity, progress, scale), pass `eps: .002`. For pixels use the default `.05`.
- Animate **one scalar per spring**. For 2D motion use two springs (x and y).

### 8.3 CSS-only spring (for non-interactive transitions)

Use when no gesture is involved (hover brighten, theme change, simple appear). CSS `linear()` can approximate a spring.

```js
/* returns { duration (ms), easing } to use in CSS transitions or Web Animations */
export function springLinear(response = .4, zeta = 1, samples = 48) {
  zeta = Math.min(zeta, 1);
  const w = 2 * Math.PI / response;
  const f = t => zeta < 1
    ? (wd => 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + (zeta * w / wd) * Math.sin(wd * t)))(w * Math.sqrt(1 - zeta * zeta))
    : 1 - Math.exp(-w * t) * (1 + w * t);
  let T = response; while (Math.abs(1 - f(T)) > .002 && T < 3) T += .01;      // settle time
  const pts = []; for (let i = 0; i <= samples; i++) pts.push(f(T * i / samples).toFixed(4));
  return { duration: Math.round(T * 1000), easing: `linear(${pts.join(',')})` };
}
// const { duration, easing } = springLinear(.4, .8);
// el.animate({ scale: [0.9, 1] }, { duration, easing, fill: 'both' });
```
`linear()` is supported in current Chrome, Safari and Firefox **(verify)**. Fallback: `var(--lg-ease-out)` at 300 ms.

NEVER use CSS transitions for drag, swipe or any gesture-driven transform. They cannot be grabbed mid-flight.

### 8.4 State table (every interactive glass element implements all of these)

| State | Visual | Motion |
|---|---|---|
| Rest | token defaults | none |
| Hover (pointer only) | `brightness(1.04)`, rim slightly stronger | 120 ms |
| Focus-visible | 2 px accent outline, offset 3 px | instant |
| Pressed | scale up (see 3.4), glow under finger, rim brighter | `inflate` spring, starts on pointer-down |
| Dragging | lens follows finger 1:1, inflated, squash/stretch by velocity | no spring while tracking, spring on release |
| Selected | accent label color, selection lens behind | lens moves with `bouncy` spring |
| Disabled | opacity .45, no press response | none |
| Loading | label replaced by spinner, size locked | spinner rotates (respect reduced motion) |

### 8.5 Press (inflate) implementation

On `pointerdown`:
1. Set `--lg-px/--lg-py`, set `data-pressed="true"`.
2. Spring `scale` from the **current** value to the press scale (see 3.4) with `inflate`.
3. Optional haptic: `navigator.vibrate?.(6)` (very short; skip if reduced motion).

On `pointerup` / `pointercancel` / `lostpointercapture`: spring `scale` back to 1 with `inflate`; remove `data-pressed`.

Use the individual CSS property `scale` (`el.style.scale = s`) instead of `transform`, so it never overwrites an existing `transform: translateX(-50%)` used for centering.

Commit the action on pointer-up (tap semantics), cancel if the pointer left and did not return. Add about 10 px hit slop around small targets.

### 8.6 Drag lens with squash and stretch, momentum and rubber band

Use for tab selectors, segmented controls, slider knobs, switch knobs, carousels.

```js
export const rubberband = (over, dim, c = .55) => (over * dim * c) / (dim + c * Math.abs(over));
export const project = (v, rate = .998) => (v / 1000) * rate / (1 - rate);   // px landing offset from release velocity (px/s)

/* squash & stretch while moving: elongate along motion, keep volume */
export function stretchFor(vx) {
  const s = Math.min(.18, Math.abs(vx) / 6000);
  return { sx: 1 + s, sy: 1 - s * .6 };
}
```

Procedure on drag:
1. `pointerdown`: stop running springs, remember `grabOffset = pointerX - elementCenterX` (use the **live** position).
2. Do not begin dragging until the pointer moved at least 6 to 10 px (hysteresis). Decide direction on first move; if the gesture is mostly vertical and the control is horizontal, cancel and let the page scroll.
3. While dragging: `x = pointerX - grabOffset`, clamp or rubber-band beyond limits, keep a short history (last 100 ms of `[time, x]`) to compute velocity, apply `stretchFor(velocity)` to `scale` (x and y separately via `transform: scale(sx, sy)` on an inner lens element).
4. `pointerup`: `landing = x + project(velocity)`; choose the nearest snap point to `landing`; `spring(x, snap, { v: velocity, ... bouncy })`; return scale to 1 with `inflate`.
5. Decide reverse vs commit using velocity **sign**, not position alone.

### 8.7 Selection lens (tab bar, segmented control)

- The lens is a separate absolutely-positioned element **inside** the bar, sized to one segment, `border-radius` concentric (bar radius minus inset).
- Lens style: `background: var(--lg-lens)`, hairline rim, small shadow. **No extra backdrop-filter** (no glass on glass).
- On press anywhere on a segment: lens springs to that segment's slot and inflates (1.12 to 1.16).
- On drag: lens follows the finger across segments with stretch; the label under the lens center takes the accent color live.
- On release: lens snaps to the chosen segment (`bouncy` if released with velocity, `smooth` otherwise) and deflates to 1.
- Commit the selection on release. For momentary actions (like an "Import" button) the lens returns to the previously selected tab.

### 8.8 Materialize and dematerialize (enter and exit)

Glass must "arrive like a material", not just fade.

Enter (menus, popovers, toasts, sheets' glass):
- `opacity 0 -> 1`, `scale 0.85 -> 1`, `filter: blur(8px) -> blur(0)` all driven by ONE progress spring `p: 0 -> 1` (`smooth`, eps .002).
- `transform-origin` = the center of the trigger relative to the element (spatial consistency). If the menu opens upward, origin is at the bottom.

Exit:
- Run the same progress `p -> 0` with `exit` response (about 0.22), starting from the **current** p (interruptible). Hide (`display:none` / `hidden`) only on completion.

```js
export function materialize(el, { from = .85 } = {}) {
  let p = 0, a;
  const set = v => { p = v; el.style.opacity = v; el.style.scale = from + (1 - from) * v; el.style.filter = `blur(${(1 - v) * 8}px)`; };
  return {
    show() { a?.stop(); el.hidden = false; a = spring(p, 1, { response: .4, eps: .002, onUpdate: set }); },
    hide() { a?.stop(); a = spring(p, 0, { response: .22, eps: .002, onUpdate: set, onDone: () => { el.hidden = true; el.style.filter = ''; } }); },
  };
}
```
(`filter` on the glass element itself is fine because it is the element, not an ancestor, but remove it at rest.)

Under reduced motion: opacity cross-fade only (about 200 ms), no scale, no blur.

### 8.9 Morphing (button becomes menu, pill becomes panel)

Native platforms morph automatically (section 11). On web, do this:

1. Use **one** glass element for both states (the same DOM node). Do not cross-fade two glass elements.
2. Springs animate its **size, position and radius**: `width`, `height`, `top/left` (or `translate`), `--lg-r` from `999px` to `28px`. Layout animation is acceptable for a single small element.
3. Content cross-fades: old content opacity 1 to 0 in the first 40% of progress, new content 0 to 1 in the last 60%. Never show both at full opacity.
4. Use one progress spring `p` and interpolate all properties from `p` so they stay in sync.
5. Reverse uses the same path (spatial consistency) with `exit` response.
6. Do NOT fake merging with SVG "gooey" filters on elements that have `backdrop-filter`; it breaks. For "two pills merge into one capsule", animate the gap between them to 0 and then swap to a single container, or run a morph as above.

### 8.10 Scroll effects

**Scroll edge effect (soft blur where content slides under glass chrome). Use this INSTEAD of a hard divider line.**

```css
.lg-edge-top {
  position: fixed; inset: 0 0 auto 0; z-index: 10; pointer-events: none;
  height: calc(env(safe-area-inset-top, 0px) + 64px);
  -webkit-backdrop-filter: blur(14px); backdrop-filter: blur(14px);
  background: linear-gradient(color-mix(in srgb, var(--bg) 70%, transparent), transparent);
  -webkit-mask-image: linear-gradient(#000 55%, transparent);
          mask-image: linear-gradient(#000 55%, transparent);
  opacity: var(--p, 0);                     /* 0..1 driven by scroll */
}
```
(`mask-image` here is on the edge element, not an ancestor of glass; keep glass elements outside it.)

Drive `--p` continuously: `p = clamp((scrollY - 24) / 32, 0, 1)` in a passive scroll listener. Soft edge for bottom bars: same pattern flipped.
Only apply where floating chrome actually overlaps content. Not under plain headers.

**Progressive blur (optional, nicer):** stack 4 absolutely-positioned layers with blur 1, 2, 4, 8 px, each masked to a different 25% band of the gradient.

**Tab bar minimize on scroll down:**
- Track scroll direction with 8 px threshold (ignore tiny jitter).
- Scroll down: bar shrinks (width from full to a compact capsule showing only the selected icon, labels fade out, lens stays). Scroll up or tap: expands.
- Animate with `smooth` spring on a progress value `m (0..1)`; interpolate width, label opacity, padding. Always interruptible.

**Large title collapse:** large title scrolls away naturally; inline title in the nav fades in (`opacity = p`). No jump.

### 8.11 Sheets, dialogs and page transitions

**Bottom sheet:**
- Detents: medium about 50% of the viewport, large about 92%.
- Partial detents: floating, inset 8 to 12 px from sides and bottom, radius 36 to 44 px, glass `--thick`.
- At the full detent: margin goes to 0, radius at top only, tint alpha rises (about 0.6 to 0.8), so it reads as a solid page.
- Drag the grabber area 1:1 with the finger. On release: `landing = y + project(v)`, snap to the nearest detent (or dismiss if beyond the bottom), spring with the release velocity (`sheet` spring, damping .88).
- Rubber-band above the top detent.
- Scrim fades 0 to .25 alpha, tied to the sheet progress.
- The user can grab the sheet while it animates. A closing sheet that is grabbed follows the finger immediately.

**Dialog / alert:** materialize (8.8) from the center, scrim dims background, scale background to 0.97 if desired. Large glass class.

**Push navigation:** new page slides from right (100% to 0), old page moves left about 30% and dims. **Glass bars do not re-animate**; they persist and only their content cross-fades. Back swipe is interactive (drag 1:1 from the left edge, velocity decides).

**Shared-element transitions:** an image thumbnail growing into a hero image. Animate position/size with one progress spring; fade chrome in/out around it.

**Reduced motion:** replace slides, scale and blur with 200 ms cross-fades.

### 8.12 Haptics and sound (optional, use sparingly)

- Causality, harmony, utility: feedback fires on the real event (lens snaps home, toggle flips, destructive confirm), on the **same frame** as the visual, and only for meaningful moments.
- Web: `navigator.vibrate?.(6)` (Android only; iOS web has no vibration API). Native: `UIImpactFeedbackGenerator(.light)`, `UISelectionFeedbackGenerator` for lens moving between segments.
- No sound by default.

### 8.13 Timing guardrails

| Kind | Range |
|---|---|
| Micro (hover, opacity) | 100 to 150 ms |
| Standard springs | settle in about 250 to 400 ms |
| Large (sheet, big morph) | up to about 500 ms |
| Never exceed | 600 ms for any UI transition |
| Exit vs enter | exit about 0.7x the enter time |
| Looping idle animation | none |

---

## 9. COMPONENT SPECIFICATIONS

For every component: use the base `.lg` class (web) or native glass (section 11). Respect the shape rules (section 4).

| Component | Shape | Size | Glass class | Press | Notes |
|---|---|---|---|---|---|
| **Icon button** | circle | 44 to 48 px (icon 20 to 24) | Small, Regular | inflate 1.10 | Optional tint if primary. Label via `aria-label`. |
| **Text button (pill)** | capsule | height 44 (36 compact), padding 20 | Small, Regular or Tint | inflate 1.08 | One tinted primary per region. |
| **Prominent button** | capsule | height 52 | Small, Tint | inflate 1.08 | White label. |
| **Floating action button** | circle | 56 to 64 px | Medium, Regular or Tint | inflate 1.10 | Bottom-trailing, 16 px from edges. |
| **Toolbar (nav bar)** | groups of capsules/circles | height 44 | Medium | per button | Do NOT use a full-width bar. Use floating groups (leading, title, trailing). Content scrolls under; add scroll edge effect. |
| **Tab bar** | capsule | height 56 to 68, max width 400 | Medium | selection lens inflates 1.14 | Floats; minimizes on scroll down; max 5 items; icons 22 px + 10 to 11 px labels. |
| **Segmented control** | capsule | height 36 to 44 | Medium or fill-style | lens inflates 1.12 | Lens is the selected pill. 2 to 5 segments. |
| **Search field** | capsule | height 40 to 44 | Medium | focus ring | Placeholder uses label-2. |
| **Slider** | track = plain 4 to 6 px capsule (NOT glass); knob = glass capsule 36 x 24 | n/a | Small | knob inflates 1.15 on touch | Track filled with accent. Knob follows finger with slight stretch. |
| **Toggle (switch)** | track capsule 52 x 32 plain colored; knob glass | knob 28 circle, press to about 38 x 28 capsule | Small | knob widens with `inflate` | On = green (or accent) track, knob slides with `bouncy`. |
| **Menu / context menu / popover** | rounded rect r 28 | width 220 to 280 | Large | row highlight = lens (r 22) | Materialize from trigger origin. Rows 44 px. Dividers hairline `var(--lg-label-2)` at 12% alpha. |
| **Bottom sheet** | rounded rect r 36 to 44 (floating) | detents | Large | drag | See 8.11. |
| **Dialog / alert** | rounded rect r 28 to 36 | width 270 to 340 | Large | buttons are capsules | Scrim required. Max 2 to 3 actions. Destructive action uses red label, not red glass. |
| **Toast / banner** | capsule or r 22 | height 48 to 56 | Medium | swipe to dismiss | Appears above the tab bar; auto-hide 4 s; Undo for destructive actions. |
| **Sidebar** | rounded rect r 28 to 36, inset 8 to 12 px from window edges | width 240 to 300 | Large | row lens | Floating glass panel; content extends beneath it. |
| **Chip / tag** | capsule | height 28 to 32 | Small | inflate 1.06 | Use only on media overlays; elsewhere use plain fills. |
| **Video / map controls** | capsule / circle | per control | Clear + dimming | inflate | The only place Clear is recommended. |
| **Card** | rounded rect r 20 to 28 | any | **Plain fill, NOT glass** | scale .98 on press | Only floating overlay cards (picture-in-picture, widgets over wallpaper) may be glass. |

### 9.1 Tab bar (web reference build)

```html
<nav class="lg tabbar" aria-label="Main">
  <span class="lens" id="lens" aria-hidden="true"></span>
  <button class="tab" aria-current="page">…icon…<span>Files</span></button>
  <button class="tab">…icon…<span>Import</span></button>
  <button class="tab">…icon…<span>Settings</span></button>
</nav>
```
```css
.tabbar{position:fixed;left:50%;translate:-50% 0;bottom:calc(env(safe-area-inset-bottom,0px) + 12px);
  width:min(22.5rem,calc(100% - 2rem));height:64px;padding:4px;--lg-r:32px;display:grid;grid-template-columns:repeat(3,1fr)}
.tab{position:relative;z-index:3;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;
  font:600 10.5px/1 system-ui;letter-spacing:.02em;color:var(--lg-label-2);background:none;border:0;border-radius:28px}
.tab.hot,.tab[aria-current="page"]{color:var(--lg-accent)}
.lens{position:absolute;z-index:0;left:var(--lg-inset);top:var(--lg-inset);bottom:var(--lg-inset);border-radius:28px; /* 32 - 4 = concentric */
  background:var(--lg-lens);box-shadow:inset 0 0 0 .5px var(--lg-rim),inset 1px 2px 3px -1px var(--lg-rim),0 4px 14px rgba(0,0,0,.12);
  will-change:transform;pointer-events:none}
```
JS: follow section 8.7 and 8.6: on `pointerdown` hop the lens to the pressed slot and inflate; on drag track 1:1 with stretch; on `pointerup` pick `round(x / slotWidth)`, run that tab's action, spring the lens home (or to the new tab if it is a real destination). Keyboard: `click` with `event.detail === 0` triggers the action.

### 9.2 Toggle and slider knob (web reference)

```css
.switch{position:relative;width:52px;height:32px;border-radius:999px;background:rgba(120,120,128,.32);border:0;transition:background-color var(--lg-t-std)}
.switch[aria-checked="true"]{background:#34c759}
.knob{position:absolute;top:2px;left:2px;width:28px;height:28px;--lg-r:999px}   /* give it class "lg" with --lg-blur:6px */
```
Move the knob with a spring on `translateX` (0 to 20 px); on press widen it with a spring on `width` (28 to 38 px) using `inflate`; on release go to the nearest end using the release velocity.

---

## 10. WEB: JS MODULE, REACT COMPONENT, TAILWIND

### 10.1 Files to create

```
src/styles/liquid-glass.css     <- sections 3.1 (tokens), 5.1 (base), 5.2 (variants), 5.3, 5.4 (fallbacks), 8.10 (.lg-edge-top)
src/lib/liquid-glass.js         <- assemble from: 8.2 spring, 8.6 helpers, 8.8 materialize, 7.2 trackLight, 6.3 refraction, 10.2 below
src/components/Glass.tsx        <- 10.3 (React only)
```
Import the CSS once, globally. Call `initLiquidGlass()` once after the DOM is ready (or per component in React, see 10.3).

### 10.2 `makeInteractive` and `initLiquidGlass` (add to liquid-glass.js)

```js
export function makeInteractive(el, { scale = 1.08, response = .3, damping = .65 } = {}) {
  const target = REDUCED_MOTION.matches ? 1 : scale;       // no swell under reduced motion
  let s = 1, anim, pressed = false;
  const to = v => { anim?.stop(); anim = spring(s, v, { response, damping, eps: .002, onUpdate: p => { s = p; el.style.scale = p; } }); };
  const down = e => {
    pressed = true;
    const r = el.getBoundingClientRect();
    el.style.setProperty('--lg-px', (e.clientX - r.left) + 'px');
    el.style.setProperty('--lg-py', (e.clientY - r.top) + 'px');
    el.dataset.pressed = 'true';
    to(target);
    if (!REDUCED_MOTION.matches) navigator.vibrate?.(6);
  };
  const up = () => { if (!pressed) return; pressed = false; delete el.dataset.pressed; to(1); };
  el.addEventListener('pointerdown', down);
  addEventListener('pointerup', up); addEventListener('pointercancel', up);
  return () => { el.removeEventListener('pointerdown', down); removeEventListener('pointerup', up); removeEventListener('pointercancel', up); };
}

export function initLiquidGlass({ light = true } = {}) {
  document.querySelectorAll('.lg[data-lg-interactive]').forEach(el => makeInteractive(el, { scale: +el.dataset.lgScale || 1.08 }));
  document.querySelectorAll('.lg[data-lg-refract]').forEach(observeRefraction);
  if (light) trackLight();
}
```

HTML usage:
```html
<button class="lg lg--circle" style="width:48px" data-lg-interactive data-lg-scale="1.12" aria-label="Share">…icon…</button>
<nav class="tabbar lg" data-lg-refract data-lg-shift="12">…</nav>
```
Guard for server rendering: at the top of the module use
`const REDUCED_MOTION = typeof matchMedia !== 'undefined' ? matchMedia('(prefers-reduced-motion: reduce)') : { matches: false };`
and the same `typeof` guard for `NO_TRANSPARENCY` and `navigator`.

### 10.3 React component

```tsx
'use client';
import { useEffect, useRef, type ElementType, type HTMLAttributes } from 'react';
import { makeInteractive, observeRefraction, releaseRefraction } from '../lib/liquid-glass';

type Props = HTMLAttributes<HTMLElement> & {
  as?: ElementType;
  variant?: 'regular' | 'clear' | 'tint' | 'thick';
  shape?: 'capsule' | 'circle' | 'panel' | 'sheet';
  interactive?: boolean;
  scale?: number;
  refract?: boolean;
};

export function Glass({ as: Tag = 'div', variant = 'regular', shape = 'capsule',
                        interactive = false, scale = 1.08, refract = false,
                        className = '', children, ...rest }: Props) {
  const ref = useRef<HTMLElement>(null);
  useEffect(() => {
    const el = ref.current!;
    const off = interactive ? makeInteractive(el, { scale }) : undefined;
    if (refract) observeRefraction(el);
    return () => { off?.(); if (refract) releaseRefraction(el); };
  }, [interactive, scale, refract]);

  const cls = ['lg',
    variant !== 'regular' && `lg--${variant}`,
    shape === 'circle' && 'lg--circle', shape === 'panel' && 'lg--panel', shape === 'sheet' && 'lg--sheet',
    className].filter(Boolean).join(' ');
  const Comp = Tag as any;
  return <Comp ref={ref} className={cls} {...rest}>{children}</Comp>;
}
```
Rules for React:
- Never put `<Glass>` inside a wrapper that animates `opacity`, `filter` or `transform`-with-`will-change` combined with masks (section 5.5). With Framer Motion, animate the glass element itself (`motion(Glass)`) rather than a parent.
- In Next.js, keep the module in client components only.
- When a Glass element mounts or unmounts conditionally (menu, toast), refraction is recreated by the `useEffect`; do not leave orphan SVG filters.

### 10.4 Tailwind

- Put the `.lg` rules in a normal CSS file (inside `@layer components { … }` if you like). Do **not** recreate the material from 15 utility classes per element. It will drift and be inconsistent.
- Use utilities for layout only: `fixed bottom-3 left-1/2 -translate-x-1/2 h-16 w-[min(22.5rem,calc(100%-2rem))]`. If you use the `scale`/`translate` properties from JS, prefer Tailwind's `translate-*` classes that map to `translate` (Tailwind 4) or add `[translate:-50%_0]`.
- Migration map for old glassmorphism code:
  `backdrop-blur-md bg-white/20 border border-white/30 shadow-lg rounded-2xl` becomes `lg lg--panel`.
  Delete the old classes; do not keep both.

### 10.5 Vue / Svelte / vanilla

Wrap `makeInteractive` / `observeRefraction` in a directive (Vue `mounted`/`unmounted`), a Svelte action (`use:glass`), or call `initLiquidGlass()` after render. Always return the cleanup function and call it on unmount.

---

## 11. NATIVE APPLE (SwiftUI, UIKit, AppKit)

### 11.1 First, the free win

Build with **Xcode 26** against the iOS/iPadOS/macOS 26 SDK. Standard controls (`TabView`, `NavigationStack` toolbars, `Button`, `Toggle`, `Slider`, `Picker` with segmented style, sheets, menus, alerts, sidebars via `NavigationSplitView`) adopt Liquid Glass automatically after recompiling.

Then **remove** old customizations that fight it:
- custom opaque `toolbarBackground`, custom tab bar backgrounds, `presentationBackground` on sheets, `UIBarAppearance` backgrounds, manual blur views behind bars;
- hard divider lines under bars (use the scroll edge effect);
- opaque colors behind toolbar buttons.

Only add custom glass (`glassEffect`) for **your own** floating controls that the system does not provide.

### 11.2 SwiftUI API cheat sheet (iOS/iPadOS/macOS/tvOS/watchOS/visionOS 26) **(verify exact signatures in current docs)**

| Need | API |
|---|---|
| Default glass (regular, capsule) | `.glassEffect()` |
| Choose shape | `.glassEffect(.regular, in: .capsule)` / `.circle` / `.rect(cornerRadius: 28)` / `RoundedRectangle(cornerRadius: 28, style: .continuous)` |
| Clear variant | `.glassEffect(.clear)` |
| Tint (primary action only) | `.glassEffect(.regular.tint(.blue))` |
| Respond to touch (inflate/shimmer) | `.glassEffect(.regular.interactive())` |
| Group glass views (shared backdrop, merging) | `GlassEffectContainer(spacing: 20) { … }` |
| Morph between views | `@Namespace var ns` + `.glassEffectID("id", in: ns)` inside a container, change state inside `withAnimation` |
| Fuse several views into one glass shape | `.glassEffectUnion(id: "group", namespace: ns)` |
| Control morph transition | `.glassEffectTransition(.matchedGeometry)` / `.materialize` / `.identity` |
| Glass buttons | `.buttonStyle(.glass)` and `.buttonStyle(.glassProminent)` |
| Scroll edge effect | `.scrollEdgeEffectStyle(.soft, for: .top)` (`.hard` for dense macOS toolbars) |
| Minimize tab bar on scroll | `.tabBarMinimizeBehavior(.onScrollDown)` |
| Accessory above tab bar | `.tabViewBottomAccessory { … }` |
| Content extends under sidebar | `.backgroundExtensionEffect()` |
| Toolbar grouping | `ToolbarSpacer(.fixed)` / `ToolbarSpacer(.flexible)`, `.sharedBackgroundVisibility(.hidden)` for an item that should not share a glass group |
| Concentric shapes | `ConcentricRectangle()` and `.containerShape(…)` |

Rules:
- Apply `.glassEffect` **after** layout modifiers (`padding`, `frame`) so the glass hugs the final bounds.
- Put several glass views that sit near each other in ONE `GlassEffectContainer`. Separate glass views sample their own backdrop and can look inconsistent.
- Use `.interactive()` only on tappable or draggable glass.
- Never apply glass to scrolling content rows.

### 11.3 SwiftUI examples

Simple custom control:
```swift
Label("Import", systemImage: "plus")
    .font(.body.weight(.semibold))
    .padding(.horizontal, 20).padding(.vertical, 12)
    .glassEffect(.regular.interactive(), in: .capsule)
```

Primary and secondary actions:
```swift
HStack(spacing: 12) {
    Button("Cancel") { dismiss() }.buttonStyle(.glass)
    Button("Save")   { save() }.buttonStyle(.glassProminent).tint(.blue)
}
```

Morphing action cluster (button expands into more buttons):
```swift
struct ActionCluster: View {
    @State private var expanded = false
    @Namespace private var ns
    var body: some View {
        GlassEffectContainer(spacing: 24) {
            HStack(spacing: 12) {
                Button { withAnimation(.bouncy) { expanded.toggle() } } label: {
                    Image(systemName: expanded ? "xmark" : "plus").frame(width: 44, height: 44)
                }
                .buttonStyle(.glass).glassEffectID("toggle", in: ns)

                if expanded {
                    Button { } label: { Image(systemName: "photo").frame(width: 44, height: 44) }
                        .buttonStyle(.glass).glassEffectID("photo", in: ns)
                    Button { } label: { Image(systemName: "camera").frame(width: 44, height: 44) }
                        .buttonStyle(.glass).glassEffectID("camera", in: ns)
                }
            }
        }
    }
}
```

Tab bar that minimizes, with soft scroll edge:
```swift
TabView {
    Tab("Files", systemImage: "folder") { FilesView() }
    Tab("Settings", systemImage: "gear") { SettingsView() }
}
.tabBarMinimizeBehavior(.onScrollDown)
```
```swift
ScrollView { … }.scrollEdgeEffectStyle(.soft, for: .top)
```

Sheets: just use `.sheet` with `.presentationDetents([.medium, .large])`. Do not add a custom `presentationBackground`. The system draws glass at partial detents and becomes more opaque at large.

### 11.4 UIKit **(verify)**

```swift
let glass = UIGlassEffect()
glass.isInteractive = true               // touch response
glass.tintColor = .systemBlue            // primary action only
let blur = UIVisualEffectView(effect: glass)
blur.cornerConfiguration = .capsule()    // corner configuration API; or layer.cornerRadius with .continuous curve
blur.contentView.addSubview(label)       // content goes in contentView, never on the effect view itself

// group glass views with shared sampling and merging
let container = UIGlassContainerEffect(); container.spacing = 20
let group = UIVisualEffectView(effect: container)

// materialize / dematerialize: animate the effect
UIView.animate(springDuration: 0.4, bounce: 0.15) { blur.effect = glass }      // appear
UIView.animate(springDuration: 0.25, bounce: 0) { blur.effect = nil }          // disappear

// buttons
var cfg = UIButton.Configuration.glass()          // .prominentGlass() for primary
```
Springs: `UIView.animate(springDuration:bounce:)` (bounce 0 = damping ratio 1, positive bounce = overshoot).

### 11.5 AppKit (macOS 26) **(verify)**

```swift
let glass = NSGlassEffectView()
glass.cornerRadius = 28
glass.tintColor = nil
glass.contentView = hostingView           // your content
let container = NSGlassEffectContainerView(); container.spacing = 16
container.contentView = stackOfGlassViews
```
Window: use a full-size content view and let content extend under the toolbar and sidebar (`.backgroundExtensionEffect()` in SwiftUI).

### 11.6 Supporting older OS versions (fallback helper)

```swift
extension View {
    @ViewBuilder
    func liquidGlass<S: Shape>(in shape: S = Capsule(), interactive: Bool = false) -> some View {
        if #available(iOS 26.0, macOS 26.0, *) {
            self.glassEffect(interactive ? .regular.interactive() : .regular, in: shape)
        } else {
            self.background(.ultraThinMaterial, in: shape)
                .overlay(shape.strokeBorder(.white.opacity(0.28), lineWidth: 0.5))
                .shadow(color: .black.opacity(0.12), radius: 14, y: 6)
        }
    }
}
```
Also respect `@Environment(\.accessibilityReduceTransparency)`: when true, use `Color(.secondarySystemBackground)` instead of any material. The system glass already adapts automatically.

### 11.7 Native motion rules

- Prefer `.bouncy`, `.smooth`, `.snappy` (or `.spring(response:dampingFraction:)` with section 3.3 values). Default `.smooth`/damping 1; `.bouncy` only for momentum or morphs.
- Drive drags with `DragGesture` and `.interactiveSpring`; on `onEnded` use `value.predictedEndTranslation` (that is the platform's momentum projection) to pick the target, and animate with the release velocity.
- Haptics: `.sensoryFeedback(.impact(flexibility: .soft), trigger: value)` or `.selection` for moving through segments.
- Honor `@Environment(\.accessibilityReduceMotion)`: replace movement and scale with `.opacity` transitions.

---

## 12. ANDROID, FLUTTER, REACT NATIVE, ELECTRON, TAURI, WINDOWS

Rule for all non-Apple platforms: there is no system Liquid Glass. Rebuild the stack from section 1 with the platform's primitives. Priorities when time or capability is limited: (1) backdrop blur + tint, (2) rim, (3) shadows, (4) inflate spring, (5) refraction shader. Never skip (2) to (4).

### 12.1 Android (Jetpack Compose)

Compose has no built-in backdrop blur of sibling content. Options: a backdrop-blur library (for example **Haze**, check its current README for the API **(verify)**), or `RenderEffect` blur on a duplicated background layer (API 31+). Refraction: AGSL `RuntimeShader` (API 33+), port the math from section 6.5.

Layers other than the backdrop sample, in plain Compose:
```kotlin
val shape = RoundedCornerShape(50)             // capsule; use RoundedCornerShape(28.dp) for panels
var pressed by remember { mutableStateOf(false) }
val scale by animateFloatAsState(
    targetValue = if (pressed) 1.10f else 1f,
    animationSpec = spring(dampingRatio = 0.65f, stiffness = 440f),   // 'inflate' token
    label = "inflate")

Box(
    Modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(20.dp, shape, ambientColor = Color.Black.copy(.16f), spotColor = Color.Black.copy(.16f))
        .clip(shape)
        // >>> backdrop blur (radius ~12.dp) from your library goes here <<<
        .background(Brush.linearGradient(listOf(Color.White.copy(.22f), Color.Transparent)))
        .background(Color.White.copy(.36f))
        .border(1.2.dp,
            Brush.linearGradient(listOf(Color.White.copy(.95f), Color.Transparent, Color.Transparent, Color.White.copy(.55f))),
            shape)
        .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }) }
)
```
Stiffness values for tokens (mass 1): snappy 504, smooth 247, bouncy 273 (damping ratio .8), inflate 440 (ratio .65), sheet 195 (ratio .88), exit 816. Use `Spring.DampingRatioNoBouncy` for ratio 1.
Respect `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (reduced motion): skip scale and bounce.
Use `WindowInsets` for safe areas. Touch targets 48 dp.

### 12.2 Flutter

```dart
class Glass extends StatelessWidget {
  const Glass({super.key, required this.child, this.radius = 999, this.blur = 12});
  final Widget child; final double radius, blur;
  @override
  Widget build(BuildContext context) {
    final br = BorderRadius.circular(radius);
    return DecoratedBox(
      decoration: BoxDecoration(borderRadius: br, boxShadow: [
        BoxShadow(color: Colors.black.withOpacity(.16), blurRadius: 34, offset: const Offset(0, 12)),
        BoxShadow(color: Colors.black.withOpacity(.08), blurRadius: 8, offset: const Offset(0, 2)),
      ]),
      child: ClipRRect(
        borderRadius: br,
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
          child: CustomPaint(
            foregroundPainter: _RimPainter(br),
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight,
                  colors: [Colors.white.withOpacity(.22 + .36), Colors.white.withOpacity(.36)]),
              ),
              child: child,
            ),
          ),
        ),
      ),
    );
  }
}

class _RimPainter extends CustomPainter {
  _RimPainter(this.br);
  final BorderRadius br;
  @override
  void paint(Canvas c, Size s) {
    final rect = Offset.zero & s;
    final shader = LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight,
      colors: [Colors.white.withOpacity(.95), Colors.transparent, Colors.transparent, Colors.white.withOpacity(.55)],
      stops: const [0, .28, .62, 1]).createShader(rect);
    c.drawRRect(br.toRRect(rect).deflate(.6), Paint()
      ..style = PaintingStyle.stroke..strokeWidth = 1.2..shader = shader);
  }
  @override bool shouldRepaint(covariant _RimPainter o) => o.br != br;
}
```
Notes: `BackdropFilter` must be inside a `ClipRRect` or it blurs the whole screen. Saturation boost is not available through `ImageFilter.blur`; compensate with slightly higher tint alpha, or use a fragment shader **(verify)**. Springs: `SpringDescription.withDampingRatio(mass: 1, stiffness: 440, ratio: .65)` and `controller.animateWith(SpringSimulation(desc, controller.value, 1.0, velocity))` using `AnimationController.unbounded` so overshoot is allowed. Respect `MediaQuery.of(context).disableAnimations` and `MediaQuery.of(context).highContrast`. Packages exist that implement lens refraction (search pub.dev for "liquid glass") **(verify quality before adopting)**.

### 12.3 React Native / Expo **(verify)**

```tsx
import { GlassView, GlassContainer, isLiquidGlassAvailable } from 'expo-glass-effect';
import { BlurView } from 'expo-blur';

const ok = isLiquidGlassAvailable();               // true on iOS 26+
return ok
  ? <GlassView glassEffectStyle="regular" isInteractive tintColor={undefined}
               style={{ borderRadius: 999, paddingHorizontal: 20, paddingVertical: 12 }}>{children}</GlassView>
  : <BlurView intensity={40} tint="systemThinMaterial"
              style={{ borderRadius: 999, overflow: 'hidden', borderWidth: 0.5, borderColor: 'rgba(255,255,255,.35)' }}>{children}</BlurView>;
```
Use `GlassContainer` to group nearby glass views. Animate with `react-native-reanimated` `withSpring({ stiffness, damping, mass })` using section 3.3 conversions (RN damping = `2*ratio*sqrt(stiffness)` for mass 1). Android: no glass, use the blur fallback plus a rim.

### 12.4 Electron, Tauri, Windows, Linux

- **Chrome of the app (buttons, tab bar, menus):** use the web tiers (sections 5 to 8, 10) inside the webview.
- **Desktop-through glass (window itself translucent):**
  - Electron: `new BrowserWindow({ transparent: true, frame: false, vibrancy: 'under-window', visualEffectState: 'active', backgroundMaterial: 'acrylic' })` (`vibrancy` is macOS, `backgroundMaterial` is Windows 11: `'mica' | 'acrylic' | 'tabbed'`) **(verify)**. Keep `body { background: transparent }`.
  - Tauri: `window-vibrancy` crate (`apply_vibrancy`, `apply_acrylic`, `apply_mica`) **(verify)**.
  - WinUI 3: `SystemBackdrop = new DesktopAcrylicBackdrop()` or `MicaBackdrop`; in-app `AcrylicBrush`.
  - WPF / WinForms: use `DwmSetWindowAttribute` with `DWMWA_SYSTEMBACKDROP_TYPE` on Windows 11 **(verify)**; draw rim and shadow yourself.
  - Linux (GTK/Qt): rely on the compositor blur if present (KWin, Hyprland); otherwise fall back to a solid translucent surface. Draw rim and shadow yourself.
- `backdrop-filter` inside a transparent Electron window blurs only your page content, not the desktop. For desktop blur use the window material.
- Pointer-first UI: smaller sizes (28 to 32 px controls), hover states, keyboard focus rings, no inflate larger than 1.06.

### 12.5 Game engines and custom renderers (Unity, Unreal, WebGL, Skia)

Render the scene to a texture, then draw the glass quad with a shader that: samples the scene texture at refracted UVs (section 6.5), blurs (two-pass Gaussian or Kawase, radius 8 to 24 px at 1x), applies saturation (`mix(luma, color, 1.9)`), adds tint, rim (distance field based, gradient by light angle), inner shading and shadow. Update the SDF uniforms from the element rect and radius. Springs run on the CPU and feed `scale`, `position` uniforms.

---

## 13. ACCESSIBILITY (MANDATORY)

### 13.1 System settings to honor

| Setting | Web signal | Required behavior |
|---|---|---|
| Reduce Transparency | `prefers-reduced-transparency: reduce` | solid surface, no blur, no glow, no refraction |
| Increase Contrast | `prefers-contrast: more` | solid surface + 1.5 px high-contrast border |
| Reduce Motion | `prefers-reduced-motion: reduce` | no springs/bounce/scale/blur-in; use 200 ms opacity cross-fades; keep color/opacity feedback |
| Forced colors (Windows HC) | `forced-colors: active` | `Canvas`/`CanvasText` colors, 1 px border, no effects |
| Larger text | user font size | use `rem`; controls grow in height; nothing clips |
| Dark / light | `prefers-color-scheme` | tokens for both; manual override supported |

Reduced motion does not mean no feedback. A pressed control still needs a visible state (color change, opacity), just not a swell.

### 13.2 Contrast and legibility

- Text 4.5:1, large text and icons 3:1, against the worst plausible backdrop (test over a busy photo, white, and black).
- If you cannot guarantee it, raise tint alpha, use `--thick`, or add a scrim under the glass.
- Do not convey state by glass appearance alone. Pair with an icon, a label, a checkmark or color plus shape.

### 13.3 Roles and keyboard

| Component | Semantics | Keyboard |
|---|---|---|
| Tab bar (navigation) | `<nav>` with buttons, `aria-current="page"` on active | Tab to move, Enter/Space activates |
| Segmented control | `role="radiogroup"` with `role="radio"` + `aria-checked` (or `tablist`/`tab` when it switches panels) | Arrow keys move and select, Home/End |
| Switch | `role="switch"` + `aria-checked` | Space toggles |
| Slider | `role="slider"` + `aria-valuemin/max/now/text` | Arrows +/- 1 step, PageUp/Down large step, Home/End |
| Menu / popover | `role="menu"`, items `role="menuitem"`, trigger `aria-haspopup="menu"` + `aria-expanded` | Arrow keys, Esc closes, focus returns to trigger |
| Dialog / sheet | `role="dialog"` + `aria-modal="true"` + label | focus trap, Esc closes, focus returns |
| Toast | `role="status"` (polite) or `role="alert"` for errors | not focus-stealing; Undo button is reachable |
| Icon buttons | `aria-label` | standard |

- Focus ring: 2 px accent outline, offset 3 px, visible on glass in light and dark. The selection lens follows keyboard focus too.
- The drag gestures (lens, sheet, switch) must have non-drag alternatives (tap, buttons, keys).
- Hit areas at least 44 px even if the glass looks smaller.

### 13.4 Motion safety

- No flashing or strobing. No looping idle animation. No full-screen moving glass.
- If a large glass surface repositions, make it semi-transparent while it travels (fade down to about 0.6 and back), as large moving objects can trigger motion sensitivity.
- Avoid abrupt brightness jumps when switching light/dark; ease over about 350 ms.

---

## 14. PERFORMANCE AND BROWSER COMPATIBILITY

### 14.1 Budgets

| Item | Limit |
|---|---|
| Visible glass surfaces at once (web/mobile) | about 6 (typical: top bar group, tab bar, one floating button, one popover) |
| Elements with refraction | 3 maximum, small/medium only |
| Blur radius | 24 px typical, 28 px hard cap for sheets |
| Full-screen glass | never |
| Animated properties | `transform`, `scale`, `translate`, `opacity` (plus `filter: blur()` on small materializing elements) |
| Frame time target | at most 16.7 ms (60 fps); degrade if average exceeds about 24 ms |

### 14.2 Rules

- `backdrop-filter` re-renders when anything behind it changes. A big scrolling list under three glass elements costs real GPU time. Keep glass small.
- Never animate `backdrop-filter` blur radius continuously. Animate opacity or scale of the glass element instead.
- Do not animate `width/height/top/left` of large glass areas. For morphs (8.9), only small elements.
- Avoid `will-change` on many elements; apply it only to elements about to move (lens, sheet) and remove after.
- Do not nest `backdrop-filter` (section 5.5).
- Avoid `box-shadow` blur radius over about 60 px, it is expensive on large elements.
- Use `content-visibility: auto` on long off-screen lists under glass.
- Test with DevTools CPU throttling (4x and 6x) and mobile emulation. Confirm scrolling stays smooth.

### 14.3 Degradation ladder (implement it)

| Level | What is on | Trigger |
|---|---|---|
| 0 | refraction + blur + sheen + light tracking | capable device |
| 1 | no refraction | non-Chromium, low-end, or reduced transparency |
| 2 | no light tracking (angle fixed at 135deg) | touch devices, low-end |
| 3 | no blur: solid translucent surface | `backdrop-filter` missing, frame time over about 34 ms |
| 4 | solid opaque surface | reduced transparency, increased contrast, forced colors |

CSS hooks:
```css
[data-lg-level="3"] .lg { -webkit-backdrop-filter: none; backdrop-filter: none; background: color-mix(in srgb, var(--lg-solid) 88%, transparent); }
[data-lg-level="4"] .lg { -webkit-backdrop-filter: none; backdrop-filter: none; background: var(--lg-solid); }
```
Simple frame-time probe:
```js
export function autoDegrade() {
  let n = 0, t0 = performance.now();
  const f = t => { if (++n < 90) return requestAnimationFrame(f);
    const avg = (t - t0) / 90, root = document.documentElement;
    if (avg > 34) root.dataset.lgLevel = '3'; else if (avg > 24) { root.dataset.lgLevel = '1'; document.querySelectorAll('.lg[data-lg-refract]').forEach(releaseRefraction); } };
  requestAnimationFrame(f);
}
```
Run it once after first interaction, not during load.

### 14.4 Browser support summary **(verify against caniuse before promising)**

| Feature | Chrome / Edge | Safari (macOS + iOS) | Firefox |
|---|---|---|---|
| `backdrop-filter` | yes | yes, needs `-webkit-` prefix | yes (current versions) |
| `backdrop-filter: url(#svg)` (refraction) | yes | no | no |
| `mask-composite` (rim) | yes | yes, with `-webkit-mask-composite: xor` | yes |
| `color-mix()` | yes | yes | yes |
| CSS `scale`/`translate` properties | yes | yes | yes |
| `linear()` easing | yes | yes | yes |
| `corner-shape: squircle` | recent Chrome only | no | no |
| `prefers-reduced-transparency` | recent | recent | partial |

Use `@supports` and the JS gate; never user-agent sniff for anything except the refraction gate in 6.1.

---

## 15. ANTI-PATTERNS (NEVER DO THESE)

1. Glass on list rows, cards of content, table cells, or whole-page backgrounds.
2. Glass inside glass (including a lens with its own `backdrop-filter` inside a glass bar).
3. Blur only, no rim, no inner highlight (that is old frosted glass, not Liquid Glass).
4. Blur above 30 px on any element, or `blur(40px)` "for the effect".
5. Gray, thin (weight 300 to 400) or low-contrast text on glass.
6. Tinting several controls, or tinting decoratively.
7. Sharp corners, radius under 12 px, or inner radius not concentric with the outer.
8. Fixed-duration `ease-in-out` keyframes for touch interactions; CSS transitions on drag transforms.
9. Waiting for pointer-up to show press feedback.
10. Locking input during an animation, or animating from the target value instead of the live value.
11. Bounce on everything. Overshoot only for momentum or physical blobs.
12. Looping shimmer or breathing glow on idle glass.
13. Animating blur radius, `width/height` of big areas, or `box-shadow` per frame.
14. `opacity`, `filter`, `mask`, `clip-path` or `mix-blend-mode` on an ancestor of a glass element.
15. Hard 1 px divider lines under glass bars; use the scroll edge effect.
16. Full-width opaque strips reserved for bars; content must extend underneath.
17. Skipping `-webkit-backdrop-filter`.
18. Skipping the reduced-transparency, increased-contrast, forced-colors and reduced-motion fallbacks.
19. Putting glass over a flat solid background and calling the demo "done" (it will look gray). Show it over real content.
20. Refraction on every element, or refraction on large panels (performance).
21. Drawing a gooey SVG filter over `backdrop-filter` elements to fake merging.
22. Using Clear glass over text or plain UI.
23. Rewriting unrelated code. Make the minimal change that adds the system.
24. Claiming a native API exists without checking the SDK version. Wrap in `#available` / feature checks.

---

## 16. AGENT WORKFLOW AND QA CHECKLIST

### 16.1 Step-by-step (follow in order)

**Step 0: Audit (read-only).**
- Find the framework, styling system, entry CSS, theme mechanism (CSS variables, Tailwind, CSS-in-JS, SwiftUI assets, Compose theme).
- List existing "glass-like" code (`backdrop-blur`, `blur(`, `.glass`, `UIBlurEffect`, `.ultraThinMaterial`).
- List components that float over content. Those are the only candidates for glass (section 2.1).

**Step 1: Decide platform and tier** with section 0.3. Write one sentence stating the tier.

**Step 2: Tokens.** Add section 3.1 tokens (or the platform's equivalent). Map the app's accent color to `--lg-accent`.

**Step 3: Primitive.** Build the glass primitive (`.lg` + variants, or `Glass` component / SwiftUI modifier) with all 8 layers, fallbacks included (section 5, or 11 to 12).

**Step 4: Components.** Convert candidates from Step 0 using section 9 specs. One at a time: nav/toolbar, tab bar, buttons, menus/sheets, toasts. Replace old glassmorphism classes; do not keep both.

**Step 5: Motion.** Add the spring helper, press inflate, selection lens, materialize/dematerialize, scroll edge effect, sheet behavior as needed (section 8). Everything interruptible.

**Step 6: Light and refraction (web).** Add `trackLight()` for fine pointers. Add refraction to at most 3 small elements (section 6) and confirm the gate.

**Step 7: Accessibility.** Section 13: reduced transparency / contrast / motion / forced colors, ARIA roles, keyboard, focus ring, contrast measurements.

**Step 8: Performance.** Count visible glass surfaces, enable `autoDegrade`, throttle CPU 4x and scroll.

**Step 9: Verify** with the checklist below. Take screenshots over three backgrounds if you can run a browser or simulator.

**Step 10: Report** per section 0.4.

### 16.2 QA checklist (tick every line before saying "done")

Visual
- [ ] All 8 layers present (backdrop, refraction or its fallback, blur+saturate, tint+sheen, inner shading, rim, shadows, crisp content).
- [ ] Rim visible on light backgrounds (outer hairline present) and on dark.
- [ ] Looks correct over: busy photo, plain white, plain black.
- [ ] Controls are capsules/circles; containers are rounded rects; inner radii are concentric.
- [ ] Only one tinted element per region.
- [ ] Text contrast measured: 4.5:1 body, 3:1 large/icons.
- [ ] Light and dark themes both checked.

Behavior and motion
- [ ] Press feedback begins on pointer-down.
- [ ] Press swell uses a spring; release returns smoothly.
- [ ] Can interrupt every animation mid-flight (tap during a move, drag during a close).
- [ ] Drag tracks 1:1 with grab offset preserved; releases with velocity; rubber-bands at limits.
- [ ] Menus/popovers materialize from the trigger and exit along the same path.
- [ ] Tab bar lens follows finger and snaps; keyboard works.
- [ ] Scroll edge effect replaces hard dividers; tab bar minimizes if specified.
- [ ] No looping idle animations.

Accessibility
- [ ] Reduced transparency: solid surfaces, no blur.
- [ ] Increased contrast: solid + border.
- [ ] Reduced motion: cross-fades only, feedback still visible.
- [ ] Forced colors: readable.
- [ ] Roles, labels, focus ring, Esc handling, focus return.
- [ ] Targets at least 44 px.

Performance and code
- [ ] At most about 6 glass surfaces visible; at most 3 with refraction.
- [ ] No ancestor of glass has `opacity<1`, `filter`, `mask`, `clip-path`, `mix-blend-mode`.
- [ ] `-webkit-backdrop-filter` present.
- [ ] Smooth at 4x CPU throttle (or degrades via `autoDegrade`).
- [ ] Cleanup on unmount (listeners, springs, SVG filters).
- [ ] No unrelated code changed. Diff is minimal and explained.
- [ ] Items marked "(verify)" were checked against current docs or listed as unverified in the report.

### 16.3 Common user requests and what to do

| User says | Do this |
|---|---|
| "Make everything glass" | Apply the rules in 2.1: glass only on floating chrome. Explain in one sentence. Keep content surfaces normal. |
| "Make it look exactly like iOS 26" | Native: use system components (11). Web: tiers 1 and 2, tab bar lens, toolbar groups, sheets, scroll edge effect. |
| "Glass doesn't show / it's just gray" | Section 17 rows 1 and 2. Check backdrop root and page background. |
| "Too blurry / can't read text" | Lower blur to 10 to 12 px, raise tint alpha by +0.10, bump label weight to 600. Do not add heavy shadows. |
| "Add animations" | Section 8 only: springs, press inflate, lens, materialize, scroll effects. No keyframe loops. |
| "Support dark mode" | Section 3.1 dark tokens, both selectors (system + manual). |
| "Works on Safari/iPhone?" | Tier 1 yes. Refraction no. Say so honestly. |
| "Make it faster" | Section 14: fewer glass surfaces, drop refraction, ladder levels. |

---

## 17. TROUBLESHOOTING TABLE

| # | Symptom | Likely cause | Fix |
|---|---|---|---|
| 1 | Glass shows no blur at all | An ancestor has `opacity<1`, `filter`, `mask`, `clip-path` or `mix-blend-mode` (backdrop root) | Move the glass out of that ancestor or animate the glass element itself (5.5) |
| 2 | Glass is a flat gray/white rectangle | Nothing colorful behind it; tint too high | Put real content behind; lower tint alpha; check the page background |
| 3 | Works in Chrome, not in Safari | Missing `-webkit-backdrop-filter`, or `url()` refraction used | Add prefix; accept Tier 1 look on Safari (6.1) |
| 4 | Blur looks right but it reads as "old frosted glass" | No rim / inner shading / light ramp | Add layers 4 and 5 (4.4, 5.1) |
| 5 | Rim invisible on light backgrounds | White rim on a white page | Keep the `0 0 0 .5px var(--lg-edge-dark)` outer line and the shadow |
| 6 | Rim looks harsh or like a plain border | Rim is uniform; no gradient mask | Use the two-lobe gradient (28% and 62% transparent stops), thickness 1 to 1.2 px |
| 7 | Refraction invisible | `maxShift` too small, blur too high, or `feImage` map flat | Shift 10 to 14 px, blur at most 4 px with refraction, confirm `scale = 2 * shift`, `color-interpolation-filters="sRGB"` |
| 8 | Refraction pushes content the wrong way | Sign of the normal | `inward: false` in `makeDisplacementMap` |
| 9 | Refraction map misaligned or stretched | `preserveAspectRatio` missing, or size changed without update | Set `preserveAspectRatio="none"`; update via `ResizeObserver` |
| 10 | Scrolling is janky | Too many glass surfaces / refraction / big blur | Section 14: reduce count, degrade, remove refraction |
| 11 | Flicker or flashing during animation | Animating blur radius or ancestor opacity | Animate scale/opacity on the glass element only |
| 12 | Press scale replaces my centering transform | Using `transform` in JS | Use the `scale` and `translate` properties, not `transform` |
| 13 | Press feels laggy | Using `click`, or CSS transition on scale | Use `pointerdown` + spring (8.5) |
| 14 | Animation jumps when interrupted | Starting from the target value | Keep the live value in a variable; `spring(live, newTarget)` (8.2) |
| 15 | Spring never settles / loops forever | `eps` too small for pixel values | Use `eps` .05 for px, .002 for 0..1 values |
| 16 | Text unreadable on glass | Low contrast backdrop, weight 400, mid-gray color | Raise tint, weight 600, use `--lg-label`, measure contrast |
| 17 | Lens looks like a second glass pane | The lens has its own `backdrop-filter` | Remove it; lens is a plain translucent fill (8.7) |
| 18 | Menu appears from the wrong place | `transform-origin` is the center | Set origin from the trigger's center relative to the menu (8.8) |
| 19 | Everything breaks with reduced motion on | Springs jump to target but layout expects motion | Provide cross-fade path; keep final states identical |
| 20 | Native: custom bar background fights glass | Old `toolbarBackground`/appearance code | Remove it (11.1) |
| 21 | Native: glass views look inconsistent | Separate glass views sampling separately | Wrap in `GlassEffectContainer` (11.2) |
| 22 | SSR crash `matchMedia is not defined` | Module touches browser APIs at import | Guard with `typeof matchMedia` / use client component (10.2, 10.3) |
| 23 | Memory growth | SVG filters and listeners never released | Call `releaseRefraction` and the `makeInteractive` cleanup on unmount |

---

## 18. CHEAT SHEET

**Recipe in 12 lines (web):**
1. Tokens from 3.1. Page has real content/colors behind.
2. `.lg` = `backdrop-filter: blur(12px) saturate(190%) brightness(1.05)` + `background: sheen + tint(.36)`.
3. Edge = hairline inset + top-left inset highlight + bottom-right soft highlight + 0.5px dark outer line.
4. Rim = 1.2px masked gradient border, stops `rim, transparent 28%, transparent 62%, rim-soft`, angle 135deg.
5. Shadows = ambient `0 12px 34px .16` + contact `0 2px 8px .08`.
6. Shape = capsule for controls, r 28 to 36 for panels, concentric inner radii (inner = outer - inset).
7. Press = spring `scale` to 1.08 to 1.14 on `pointerdown` (response .3, damping .65), glow under finger.
8. Lens in bars = plain translucent capsule, follows finger, stretch by velocity, snaps with bouncy spring.
9. Menus/toasts = materialize: opacity + scale .85 to 1 + blur 8 to 0, origin at trigger.
10. Scroll = soft edge blur under bars, tab bar minimizes on scroll down.
11. Optional Chromium refraction: SDF displacement map, shift 10 to 14 px, on at most 3 small elements.
12. Fallbacks: no backdrop-filter, reduced transparency, increased contrast, forced colors, reduced motion.

**Numbers to remember:** blur 12 (small/medium) / 24 (large) | saturate 190% | tint .36 (light) .40 (dark) | rim 1.2px | press 1.08 to 1.14 | inflate spring .30 / .65 | default spring .40 / 1.0 | exit .22 / 1.0 | sheet .45 / .88 | capsule radius 999 | panel radius 28 | sheet radius 36 to 44 | touch target 44 | glass surfaces at most 6 | refraction at most 3.

**Three-sentence summary to tell the user:** "I implemented Liquid Glass as a layered material (blur, tint, specular rim, inner shading, shadow) on floating UI only, with spring-based press, drag and morph motion. Where the platform supports it I added edge refraction; everywhere else it falls back gracefully, and it respects reduced transparency, contrast and motion settings. Content surfaces stay normal so text remains readable."
