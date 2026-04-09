# Donations Integration Guide

This guide explains how to send donations from your mobile app to Firebase so they appear in the admin dashboard.

## Firebase Collection Structure

Donations are stored in the `/donations` path in Firebase Realtime Database with the following structure:

```json
{
  "donations": {
    "donation_id_1": {
      "campaignId": "campaign_key_from_campaigns",
      "donorName": "John Doe",
      "donorEmail": "john@example.com",
      "amount": 500,
      "paymentMethod": "GCash",
      "status": "completed",
      "createdAt": 1712592000000
    },
    "donation_id_2": {
      "campaignId": "campaign_key_from_campaigns",
      "donorName": "Jane Smith",
      "donorEmail": "jane@example.com",
      "amount": 1000,
      "paymentMethod": "PayMaya",
      "status": "completed",
      "createdAt": 1712592100000
    }
  }
}
```

## Required Fields

| Field | Type | Description |
|-------|------|-------------|
| `campaignId` | String | The Firebase key of the campaign being donated to |
| `donorName` | String | Name of the donor (or "Anonymous") |
| `donorEmail` | String | Email of the donor |
| `amount` | Number | Donation amount in Philippine Peso (₱) |
| `paymentMethod` | String | Payment method: "GCash" or "PayMaya" |
| `status` | String | Status of the donation: "pending", "completed", "failed" |
| `createdAt` | Number | Timestamp of the donation (use `Date.now()`) |

## How to Send a Donation

### Example: React Native / Flutter Integration

**Firebase Setup (in your mobile app):**

```javascript
import { initializeApp } from 'firebase/app';
import { getDatabase, ref, push, set } from 'firebase/database';

// Initialize Firebase (use same config as admin dashboard)
const firebaseConfig = {
  apiKey: "AIzaSyAlFuluvqONN0GfRfkv1CF85rGdm75dOoU",
  authDomain: "ulamshare-4f2b9.firebaseapp.com",
  databaseURL: "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "ulamshare-4f2b9",
  storageBucket: "ulamshare-4f2b9.firebasestorage.app",
  messagingSenderId: "521750995424",
  appId: "1:521750995424:web:3f26ee1e971685409ecb85"
};

const app = initializeApp(firebaseConfig);
const db = getDatabase(app);

// Function to send a donation
async function createDonation(campaignId, donorName, donorEmail, amount, paymentMethod) {
  try {
    const donationsRef = ref(db, 'donations');
    const newDonationRef = push(donationsRef);
    
    await set(newDonationRef, {
      campaignId: campaignId,
      donorName: donorName,
      donorEmail: donorEmail,
      amount: Number(amount),
      paymentMethod: paymentMethod, // "GCash" or "PayMaya"
      status: "completed", // or "pending" if transaction not finalized
      createdAt: Date.now()
    });
    
    console.log('Donation saved successfully!');
    return true;
  } catch (error) {
    console.error('Failed to save donation:', error);
    return false;
  }
}

// Example usage after successful payment
createDonation(
  'campaign_key_123',           // Campaign ID
  'John Doe',                   // Donor name
  'john@example.com',           // Donor email
  500,                          // Amount
  'GCash'                       // Payment method
);
```

## Payment Flow

1. **User selects campaign** → views campaign details
2. **User enters donation amount** → chooses GCash/PayMaya
3. **Mobile app processes payment** → integrates with GCash/PayMaya API
4. **Payment succeeds** → call `createDonation()` to save to Firebase
5. **Admin dashboard updates** → campaign progress bar increases automatically
6. **Donation appears** in Donations view

## Payment Integration

For GCash/PayMaya integration on mobile:

- **GCash**: Use GCash API or third-party payment provider (e.g., Paymongo)
- **PayMaya**: Use PayMaya API or third-party payment provider (e.g., Paymongo)

Popular solutions:
- **Paymongo** (https://paymongo.com) - handles both GCash and PayMaya
- **DragonPay** (https://www.dragonpay.ph)
- **Instapay** - for interbank transfers

## Automatic Updates

Once a donation is saved to Firebase:
- ✅ Admin dashboard campaigns immediately show updated progress bars
- ✅ Donation stats (total, count, average) update in real-time
- ✅ Donations table populates with payment details
- ✅ Campaign progress updates for viewers

## Security Notes

- Keep your Firebase configuration secure (don't commit API keys)
- Validate donation amounts on backend before saving
- Verify payment method transactions before marking status as "completed"
- Consider using Firebase Security Rules to validate donation data

## Testing

To test in your admin dashboard:

1. Manually add a test donation to `/donations` in Firebase Console
2. Use this structure:
```json
{
  "campaignId": "your_campaign_key",
  "donorName": "Test Donor",
  "donorEmail": "test@example.com",
  "amount": 500,
  "paymentMethod": "GCash",
  "status": "completed",
  "createdAt": 1712592000000
}
```
3. Check if dashboard updates immediately

## Admin Dashboard Views

**Progress Bars**: Show actual donation percentage vs goal
```
₱5,000 raised · 50% · 3 donations
```

**Donations Tab**: Complete transaction history with:
- Donor name and email
- Campaign name
- Donation amount
- Payment method (GCash/PayMaya)
- Date

**Dashboard Stats**:
- Total Donations count
- Total Raised (₱)
- Average Donation amount
