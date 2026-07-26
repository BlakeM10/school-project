#!/usr/bin/env node
/**
 * Seeds the read-only `drills` collection from drills.json using the Admin SDK
 * (client writes to `drills` are denied by the security rules).
 *
 * Usage:
 *   Against the emulator:  FIRESTORE_EMULATOR_HOST=localhost:8080 node seed_drills.js <project-id>
 *   Against production:    GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> node seed_drills.js <project-id>
 *
 * Requires: npm install firebase-admin
 */
const { initializeApp, applicationDefault } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const { drills } = require('./drills.json');

const projectId = process.argv[2];
if (!projectId) {
  console.error('Usage: node seed_drills.js <project-id>');
  process.exit(1);
}

initializeApp({ credential: applicationDefault(), projectId });
const db = getFirestore();

(async () => {
  const batch = db.batch();
  for (const [id, drill] of Object.entries(drills)) {
    batch.set(db.collection('drills').doc(id), {
      ...drill,
      updatedAt: FieldValue.serverTimestamp(),
    });
  }
  await batch.commit();
  console.log(`Seeded ${Object.keys(drills).length} drills into project ${projectId}`);
})().catch((err) => {
  console.error('Seeding failed:', err);
  process.exit(1);
});
