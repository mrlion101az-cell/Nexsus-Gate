# NexusGate v0.3.0

A password-to-join gate, independent of whitelist. Nobody plays until they type the current
password for their platform -- Java players and Bedrock/Xbox players each need their OWN
password (added in v0.2.0; v0.1.0 only gated Java and let Bedrock skip the gate entirely).

## v0.3.0: fixed players getting disconnected right after typing the password

Root cause: the freeze mechanic works by cancelling movement, but that alone doesn't stop
gravity -- every tick the server tries to pull a frozen player down and cancels it, and the
player's client quietly queues up the difference. Over a long freeze (up to the 60s timeout),
that queue can grow; the instant the freeze lifts, the client sends a packet trying to "catch
up" all at once, and the server's own built-in anti-speedhack check reads that as an
impossible jump -- disconnecting the player right after a correct password. Fixed by turning
off gravity for the whole time a player is pending, and forcing a hard position resync the
moment they authenticate. Full technical writeup in CHANGES.md.

**If it still happens after updating:** it's a different cause than the one above. Check
`plugins/NexusGate/auth-log.txt` for what actually happened around that player's disconnect
(a `LOCKOUT-REJECT` line means their IP was locked out -- possibly from someone else on the
same network failing the password earlier, see "A note on the IP lockout" below) and send me
the exact disconnect message the player saw.

## New in v0.3.0: who's used the password, and banning one player without changing it

`/nexusgate knownusers` lists every gamer tag that's ever successfully typed the password
(first seen, last seen, how many times, which platform). `/nexusgate ban <player> [reason]`
revokes just that one player's access -- checked before they can even finish connecting,
independent of the shared password (nobody else needs to know it changed) and independent of
the server's own `/ban` (this is layered on top, NexusGate-only). Kicks them immediately if
they're already online. `/nexusgate unban <player>` reverses it, `/nexusgate banlist` shows
who's banned and why. See "Commands" below.

## Get unblocked right now

As of v0.2.2, both passwords live in **plain text** directly in `plugins/NexusGate/config.yml`,
under a `passwords:` section. Open that file and you'll see (or, on an upgraded server, need to
add) exactly this:

```yaml
passwords:
  java: "5801"
  bedrock: "2981"
```

Type the password in, save the file, and it works on your very next `/login` attempt -- no
reload, no restart. That's the whole fix: no more hash, no more separate password.yml, no more
"is it actually stored or not" -- it's just sitting right there in the config, in the open, and
whatever's on that line is exactly what `/login <that>` gets checked against.

The same values from the config above -- `java: 5801`, `bedrock: 2981` -- are also what a brand
new install ships with by default. If you're upgrading an EXISTING server instead, your
config.yml won't have those filled in for you automatically, so either edit the file by hand as
shown above, or run these two commands once from console (does the exact same edit for you):

```
/nexusgate setpassword java 5801
/nexusgate setpassword bedrock 2981
```

Run `/nexusgate status` afterward and it'll print the current password for each platform back to
you, so you can confirm it's exactly what you think it is.

## Why v0.2.0/v0.2.1 kept failing, and why this is the real fix

Two real bugs in a row came from the same root cause: the password lived as a salted hash in a
separate `password.yml`, so there was never a way to just look at it and confirm what was
actually stored versus what you were typing. v0.2.0 had a command that could silently overwrite
the wrong platform's hash; v0.2.1 fixed that specific command bug but the underlying "you can't
see what's actually stored" problem was still there for anything else that could cause a
mismatch. Moving to plain text in config.yml removes that whole category of bug -- there's
nothing hidden left to get out of sync with what you typed.

This is a real security trade-off, worth saying plainly: anyone who can read config.yml (i.e.
anyone with file access to your server) can now read the password too. For a shared "front door"
password on a private server -- where file access already means full control of the server
anyway -- that's a reasonable trade for being able to see and trust exactly what's configured.
It would not be a reasonable trade for a real per-player account credential, which this was
never meant to be.

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
clean, zero errors, zero warnings (`-Xlint:all`) for the v0.1.0 code and everything added since,
including this v0.2.2 change. That confirms the Java itself is internally consistent, but not
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
2. **Set both passwords**, either directly in `config.yml` under `passwords:`, or from console:
   - `/nexusgate setpassword java yourjavapasswordhere`
   - `/nexusgate setpassword bedrock yourbedrockpasswordhere`
   - Until a platform's password is set (blank in config.yml), that platform's players join
     unguarded and a warning is logged (a fresh install shouldn't lock out the only person who
     can fix that).
3. Change either one any time the same way -- they're fully independent, and changing one
   never touches the other. Anyone already in when you change it stays in for that session.

## Commands

- `/login <password>` -- what players type. Only usable while pending.
- `/nexusgate setpassword java <password>` / `/nexusgate setpassword bedrock <password>` --
  set/change one platform's password (just edits the same `passwords:` lines in config.yml for
  you). `java`/`bedrock` must be spelled out once any password already exists (only a
  brand-new, never-configured install accepts the shorter `/nexusgate setpassword <password>`
  and treats it as Java). (`nexusgate.admin`, default: op)
- `/nexusgate reload` -- reload config.yml settings (timeout, attempts, etc; passwords are
  always read live from disk anyway, so this isn't needed just to pick up a password change).
- `/nexusgate status` -- shows each platform's current password, timeout/attempt settings, how
  many players are currently stuck at the gate, and how many IPs are currently locked out.
- `/nexusgate knownusers` -- every gamer tag that's ever typed the password correctly, most
  recently seen first: platform, login count, last seen, and whether they're currently banned.
- `/nexusgate ban <player> [reason]` -- revokes that one player's access to the password gate
  (independent of the shared password and of the server's normal `/ban`). Kicks them
  immediately if online. (`nexusgate.admin`)
- `/nexusgate unban <player>` -- reverses it. (`nexusgate.admin`)
- `/nexusgate banlist` -- who's currently NexusGate-banned, why, by whom, and when.

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

- `passwords.java` / `passwords.bedrock` -- the passwords themselves, in plain text, right here.
  See "Get unblocked right now" above.

Any old `plugins/NexusGate/password.yml` from before v0.2.2 (a salted hash) is no longer read.

## A note on the IP lockout

It's in-memory only and resets on a server restart -- a real deterrent against someone sitting
there guessing, but not a permanent ban list. As of v0.3.0 there's a real permanent option
alongside it: `/nexusgate ban <player>` for a specific person, independent of the lockout.

It's also **keyed by IP, not by player**. If two different people play from the same
house/router (same public IP), one of them failing the password enough times locks out BOTH of
them for the cooldown -- this can look exactly like "my friend can't connect" without either of
them having done anything wrong. `auth-log.txt` now logs a `LOCKOUT-REJECT` line whenever this
happens, so you can tell a shared-IP lockout apart from an actual bug report.
