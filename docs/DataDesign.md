# GymPlayer Data Design

## Local Room Schema

GymPlayer is local-first. The app starts without network validation and writes workout data to Room before any cloud sync.

| Table | Purpose | Key fields |
| --- | --- | --- |
| `user_session` | Cached Firebase login state | `uid`, `email`, `loggedIn`, `lastSyncAt` |
| `machines` | Cached multilingual gym machine master data | `id`, `number`, Japanese `name/bodyPart`, translated name/body-part fields, `icon`, `targetSets`, `defaultWeight`, `imageStorageUrl`, `updatedAt` |
| `workout_sessions` | One workout day/session | `id`, `uid`, `date`, `startedAt`, `endedAt`, `systolic`, `diastolic`, `pulse`, `weightKg`, `bodyFatPercent`, `muscleMassKg`, `bodyWaterPercent`, `bmi`, `basalMetabolism`, `visceralFat`, `synced` |
| `workout_sets` | Per-machine set records | `id`, `sessionId`, `machineId`, `machineNumber`, `machineName`, `setIndex`, `weightKg`, `reps`, `completedAt` |
| `playlists` | Local music playlists | `id`, `name`, `folderUri`, `createdAt` |
| `tracks` | Ordered tracks in a playlist | `id`, `playlistId`, `uri`, `title`, `artist`, `durationMs`, `orderIndex` |

Machine/set weight values uploaded to Firestore are always stored in pounds (`lb`). The local UI setting controls machine weight display/input conversion only. Student body weight is always stored and displayed in kilograms (`kg`).

Machine numbers are stored as strings because one physical machine can have multiple training menu entries for different body parts, such as `11a` and `11b`. Numeric-only legacy values are migrated and synced as their string equivalents.

## Firestore Collections

User workout data is private and stored below the authenticated Firebase Auth uid.

```text
users/{uid}/workoutSessions/{sessionId}
```

Example workout document:

```json
{
  "uid": "firebase-auth-uid",
  "date": "2026-07-05",
  "startedAt": "2026-07-05T17:10:00",
  "endedAt": "2026-07-05T18:18:00",
  "systolic": 120,
  "diastolic": 80,
  "pulse": 72,
  "weightKg": 72.4,
  "bodyFatPercent": 18.5,
  "muscleMassKg": 52.3,
  "bodyWaterPercent": 58.1,
  "bmi": 23.1,
  "basalMetabolism": 1540,
  "visceralFat": 8.0,
  "sets": [
    {
      "machineId": "machine-3",
      "machineNumber": "11a",
      "machineName": "チェストプレス",
      "setIndex": 1,
      "weightLb": 88,
      "storedWeightUnit": "lb",
      "reps": 10,
      "completedAt": "2026-07-05T17:25:00"
    }
  ],
  "syncedAt": 1783242000000
}
```

Gym machine master data is downloaded from:

```text
machines/{machineId}
```

Example machine document:

```json
{
  "number": "11a",
  "name": "チェストプレス",
  "bodyPart": "胸部",
  "translations": {
    "ja": {"name": "チェストプレス", "bodyPart": "胸部"},
    "zh-CN": {"name": "坐姿推胸", "bodyPart": "胸部"},
    "en": {"name": "Chest Press", "bodyPart": "Chest"},
    "ko": {"name": "체스트 프레스", "bodyPart": "가슴"}
  },
  "icon": "machine_chest_press",
  "targetSets": 3,
  "defaultWeight": 40,
  "imageStorageUrl": "gs://gymplayer-2bc5b.firebasestorage.app/machines/machine-3.png",
  "updatedAt": 1783242000000
}
```

Machine images are stored in Firebase Storage. `imageStorageUrl` keeps the canonical `gs://` URL for the image object so the app can resolve it through Firebase Storage SDKs.

## Sync Status

- `workout_sessions.synced = false`: created or changed locally and pending upload.
- `workout_sessions.synced = true`: uploaded successfully to Firestore.
- `user_session.lastSyncAt`: last successful manual sync time.
- Machine data is server-owned; the newest Firestore `updatedAt` wins when downloaded.
- App language is stored at `users/{uid}/settings/app` as `languageCode` (`ja`, `zh-CN`, `en`, or `ko`) plus a server `updatedAt` timestamp. A locally changed language is uploaded on manual sync; otherwise the cloud value is restored.
- Legacy machine documents without `translations` remain valid. Missing translations fall back to Japanese `name/bodyPart`.

## Security Rules

Recommended Firestore rules:

```text
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    match /machines/{machineId} {
      allow read: if request.auth != null;
      allow write: if false;
    }
  }
}
```
