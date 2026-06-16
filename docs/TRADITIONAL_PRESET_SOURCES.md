# Traditional Preset Sources

This project encodes traditional preset note sets and intonation offsets from published kora tuning references.

## Source references

- `kora-music.com` "Kora tuning and scales" (Hardino, Sauta, Tomora Ba/Silaba interval patterns and deviation notes)
- `robertkingsings.com` "Kora tuning systems and intervals"

## Intonation encoding method

We use a concert-F nominal reference and encode per-note cent offsets relative to equal temperament for the scale degrees present in each preset.

Derived templates:

- `Tomora Ba / Silaba`: `F G A Bb C D E` with cents `[0, 0, -15, 0, 0, 0, -15]`
- `Tomora Mesengo`: `F G Ab Bb C D Eb` with cents `[0, +30, +25, 0, 0, +30, +25]`
- `Sauta`: `F G A B C D E` with cents `[0, -15, +5, +5, 0, -15, +5]`
- `Hardino`: `F G A Bb C D E` with cents `[0, -15, +5, 0, 0, -15, +5]`

These are applied to all octaves for preset generation in `TraditionalPresets`.

For 22-string layouts, the extra bass string is on the right side and is tuned a fourth above the left bass string. In the concert-F reference layout, that extra string is `Bb2` even when the regular Sauta fourth-degree strings are raised to `B`.

For 21- and 22-string layouts, preset rows follow physical tuning order from the bass: left bass, right bass, then the next left-side bass strings before continuing through the left/right bridge pattern. The left/right side helpers still expose each side low-to-high.
