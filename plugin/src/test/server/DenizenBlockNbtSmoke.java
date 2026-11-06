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

    private void checkLocalSchematics(Block target) throws Exception {
        File[] files = new File("fixtures").listFiles((dir, name) -> name.endsWith(".schem"));
        if (files == null) {
            return;
        }
        int loaded = 0, skipped = 0, entities = 0;
        for (File file : files) {
            CompoundBinaryTag root;
            try (FileInputStream stream = new FileInputStream(file)) {
                root = BinaryTagIO.reader().readNamed(stream, BinaryTagIO.Compression.GZIP).getValue();
            }
            if (root.getInt("Version") == 3 || root.getCompound("Schematic").getInt("Version") == 3) {
                skipped++;
                continue;
            }
            CuboidBlockSet set;
            try (FileInputStream stream = new FileInputStream(file)) {
                set = SpongeSchematicHelper.fromSpongeStream(stream);
            }
            loaded++;
            for (FullBlockData block : set.blocks) {
                if (block.tileEntityData == null) {
                    continue;
                }
                entities++;
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
        check(entities > 0, "local fixtures include block entities");
        getLogger().info("SMOKE676 FIXTURES loaded=" + loaded + " skippedV3=" + skipped + " blockEntities=" + entities);
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
            chest.getBlockInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
            NamespacedKey key = new NamespacedKey(this, "fixture");
            chest.getPersistentDataContainer().set(key, PersistentDataType.STRING, "synthetic");
            check(chest.update(true, false), "source chest update");
            FullBlockData template = new FullBlockData(source);
            check(template.tileEntityData != null, "source block entity exists");
            target.setType(Material.AIR, false);
            template.set(target, false);
            Chest pasted = (Chest) target.getState();
            check(new ItemStack(Material.DIAMOND, 3).equals(pasted.getBlockInventory().getItem(0)), "chest items round trip");
            check("synthetic".equals(pasted.getPersistentDataContainer().get(key, PersistentDataType.STRING)), "PDC round trip");
            checkLocalSchematics(world.getBlockAt(8, 80, 0));

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
        catch (Throwable error) {
            failures++;
            getLogger().log(java.util.logging.Level.SEVERE, "SMOKE676 unexpected failure", error);
            finish();
        }
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
