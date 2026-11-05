# lipabill.app web (pay-link fallback)

Static site for `https://lipabill.app` — deploy this folder (or merge into the
existing Vercel project) next to `/ota/`.

## Pay landing (`/p/{token}`)

When someone opens a shared pay QR/link without LipaBill installed, the HTTPS
URL loads `p/index.html`. Client-side JS (`p/paylink.js`) decodes the token the
same way as `PayLink.parse` in the Android app, then offers:

- Copy details
- Open M-Pesa (launches the wallet app / Play Store — does **not** prefill)
- Open in LipaBill (`lipabill://p/…`)
- Get LipaBill (Play Store)

## App Links

`/.well-known/assetlinks.json` lists the Play release + debug signing certs for
`com.lipabill.app`. After this file is live on the apex host, Android verifies
the `https://lipabill.app/p/` intent-filter (`android:autoVerify="true"`).

## Verify decode parity

```bash
node web/p/verify-fixtures.mjs
./gradlew :app:testPlayDebugUnitTest --tests com.lipabill.app.pay.PayLinkFixturesTest
```

Both use `web/p/fixtures.json` golden vectors.

## Vercel

`vercel.json` rewrites `/p/:token` → `/p/index.html`. Point the project root at
`web/` or copy these paths into the site that already hosts `/ota/`.
