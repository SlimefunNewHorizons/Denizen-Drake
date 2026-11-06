import com.denizenscript.denizen.scripts.commands.world.SchematicCommand;
import com.denizenscript.denizen.utilities.blocks.BlockSet;
import com.denizenscript.denizen.utilities.blocks.CuboidBlockSet;
import com.denizenscript.denizen.utilities.blocks.FullBlockData;
import com.denizenscript.denizen.utilities.blocks.SpongeSchematicHelper;
import com.denizenscript.shaded.net.adventure.nbt.BinaryTagIO;
import com.denizenscript.shaded.net.adventure.nbt.CompoundBinaryTag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.FileInputStream;

/** Separate test plugin: never package this class inside the gameplay jar. */
public class DenizenBlockNbtSmoke extends JavaPlugin {
    private int failures;
    private int aborted;
    private int succeeded;
    private Block untouched;

    private final int[] fixtureCounts = new int[3];

    private void checkLocalSchematics(Block target, Runnable done) {
        File[] files = new File("fixtures").listFiles((dir, name) -> name.endsWith(".schem"));
        if (files == null || files.length == 0) {
            done.run();
            return;
        }
        new org.bukkit.scheduler.BukkitRunnable() {
            private int next;

            @Override
            public void run() {
                try {
                    if (next < files.length) {
                        checkSchematicFile(files[next++], target);
                        return;
                    }
                    cancel();
                    check(fixtureCounts[2] > 0, "local fixtures include block entities");
                    getLogger().info("SMOKE676 FIXTURES loaded=" + fixtureCounts[0]
                            + " skippedV3=" + fixtureCounts[1] + " blockEntities=" + fixtureCounts[2]);
                    done.run();
                }
                catch (Throwable error) {
                    cancel();
                    failures++;
                    getLogger().log(java.util.logging.Level.SEVERE, "SMOKE676 fixture failure", error);
                    finish();
                }
            }
        }.runTaskTimer(this, 1, 1);
    }

    private void checkSchematicFile(File file, Block target) throws Exception {
        CompoundBinaryTag root;
        try (FileInputStream stream = new FileInputStream(file)) {
            root = BinaryTagIO.reader().readNamed(stream, BinaryTagIO.Compression.GZIP).getValue();
        }
        if (root.getInt("Version") == 3 || root.getCompound("Schematic").getInt("Version") == 3) {
            fixtureCounts[1]++;
            return;
        }
        // Only adapt the removed chain palette name in disposable copies.
        CompoundBinaryTag.Builder palette = CompoundBinaryTag.builder();
        for (java.util.Map.Entry<String, ? extends com.denizenscript.shaded.net.adventure.nbt.BinaryTag> entry : root.getCompound("Palette")) {
            String name = entry.getKey();
            if (name.equals("minecraft:chain") || name.startsWith("minecraft:chain[")) {
                name = name.replace("minecraft:chain", "minecraft:iron_chain");
            }
            palette.put(name, entry.getValue());
        }
        CompoundBinaryTag compatible = CompoundBinaryTag.builder().put(root).put("Palette", palette.build()).build();
        java.io.ByteArrayOutputStream encoded = new java.io.ByteArrayOutputStream();
        BinaryTagIO.writer().writeNamed(java.util.Map.entry("Schematic", compatible), encoded, BinaryTagIO.Compression.GZIP);
        CuboidBlockSet set = SpongeSchematicHelper.fromSpongeStream(new java.io.ByteArrayInputStream(encoded.toByteArray()));
        fixtureCounts[0]++;
        for (FullBlockData block : set.blocks) {
            if (block.tileEntityData == null) {
                continue;
            }
            fixtureCounts[2]++;
            target.setType(Material.AIR, false);
            boolean previous = SchematicCommand.noPhys;
            try {
                SchematicCommand.noPhys = true;
                block.set(target, false);
            }
            catch (RuntimeException error) {
                check(false, "local schematic material=" + block.data.getMaterial() + " error=" + error.getClass().getSimpleName());
            }
            finally {
                SchematicCommand.noPhys = previous;
            }
        }
    }

    private void checkLegacyBedArchive(Block target) throws Exception {
        CompoundBinaryTag legacy = CompoundBinaryTag.builder().putString("Id", "minecraft:bed")
                .putIntArray("Pos", new int[]{0, 0, 0}).putString("unknown", "preserve-me")
                .put("PublicBukkitValues", CompoundBinaryTag.builder().putString("test:payload", "synthetic").build()).build();
        java.util.Set<NamespacedKey> before = new java.util.HashSet<>(target.getChunk().getPersistentDataContainer().getKeys());
        if ("verify".equals(System.getProperty("smoke676.phase"))) {
            check(before.stream().filter(k -> k.getNamespace().equals("denizen") && k.getKey().startsWith("legacy_bed_nbt/")).count() >= 2,
                    "bed archives persisted across server restart");
        }
        if ("verify".equals(System.getProperty("smoke676.phase"))) {
            boolean persistedPayload = false;
            for (NamespacedKey archive : before) {
                if (archive.getNamespace().equals("denizen") && archive.getKey().startsWith("legacy_bed_nbt/")) {
                    byte[] bytes = target.getChunk().getPersistentDataContainer().get(archive, PersistentDataType.BYTE_ARRAY);
                    CompoundBinaryTag saved = BinaryTagIO.reader().readNamed(new java.io.ByteArrayInputStream(bytes), BinaryTagIO.Compression.GZIP).getValue();
                    persistedPayload |= legacy.equals(saved);
                }
            }
            check(persistedPayload, "complete bed payload loaded from disk before repaste");
        }
        FullBlockData bed = new FullBlockData(Bukkit.createBlockData(Material.WHITE_BED));
        bed.tileEntityData = legacy;
        bed.set(target, false);
        java.util.Set<NamespacedKey> first = new java.util.HashSet<>(target.getChunk().getPersistentDataContainer().getKeys());
        bed.set(target, false);
        check(first.equals(target.getChunk().getPersistentDataContainer().getKeys()), "repeated bed paste is idempotent");
        boolean found = false;
        for (NamespacedKey archive : first) {
            if (!archive.getNamespace().equals("denizen") || !archive.getKey().startsWith("legacy_bed_nbt/")) {
                continue;
            }
            byte[] bytes = target.getChunk().getPersistentDataContainer().get(archive, PersistentDataType.BYTE_ARRAY);
            CompoundBinaryTag saved = BinaryTagIO.reader().readNamed(new java.io.ByteArrayInputStream(bytes), BinaryTagIO.Compression.GZIP).getValue();
            if (legacy.equals(saved)) {
                found = true;
            }
        }
        check(found, "complete bed NBT and PDC archived without mutation");
        bed.tileEntityData = CompoundBinaryTag.builder().put(legacy).putString("unknown", "second-version").build();
        bed.set(target, false);
        check(target.getChunk().getPersistentDataContainer().getKeys().containsAll(first), "new bed payload retains previous archives");
        bed.tileEntityData = CompoundBinaryTag.builder().putString("id", "minecraft:chest").build();
        boolean rejected = false;
        try {
            bed.set(target, false);
        }
        catch (IllegalStateException expected) {
            rejected = true;
        }
        check(rejected, "chest NBT on a bed is rejected");
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            failures++;
            getLogger().severe("SMOKE676 FAIL " + message);
        }
    }

    @Override
    public void onEnable() {
        // Refuse to write to anything other than an empty, loopback test server.
        if (!"127.0.0.1".equals(getServer().getIp()) || !Bukkit.getOnlinePlayers().isEmpty()
                || Bukkit.getWorlds().size() != 1 || !"smoke676".equals(Bukkit.getWorlds().get(0).getName())) {
            throw new IllegalStateException("Requires a disposable loopback server with only world smoke676");
        }
        Bukkit.getScheduler().runTaskLater(this, this::runChecks, 20);
    }

    private void runChecks() {
        try {
            World world = Bukkit.getWorlds().get(0);
            Block source = world.getBlockAt(0, 80, 0);
            Block target = world.getBlockAt(2, 80, 0);
            source.setType(Material.CHEST, false);
            Chest chest = (Chest) source.getState();
            chest.getSnapshotInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
            NamespacedKey key = new NamespacedKey(this, "fixture");
            chest.getPersistentDataContainer().set(key, PersistentDataType.STRING, "synthetic");
            check(chest.update(true, false), "source chest update");
            Chest baseline = (Chest) source.getState();
            check(new ItemStack(Material.DIAMOND, 3).equals(baseline.getBlockInventory().getItem(0)), "source chest baseline items");
            check("synthetic".equals(baseline.getPersistentDataContainer().get(key, PersistentDataType.STRING)), "source chest baseline PDC");
            FullBlockData template = new FullBlockData(source);
            check(template.tileEntityData != null, "source block entity exists");
            target.setType(Material.AIR, false);
            template.set(target, false);
            Chest pasted = (Chest) target.getState();
            check(new ItemStack(Material.DIAMOND, 3).equals(pasted.getBlockInventory().getItem(0)), "chest items round trip");
            check("synthetic".equals(pasted.getPersistentDataContainer().get(key, PersistentDataType.STRING)), "PDC round trip");
            checkLocalSchematics(world.getBlockAt(8, 80, 0), () -> checkInvalidPaste(world, template));
        }
        catch (Throwable error) {
            failures++;
            getLogger().log(java.util.logging.Level.SEVERE, "SMOKE676 unexpected failure", error);
            finish();
        }
    }

    private void checkInvalidPaste(World world, FullBlockData template) {
        try {
            checkLegacyBedArchive(world.getBlockAt(-8, 80, -1));
        }
        catch (Exception error) {
            throw new IllegalStateException("Synthetic bed archive check failed", error);
        }

        // Deliberately invalid fixture reproduces the missing entity, not the cause in Dallas.
        FullBlockData invalid = new FullBlockData(Bukkit.createBlockData(Material.STONE));
        invalid.tileEntityData = template.tileEntityData;
        CuboidBlockSet set = new CuboidBlockSet();
        set.x_width = 2;
        set.y_length = 1;
        set.z_height = 1;
        set.blocks = new FullBlockData[]{invalid, new FullBlockData(Bukkit.createBlockData(Material.GOLD_BLOCK))};
        BlockSet.InputParams input = new BlockSet.InputParams();
        input.centerLocation = world.getBlockAt(4, 80, 0).getLocation();
        untouched = world.getBlockAt(5, 80, 0);
        untouched.setType(Material.DIAMOND_BLOCK, false);
        SchematicCommand.noPhys = false;
        input.onError = () -> {
            aborted++;
            check(!SchematicCommand.noPhys, "physics restored before failure callback");
        };
        set.setBlocksDelayed(() -> succeeded++, input, 50);
        Bukkit.getScheduler().runTaskLater(this, this::finish, 8);
    }

    private void finish() {
        check(aborted == 1, "exactly one abort after eight ticks");
        check(succeeded == 0, "no success callback on failure");
        check(!SchematicCommand.noPhys, "global physics flag restored");
        check(untouched != null && untouched.getType() == Material.DIAMOND_BLOCK, "no blocks placed after failure");
        getLogger().info("SMOKE676 RESULT failures=" + failures + " aborts=" + aborted + " successes=" + succeeded);
        Bukkit.shutdown();
    }
}
