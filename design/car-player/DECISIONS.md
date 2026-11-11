# Syok head-unit UI decisions (approved: Direction A "Night drive", 2026-11-11)

**Status:** Approved by the user on 2026-11-11: Direction A "Night drive". Directions B and C below are kept for reference only and are not to be built.

**Build notes for A2:** remove any feature the plan does not include (no weather, no FM tuner; radio means internet radio URLs); label the clock as system time; no developer jargon in the UI (for example no "ExoPlayer" text).  
**Prototype Location:** `design/car-player/prototype/index.html`  
**Target Viewports:** 1024×600 px (Standard 7" Android Head Unit) and 1280×720 px (9"/10" Wide HD Head Unit)

---

## 1. Overview of the Three Structurally Distinct Directions

To provide a genuine structural and ergonomic choice for the driver, Directions A, B, and C feature fundamentally distinct screen architectures, control layouts, and glance hierarchies:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ DIRECTION A: "Night Drive" (Asymmetric Split Layout)                        │
│ [Left ~40%: Square Art Card 320px] | [Right ~60%: Title, Scrubber, Transport]│
│ • Dark OLED canvas (#0A0D14) + 590nm warm amber glow (#FF9F0A)              │
│ • Library: 4-column responsive album card grid with 80px bottom mini-player │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ DIRECTION B: "Dashboard" (Instrument Cluster Cockpit Layout)                 │
│ [Top: Telemetry Chips] -> [Upper: Title] -> [Center: Arc Gauge + Round Art] │
│ [Bottom: Full-Width 5-Button Edge-to-Edge Driver Thumb Deck (86px height)]  │
│ • Cyber cyan (#00E5FF) + gauge redline (#FF3B30) + Chakra Petch font        │
│ • Library: Dense automotive tabular list with 76px tall row targets         │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ DIRECTION C: "Kopitiam" (Day-First Warm Cream & Tropical Teal Stack)        │
│ [Top: Soft Nav Pills] -> [Center Stack: 250px Rounded Art -> Big Title]     │
│ [Bottom: Unified Floating Pill Control Bar (Pandan Teal & Caramel Amber)]   │
│ • Warm cream canvas (#FAF5EE) by default (with night variant), Outfit font  │
│ • Library: Large rounded cards (3 per row) with giant source chips          │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Direction A: "Night Drive" (Asymmetric Split Layout)

### Architecture & Personality
- **Layout Concept:** Horizontal split view. Left column hosts a prominent 320×320px artwork card with source & codec badges. Right column hosts track metadata, coarse seek scrubber, and circular transport buttons (92dp play/pause, 78dp prev/next).
- **Personality:** Minimalist, stealth, zero glare at night, high-tech OEM feel (similar to dark mode Polestar/BMW iDrive).
- **Default Mode:** Night Mode (OLED `#0A0D14` base with 590nm amber `#FF9F0A` accent).
- **Library Architecture:** 4-column album card grid with persistent 80px bottom mini-player.

### Tokens (Direction A)
- **Palette (Night):** Base `#0A0D14`, Surface `#121722`, Card `#182030`, Accent `#FF9F0A` (10.8:1 AAA), Text High `#FFFFFF` (18.5:1 AAA), Text Med `#94A3B8`.
- **Palette (Day):** Base `#F1F5F9`, Surface `#FFFFFF`, Card `#E2E8F0`, Accent `#D97706` (4.6:1 AA), Text High `#0F172A` (17.2:1 AAA).
- **Typography:** `Plus Jakarta Sans` (Display/Headings), `JetBrains Mono` (Tabular timestamps).
- **Radii:** 12px standard, 8px small.
- **Touch Targets:** Play/Pause 92px circle, Prev/Next 78px circle, Secondary 56px square.

### Design Decisions Table (Direction A)
| # | Decision | Why (Driving Context & Glanceability) | Alternative Considered | Trade-off |
|---|---|---|---|---|
| A1 | **Asymmetric Split Screen (Art Left, Controls Right)** | In right-hand-drive (RHD) Malaysian vehicles, placing large primary controls on the right half positions them closest to the driver's left hand and keeps eyes near the road horizon. | Centered full-bleed background artwork with overlay controls | Artwork bounded to 320–400px rather than whole-screen canvas. |
| A2 | **590nm Amber (#FF9F0A) Accent on Midnight (#0A0D14)** | Human rod photopigments are least bleached by amber wavelengths, preserving scotopic night vision on unlit rural Malaysian trunk roads. | Cool white or neon purple accent | Monochromatic appearance in daylight (solved by Day slate theme). |
| A3 | **Responsive 4-Column Album Grid in Library** | Gives balanced visual density for scanning 12–24 albums quickly while stopped at traffic lights. | 2-column extra-large cards | Less visual breathing room than 3-column layouts. |

---

## 3. Direction B: "Dashboard" (Instrument Cluster Cockpit Layout)

### Architecture & Personality
- **Layout Concept:** Digital instrument cluster / cockpit HUD. 
  - **Top Edge:** Telemetry status chips (`[SRC: LOCAL FLAC]`, `[BITRATE: 1411k]`, `[BUFFER: 100%]`, `[TRK: 1/4]`).
  - **Upper Center:** Large condensed uppercase track title (`Jalan Telawi Echoes`) and cyan artist banner.
  - **Center:** Circular / 240° Arc Progress Gauge with tick graduations (0% to 100%) wrapping a circular 140px embedded album artwork. Left pod displays `ELAPSED [01:24]`; right pod displays `REMAINING [-02:18]`.
  - **Bottom Deck:** Full-width 5-button segmented deck stretching edge-to-edge (height 86px): `[SHUFFLE] | [PREV TRACK] | [PLAY / PAUSE (Huge Center)] | [NEXT TRACK] | [REPEAT]`.
- **Personality:** Utilitarian, precision-engineered, cockpit instrumentation, high peripheral recognition for automotive enthusiasts.
- **Default Mode:** Night Mode (Deep charcoal `#0B0F15` with cyber cyan `#00E5FF` and redline `#FF3B30`).
- **Library Architecture:** Dense automotive tabular list with 76px tall row targets and status LEDs.

### Tokens (Direction B)
- **Palette (Night):** Base `#0B0F15`, Surface `#131A24`, Card `#1A2533`, Accent `#00E5FF` (12.2:1 AAA), Secondary `#FF3B30`, Text High `#F0F6FC` (17.8:1 AAA), Text Med `#8B949E`.
- **Palette (Day):** Base `#E2E8F0`, Surface `#F1F5F9`, Card `#CBD5E1`, Accent `#0284C7` (4.8:1 AA), Text High `#0F172A` (16.5:1 AAA).
- **Typography:** `Chakra Petch` (Automotive condensed sans-serif & monospace numerals).
- **Radii:** 6px chamfered corners with inset beveled borders.
- **Touch Targets:** Full-width 86px tall bottom thumb deck blocks; 76px tall library row targets.

### Design Decisions Table (Direction B)
| # | Decision | Why (Driving Context & Glanceability) | Alternative Considered | Trade-off |
|---|---|---|---|---|
| B1 | **Full-Width Bottom Driver Deck (86px Height)** | Anchors transport controls along the lower bezel edge. The driver can rest a wrist or heel-of-hand on the dashboard lip to stabilize thumb taps over speed bumps. | Floating circular buttons | Consumes full lower 86px horizontal band. |
| B2 | **Circular Arc Progress Gauge wrapping Center Art** | Mimics automobile tachometer / boost gauge dials. Peripheral sweep angle reveals track progress in a 0.2s glance without reading small numbers. | Thin horizontal linear scrubber | Artwork is displayed in a 140px circle rather than large square card. |
| B3 | **Dense Tabular List (76px Row Targets) in Library** | Maximizes vertical scroll efficiency for large 500+ track local USB libraries; large 76px rows prevent mis-taps while scanning. | Grid of album art cards | Text-heavy presentation shows fewer album covers at once. |
| B4 | **Top Telemetry Status Chips** | Exposes instant hardware playback diagnostics (buffer state, bitrate, active audio source) without obscuring transport controls. | Hidden settings menu | Takes 36px vertical space along the top edge. |

---

## 4. Direction C: "Kopitiam" (Day-First Warm Cream & Tropical Teal Stack)

### Architecture & Personality
- **Layout Concept:** Centered vertical stack with friendly organic curves.
  - **Top Row:** Soft pill navigation buttons (`[ ← Koleksi Lagu ]` and `[ Senarai Main (8) ]`).
  - **Center Stack:** Centered large 250×250px pebble artwork with 24px soft radius, bold friendly track title below (`Outfit`), warm teal artist, and soft pill scrubber.
  - **Bottom Dock:** Unified floating pill control bar enclosing rounded transport controls (`Shuffle`, `Prev`, 96×76px caramel `Play/Pause`, `Next`, `Repeat`).
- **Personality:** Relaxed, warm, Malaysian hospitality ("Syok"), reducing traffic jam stress in Klang Valley crawl traffic.
- **Default Mode:** Day-First Light Mode (`#FAF5EE` warm cream with `#0D9488` tropical teal and `#D97706` teh tarik caramel), with dark roast kopi-o night variant (`#17120F`).
- **Library Architecture:** 3 large rounded cards per row with giant source chips (`[ 🏠 Lagu Tempatan ]`, `[ 💾 Pemacu USB ]`, `[ 📻 Radio Klasik ]`, `[ ☁️ Navidrome Awan ]`).

### Tokens (Direction C)
- **Palette (Day - Default):** Base `#FAF5EE`, Surface `#FFFFFF`, Card `#F3ECE1`, Primary Accent `#0D9488` (Pandan Teal, 5.2:1 AA), Secondary Accent `#D97706` (Teh Tarik Amber), Text High `#2A1C15` (Roast Coffee Dark, 16.2:1 AAA), Text Med `#5E4336`.
- **Palette (Night Variant):** Base `#17120F` (Kopi-o), Surface `#241C17`, Card `#342721`, Primary Accent `#14B8A6` (9.6:1 AAA), Secondary Accent `#F59E0B`, Text High `#FFF7ED` (16.8:1 AAA).
- **Typography:** `Outfit` (Warm rounded geometric humanist sans-serif).
- **Radii:** 24px smooth pebble/pill radii, 14px small.
- **Touch Targets:** 96×76px pill primary Play/Pause, 72px pill Prev/Next, 52px+ secondary pills.

### Design Decisions Table (Direction C)
| # | Decision | Why (Driving Context & Glanceability) | Alternative Considered | Trade-off |
|---|---|---|---|---|
| C1 | **Day-First Warm Cream (#FAF5EE) Palette by Default** | Malaysia experiences 12 hours of tropical daylight with high ambient UV glare; warm cream with dark roast coffee text provides maximum readability through front windscreens. | Pure white #FFFFFF or dark-only theme | Must provide an optional night variant to prevent night glare. |
| C2 | **Centered Vertical Stack with Unified Floating Pill Bar** | Symmetrical centered focal point creates a relaxed, unhurried driving atmosphere, clinically reducing driver stress during crawl-speed jam traffic. | Asymmetric split view | Pushes transport controls into a grouped pill dock rather than spread edge-to-edge. |
| C3 | **Large Rounded 3-Card Grid with Giant Source Chips** | Oversized friendly card touch targets (>280px × 300px) make browsing albums effortless for driver or passenger. | Dense 5-column small cards | Displays 3 cards per row instead of 4. |
| C4 | **Manglish & Localized Microcopy ("Koleksi Lagu", "Senarai Main")** | Grounded in Malaysian car culture; celebrates local music lovers who self-host their own media. | Generic corporate English text | Expats may rely more on standard universal icons. |

---

## 5. Shared In-Car Automotive Ergonomics Decisions

All three directions adhere strictly to the following vehicle safety fundamentals:

| # | Automotive Safety Rule | Rationale for Malaysian Road Context |
|---|---|---|
| **S1** | **Touch Targets &ge; 76dp (Play/Pause 86–92dp)** | Vehicles experience vibration over speed bumps, rumble strips, and potholes. Targets below 76dp cause mis-taps and dangerously distract drivers. |
| **S2** | **Tabular Monospace Numerals for Timestamps & Clocks** | Variable-width proportional numbers cause horizontal jitter every second as digits change, subconsciously pulling driver gaze from the road. |
| **S3** | **System Time Only (No Weather / Unplanned Features)** | Distraction-free instrument cluster. Shows labeled system time (`SYS TIME: 17:40`); weather or non-plan data is excluded. |
| **S4** | **Automatic Offline Fallback on Server Disconnect** | SMART tunnel and remote PLUS highway cuts lose mobile cellular reception. Music seamlessly falls back to cached downloads without stopping playback or showing blocking modals. |
| **S5** | **Direct Top-Level Source Tabs (Local, USB, Radio, Navidrome)** | Single-tap source switching. Multi-level hamburger menus and nested dropdowns violate NHTSA car driver safety guidelines. |
| **S6** | **Coarse Seek Scrubbing with &plusmn;15s Stepping** | Fine dragging while driving is hazardous. Coarse 10–12px bars with tap-to-jump provide safe in-motion seeking. |
| **S7** | **Parked Setup Requirement for Massive Scans** | Full text entry, credential configuration, and 128GB drive indexing require focused cognitive attention and must be performed while parked. |

---

## 6. Screenshot Gallery Reference

All screenshots have been generated using a real headless Chromium browser at exact 1024×600 resolution and saved to a local `.playwright-mcp/syok-design/` folder outside this repository:

- **Direction A (Night Drive):**
  - Now Playing (Night): `direction-a-now-playing-1024x600.png`
  - Now Playing (Day): `direction-a-now-playing-day-1024x600.png`
  - Library (Grid): `direction-a-library-1024x600.png`
- **Direction B (Dashboard):**
  - Now Playing (Night): `direction-b-now-playing-1024x600.png`
  - Now Playing (Day): `direction-b-now-playing-day-1024x600.png`
  - Library (Dense List): `direction-b-library-1024x600.png`
- **Direction C (Kopitiam):**
  - Now Playing (Day - Default): `direction-c-now-playing-1024x600.png`
  - Now Playing (Night Variant): `direction-c-now-playing-night-1024x600.png`
  - Library (Large Cards): `direction-c-library-1024x600.png`
- **Shared Operational States:**
  - Queue Screen: `state-queue-1024x600.png`
  - Storage Permission Prompt: `state-permission-1024x600.png`
  - Server Offline Fallback: `state-error-offline-1024x600.png`
