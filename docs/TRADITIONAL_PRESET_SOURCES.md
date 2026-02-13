# Traditional Preset Sources

This project encodes traditional preset note sets and intonation offsets from published kora tuning references.

## Source references

- `kora-music.com` "Kora tuning and scales" (Hardino, Sauta, Tomora Ba/Silaba interval patterns and deviation notes)
- `robertkingsings.com` "Kora tuning systems and intervals"

## Intonation encoding method

We use a concert-F nominal reference and encode per-note cent offsets relative to equal temperament for the scale degrees present in each preset.

Derived templates:

- `Tomora Ba / Silaba`: `F G A Bb C D E` with cents `[0, 0, -15, 0, 0, 0, -15]`
- `Sauta`: `F G A B C D E` with cents `[0, -15, +5, +5, 0, -15, +5]`
- `Hardino`: `F G A Bb C D E` with cents `[0, -15, +5, 0, 0, -15, +5]`

These are applied to all octaves for preset generation in `TraditionalPresets`.
