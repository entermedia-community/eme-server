---
name: mediadb-auth
description: Use for anything about how people, browsers, apps, scripts and agents log in to the EME server — getting a Bearer token (bin/refreshtoken.sh), registering a public key or an application password, the services/authentication endpoints (token, registerkey, listkeys, revokekey, login, sendusercode), browserkey.js browser auto-login, email login links (confirmlogin.html), key/token troubleshooting, or when a curl call gets 401/403. Replaces the old password-derived "entermedia key" (md542) and the emekey cookie, which no longer exist. Consult before touching AutoLoginOAuth, AdminModule.oauthToken/registerKey, StringEncryption key code, the userkey table, or any credential in a script, template or skill.
---

# MediaDB authentication

Users have no password. The `password` field, the password authenticators (file, LDAP, JAAS, Windows, SPNEGO), the legacy
login form and the change-password pages are gone; `tempSecurityKeyAuthenticator` (emailed `templogincode`) is the only
login check. Anything that used to log in to another EME server as a user (`EMPushManager` via the `push_server_apppassword`
catalog setting, `entermediapublisher` via the destination `apppassword`, `UpdateModule` sync via the sync entry's
`apppassword`) sends an application password as `Authorization: Bearer`. FTP/SFTP/LDAP/Vizone credentials come from their
own config (`password` on the repository, `ldap.xml` or the publish destination), never from a user record.

There is no `entermedia.key`, `md542` value, `emekey` cookie or `getEnterMediaKey()` any more. Credentials are rows in the
system `userkey` table (`plugins/system/html/data/fields/userkey.xml`), one per device or tool, each revocable on its own.

| `keytype` | What it is | How it is used |
|---|---|---|
| `publickey` | Ed25519 (or P-256) public key; the private key stays on the device | Sign a login (`grant_type=key`) and get a 7 day Bearer token |
| `apppassword` | Server generated secret, shown once, only its SHA-256 is stored | Send `Authorization: Bearer emeapp.<key_id>.<secret>` directly, no expiry |

Row fields: `id`, `userid`, `label`, `keytype`, `algorithm` (`Ed25519`, `P-256`, or `SHA-256` for app passwords, detected
server side), `publickey` or `secrethash`, `created`, `lastused`, `enabled`. Revoke = `enabled=false`; it takes effect on
the next request. Keys do not expire.

## Getting a token for curl / agents

```bash
AUTH="Authorization: Bearer $(bin/refreshtoken.sh | tail -n1)"   # run from the repo root
curl -H "$AUTH" http://localhost:8080/site/mediadb/services/authentication/listkeys.json
```

`bin/refreshtoken.sh` reads `.env` (`EME_USER`, `EME_KEY_ID`, `EME_PRIVATE_KEY`, or `EME_APP_PASSWORD`; optional
`EME_SERVER`), signs a login, caches the token with its date in `.refreshtoken` (reused for 6 days) and prints it as its
last line, so always take `tail -n1`. Other modes: `--force` (ignore the cache, e.g. after a 401), `--register` (one time:
emails a login code to `EME_USER`, registers this machine's key), `--app-password` (same check, registers an application
password instead), `--keygen`. An agent cannot register for itself; the code needs a person at a terminal. With nothing in
`.env` and no terminal the script prints "no registered key" and returns an empty token.

## Endpoints (`/site/mediadb/services/authentication/`)

- `sendusercode.json` — POST `username` or `email`; emails a 6 digit code (and a login link token).
- `token.json` — POST `grant_type=key` with `username`, `key_id`, `timestamp` (ms), `nonce` (16-128 chars), `signature`
  (base64url signature of `username\nkey_id\ntimestamp\nnonce`, no trailing newline). Timestamp must be within 5 minutes
  and each nonce works once. Add `createsession=true` (browsers) to also start the server session; the response then has
  `redirect` = the page that was refused before the login page. `grant_type=otp` with `email`, `code` and `publickey`
  redeems an emailed code and registers the key in one call. Errors are `{ "error", "error_description" }` with HTTP 400.
- `registerkey.json` — POST `publickey` + `label`, or `keytype=apppassword` + `label`. Needs a logged in user, or
  `grant_type=otp` with `username` and `code`. Refused for callers that logged in with an application password and while
  an administrator is switched to another user.
- `login.json` — session login with an emailed `templogincode` (plus `email` or `accountname`); add `publickey` (or `keytype=apppassword`) to register in the same call. There is no password login.
- `listkeys.json`, `revokekey.json` (`keyid`) — manage your own keys.

Algorithm details are in `StringEncryption` (`generateKeyPair`, `verifySignature`, `verifyKeyLogin`, `createAccessToken`,
app password helpers). The token is `{userid}keyexp{keyid}.{expiryMillis}.{hmac}`, with the HMAC bound to the public key.
Bearer values are resolved by `UserManager.getUserByBearerToken`, called from `AutoLoginOAuth`.

## Browsers

`plugins/community/html/default/components/javascript/emedia/browserkey.js` (included from `theme/layouts/includes.html`)
makes a non-extractable key in IndexedDB (`emebrowserkey`) on the first logged-in page load, registers the public key,
and signs in again when a page loads logged out. Logout deletes and revokes the key. No persistent login cookie exists;
only the normal server session. It needs a secure context (`https` or `localhost`) and logs every decision to the browser
console with the prefix `[browserkey]`. Delete only `JSESSIONID` (not IndexedDB) to test auto-login.

Email links never carry a key: they go to `authentication/confirmlogin.html` with a single use, one hour `linktoken`
(stored on the `templogincode` row) and sign in on the `POST`, never on `GET`.

## Pitfalls we hit

- Fields in a `.xml` fields file must be `index="true"` to be saved at all (see `field-definitions-maintenance`).
  `publickey`/`secrethash` with `index="false"` made every key login fail with a generic `invalid_grant`.
- `redirect` is a reserved page value (`WebPageRequest.redirect` refuses once it is set); the JSON field is built from
  `loginredirect`.
- Velocity `#ifnotnull($x)` needs the `$`; without it the branch always runs. Keep `token.json` valid JSON on every path
  because `browserkey.js` hides parse failures.
- Java changes need `bin/restart.sh` (it needs VS Code reachable on port 3710). Compile for checking only with
  `BUILD_DIR=<scratch dir> bin/compile.sh` so the running server's `build/` is untouched.
- Application logs go to the VS Code debug console, not a file; `tomcat/logs/localhost_access_log.*` shows which
  requests happened and their status codes.
