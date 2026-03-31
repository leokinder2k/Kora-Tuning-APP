"""
Discards all empty draft releases across all tracks.
"""
import sys
from google.oauth2 import service_account
from googleapiclient.discovery import build

SERVICE_ACCOUNT_FILE = ".local-signing/play-service-account.json"
PACKAGE_NAME = "com.leokinder2k.koratuningcompanion"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]

creds = service_account.Credentials.from_service_account_file(
    SERVICE_ACCOUNT_FILE, scopes=SCOPES
)
service = build("androidpublisher", "v3", credentials=creds)

edit = service.edits().insert(body={}, packageName=PACKAGE_NAME).execute()
edit_id = edit["id"]
print(f"Opened edit: {edit_id}")

try:
    tracks_response = service.edits().tracks().list(
        packageName=PACKAGE_NAME, editId=edit_id
    ).execute()

    fixed_any = False
    for track in tracks_response.get("tracks", []):
        track_name = track["track"]
        releases = track.get("releases", [])

        valid_releases = [r for r in releases if r.get("versionCodes")]
        empty_drafts = [r for r in releases if not r.get("versionCodes") and r.get("status") == "draft"]

        if empty_drafts:
            print(f"Track '{track_name}': removing {len(empty_drafts)} empty draft(s), keeping {len(valid_releases)} valid release(s)")
            service.edits().tracks().update(
                packageName=PACKAGE_NAME,
                editId=edit_id,
                track=track_name,
                body={"track": track_name, "releases": valid_releases}
            ).execute()
            fixed_any = True
        else:
            print(f"Track '{track_name}': OK ({len(valid_releases)} valid release(s))")

    if fixed_any:
        result = service.edits().commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
        print(f"\nCommitted. Edit ID: {result['id']}")
    else:
        service.edits().delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
        print("\nNothing to fix.")

except Exception as e:
    try:
        service.edits().delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
    except Exception:
        pass
    print(f"\nError: {e}", file=sys.stderr)
    sys.exit(1)
