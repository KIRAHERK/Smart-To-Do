---
name: Thakkirni (ذكّرني)
description: An Arabic voice to-do notebook in deep emerald and gold on warm ivory, built big for an older reader.
colors:
  emerald: "#0E5E4F"
  emerald-deep: "#0A4A3F"
  emerald-bright: "#18876F"
  hero-glow: "#4FD1AE"
  mint: "#D5ECE4"
  on-mint: "#032C24"
  gold: "#B98A2E"
  gold-text: "#7E5B12"
  gold-container: "#F5E9CF"
  terracotta: "#C0582F"
  terracotta-container: "#FBE4D9"
  ivory-ground: "#F6F3EC"
  paper: "#FBF9F5"
  surface: "#FFFFFF"
  surface-container: "#F3EFE7"
  surface-variant: "#EFEBE2"
  ink: "#1D1B17"
  ink-muted: "#615B50"
  ink-muted-web: "#57514A"
  outline: "#CFC7B7"
  outline-variant: "#E7E1D5"
  rule-web: "#E2DBCC"
  on-deep-muted: "#BFE6DA"
  phone-black: "#0F0F0E"
  error: "#BF3A2B"
  success: "#2E8B57"
  status-partly: "#C98A12"
  status-in-progress: "#2F5DA8"
  status-postponed: "#D27A1F"
  status-missed: "#8D6E63"
  status-cancelled: "#6F6A62"
  status-not-started: "#7A8580"
  dark-ground: "#12110F"
  dark-surface: "#1C1B18"
  dark-ink: "#EDE8DF"
  dark-emerald: "#7FD4BE"
  dark-gold: "#E5C27A"
  dark-success: "#6FD39A"
  dark-hero-start: "#0B3D34"
  dark-hero-end: "#136B59"
typography:
  display: { fontFamily: "IBM Plex Sans Arabic", fontSize: "34sp", fontWeight: 700, lineHeight: "44sp" }
  headline: { fontFamily: "IBM Plex Sans Arabic", fontSize: "26sp", fontWeight: 700, lineHeight: "36sp" }
  title: { fontFamily: "IBM Plex Sans Arabic", fontSize: "23sp", fontWeight: 700, lineHeight: "32sp" }
  title-medium: { fontFamily: "IBM Plex Sans Arabic", fontSize: "20sp", fontWeight: 600, lineHeight: "29sp" }
  body: { fontFamily: "IBM Plex Sans Arabic", fontSize: "19sp", fontWeight: 400, lineHeight: "29sp" }
  body-medium: { fontFamily: "IBM Plex Sans Arabic", fontSize: "17sp", fontWeight: 400, lineHeight: "25sp" }
  label: { fontFamily: "IBM Plex Sans Arabic", fontSize: "17sp", fontWeight: 600, lineHeight: "24sp" }
  label-medium: { fontFamily: "IBM Plex Sans Arabic", fontSize: "15sp", fontWeight: 500, lineHeight: "21sp" }
  web-display: { fontFamily: "IBM Plex Sans, system-ui, sans-serif", fontSize: "clamp(36px, 3.9vw, 56px)", fontWeight: 700, lineHeight: 1.06, letterSpacing: "-0.02em" }
  web-display-ar: { fontFamily: "El Messiri, IBM Plex Sans, system-ui, sans-serif", fontSize: "clamp(30px, 3.4vw, 50px)", fontWeight: 700, lineHeight: 1.3, letterSpacing: "0em" }
  web-headline: { fontFamily: "IBM Plex Sans, system-ui, sans-serif", fontSize: "clamp(32px, 3.4vw, 46px)", fontWeight: 700, lineHeight: 1.1, letterSpacing: "-0.015em" }
  web-body: { fontFamily: "IBM Plex Sans, Noto Naskh Arabic, system-ui, sans-serif", fontSize: "18px", fontWeight: 400, lineHeight: 1.6 }
  web-body-ar: { fontFamily: "Noto Naskh Arabic, IBM Plex Sans, system-ui, sans-serif", fontSize: "20px", fontWeight: 400, lineHeight: 1.8 }
  web-quote: { fontFamily: "Noto Naskh Arabic, serif", fontSize: "25px", fontWeight: 400, lineHeight: 1.6 }
rounded: { xs: "8px", sm: "12px", row: "16px", md: "18px", card: "22px", lg: "24px", dialog: "28px", xl: "32px", pill: "999px" }
spacing: { xs: "4px", sm: "8px", md: "14px", lg: "16px", xl: "24px", web-gutter: "24px", web-section: "104px", web-section-compact: "72px" }
components:
  button-talk: { backgroundColor: "{colors.emerald-deep}", textColor: "{colors.surface}", typography: "{typography.title}", rounded: "{rounded.pill}", height: "70px", padding: "0 34px 0 10px" }
  button-download: { backgroundColor: "{colors.emerald}", textColor: "{colors.surface}", rounded: "{rounded.md}", height: "56px", padding: "16px 28px" }
  button-download-hover: { backgroundColor: "{colors.emerald-deep}" }
  card: { backgroundColor: "{colors.surface}", rounded: "{rounded.card}", padding: "16px" }
  pill: { rounded: "{rounded.pill}", typography: "{typography.label-medium}", padding: "4px 10px" }
  meta-chip: { backgroundColor: "{colors.surface-container}", textColor: "{colors.ink-muted}", rounded: "{rounded.xs}", padding: "3px 8px" }
  done-button: { textColor: "{colors.success}", rounded: "{rounded.pill}", size: "54px" }
  dialog: { backgroundColor: "{colors.surface}", rounded: "{rounded.dialog}" }
  language-toggle: { textColor: "{colors.ink}", rounded: "{rounded.pill}", height: "40px", padding: "6px 16px" }
  phone-frame: { backgroundColor: "{colors.phone-black}", rounded: "{rounded.xl}", padding: "7px" }
---

# Design System: Thakkirni (ذكّرني)

Formerly Smart To-Do. Mark: a cream microphone on emerald with a gold thread tied round its stem (`ic_launcher_foreground.xml`, `docs/icon.svg`).

**App colours (Settings → Display):** `Accent` in `Theme.kt` swaps only `primary`, the primary container and the header gradient: Green `#0E5E4F` (default), Blue `#1F4E8C`, Purple `#5B3A8C`, Red `#9E2335`, Pink `#A3306B`, each with a dark-mode partner. Gold, the warm ground and the status colours never change.

## Overview

**Creative North Star: "The Emerald Notebook"**

A personal assistant's notebook, bound in deep emerald with a gold thread, on warm ivory paper. The app is the source of the world (`ui/theme/Theme.kt`); the download page (`docs/index.html`) is its second surface and borrows the same ground, ink, emerald and gold, then sets them at web scale.

The app is built for one older reader who speaks rather than types: few, big things per screen, high contrast, every type role above Material defaults and multiplied by a 1.15 default text scale (user options 100, 115, 130, 150%). Arabic right-to-left is first; English is available. Calm and premium, not "default app": no grey app bars, no cool greys, soft rounded cards on a warm ground, and one breathing emerald Talk button as the only thing that moves at rest.

The web page keeps the app's palette and corners but trades cards for editorial rules: hairline dividers, one 2px gold rule, a deep emerald shelf carrying five real phone screens. It opens in English (IBM Plex) and switches whole-page to Arabic (El Messiri headlines, Noto Naskh Arabic body, RTL).

**Key Characteristics:**
- Warm ivory ground, ink-brown text, deep emerald fields, gold as the single accent.
- Large type, generous line height, 48dp minimum touch targets (Talk 70dp, Done 54dp).
- Generous rounding (8 to 32px), pills for status, hairline borders over shadows.
- Arabic-first in the app; a full Arabic/English switch on the web.

## Colors

Warm, low-chroma neutrals carrying one deep emerald voice and a thread of gold; light and dark schemes are both defined.

### Primary
- **Notebook Emerald** (emerald): primary actions, links, selected states, the web download button and logo tile.
- **Deep Binding Emerald** (emerald-deep): start of the app's hero gradient; the web shelf, footer, hover state and `theme-color`.
- **Lit Emerald** (emerald-bright): end of the hero gradient (deep to bright, linear). **Hero Glow** (hero-glow) is the Talk button's breathing halo only.
- **Mint Wash** (mint / on-mint): primary container, selected choice rows, web step numerals and text selection.

### Secondary
- **Thread Gold** (gold): the single accent. App: progress ring (light gold `#F3D99B` on the hero), brand gold. Web: the 2px rule over the "kept" rows and the focus ring. As text it darkens to **Gold Ink** (gold-text) for contrast.

### Tertiary
- **Terracotta** (terracotta): the "Important" flag pill only.

### Neutral
- **Ivory Ground** (ivory-ground): every screen and page background. **Paper** (paper): low containers, the web "kept" band and the "You'll need" note. **Surface** white: cards and dialogs.
- **Ink** (ink) for text; **Muted Ink** (ink-muted app, ink-muted-web on the page) for secondary text.
- **Outline / Outline Variant / Web Rule**: hairline card borders and section dividers.
- **Status set** (success, status-*, error): one hue per task status, always shown with its word.
- **Dark scheme**: dark-ground, dark-surface, dark-ink, dark-emerald, dark-gold, dark-success, darker hero gradient (dark-hero-start to dark-hero-end). Tints double their alpha in dark (12% to 22%).

### Named Rules
**The One Thread Rule.** Gold is a thread, not a fill: rules, rings, focus and small labels. Never a button or a large field.

**The Emerald Field Rule.** Large coloured areas are emerald only: the hero gradient in the app, flat emerald-deep on the web, always with white text.

**The Never Colour Alone Rule.** Every status colour sits in a pill with its name; every meta chip carries an icon with a content description.

## Typography

**App Font:** IBM Plex Sans Arabic (bundled: 400, 500, 600, 700)
**Web, English:** IBM Plex Sans (self-hosted woff2, 400/600/700)
**Web, Arabic:** El Messiri (headlines), Noto Naskh Arabic (body and quoted speech)

**Character:** a clear business sans for the app; on the web Arabic gets a calligraphic display face and a book Naskh so the Arabic mode reads as its own voice, not a fallback.

### Hierarchy
- **Display** (700, 34/44sp; headlineLarge 31, headlineMedium 28): large moments.
- **Headline** (700, 26/36sp): inner screen titles on the ground.
- **Title** (700, 23/32sp): Talk label, dialog titles, greeting. **Title Medium** (600, 20/29sp): task titles, section headers.
- **Body** (400, 19/29sp; medium 17/25, small 15/22). **BigText** 22/33sp for the spoken transcript.
- **Label** (600, 17/24sp; medium 500 15/21sp, small 13/18sp): pills, chips, buttons.
- **Web Display** (700, clamp 36 to 56px, 1.06, -0.02em, max 21ch, two lines; Arabic clamp 30 to 50px, 30ch; 30px under 640px, Arabic 28px). **Web Headline** (700, clamp 32 to 46px, 1.1). **Web Body** 18px/1.6, measure 44 to 60ch. **Spoken examples** in Naskh 25px/1.6, always RTL.

### Named Rules
**The Big Print Rule.** No app role is set at Material's default size; the 1.15 text scale is applied on top through density, so new screens use theme roles, never raw sp.

**The Two Scripts Rule.** In Arabic mode body grows to 20px/1.8, captions and steps grow by 2px, headings loosen to 1.35 to 1.4 line height and letter-spacing returns to 0. Never track Arabic.

## Layout

App: single column, 16dp card padding, 5dp vertical gaps between cards, 6 to 8dp chip gaps, section headers 20dp above. Inner screens put a large title straight on the ground with a 52dp back button. The Talk button floats bottom-centre over content.

Web: `min(1200px, 100% - 48px)` container. First viewport: headline left, download block right, then the emerald shelf of five phones (216px each, alternate phones dropped 36px). Sections 104px vertical (72px under 960px). Two-column grids (1fr / 1.2 to 1.6fr) collapse to one under 960px; the shelf becomes a sideways snap-scroller (phones min(62vw, 240px)). Under 640px nav links hide except the language toggle and the download button goes full width. RTL mirrors via logical properties (`margin-inline`, `text-align: end`).

## Elevation & Depth

Mostly flat with tonal layering: ivory ground, white or paper surfaces, hairline borders. Shadow is reserved for things you act on or hold.

### Shadow Vocabulary
- **Card hush** (1dp Material elevation + 1px outline-variant border): every app card.
- **Talk lift** (12dp, emerald-tinted ambient and spot): the Talk button only, plus its breathing hero-glow halo.
- **Download lift** (`box-shadow: 0 10px 24px -14px rgba(10,74,63,.7)`): web primary button.
- **Phone hold** (`box-shadow: 0 30px 50px -26px rgba(0,0,0,.75), 0 10px 18px -12px rgba(0,0,0,.5)`): phone frames on the shelf.

**The Hairline First Rule.** Separate with a 1px rule or a tonal step before reaching for a shadow.

## Shapes

Generous, consistent rounding: 8 (extra small, chips 10), 12, 16 (rows, text fields, choice rows), 18 (medium; web buttons and note), 22 (cards), 24 to 26 (hero blocks), 28 (dialogs), 32 (phone frames). Pills (50%/999px) for status, category, Talk and the language toggle. Icon tiles are rounded squares at 32% of their size. Section headers carry a 4x20dp rounded emerald bar. Circles for Done, progress ring, step numerals and empty-state halos. The hero carries three faint white circles (5 to 7% alpha).

## Components

### Buttons
- **Talk (signature):** wide pill, hero gradient, white 52dp mic disc, Title label, min height 70dp. Breathes: halo alpha 0.25 to 0.55 and scale 1 to 1.06 over 1400ms linear, reversing.
- **Download (web):** emerald, 18px radius, 56px min height, Plex/Messiri 600 19px, arrow-down icon after the label. Hover: emerald-deep, lift 1px over 0.2s `cubic-bezier(0.16, 1, 0.3, 1)`. Focus: 2px gold outline, 3px offset.
- **Done:** 54dp circle, 2.5dp success ring at 55%, faint success tint, check icon.
- **Dialog actions:** text buttons; destructive confirm in error colour.

### Chips
- **Pill:** status or category hue at 14% (26% dark) behind text in that hue, optional 16dp icon. **Meta chip:** surface-container, 10dp radius, icon plus muted label.

### Cards / Containers
- **App card:** white, 22dp, 1px outline-variant, 1dp shadow, 16dp padding. **Banner:** a card tinted in its colour with a filled icon tile and a chevron.
- **Hero (Today):** 24dp gradient block with greeting, date, next task, gold progress ring and frosted quick actions (white 14%, 18dp, min 48dp).

### Inputs / Fields
- Outlined text field, 16dp radius, Title Medium text, inside a 28dp dialog. Typing is the exception; choice dialogs list options as 16dp rows (mint when selected) with radio buttons.

### Navigation
- **App:** large title on the ground with back arrow; settings rows are tinted icon tiles, title, value and chevron.
- **Web bar:** logo tile (emerald, 10px radius, white mic) and name; muted nav links; the language toggle, a hairline pill labelled in the other language (العربية / English) set in that language's font.

### Language Switch (web)
Each translatable node carries `data-ar`; the English is captured at load. Switching sets `lang` and `dir`, swaps content, the toggle label and the page title, and remembers the choice (`?lang=ar` or localStorage). Font tokens flip under `html[lang="ar"]`.

### Phone Shelf (web signature)
Five real screenshots in 32px black frames on the emerald-deep shelf, captions (display 19px, on-deep-muted 15px) below. On load they rise 48px once, 0.8s, staggered 60ms; none under reduced motion.

## Do's and Don'ts

### Do:
- **Do** set every screen on ivory-ground (dark-ground in dark) and every card on white with a hairline border.
- **Do** use theme type roles so the 1.15 text scale and user scale reach new text.
- **Do** keep touch targets at 48dp or more, and pair every status colour with its word.
- **Do** switch fonts with the language on the web: Plex for English, El Messiri and Noto Naskh Arabic for Arabic.
- **Do** honour reduced motion on the web: no rise, no transitions, no smooth scroll.

### Don't:
- **Don't** use a grey Material app bar; titles sit on the warm ground.
- **Don't** fill buttons or large fields with gold; it is a thread.
- **Don't** introduce cool greys or pure black text; ink is #1D1B17.
- **Don't** apply negative letter-spacing to Arabic.
- **Don't** add a second moving element at rest beside the Talk button.
