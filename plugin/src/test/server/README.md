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
with palette names valid for the running server. The diagnostic adapts only
`minecraft:chain` to `minecraft:iron_chain` in memory; the files stay intact. Do not commit real schematics,
NBT, inventory contents, or logs. Sponge v3 is skipped explicitly. The diagnostic
places block entities at a disposable target and compares synthetic chest data.
It then deliberately attaches chest NBT to stone to exercise delayed-paste abort.

Read `SMOKE676 RESULT` and require `failures=0`, `aborts=1`, `successes=0`.
The server's normal shutdown exit code alone does **not** mean the checks passed.
The deliberate stone/chest mismatch must produce one explicit IllegalStateException, not an NPE.

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

## Bed archive and persistence regression

The 26.2 helper now recognizes old bed NBT only on bed blocks. It stores the
complete named GZIP NBT in the destination chunk PDC under
`denizen:legacy_bed_nbt/<x>_<y>_<z>/<sha256>`. The hash addresses the serialized
payload; different payloads retain separate entries. The source NBT is unchanged.
It is a recovery archive, not a replacement for other plugins' bed PDC APIs.
No automatic deletion or restoration is performed. Unexpected entity types on
beds and block entity NBT on ordinary blocks still abort the paste.

The chest fixture now fills the snapshot inventory before updating and asserts
the source's live inventory and PDC before capturing it. A synthetic bed test
covers unknown tags/PDC, duplicate pastes, retention of earlier payloads, and
rejection of chest NBT on a bed. Run a second time on the same disposable world
with `-Dsmoke676.phase=verify` (before `-jar`) to require the full saved payload
**before** any repaste, proving persistence through shutdown and restart.

Local schemas are processed one per scheduler tick to avoid combining all file
parsing in a single long task. Each schema still loads synchronously; very large
fixtures can delay a tick on this isolated diagnostic server. This scheduling
change does not alter the gameplay plugin's schematic loader.

## Recorded result after the fix, 2026-11-05 CLT (UTC-4)

`build_seguro.sh 25 ... mvn -B -Pruntime-26.2 -DBUILD_NUMBER=676
-DBUILD_CLASS=CUSTOM clean package`: 1m17s, 11 tests, zero failures/errors/skips.
On Purpur 26.2-138 / Java25, three diagnostic runs reported
`loaded=24 skippedV3=2 blockEntities=58` and
`failures=0 aborts=1 successes=0`, with zero NPE each. The second and third
runs required the archived synthetic bed payload to be present from disk before
repasting. Both source and pasted chest inventory/PDC baselines passed.

The first run parsed all fixtures in one task and triggered a watchdog warning;
the diagnostic was then changed to one fixture per tick. The final source ran
successfully with no watchdog warning. Its one scheduler IllegalStateException
is the deliberate stone/chest mismatch. Citizens is intentionally absent and
its reduced-functionality warning is expected. All runs saved and shut down.
No original schemas were changed. Sponge v3 remains untested. These runs do
not identify the exact schema behind a live Dallas task, nor establish that
the live server is fixed. No production artifact was replaced.
