# Setup guide

Everything needed to get Smart To-Do onto his phone and keep it updated.
Steps 1–3 are done once by you (the maintainer); step 4 is on his phone.

## 1. Repository

The repository must be **public** so the app can check for new versions without a password. ✅ Done.

## 2. Signing key (once, about 5 minutes)

Android only installs an update over the existing app if it is signed with the **same key**.
If the key is lost, the app would have to be uninstalled first, which deletes his data
(backups can bring it back, but it's a hassle). So the key is created once and kept safe.

You received two private files:

- `smarttodo-release.jks`: the key itself
- `github-secrets-PRIVATE.txt`: the two values to paste into GitHub

**Keep both somewhere private**, for example email them to yourself and save them in your
own Google Drive. **Never** upload them to the repository.

Add the two secrets in GitHub:

1. Open the repository on github.com → **Settings** (top bar of the repo).
2. Left menu: **Secrets and variables** → **Actions**.
3. Click **New repository secret**.
   - Name: `SIGNING_PASSWORD`. Value: copy the password line from the text file. **Add secret**.
4. Click **New repository secret** again.
   - Name: `SIGNING_KEYSTORE_BASE64`. Value: copy the whole long line from the text file. **Add secret**.

## 3. Gemini API key (free, about 2 minutes)

1. Go to **https://aistudio.google.com** and sign in with a Google account.
2. Click **Get API key** → **Create API key** → copy it.
3. **Do not add a payment card / billing.** Without billing the key can never cost money;
   when the free daily limit is reached the app simply waits and keeps what he said in the Inbox.
4. Don't paste the key in chats or in the repository. You'll type it into the app on his phone (step 4).

Notes:
- A Gemini app subscription (Google AI Pro, etc.) does **not** include API usage. The API has its own free tier.
- The app picks the best free model automatically and switches to another one if a limit is reached.
- On the free tier Google may use what is sent to improve its products. Tell him not to dictate passwords or bank details.

## 4. Install on his phone (Samsung Galaxy A9)

1. On his phone, open `https://github.com/kiraherk/smart-to-do/releases/latest`.
2. Tap `SmartToDo-vX.Y.Z.apk` to download, then open it.
3. If Android asks, allow **Install unknown apps** for the browser, then **Install**.
4. Open **مهامي الذكية**. The setup checklist appears:
   - **Microphone**: Allow.
   - **Notifications**: Allow (Android 13+ only).
   - **Keep reminders running**: Allow. On Samsung also open
     *Settings → Apps → مهامي الذكية → Battery → Unrestricted*.
     If Samsung shows "Sleeping apps", make sure the app is **not** in that list.
   - **Speech language**: choose his dialect (e.g. Arabic – Saudi Arabia, Egypt, Lebanon…).
   - **Reading voice**: if not green, tap Install and download the Arabic voice.
   - **Gemini key**: opens Settings → *Gemini API key* → paste the key → *Test connection*
     should say "Working ✓".
   - **Backup folder** (recommended): choose a folder such as *Documents*. A copy is saved there
     every day and stays even if the app is removed.
5. Tap **Finish** and try: press «تكلّم» and say
   *«ذكرني بكرة الساعة عشرة أتصل بالبنك»*.

Voice recognition uses the Google app, so keep the **Google** app installed and updated.

## Releasing a new version

After changes are merged:

- On GitHub: **Actions** → **Release** → **Run workflow** → type the new version (e.g. `0.2.0`,
  always higher than the last one) → **Run workflow**. Or push a tag: `git tag v0.2.0 && git push origin v0.2.0`.

The workflow runs the tests, builds the APK signed with the secret key and publishes it under
**Releases**. Within a day his phone shows "New version", or he can go to *Settings → Check
for updates → Update now*. His data stays: the app also makes a backup just before updating.

You can also use [Obtainium](https://github.com/ImranR98/Obtainium) pointed at this repository.

## If something goes wrong

- **The voice doesn't understand**: check the speech language in Settings, Wi-Fi, and that the Google app is up to date.
- **"AI key not working"**: create a new key in AI Studio and paste it in Settings.
- **"Free limit used up"**: nothing is lost; what he said is in the Inbox. Tap *Organise now* later.
- **Reminders don't ring**: redo the battery step (Unrestricted) and make sure notifications are on.
- **New phone / reinstall**: install the app, then *Settings → Restore backup* and pick the latest
  `smarttodo-backup-….json` from the backup folder (or one he sent to himself by email/WhatsApp).
