# Data Privacy Inventory

This inventory reflects the current codebase review. Re-check it before every store submission and whenever microphone, score import/export, analytics, account, or network behavior changes.

| Data item | Why needed | Stored where | Retention | Avoidable? | Third parties |
| --- | --- | --- | --- | --- | --- |
| Microphone audio samples | Live pitch detection for tuner | Processed in memory only | Not retained | Required only when tuner is used | None |
| Instrument tuning profile | Restore the user's kora setup between sessions | Local DataStore preferences | Until app data is cleared/uninstalled or overwritten | No, it is core app functionality | None |
| Custom preset display name | Lets the user identify saved tunings | Local DataStore preferences | Until user deletes preset or clears app data | Optional; user controls creation | None |
| App theme/language preference | Restore user settings | Local DataStore preferences / platform locale setting | Until changed or app data is cleared | Optional but expected app behavior | None |
| Imported MusicXML/MXL/MIDI/PDF bytes | Convert selected scores to kora notation | Memory during processing | Not intentionally retained | Required only when notation import is used | None |
| Generated MIDI/WAV/PDF exports | User-requested export/share | Temporary app cache, then shared by explicit user action | OS-managed cache lifetime unless user saves/share target keeps copy | Optional | Only share targets chosen by user |
| Crash logs / diagnostics | Not collected by app code | Not applicable | Not applicable | Yes | None |
| Analytics / ads / tracking IDs | Not collected by app code | Not applicable | Not applicable | Yes | None |
| Account, email, payment, location, contacts | Not collected by app code | Not applicable | Not applicable | Yes | None |

## Store Declaration Notes

- Google Play Data safety should reflect no off-device collection by the app code reviewed here.
- Microphone access should be disclosed as on-device processing for app functionality.
- User-selected file processing should be described as local processing. If a future implementation uploads files for OCR, analytics, support, or crash reporting, update this inventory, the privacy policy, and store declarations before release.
- Generated exports shared with another app are user-initiated transfers; the receiving app controls its own copy.
