# Mobile App Integration

This admin dashboard writes campaign records to the shared Firebase Realtime Database node:

- `campaigns`

Mobile apps should connect to the same Firebase project and read `campaigns` from the Realtime Database.

## Recommended data access pattern

1. Use the same Firebase config values as the admin web app.
2. Read from the path `/campaigns`.
3. Filter campaigns using one or both of these fields:
   - `hidden: false`
   - `published: true`

## Sample campaign JSON record

```json
{
  "campaignId": "-N4pxyZxj9c1qA2BcD3e",
  "title": "Typhoon Carina Relief Fund",
  "description": "Support families affected by Typhoon Carina with emergency shelter, food, and medical assistance.",
  "goal": 500000,
  "date": "2026-06-30",
  "cat": "Disaster Relief",
  "hidden": false,
  "published": true,
  "status": "Draft",
  "raised": 0,
  "createdAt": 1712467200000,
  "updatedAt": 1712467200000
}
```

## Mobile client sample (Realtime Database)

### JavaScript example

```js
import { initializeApp } from 'https://www.gstatic.com/firebasejs/10.7.1/firebase-app.js';
import { getDatabase, ref, onValue } from 'https://www.gstatic.com/firebasejs/10.7.1/firebase-database.js';

const firebaseConfig = {
  apiKey: 'AIzaSyAlFuluvqONN0GfRfkv1CF85rGdm75dOoU',
  authDomain: 'ulamshare-4f2b9.firebaseapp.com',
  databaseURL: 'https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app',
  projectId: 'ulamshare-4f2b9',
  storageBucket: 'ulamshare-4f2b9.firebasestorage.app',
  messagingSenderId: '521750995424',
  appId: '1:521750995424:web:3f26ee1e971685409ecb85',
  measurementId: 'G-67NLB4FRVF'
};

const app = initializeApp(firebaseConfig);
const db = getDatabase(app);
const campaignsRef = ref(db, 'campaigns');

onValue(campaignsRef, (snapshot) => {
  const data = snapshot.val() || {};
  const campaigns = Object.entries(data)
    .map(([key, value]) => ({ campaignId: key, ...value }))
    .filter(c => c.published && !c.hidden);
  console.log('Mobile campaigns', campaigns);
});
```

## Notes

- The admin app now stores `campaignId` inside each campaign record, so mobile clients can use it directly.
- If a mobile app already uses the campaign object key as the ID, it will still work.
- For secure mobile access, apply Firebase Realtime Database rules on the `campaigns` node as needed.
