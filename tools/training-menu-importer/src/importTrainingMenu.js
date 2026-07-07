#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { parse } from "csv-parse/sync";
import { applicationDefault, cert, getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

const DEFAULT_COLLECTION = "machines";
const DEFAULT_IMAGE_PREFIX = "machines";
const DEFAULT_IMAGE_EXTENSION = "png";
const REQUIRED_COLUMNS = ["id", "number", "name", "bodyPart"];
const OPTIONAL_COLUMNS = ["icon", "targetSets", "defaultWeight", "imageStorageUrl", "updatedAt"];
const MAX_BATCH_WRITES = 500;

function usage() {
  return `
Usage:
  node src/importTrainingMenu.js --csv <file> [options]

Options:
  --csv <file>              CSV file path. Required.
  --collection <name>       Firestore collection to overwrite. Default: ${DEFAULT_COLLECTION}
  --project-id <id>         Firebase project id. Optional.
  --service-account <file>  Service account JSON path. Optional.
  --image-bucket-url <url>  gs:// Firebase Storage bucket URL used to fill empty imageStorageUrl values.
  --image-prefix <path>     Storage folder for generated image URLs. Default: ${DEFAULT_IMAGE_PREFIX}
  --image-extension <ext>   Image extension for generated image URLs. Default: ${DEFAULT_IMAGE_EXTENSION}
  --dry-run                 Validate and print a preview without writing.
  --help                    Show this help.
`;
}

function parseArgs(argv) {
  const args = {
    collection: DEFAULT_COLLECTION,
    dryRun: false,
    imagePrefix: DEFAULT_IMAGE_PREFIX,
    imageExtension: DEFAULT_IMAGE_EXTENSION,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (token === "--help") {
      args.help = true;
    } else if (token === "--dry-run") {
      args.dryRun = true;
    } else if (token === "--csv") {
      args.csv = argv[++index];
    } else if (token === "--collection") {
      args.collection = argv[++index];
    } else if (token === "--project-id") {
      args.projectId = argv[++index];
    } else if (token === "--service-account") {
      args.serviceAccount = argv[++index];
    } else if (token === "--image-bucket-url") {
      args.imageBucketUrl = argv[++index];
    } else if (token === "--image-prefix") {
      args.imagePrefix = argv[++index];
    } else if (token === "--image-extension") {
      args.imageExtension = argv[++index];
    } else {
      throw new Error(`Unknown argument: ${token}`);
    }
  }

  if (!args.help && !args.csv) throw new Error("--csv is required");
  if (!args.help && !args.collection) throw new Error("--collection cannot be empty");
  if (!args.help && args.imageBucketUrl && !args.imageBucketUrl.startsWith("gs://")) {
    throw new Error("--image-bucket-url must start with gs://");
  }
  if (!args.help && args.imageExtension.startsWith(".")) {
    args.imageExtension = args.imageExtension.slice(1);
  }

  return args;
}

function readCsv(csvPath) {
  const absolutePath = path.resolve(csvPath);
  const content = fs.readFileSync(absolutePath, "utf8");
  const records = parse(content, {
    bom: true,
    columns: true,
    skip_empty_lines: true,
    trim: true,
  });

  if (records.length === 0) throw new Error("CSV has no data rows");

  return records;
}

function parseInteger(value, field, rowNumber, fallback) {
  if (value === undefined || value === null || String(value).trim() === "") {
    if (fallback !== undefined) return fallback;
    throw new Error(`Row ${rowNumber}: ${field} is required`);
  }

  const parsed = Number(value);
  if (!Number.isInteger(parsed)) {
    throw new Error(`Row ${rowNumber}: ${field} must be an integer`);
  }

  return parsed;
}

function parseMachineNumber(value, rowNumber) {
  if (value === undefined || value === null || String(value).trim() === "") {
    throw new Error(`Row ${rowNumber}: number is required`);
  }

  const number = String(value).trim();
  if (!/^\d+[A-Za-z]*$/.test(number)) {
    throw new Error(`Row ${rowNumber}: number must start with digits and may end with letters, for example 11a`);
  }

  return number;
}

function parseTimestamp(value, rowNumber, fallback) {
  if (value === undefined || value === null || String(value).trim() === "") return fallback;

  const raw = String(value).trim();
  if (/^\d+$/.test(raw)) return Number(raw);

  const parsed = Date.parse(raw);
  if (Number.isNaN(parsed)) {
    throw new Error(`Row ${rowNumber}: updatedAt must be epoch milliseconds or an ISO date`);
  }

  return parsed;
}

function buildImageStorageUrl(recordValue, id, rowNumber, { imageBucketUrl, imagePrefix, imageExtension }) {
  const existingValue = String(recordValue ?? "").trim();
  if (existingValue) {
    if (!existingValue.startsWith("gs://")) {
      throw new Error(`Row ${rowNumber}: imageStorageUrl must start with gs://`);
    }
    return existingValue;
  }

  if (!imageBucketUrl) return "";

  const bucket = imageBucketUrl.replace(/\/+$/, "");
  const prefix = String(imagePrefix ?? "").replace(/^\/+|\/+$/g, "");
  const extension = String(imageExtension ?? "").replace(/^\.+/, "") || DEFAULT_IMAGE_EXTENSION;
  const imageName = `${id}.${extension}`;

  return prefix ? `${bucket}/${prefix}/${imageName}` : `${bucket}/${imageName}`;
}

function assertColumns(record) {
  const columns = Object.keys(record);
  const allowedColumns = new Set([...REQUIRED_COLUMNS, ...OPTIONAL_COLUMNS]);
  const missing = REQUIRED_COLUMNS.filter((column) => !columns.includes(column));
  const unknown = columns.filter((column) => !allowedColumns.has(column));

  if (missing.length > 0) throw new Error(`CSV is missing required column(s): ${missing.join(", ")}`);
  if (unknown.length > 0) throw new Error(`CSV has unknown column(s): ${unknown.join(", ")}`);
}

function normalizeRecords(records, options) {
  assertColumns(records[0]);

  const now = Date.now();
  const seenIds = new Set();

  return records.map((record, index) => {
    const rowNumber = index + 2;
    const id = String(record.id ?? "").trim();
    const name = String(record.name ?? "").trim();
    const bodyPart = String(record.bodyPart ?? "").trim();

    if (!id) throw new Error(`Row ${rowNumber}: id is required`);
    if (seenIds.has(id)) throw new Error(`Row ${rowNumber}: duplicate id "${id}"`);
    seenIds.add(id);
    if (!name) throw new Error(`Row ${rowNumber}: name is required`);
    if (!bodyPart) throw new Error(`Row ${rowNumber}: bodyPart is required`);

    const number = parseMachineNumber(record.number, rowNumber);
    const targetSets = parseInteger(record.targetSets, "targetSets", rowNumber, 3);
    const defaultWeight = parseInteger(record.defaultWeight, "defaultWeight", rowNumber, 40);
    const updatedAt = parseTimestamp(record.updatedAt, rowNumber, now);
    const imageStorageUrl = buildImageStorageUrl(record.imageStorageUrl, id, rowNumber, options);
    const icon = String(record.icon ?? "").trim() || "🏋";

    if (targetSets <= 0) throw new Error(`Row ${rowNumber}: targetSets must be greater than 0`);
    if (defaultWeight < 0) throw new Error(`Row ${rowNumber}: defaultWeight must be 0 or greater`);

    return {
      id,
      data: {
        number,
        name,
        bodyPart,
        icon,
        targetSets,
        defaultWeight,
        imageStorageUrl,
        updatedAt,
      },
    };
  });
}

function initializeAdmin({ projectId, serviceAccount }) {
  if (getApps().length === 0) {
    if (serviceAccount) {
      const serviceAccountPath = path.resolve(serviceAccount);
      const credentials = JSON.parse(fs.readFileSync(serviceAccountPath, "utf8"));
      initializeApp({
        credential: cert(credentials),
        projectId: projectId || credentials.project_id,
      });
    } else {
      initializeApp({
        credential: applicationDefault(),
        projectId,
      });
    }
  }

  return getFirestore();
}

async function commitBatch(db, operations) {
  for (let start = 0; start < operations.length; start += MAX_BATCH_WRITES) {
    const batch = db.batch();
    const chunk = operations.slice(start, start + MAX_BATCH_WRITES);
    chunk.forEach((operation) => operation(batch));
    await batch.commit();
  }
}

async function deleteCollection(db, collectionRef) {
  const snapshot = await collectionRef.get();
  const deleteOperations = snapshot.docs.map((doc) => (batch) => batch.delete(doc.ref));
  await commitBatch(db, deleteOperations);
  return snapshot.size;
}

async function overwriteCollection(db, collectionName, menuItems) {
  const collectionRef = db.collection(collectionName);
  const deleted = await deleteCollection(db, collectionRef);
  const writeOperations = menuItems.map((item) => (batch) => batch.set(collectionRef.doc(item.id), item.data));
  await commitBatch(db, writeOperations);
  return { deleted, written: menuItems.length };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));

  if (args.help) {
    console.log(usage().trim());
    return;
  }

  const records = readCsv(args.csv);
  const menuItems = normalizeRecords(records, args);

  console.log(`Validated ${menuItems.length} training menu item(s).`);
  console.table(
    menuItems.slice(0, 10).map((item) => ({
      id: item.id,
      number: item.data.number,
      name: item.data.name,
      bodyPart: item.data.bodyPart,
      imageStorageUrl: item.data.imageStorageUrl,
      targetSets: item.data.targetSets,
      defaultWeight: item.data.defaultWeight,
    })),
  );

  if (args.dryRun) {
    console.log(`Dry run complete. No Firestore writes were made. Target collection: ${args.collection}`);
    return;
  }

  const db = initializeAdmin(args);
  const result = await overwriteCollection(db, args.collection, menuItems);
  console.log(`Overwrite complete for collection "${args.collection}". Deleted ${result.deleted}, wrote ${result.written}.`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
