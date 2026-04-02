Bridge reference upload location.

Put bridge source images here before the bridge redraw task:

- `Images/bridge-reference/photo-01.jpg`
- `Images/bridge-reference/photo-02.jpg`
- `Images/bridge-reference/sketch-01.jpg`
- `Images/bridge-reference/sketch-02.jpg`

Use close, well-lit shots of the center bridge from the front if possible.

Notes:

- The interactive kora bridge is currently drawn in code in:
  - `app/src/main/java/com/leokinder2k/koratuningcompanion/scaleengine/ui/InstantOverviewScreen.kt`
- The same `drawKoraBody()` renderer is used across the main interactive kora views, so one bridge fix will apply to both.
- After images are added here, Claude can review/reference them and Codex can implement the redraw/update.
