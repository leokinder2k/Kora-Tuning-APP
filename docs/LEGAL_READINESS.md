# Legal and Regulatory Readiness

Reviewed: 2026-06-10

This checklist is an engineering readiness aid, not legal advice. Have a qualified professional review the final store listing, privacy policy, terms, regional compliance obligations, and commercial sales model before launch.

## Current App Posture

- Native music education and tuning companion for kora.
- No account system found.
- No advertising SDK, analytics SDK, crash reporting SDK, or tracking SDK found.
- No direct payment collection found.
- Android uses `RECORD_AUDIO` for live tuning; microphone audio is processed locally.
- Local tuning settings and user presets are stored in app-private DataStore preferences.
- User-selected notation files are processed locally; exports are shared only through explicit user action.
- Android backups are disabled and data extraction rules exclude local files, databases, and shared preferences.
- Android cleartext traffic is disabled.
- Android file sharing is limited to cache exports through a non-exported `FileProvider`.

## Must Fix Before Launch

- Publish `PRIVACY_POLICY.md` at a public, stable, non-PDF URL controlled by the publisher.
- Update the in-app About -> Privacy Policy URL so it points to the same public policy URL used in stores.
- Provide a current support/contact URL or email controlled by the publisher in each store listing.
- Complete Google Play Data safety declarations to match the final release artifact, not only the source code assumption.
- Complete Apple App Privacy details if the iOS app is submitted.
- Verify final age rating, target audience, and content rating declarations in Google Play Console and App Store Connect.
- Confirm app name, screenshots, store descriptions, support links, and privacy links are consistent across Android, iOS, and desktop distribution pages.

## Should Fix Soon

- Replace GitHub Issues as the primary public support route with a publisher-controlled support email or support page.
- Add a short in-app support/contact entry near the privacy policy link.
- Keep a dated privacy-impact checklist for future changes to microphone behavior, notation imports, exports, analytics, crash reporting, or network access.
- Keep a third-party notices file for open-source licenses and bundled asset/source acknowledgements.
- Version the privacy policy when data practices change.

## Needs Legal Review

- Terms of service or end-user license terms for paid or commercial distribution.
- Refund, cancellation, warranty, and support obligations if sold directly or through marketplaces.
- Copyright and licensing obligations for bundled assets, traditional tuning references, generated exports, documentation, screenshots, and reference images.
- Cultural attribution and permissions for traditional tuning material and language/localisation content.
- Children/family-targeting declarations if marketing or education positioning targets children, schools, or families.
- Regional privacy requirements for every launch territory.
- Store listing claims, accuracy claims, musician endorsement claims, and educational claims.
- Trademark review for app name, icon, package identifiers, and public branding.

## Not Applicable Based On Current Code Review

- Cookie notice: no web cookies or tracking SDKs were found in the native app code.
- Account deletion workflow: no account creation or server-side user account system was found.
- Payment data handling: no payment SDK or direct payment collection was found.
- Medical, legal, financial, or safety disclaimer: the app is a musical tuning/education tool, not a regulated advice product.
- Third-party advertising disclosures: no advertising SDKs were found.
- Location disclosure: no location permission or location API usage was found.
- Contact/calendar/SMS disclosure: no contacts, calendar, SMS, or call-log access was found.

## Privacy Declaration Baseline

Use this as a baseline only after verifying the final release build:

- Microphone audio: accessed only when the tuner is used, processed on-device, not recorded, stored, uploaded, or shared by app code.
- Local settings and presets: stored only on the device for app functionality, retained until edited, cleared, or uninstalled.
- Imported notation files: user-selected, processed locally, not retained intentionally by the app after processing.
- Generated exports: created only by user action, stored in app cache before Android share/export, and then controlled by the user-selected destination.
- Network: no app backend or telemetry endpoint found. If future SDKs or network calls are added, update privacy policy and store declarations before release.

## Official Policy References Checked

- Google Play Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Apple App Privacy details: https://developer.apple.com/app-store/app-privacy-details/
- Apple App Review Guidelines, Privacy: https://developer.apple.com/app-store/review/guidelines/#privacy
