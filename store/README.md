# Publishing Mise on Google Play — checklist

Everything the store needs is in this folder: `listing.md`, `data-safety.md`, `declarations.md`,
`icon-512.png`, `feature-graphic.png`, `screenshots/`. The privacy policy is live at
https://vineet-agarwal-codes.github.io/privacy.html.

## 1. The account (Vineet's mother)
1. At https://play.google.com/console, sign in with her Google account and choose **Personal** account.
2. Pay the US$25 fee with a card, verify her identity (PAN/Aadhaar) and her phone number.
3. Developer name: what should show under the app on Play (her name, or a name she chooses).
4. Public contact: email vineet140502@gmail.com, phone +91 7017223071, website https://vineet-agarwal-codes.github.io.
5. **Users and permissions → Invite new users:** vineet140502@gmail.com with **Admin** rights.

## 2. The upload key (once)
The key that signs every upload is on the VM: `~/.config/habit-tags/mise-upload.jks` and
`~/.config/habit-tags/release.env` (its passwords). **Copy both somewhere safe and offline**
(e.g. a password manager or an encrypted USB stick). If they're lost, Google can reset the upload key,
but it takes days.

## 3. Build and sign a release
1. GitHub → Actions → **release** → Run workflow (or `gh workflow run release.yml`). It runs the tests,
   builds `app-release.aab` and checks the manifest has no debug-only parts.
2. On the VM: `android/scripts/sign-release.sh <run-id>` → signed bundle in `~/mise-release/`.

## 4. Create the app
1. **Create app:** name "Mise: Shape your spaces", default language English (India), App, Free.
2. **App signing:** accept Play App Signing (Google holds the app signing key; we keep the upload key).
3. **Store listing:** texts from `listing.md`, icon, feature graphic, 2–8 screenshots.
4. **App content:** privacy policy URL; Ads = No; App access = all features available without login;
   Content rating questionnaire (answers in `declarations.md`); Target audience 18+ (or 13+);
   Data safety (answers in `data-safety.md`); Foreground service declaration with the short video
   (script in `declarations.md`).

## 5. Closed test (required for new personal accounts)
1. **Testing → Closed testing → Create track.** Upload the signed `.aab`.
2. Add at least **12 testers** (their Google account emails) as an email list. They must stay opted in
   for **14 days in a row**.
3. Send them the opt-in link from the track page; each taps "Become a tester", then installs from Play.
4. Ask them to really use it: at least one space, a few taps, the gate once.

## 6. Production
After 14 days with 12+ testers, **Dashboard → Apply for production access**, answer the questions
about the test, then promote the release to Production.

The application ID `app.mise` is permanent once the first bundle is uploaded.
