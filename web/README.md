# lipabill.app pay-link fallback (source mirror)

Static pay-link assets for `https://lipabill.app`. **Production hosts these from
the marketing site repo** (`lipabill_web`), not from a separate Vercel project
rooted at this folder.

Copy / sync into `lipabill_web`:

| This folder | Deployed path on lipabill.app |
|---|---|
| `p/index.html`, `p/paylink.js`, `p/fixtures.json` | `public/p/…` |
| `.well-known/assetlinks.json` | `public/.well-known/assetlinks.json` |
| `vercel.json` rewrites/headers | `lipabill_web/next.config.ts` |

If you change decode logic or fixtures here, update `lipabill_web` the same way
and redeploy that repo — otherwise `/p/{token}` and App Links stay 404 on the
live domain.

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

`vercel.json` documents the intended rewrites (`/p/:token` → landing). On the
live Next.js site those live in `next.config.ts` instead.
