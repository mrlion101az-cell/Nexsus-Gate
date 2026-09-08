# NexusGate v0.2.1

A password-to-join gate, independent of whitelist. Nobody plays until they type the current
password for their platform -- Java players and Bedrock/Xbox players each need their OWN
password (added in v0.2.0; v0.1.0 only gated Java and let Bedrock skip the gate entirely).

## If you hit a "correct password says wrong" bug on v0.2.0

That was a real bug in v0.2.0, now fixed in v0.2.1 -- see CHANGES.md for the full explanation.
Short version: `/nexusgate setpassword <password>` with no `java`/`bedrock` in it used to
silently default to setting the JAVA password, which meant running it out of habit right
after upgrading (to set what you thought was a new Bedrock password) could silently overwrite
your working Java one instead. **To recover immediately, on v0.2.0 or v0.2.1 alike, run:**
`/nexusgate setpassword java <your real password>` from console. In v0.2.1, that ambiguous
bare form is refused outright once any password already exists, so this can't happen again.

## What's new since v0.1.0

You asked for Bedrock/Xbox players to also have to type in a password, separate from the Java
one. That's in: Bedrock/Xbox connections go through the exact same freeze-until-`/login`
flow Java players always have, but checked against their own password, set independently with
`/nexusgate setpassword bedrock <password>` and `/nexusgate setpassword java <password>`.
Both must now be spelled out explicitly once a server has been configured at all (see the bug
note above for why the shortcut that used to skip this was removed).

**If you're upgrading from v0.1.0, one thing needs your attention:** your existing
`config.yml` already has `exempt-bedrock: true` written to it from before. That flag still
means the same thing it always did -- Bedrock/Xbox players skip the gate entirely -- so on its
own, upgrading the jar will NOT start gating your Bedrock players; they'll keep joining
unguarded until you either change that line to `exempt-bedrock: false` in your existing
`config.yml`, or delete/rename that file so a fresh copy generates with the new default
(`false`). Either way, then set a Bedrock password. Your existing Java password is completely
unaffected either way -- the plugin automatically moves it into the new format the first time
it loads, with a one-line log message confirming it did.

## Important: this build could not be compile-verified against the real Paper API

Same situation as v0.1.0, and as the Nexus plugins built since: this sandbox has no route to
Maven Central or repo.papermc.io, so there's no way to pull down the actual Paper API jar to
compile against. To compensate, I hand-wrote minimal stand-in versions of every Bukkit/Paper
class this plugin touches and compiled the real source against those by hand with `javac` --
clean, zero errors, zero warnings (`-Xlint:all`) for both the pre-existing v0.1.0 code and
everything added for v0.2.0. That confirms the Java itself is internally consistent, but not
that every signature matches the real jar exactly for your exact server build.

**What to do:** the included `pom.xml` lets you (or your normal build setup) run `mvn package`
on your own machine, which has real internet access. If it doesn't compile cleanly, send me
the exact error and I'll fix it directly.

## How it works

1. Player joins -> frozen in place (no movement, interaction, damage, inventory, chat, or
   commands except `/login`), told to run `/login <password>`.
2. Correct password for their platform -> unfrozen instantly, free to play.
3. Wrong password -> more tries (3 total by default, configurable), told how many are left.
4. Final wrong password, or a timeout (60s by default) with nothing typed -> kicked, and their
   IP gets locked out for an escalating cooldown, checked and enforced *before* they even
   finish connecting next time.
5. Which password applies is decided by platform detection (Floodgate if it's installed, a
   UUID-based fallback if it isn't) -- Java players are checked against the Java password,
   Bedrock/Xbox players against the Bedrock one. `settings.exempt-bedrock: true` skips this
   entirely for Bedrock/Xbox, same as v0.1.0's only option.

This does not touch or replace your whitelist -- it's a completely separate layer.

## Setup

1. Drop the compiled jar in `plugins/`, start the server once so it generates
   `plugins/NexusGate/config.yml`.
2. **Set both passwords from console** (not in-game chat, so neither ever touches chat logs):
   - `/nexusgate setpassword java yourjavapasswordhere`
   - `/nexusgate setpassword bedrock yourbedrockpasswordhere`
   - Until a platform's password is set, that platform's players join unguarded and a warning
     is logged (a fresh install shouldn't lock out the only person who can fix that).
3. Change either one any time the same way -- they're fully independent, and changing one
   never touches the other. Anyone already in when you change it stays in for that session.

## Commands

- `/login <password>` -- what players type. Only usable while pending.
- `/nexusgate setpassword java <password>` / `/nexusgate setpassword bedrock <password>` --
  set/change one platform's password. `java`/`bedrock` must be spelled out once any password
  already exists (only a brand-new, never-configured install accepts the shorter
  `/nexusgate setpassword <password>` and treats it as Java). (`nexusgate.admin`, default: op)
- `/nexusgate reload` -- reload config.yml (does not touch either stored password).
- `/nexusgate status` -- shows whether each platform's password is set, current
  timeout/attempt settings, how many players are currently stuck at the gate, and how many
  IPs are currently locked out.

## Config highlights (`plugins/NexusGate/config.yml`)

- `settings.timeout-seconds` / `settings.max-attempts` -- self-explanatory.
- `settings.exempt-operators` -- defaults to **false** on purpose. Ops are gated like everyone
  else unless you flip this.
- `settings.trusted-uuids` -- a list of UUIDs that always skip the gate entirely, on either
  platform. The `nexusgate.bypass` permission does the same thing for anyone you grant it to.
- `settings.exempt-bedrock` -- now defaults to **false** (new installs) so Bedrock/Xbox
  players are gated by their own password like everyone else. Set `true` to go back to
  letting them skip it entirely, v0.1.0-style. See the upgrade note above if you already have
  a config.yml from before this version.
- `lockout.durations-seconds` -- the escalating IP lockout ladder (shared across both
  platforms -- an IP that gets locked out is locked out regardless of which password it was
  guessing).
- `messages.*` -- every player-facing message, `&`-color-coded.

Both passwords live in `plugins/NexusGate/password.yml` as salted SHA-256 hashes, under
separate `java:`/`bedrock:` sections -- never in plaintext, never in `config.yml`.

## A note on the IP lockout

It's in-memory only and resets on a server restart -- a real deterrent against someone sitting
there guessing, but not a permanent ban list. If you want permanent bans layered on top for
repeat offenders, that's a natural future addition (e.g. auto-add to a ban list after N
lockouts) -- just say the word.
