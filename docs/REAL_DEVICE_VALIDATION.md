# Real-Device Tuner Validation

This checklist validates tuning behavior on actual microphones and strings (outside synthetic unit tests).

## Scope

- Device microphone capture path
- Background noise resilience
- Realtime vs Precision mode tradeoff
- Cent-stability while sustaining a pluck

## Setup

- Quiet room first, then moderate-noise room
- At least 2 Android devices with different microphones
- One stable reference source:
  - Lab/function tone generator, or
  - Calibrated tuner app / reference instrument

## Procedure

1. Open `Tuner` tab and grant microphone permission.
2. In `Precision` mode, play steady reference tones at:
  - `110 Hz`, `220 Hz`, `440 Hz`, `659.255 Hz`.
3. Hold each tone for ~3 seconds and record:
  - displayed frequency,
  - cent deviation drift (max absolute),
  - confidence.
4. Repeat in `Realtime` mode and compare lock speed vs stability.
5. Repeat steps 2-4 while adding moderate background noise.
6. Validate with real kora plucks:
  - one string at a time,
  - soft and hard pluck,
  - short and sustained decay.

## Acceptance targets

- Precision mode:
  - settles to stable reading within 1 second for sustained tones,
  - cent drift remains low and practical for tuning workflow,
  - expected to outperform Realtime mode on stability.
- Realtime mode:
  - faster response than Precision mode,
  - acceptable short-window guidance for on-the-fly checks.

Note: strict ±0.1 cent behavior is currently guaranteed by synthetic detector tests, while real-mic behavior depends on device hardware, room noise, and pluck consistency.
