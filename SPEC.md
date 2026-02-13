Leokinder2k Chromatic Kora Authority
Executable Specification
1. Product Purpose

This application is a professional authority tool for chromatic kora players.
It provides tuning calculation, lever configuration, peg adjustment suggestions, and a high-precision live tuner.

Default reference state:
All levers OPEN (E major system).
Concert pitch: A = 440 Hz.

2. Feature: Instrument Configuration

Goal:
Allow users to define their kora layout.

Behavior:

User can choose 21 or 22 strings.

User defines open (natural) tuning for each string.

App automatically calculates closed pitch (+1 semitone).

User can save instrument profile.

3. Feature: Scale Calculation Engine

Goal:
When user selects a key and scale, app calculates required lever and peg adjustments.

Behavior:

User selects:

Root note

Scale type

Engine calculates:

Lever-only version

Peg-correct version

Engine preserves traditional string roles.

Engine avoids unnatural string reassignment.

If voicing conflict occurs:

Suggest alternative voicing.

Output:

Full string table (L/R low → high)

Lever state per string

Peg retune requirement indicator

4. Feature: Guided Setup Mode

Goal:
Guide user step-by-step through tuning adjustments.

Behavior:

App shows one string at a time.

Displays:

String ID

Target pitch

Lever state

Peg adjustment (if required)

Shows completion progress.

5. Feature: Instant Overview Mode

Goal:
Display full tuning layout at once.

Behavior:

Show:

All strings

Lever state

Peg retune indicator

Toggle between:

Visual kora diagram

Professional table view

6. Feature: Live Tuner

Goal:
Provide high-precision tuning.

Behavior:

Detect pitch with ±0.1 cent sensitivity.

Display:

Detected pitch

Closest string match

Target pitch

Cent deviation

Lever state requirement

Peg adjustment requirement

7. Feature: Traditional Presets

Goal:
Provide built-in traditional kora tunings.

Behavior:

User selects preset.

App loads predefined tuning configuration.

Works with 21/22 string setups.