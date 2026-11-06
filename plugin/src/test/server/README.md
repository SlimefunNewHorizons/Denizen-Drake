# Standalone 26.2 diagnostic

This plugin is outside the Maven test source root and never goes in Denizen.jar.
Compile it against the **shaded** runtime-26.2 distribution and the server API
classpath. Package the resulting class together with this directory's plugin.yml.
Install only Denizen and the diagnostic on a disposable loopback server with
world name `smoke676`, no players, and no external integrations. Use Java 25,
a bounded heap, and a process timeout. It shuts down the server itself.

Disable the Nether with `misc.enable-nether: false` in `config/paper-global.yml`,
and the End with `settings.allow-end: false` in `bukkit.yml`. On the tested
26.2 server, server.properties `allow-nether=false` alone did not prevent the
Nether from loading. The guard refuses to run with more than one world.

Optional `fixtures/*.schem` files must be local copies of Sponge v2 schematics
with palette names valid for the running server. Do not commit real schematics,
NBT, inventory contents, or logs. Sponge v3 is skipped explicitly. The diagnostic
places block entities at a disposable target and compares synthetic chest data.
It then deliberately attaches chest NBT to stone to exercise delayed-paste abort.

Read `SMOKE676 RESULT` and require `failures=0`, `aborts=1`, `successes=0`.
The server's normal shutdown exit code alone does **not** mean the checks passed.
The deliberate stone/chest mismatch is expected to produce one scheduler NPE.

## Recorded result, 2026-11-05

Purpur 26.2-138, Java 25, runtime-26.2 distribution build 676:

- The first attempt stopped at the world guard because the Nether was enabled.
- After correcting only the disposable server's Nether setting, Denizen loaded.
- 24 local v2 copies loaded, two v3 copies skipped, 58 block-entity records tried.
- 44 old bed records threw NPE: 28 white, 12 red, four yellow beds.
- The synthetic chest PDC check passed; the item equality check failed. This is
  inconclusive: the fixture writes through `getBlockInventory()` before updating
  the captured state. Validate the source inventory baseline and distinguish
  live inventory from snapshot inventory before diagnosing item loss.
- Eight ticks after the deliberately invalid paste: one failure callback, no
  success callback, no later block placement, and the physics flag restored.
- Aggregate result: **45 failures**, not a passing runtime test. Clean shutdown
  exit 0; no further attempts after the two-failure stop rule.

[Paper's 26.2 announcement](https://papermc.io/news/26-2/) confirms removal of
bed block entities and the need to retain custom PDC elsewhere. The tested NMS
jar has BedBlock extending HorizontalDirectionalBlock and no BedBlockEntity.
The diagnostic reproduces this compatibility problem; it does not prove which
schematic originally triggered a particular live server task.
