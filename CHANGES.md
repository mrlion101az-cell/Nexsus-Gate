# NexusGate changelog

## v0.2.2 -- passwords moved to plain text in config.yml

Requested directly, after v0.2.1 still wasn't cutting it: "why don't we make an update to what
the password is in the config file so I can physically see it. And if I type it, it lets me
in." Fair -- two real bugs in a row (v0.2.0's silent overwrite, then whatever was still wrong
after v0.2.1) both came from the same root problem: the password lived as a salted hash in a
separate password.yml, so there was never a way to just look at it and confirm what was actually
stored versus what you were typing. That indirection is gone now.

- Both passwords now live directly in `config.yml` under `passwords.java` / `passwords.bedrock`,
  in plain text. Open the file, see exactly what's set, edit it by hand, save -- it takes effect
  on the very next join or `/login` attempt. No reload, no restart, no hashing, no salt, no
  password.yml.
- `/nexusgate setpassword java|bedrock <password>` still works exactly the same way -- it just
  writes straight into those same two config.yml lines now instead of into password.yml.
- `/nexusgate status` now shows the actual current password for each platform (not just
  yes/no) -- there's no confidentiality left to protect once it's plaintext in the config file
  anyway, so it might as well be useful for confirming what's set.
- **If you're upgrading from v0.2.0/v0.2.1:** your old password.yml (the hashed one) is no
  longer read at all -- a hash can't be converted back into the plaintext it came from, so there
  is no way to auto-recover the old password from it. The plugin will add empty
  `passwords: {java: "", bedrock: ""}` lines to your existing config.yml automatically on next
  load and log a warning if it also finds an old password.yml, but you need to type the actual
  password(s) in yourself -- either directly in config.yml, or via
  `/nexusgate setpassword java <password>` / `/nexusgate setpassword bedrock <password>` (either
  way ends up in the same place now).
- Audit log lines (`auth-log.txt`) for PENDING/SUCCESS/FAIL/TIMEOUT now all include which
  platform (`kind=java` or `kind=bedrock`) the attempt was checked against, to make any future
  "why isn't my password working" report much faster to diagnose without guessing.
- Worth saying plainly: this is a real security trade-off, not a cosmetic change. Storing the
  password in plaintext means anyone who can read config.yml (anyone with file access to the
  server, basically) can read it. For a shared "front door" password on a private server where
  file access already implies full control anyway, that's a reasonable trade for being able to
  see and trust exactly what's configured. It would NOT be a reasonable trade for an actual
  per-player account credential.
- Compile-verified clean (0 errors, 0 warnings, `-Xlint:all`) against the hand-built stub
  library, regression-checked against NexusWarbeasts, NexusFamily, and NexusBathroom -- no
  breakage in any of them.

## v0.2.1 -- fixed a real bug: setpassword could silently overwrite the wrong platform

Reported: after upgrading to v0.2.0, a previously-correct password started being rejected,
with nothing else changed.

**Root cause, confirmed:** v0.2.0 kept the old two-word command,
`/nexusgate setpassword <password>`, working by having it silently default to the JAVA
password -- meant purely as backward compatibility so existing muscle memory wouldn't break.
But that's exactly the command someone reaches for right after this update, out of habit,
when they actually mean to set the NEW Bedrock password (not knowing/remembering they now
need to add `bedrock` before it). Run that way, it silently overwrote the working Java
password with whatever was meant for Bedrock -- no warning, no error, just a quietly wrong
password from then on.

**Fix:** the bare two-word form now only works on a genuinely fresh install (neither password
configured yet) -- same as v0.1.0 always behaved, no change there. The moment either password
already exists (any upgraded server, i.e. every server that hit this bug), the bare form is
refused outright with a message spelling out both explicit options
(`/nexusgate setpassword java <password>` / `/nexusgate setpassword bedrock <password>`)
instead of silently guessing. This can't happen again after this fix.

**To recover right now** (this doesn't require the fixed jar -- it works on v0.2.0 too):
run `/nexusgate setpassword java <your real password>` from console to put the Java password
back to what it should be. If you also want a Bedrock password, set it explicitly and
separately with `/nexusgate setpassword bedrock <bedrock password>`.

Compile-verified clean (0 errors, 0 warnings) same as every other change in this plugin.

## v0.2.0 -- separate Bedrock/Xbox password

Requested: "can we make an update to where we could have a separate password for Xbox
players? So Xbox and Java players have to type in a password."

- `PasswordManager` now tracks two fully independent passwords (`PasswordKind.JAVA` /
  `PasswordKind.BEDROCK`), each its own salted SHA-256 hash in `password.yml` under separate
  `java:`/`bedrock:` sections.
- Automatic, lossless migration on first load after upgrading: v0.1.0's old top-level
  `password-set`/`salt`/`hash` keys are moved into the new `java:` section and the file is
  rewritten, so an existing Java password survives the upgrade untouched. Logged once.
- `BedrockUtil` reworked to key off a `UUID` instead of a `Player` (so it also works from
  `AsyncPlayerPreLoginEvent`, which has no `Player` object yet) and gained `kindOf(...)`, a
  small helper that picks which password applies to a given connection.
- `GateListener`: Bedrock/Xbox connections now go through the same freeze/gate flow as Java
  instead of being auto-exempted -- `settings.exempt-bedrock` is repurposed from "always skip
  Bedrock" to "still allow explicitly opting Bedrock back out entirely", and now defaults to
  `false` for new installs (existing config.yml files keep whatever they already have on disk
  -- see the README's upgrade note, this can't be rewritten automatically without clobbering
  an admin's own customizations). A platform whose password isn't configured yet is still
  auto-exempted (gating with no password would just lock everyone out), same principle as
  v0.1.0 had for the single password.
- `/nexusgate setpassword [java|bedrock] <password>` -- the old two-word form
  (`/nexusgate setpassword <password>`) still works and still means "set the Java password",
  so existing muscle memory/scripts don't break.
- `/nexusgate status` now reports both platforms' password state separately.
- `/login` resolves which password to check against automatically based on platform
  detection -- players never need to specify which one they're entering.
- Compile-verified clean (0 errors, 0 warnings, `-Xlint:all`) against the hand-built stub
  library, and regression-checked against NexusWarbeasts, NexusFamily, and NexusBathroom's
  existing source after the stub additions this required (`AsyncPlayerPreLoginEvent` gained
  `getUniqueId()`/`getName()`) -- no breakage in any of them.

## v0.1.0 -- initial release

Java-only password-to-join gate, independent of whitelist. See the "How it works" section of
the README for the full mechanic (freeze-on-join, `/login`, escalating IP lockouts, Bedrock
auto-exempt). Delivered as `NexusGate-v0_1_0-source.zip`.
