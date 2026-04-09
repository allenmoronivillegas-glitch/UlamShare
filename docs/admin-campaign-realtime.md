# Admin-to-Mobile Campaign Sync (Firebase Realtime Database)

This app now reads campaigns from Realtime Database path:

- `/campaigns/{campaignId}`

## Recommended campaign shape

```json
{
  "campaigns": {
    "campaign_001": {
      "title": "Scholarship Drive 2026",
      "description": "Help 500 students finish college",
      "goalAmount": 500000,
      "raisedAmount": 125000,
      "isPublished": true,
      "isFeatured": true,
      "createdAt": 1775664000000,
      "updatedAt": 1775664000000,
      "createdBy": "admin_uid_1"
    }
  }
}
```

## Web admin write flow

When admin/moderator clicks **Create campaign** in web admin:

1. Validate required fields (`title`, `goalAmount`).
2. Save campaign under `/campaigns/{id}`.
3. Set `isPublished = true` when campaign should be visible on mobile.
4. Optionally set `isFeatured = true` so it appears first in guest home.

## Mobile read behavior

`HomeGuestActivity` listens in real time to `/campaigns` and renders:

- top published + featured campaign first (fallback: highest raised)
- title, description, raised amount, and progress percent

If no published campaigns exist, the app shows an empty state message.

## Suggested security rules

Use Firebase Auth custom claims (`role`) for admin/moderator control:

```json
{
  "rules": {
    "campaigns": {
      ".read": true,
      "$campaignId": {
        ".write": "auth != null && (auth.token.role === 'admin' || auth.token.role === 'moderator')"
      }
    }
  }
}
```

Adjust `.read` if only signed-in users should view campaigns.
