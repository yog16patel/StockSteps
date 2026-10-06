import { readFile } from 'node:fs/promises';
import { before, after, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, deleteDoc, collection, getDocs } from 'firebase/firestore';

let env;
const valid = { symbol: 'AAPL', addedAt: 100, updatedAt: 200 };
const item = (db, uid = 'alice', symbol = 'AAPL') => doc(db, `users/${uid}/watchlist/${symbol}`);
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-stocksteps', firestore: {
    host: '127.0.0.1', port: 8085, rules: await readFile('../firestore.rules', 'utf8')
  } });
});
beforeEach(() => env.clearFirestore());
after(() => env.cleanup());
test('owner can create, list, read, update and delete', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await assertSucceeds(setDoc(item(db), valid));
  await assertSucceeds(getDocs(collection(db, 'users/alice/watchlist')));
  await assertSucceeds(getDoc(item(db)));
  await assertSucceeds(setDoc(item(db), { ...valid, updatedAt: 300 }));
  await assertSucceeds(deleteDoc(item(db)));
});
test('unauthenticated access is denied', async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(setDoc(item(db), valid));
  await assertFails(getDoc(item(db)));
  await assertFails(deleteDoc(item(db)));
});
test('another account cannot read, list, write or delete', async () => {
  const db = env.authenticatedContext('bob').firestore();
  await assertFails(setDoc(item(db), valid));
  await assertFails(getDoc(item(db)));
  await assertFails(getDocs(collection(db, 'users/alice/watchlist')));
  await assertFails(deleteDoc(item(db)));
});
test('financial fields, missing fields, mismatched ids and bad timestamps are denied', async () => {
  const db = env.authenticatedContext('alice').firestore();
  for (const data of [
    { ...valid, price: 255 }, { symbol: 'AAPL', addedAt: 100 },
    { ...valid, symbol: 'NVDA' }, { ...valid, addedAt: -1 },
    { ...valid, updatedAt: 99 }, { ...valid, addedAt: '100' },
    { ...valid, updatedAt: 200.5 }
  ]) await assertFails(setDoc(item(db), data));
  await assertFails(setDoc(item(db, 'alice', 'aapl'), { ...valid, symbol: 'aapl' }));
});
test('Canadian symbols are accepted; unrelated user documents are denied', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await assertSucceeds(setDoc(item(db, 'alice', 'SHOP.TO'), { ...valid, symbol: 'SHOP.TO' }));
  await assertFails(setDoc(doc(db, 'users/alice'), { admin: true }));
});
test('AI summary collection is backend-only for guests and signed-in users', async () => {
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'newsSimplifications/example'), { result: 'server summary' });
  });
  for (const db of [env.unauthenticatedContext().firestore(), env.authenticatedContext('alice').firestore()]) {
    const summary = doc(db, 'newsSimplifications/example');
    await assertFails(getDoc(summary));
    await assertFails(getDocs(collection(db, 'newsSimplifications')));
    await assertFails(setDoc(summary, { result: 'forged summary' }));
    await assertFails(deleteDoc(summary));
  }
});

test('listing metadata is accepted and malformed metadata is denied', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await assertSucceeds(setDoc(item(db), { ...valid, name: 'Apple', exchange: 'NASDAQ', currency: 'USD', exchangeFullName: 'Nasdaq' }));
  for (const fields of [{ exchange: 12 }, { currency: 'X'.repeat(11) }, { name: 'X'.repeat(301) }, { exchangeFullName: false }]) {
    await assertFails(setDoc(item(db), { ...valid, ...fields }));
  }
});
