---
name: NeuroShield Mobility
colors:
  surface: '#f8f9ff'
  surface-dim: '#ccdbf3'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e6eeff'
  surface-container-high: '#dce9ff'
  surface-container-highest: '#d5e3fc'
  on-surface: '#0d1c2e'
  on-surface-variant: '#434751'
  inverse-surface: '#233144'
  inverse-on-surface: '#eaf1ff'
  outline: '#737783'
  outline-variant: '#c3c6d3'
  surface-tint: '#2c5cae'
  primary: '#00387e'
  on-primary: '#ffffff'
  primary-container: '#1a4fa0'
  on-primary-container: '#abc5ff'
  inverse-primary: '#aec6ff'
  secondary: '#006a63'
  on-secondary: '#ffffff'
  secondary-container: '#99efe5'
  on-secondary-container: '#006f67'
  tertiary: '#6f1f00'
  on-tertiary: '#ffffff'
  tertiary-container: '#962d00'
  on-tertiary-container: '#ffb39a'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d8e2ff'
  primary-fixed-dim: '#aec6ff'
  on-primary-fixed: '#001a42'
  on-primary-fixed-variant: '#024394'
  secondary-fixed: '#9cf2e8'
  secondary-fixed-dim: '#80d5cb'
  on-secondary-fixed: '#00201d'
  on-secondary-fixed-variant: '#00504a'
  tertiary-fixed: '#ffdbd0'
  tertiary-fixed-dim: '#ffb59d'
  on-tertiary-fixed: '#390c00'
  on-tertiary-fixed-variant: '#832600'
  background: '#f8f9ff'
  on-background: '#0d1c2e'
  surface-variant: '#d5e3fc'
typography:
  headline-xl:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 48px
  headline-xl-mobile:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 40px
  headline-lg:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 38px
  headline-md:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 34px
  headline-sm:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 30px
  body-xl:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 20px
    fontWeight: '400'
    lineHeight: 32px
  body-lg:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 26px
  label-xl:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 18px
    fontWeight: '700'
    lineHeight: 24px
  label-lg:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 16px
    fontWeight: '700'
    lineHeight: 22px
  label-md:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-tablet: 1.5rem
  margin: 1.25rem
  margin-tablet: 2rem
  margin-desktop: 3rem
  space-xs: 0.5rem
  space-sm: 0.75rem
  space-md: 1.25rem
  space-lg: 1.75rem
  space-xl: 2.5rem
---

## Brand & Style

This design system establishes an empathetic, clinically rigorous, and stabilizing interface crafted specifically for individuals living with Parkinson's disease, their occupational therapists, and caregivers. The visual language reconciles medical-grade reliability with dignified accessibility. It avoids clinical coldness while steering clear of patronizing oversimplification.

The core design style merges **Modern High-Contrast Functionalism** with **Tactile Spatial Definition**:
- **Cognitive Steadiness:** High visual quietude with strict color-coded semantic anchors to reduce cognitive fatigue during tremors or "off" periods.
- **Physical Accommodation:** Oversized hit areas, intentional physical boundaries, and distinct edge definitions compensate for motor fluctuations, hypometria (undershooting movements), and action tremors.
- **Emotional Tone:** Authoritative, reassuring, and dignified. The visual hierarchy communicates safety, biological preservation, and progressive physical capability rather than decline.

## Colors

The palette is engineered to meet strict WCAG AAA contrast guidelines (minimum 7:1 for vital clinical information and 4.5:1 for interactive states), countering the reduced contrast sensitivity common in neurological aging.

### Color Roles & Rationale
- **Primary (`#1A4FA0` — Deep Calm Medical Blue):** Anchor for navigation headers, primary physical actions, confirmation states, and brand trust. Yields a contrast ratio >8:1 against `#F4F6F9`.
- **Secondary (`#0F766E` — Therapeutic Sage Teal):** Represents muscle rehabilitation metrics, cadence completion, and active rhythm alignment. Signals steady, sustained therapeutic progress without visual alarm.
- **Tertiary (`#C2410C` — High-Visibility Safety Ochre/Rust):** Reserved strictly for fall risk warnings, freezing-of-gait flags, and missed medication critical thresholds. Chosen over pure blinding red to minimize cardiovascular anxiety while preserving rapid visual acquisition.
- **Neutral Canvas (`#F4F6F9` — Cool Off-White Clinical Tint):** Replaces glaring pure white backgrounds to eliminate photophobia and optical glare.
- **Surface Elevation (`#FFFFFF` — Pure White):** Used exclusively for interactive functional cards and dialog containers to ensure distinct separation from the canvas.
- **Surface Boundaries (`#94A3B8` / `#334155`):** Structural borders on white cards maintain clear visual separation even on washed-out displays or under bright indoor lighting.
- **Text Primary (`#0F172A` — Deep Slate):** Delivers a near 15:1 contrast against pure white cards, optimizing scan speed for aging eyes.

## Typography

Typography is built around **Atkinson Hyperlegible Next**, designed explicitly for vision impairment and low optical clarity. Its distinct glyph shapes prevent common character confusion (such as lowercase `l` vs. numeral `1` vs. uppercase `I`, or `O` vs. `0`), which is critical when seniors interpret medication milligram doses or motor therapy step counts.

### Key Rules
- **Minimum Type Floor:** No UI text is smaller than 14px (`label-md`). Instructional text and body content never drop below 16px (`body-md`), with 18px (`body-lg`) as the standard default for general reading.
- **Generous Leading:** Line height is intentionally scaled up by 35%–45% across all levels to prevent adjacent line visual tracking collisions during saccadic eye movements.
- **No Decorative Letter Spacing:** Tracking is kept natural or widened slightly for capital letters; tight tracking is explicitly forbidden.
- **Weight Contrast:** Primarily relies on Medium (500), SemiBold (600), and Bold (700) weights to prevent glyph stems from fading against light backgrounds.

## Layout & Spacing

The layout philosophy follows a **Single-Axis Guardrail Grid**. Layouts prioritize vertical, predictable single-column flows on mobile displays to eliminate dual-axis cognitive clutter and prevent accidental edge taps.

### Layout Grid & Adaptation
- **Mobile (Handheld Android):** 4-column layout with 20px (`1.25rem`) side margins and 16px (`1rem`) gutters. Content cards generally span all 4 columns to maximize the horizontal strike surface.
- **Tablet / Mounted Stand (Rehab Mode):** 8-column layout with 32px (`2rem`) side margins. Actions split into dual-hand accessibility zones, keeping buttons out of unreachable screen centers.
- **Vertical Rhythm:** Components adhere to strict 8dp baseline intervals. Vertical spacing between distinct operational cards uses `space-lg` (28px) to visually buffer tap targets and reduce accidental multi-touch triggers.
- **Tremor Buffering:** Every interactive target maintains an empty clearance buffer of at least 12px around its outer boundary.

## Elevation & Depth

Elevation for motor rehabilitation relies on **Crisp Boundary Containment** combined with **Low-Diffusion Contact Shadows**. Parkinson’s visual processing struggles with ambiguous floating surfaces or soft blurred edges, demanding unambiguous physical definition.

### Depth Mechanics
- **Ground Floor Canvas (`#F4F6F9`):** Completely flat base plane representing passive context.
- **Resting Interactive Surface (Elevation Level 1):** Solid `#FFFFFF` card containing a mandatory 1.5px solid border (`#CBD5E1`) and a direct contact shadow: `0px 2px 4px rgba(15, 23, 42, 0.08)`.
- **Active Cueing & Critical Alert Containers (Elevation Level 2):** Elevated for immediate auditory/visual feedback: 2px solid border (`#1A4FA0` or `#C2410C`), backed by `0px 6px 12px rgba(26, 79, 160, 0.14)`.
- **Modals & Emergency Disclosures (Elevation Level 3):** Grounded over a 60% opacity deep neutral backdrop (`#0F172A`) with high physical isolation: 2px border in `#475569` and `0px 12px 28px rgba(15, 23, 42, 0.22)`.

## Shapes

The design system employs **Roundedness Level 2** (8px base corner radius on buttons, 16px on structural clinical cards). 

### Rationale
- Completely sharp corners (`0`) create harsh visual friction and read as intimidating clinical diagnostic machinery.
- Ultra-round pill shapes (`3`) degrade button perimeter definitions, reducing the perceived strike surface for users experiencing hand tremors.
- Controlled 16px corners create friendly, modern enclosures that still retain clear geometric bounds, helping users visually register touch boundaries.

## Components

### 1. Primary & Secondary Buttons
- **Touch Target Dimensions:** Minimum height of **60dp** for standard actions and **72dp–80dp** for critical actions (such as starting a cadence session or acknowledging a fall status).
- **Primary:** Solid `#1A4FA0` fill, white `#FFFFFF` text, bold `label-xl` type, 12px border radius. Minimum horizontal padding of 24px.
- **Secondary / Safety Confirmation:** Pure white `#FFFFFF` fill, 2px solid border in `#1A4FA0`, text in `#1A4FA0`.
- **Touch States:** Press states transition to a darker `#133E7C` with a simultaneous 30ms haptic tick on Android, providing immediate physical confirmation to counter reduced tactile sensation.

### 2. Clinical Staging Badges (e.g., Hoehn & Yahr Stage 2)
- **Container:** High-contrast pill capsule with a solid 1.5px border.
- **Coloration:** Soft slate-blue tint background (`#E2E8F0`), deep `#0F172A` text, paired with a saturated status dot indicator (8dp diameter).
- **Sizing:** Minimum height of 36dp with 14px horizontal padding, using `label-md` or `label-lg` for clear legibility.

### 3. Auditory Rhythm Cueing Visual Widgets
- **Purpose:** Synchronizes gait pacing to prevent motor freezing (Freezing of Gait - FoG).
- **Structure:** Central circular metronome pacing element minimum 120dp x 120dp.
- **Visual Mechanics:** Uses high-contrast concentric pulsing rings in `#0F766E` (Therapeutic Teal). Expanding rings pair with dynamic textual beat markers ("STEP - 1", "STEP - 2") in `headline-lg`. A high-contrast visual toggle allows users to easily swap between visual-only, audio-click, and rhythmic chime modes.

### 4. Fall Risk Safety Tags
- **Appearance:** Solid `#FFF7ED` container bounded by a 2px `#C2410C` high-contrast stroke.
- **Content:** Bold warning triangle icon paired with unambiguous, non-punitive clinical text: `FALL RISK: ELEVATED (ASSISTANCE RECOMMENDED)`.
- **Sizing:** Minimum text size of 16px bold (`label-lg`), with a 12dp internal safety clearance buffer.

### 5. Cards & Clinical Containers
- **Visuals:** `#FFFFFF` background, 16px rounded corners, 1.5px border in `#CBD5E1`.
- **Padding:** Minimum internal padding of 20dp (`space-md` to `space-lg`).
- **Dividers:** Thick 1.5px dividers in `#E2E8F0` with at least 16dp spacing to keep individual data rows distinct.

### 6. Form Controls (Checkboxes, Switches, Radio Buttons)
- **Selection Targets:** Checkbox and radio hit areas measure at least **56dp x 56dp**, with visible glyph boxes sized at **28dp x 28dp** (well above standard 18dp Android patterns).
- **Check Indicator:** Bold 3.5px checkmark stroke ensuring clear state visibility.
- **Switches:** Oversized 40dp toggle thumb set inside a 48dp track with explicit inner text labels (`ON` / `OFF`).

### 7. Senior-Friendly Android Navigation
- **Bottom Navigation Bar:** Height of **76dp** (surpassing the standard 56dp). Accommodates 3 to 4 destinations maximum (e.g., *Today's Exercises*, *Cadence Cue*, *My Progress*, *Help*).
- **Navigation Items:** Dual-modality icons (32dp size) paired with permanent text labels in `label-md` (14px bold). Never rely on icons alone.
- **Emergency / Assistance Action:** Fixed-position bottom sheet or persistent floating pill button (minimum 64dp height) styled in `#C2410C` with high-contrast text: `CALL ASSISTANCE`.