# Training Menu Firestore Importer

This tool imports training menu master data from CSV into Firestore.

The GymPlayer app currently reads the training menu master data from the Firestore collection documented as `machines` in `docs/DataDesign.md`.

## CSV Columns

| Column | Required | Firestore field | Example |
| --- | --- | --- | --- |
| `id` | Yes | document id | `machine-44` |
| `number` | Yes | `number` | `44` |
| `name` | Yes | `name` | `レッグプレス` |
| `bodyPart` | Yes | `bodyPart` | `脚` |
| `icon` | No | `icon` | `🏋` |
| `targetSets` | No | `targetSets` | `3` |
| `defaultWeight` | No | `defaultWeight` | `40` |
| `imageStorageUrl` | No | `imageStorageUrl` | `gs://gymplayer-2bc5b.firebasestorage.app/machines/machine-44.png` |
| `updatedAt` | No | `updatedAt` | `1783242000000` |

If `updatedAt` is empty, the importer uses the current timestamp for every row in that run.

If `imageStorageUrl` is empty, you can pass `--image-bucket-url` to generate it from the document id:

```bash
npm run import -- --csv sample-training-menu.csv --image-bucket-url gs://your-bucket.firebasestorage.app
```

The default generated path is:

```text
gs://your-bucket.firebasestorage.app/machines/{id}.png
```

You can change the generated folder or extension:

```bash
npm run import -- --csv sample-training-menu.csv --image-bucket-url gs://your-bucket.firebasestorage.app --image-prefix training-menu --image-extension jpg
```

## Setup

```bash
cd tools/training-menu-importer
npm install
```

Authenticate with one of these options:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/service-account.json
```

or pass a service account JSON file directly:

```bash
npm run import -- --csv sample-training-menu.csv --service-account ./src/keys/gymplayer-2bc5b-firebase-adminsdk-fbsvc-606ec218c8.json
```

## Dry Run

Validate and preview without writing to Firestore:

```bash
npm run dry-run
```

## Import

This performs a full overwrite:

1. Validate the CSV.
2. Delete every existing document in the target collection.
3. Write every CSV row as a new document.

```bash
npm run import -- --csv sample-training-menu.csv
```

By default, the target collection is `machines`. You can override it:

```bash
npm run import -- --csv sample-training-menu.csv --collection trainingMenu
```

You can also set a Firebase project id explicitly:

```bash
npm run import -- --csv sample-training-menu.csv --project-id your-firebase-project-id
```


```bash
npm run import -- --csv sample-training-menu.csv --service-account src/keys/gymplayer-2bc5b-firebase-adminsdk-fbsvc-606ec218c8.json
```