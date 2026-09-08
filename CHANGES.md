# NexusGate changelog

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
