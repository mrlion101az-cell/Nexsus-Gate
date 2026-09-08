# NexusGate changelog

## v0.5.0 -- ban-evasion alerts can now auto-ban, on explicit request

Follow-up to v0.4.0, same session: "Make an auto ban... I want to turn this server into a
[fortified] military base... take the security of this server to level one hundred out of one
out of ten." This explicitly reverses the design decision behind v0.4.0's alert-only behavior --
worth stating plainly, since it's a real trade-off, not a pure improvement.

**New: `alt-detection.mode` in config.yml, two values.** `"watch"` is the old v0.4.0 behavior
(alert nexusgate.watch + log ALT-ALERT, never block). `"autoban"` -- what config.yml now ships
with -- rejects the connection immediately the moment a brand-new account's IP matches a
currently-banned player's IP history, and auto-bans that account right then (`/nexusgate ban`,
programmatically, with reason "Auto-ban: shared connection with banned player(s) ..." and
banned-by "NexusGate (auto)"). A second setting, `alt-detection.ban-ip-too` (default `true`),
also auto-IP-bans the connecting address itself, not just the account -- so a repeat attempt
from a fresh account on the same connection is rejected at the door too, not just the one that
tripped the alert.

**The trade-off, said plainly (same as every other security default in this plugin's history):**
this is a heuristic, not a certainty. Two people who genuinely share a house, a router, a dorm,
or a mobile carrier's IP pool can trip this exactly as easily as one person evading a ban on
purpose. Every auto-ban still fires the same live nexusgate.watch alert and AUTOBAN audit-log
line the alert-only mode always used, specifically so a false positive gets noticed fast --
reversing one is `/nexusgate unban <player>` and, if it was also IP-banned,
`/nexusgate unbanip <ip>`. Dialing back to the safer, human-reviewed behavior at any time is one
config.yml line (`alt-detection.mode: "watch"`) plus `/nexusgate reload`.

`/nexusgate status` now also reports the current alt-detection mode (and, in autoban mode,
whether ban-ip-too is on) so it's visible at a glance without opening config.yml.

- Compile-verified clean (0 errors, 0 warnings, `-Xlint:all`) against the same stub library as
  v0.4.0 -- no new stub surface was needed for this change.

## v0.4.0 -- IP bans + ban-evasion alerts

Requested: "make sure that nobody... has access to bringing harm or destruction... to the
players that are trying to now currently play" -- more generally, a way to stop a banned player
from just reconnecting on a fresh account. Before writing any auto-reject logic, I asked one
specific question: when a brand-new account connects from an IP that's also on file for a
banned player, should it be let in with staff alerted, or rejected outright? **Explicit answer:
let them in, alert staff instantly.** That decision drives everything below -- a shared
IP (same house, same router, a college dorm, a phone hotspot) is common and innocent, so a
heuristic IP match must never be the thing that locks someone out. Only a human, deciding on
purpose, gets to do that.

**New: IP bans (`/nexusgate banip <ip> [reason]`).** A second, independent ban list, keyed by IP
instead of UUID. Checked in `AsyncPlayerPreLoginEvent`, same as a player ban, and DOES
auto-reject -- because unlike the alert below, this is a direct admin decision, not a guess.
Kicks anyone already online from that IP at the moment it's applied. `/nexusgate unbanip <ip>`
reverses it, `/nexusgate ipbanlist` shows what's currently IP-banned, why, by whom, and when.
New `messages.ip-banned-kick` config line. Stored as a list of maps in `access.yml` rather than
IP-keyed config sections, because Bukkit's config paths treat "." as a separator and an IP
address is full of them -- using one as a path segment directly would silently split
`1.2.3.4` into four nested sections instead of storing it as one key.

**New: IP history per known player.** Every successful login now also appends that connection's
IP to a capped (8 most recent) history list for that player in `access.yml`, alongside the
existing first-seen/last-seen/logins/platform fields already tracked since v0.3.0.

**New: ban-evasion ALERT (never auto-reject).** On every pre-login, NexusGate now checks whether
the connecting IP appears in any currently-UUID-banned player's IP history. If it does, the
connection is let through completely normally -- nothing is blocked, nothing is delayed -- but
everyone holding the new `nexusgate.watch` permission gets an instant in-game message naming
the connecting player and which banned player(s) share that IP, and an `ALT-ALERT` line is
written to `auth-log.txt`. It's then a one-command human judgment call: `/nexusgate altcheck
<player>` shows that player's full IP history and everyone else who's ever shared each of those
IPs (flagging which of them are banned), so an admin can tell "obviously the same person
evading a ban" apart from "two roommates" before reaching for `/nexusgate ban` or
`/nexusgate banip`.

**New: first-time-login alert.** The same `nexusgate.watch` audience also gets notified the
first time any gamer tag ever successfully authenticates, so a server with a lot of turnover
doesn't need `/nexusgate knownusers` polled manually to notice new arrivals.

**New commands, all `nexusgate.admin`:** `/nexusgate banip <ip> [reason]`, `/nexusgate unbanip
<ip>`, `/nexusgate ipbanlist`, `/nexusgate altcheck <player>`.

**New permission:** `nexusgate.watch` (default: op) -- who receives the live ban-evasion and
first-time-login alerts. Separate from `nexusgate.admin` so you can hand it to trusted staff who
shouldn't necessarily be able to change the password or issue bans themselves.

- Compile-verified clean (0 errors, 0 warnings, `-Xlint:all`) against the hand-built stub
  library (extended this round: `Bukkit.getOnlinePlayers()`, `Bukkit.isPrimaryThread()`,
  `BukkitScheduler.runTask(Plugin, Runnable)`, `ConfigurationSection.getMapList(String)` and a
  `YamlConfiguration` implementation of it, `ChatColor.stripColor(String)`). Same sandbox network
  restriction as every prior version -- see the "Important" section of the README. This session
  didn't have the other Nexus plugins' source on hand to regression-check against either --
  still worth doing whenever they're in the same session again.

## v0.3.0 -- fixed the post-login disconnect, added an access log + in-plugin bans

Reported: players were getting disconnected shortly after successfully typing `/login
<password>`, and a request for two new things: a log of every gamer tag that has ever used the
password, and a way to ban a specific player from using it (independent of the server's normal
`/ban`) without having to change the password for everyone.

**The disconnect bug.** Root cause: the freeze mechanic works by cancelling `PlayerMoveEvent`,
but that alone doesn't stop gravity -- every tick the server tries to pull a frozen player down,
the move gets cancelled, and the player's client keeps a queue of position changes it still
thinks are pending. Over the up-to-60-second freeze window this queue can grow, and the instant
the freeze lifts (right after a correct password), the client sends a packet trying to "catch up"
all at once -- which the server's own built-in anti-speedhack check reads as an impossible jump
and disconnects the player right then. That's the actual mechanism behind "disconnected shortly
after typing the password."

Fix, in `GateListener`/`LoginCommandExecutor`:
- `player.setGravity(false)` the moment a player becomes pending (`onJoin`), so there's no
  downward pull for the freeze to keep fighting all timeout window long.
- `onMove` now also explicitly resets the player back to `from` in addition to cancelling the
  event -- redundant on most servers (cancelling is supposed to do this on its own) but costs
  nothing and removes a dependency on that being true on every fork/version.
- New `GateListener.completeAuth(player)`, called right after a correct password: restores
  gravity and re-teleports the player to their own current location, forcing a hard
  client/server position resync before normal movement resumes -- this is what actually flushes
  any queued client-side "catch up" before it can trigger the false speedhack kick.
- Also added an audit-log line (`LOCKOUT-REJECT`) for the pre-login IP-lockout rejection path,
  which previously logged nothing at all -- and a config.yml comment flagging that the IP
  lockout is shared by everyone behind the same router: if two players are on the same home
  network, one of them failing the password enough times locks out both, which can look
  exactly like "my friend can't connect" without being a bug. Check `auth-log.txt` for a
  `LOCKOUT-REJECT` line on the IP in question to tell the two apart.

**New: access log.** Every successful `/login` now also records into a new `access.yml` --
gamer tag, first-seen, last-seen, total logins, and platform (java/bedrock). `/nexusgate
knownusers` lists everyone who's ever gotten in, most-recently-seen first.

**New: NexusGate-specific bans.** `/nexusgate ban <player> [reason]` revokes just that one
player's access to the password gate -- checked at the earliest possible point
(`AsyncPlayerPreLoginEvent`, before they can even finish connecting), independent of the shared
password (nobody else needs to know it changed) and independent of the server's own `/ban`
(this is a NexusGate-only decision, layered on top). Kicks them immediately if they're already
online when banned. `/nexusgate unban <player>` reverses it, `/nexusgate banlist` shows who's
banned, why, by whom, and when. New `messages.banned-kick` config line for the message a banned
player sees.

- Compile-verified clean (0 errors, 0 warnings, `-Xlint:all`) against a hand-built stub of
  every Bukkit/Paper class this plugin touches (same sandbox network restriction as before --
  see the "Important" section of the README). This session didn't have the other Nexus
  plugins' source on hand to regression-check against the way past NexusGate sessions did --
  worth re-running that check next time they're both in the same session.

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
