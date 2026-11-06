package com.denizenscript.denizen.utilities.blocks;

import com.denizenscript.denizen.nms.NMSHandler;
import com.denizenscript.denizen.nms.interfaces.BlockHelper;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.scripts.commands.world.SchematicCommand;
import com.denizenscript.denizencore.objects.core.DurationTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CuboidBlockSetFailureTest {
    private static MockedStatic<Bukkit> bukkit;
    private static BukkitScheduler scheduler;
    private Runnable tick;
    private boolean cancelled;

    @BeforeClass
    public static void initializeBukkitBeforeNms() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getBukkitVersion).thenReturn("26.2-R0.1-SNAPSHOT");
        BlockData structureVoid = mock(BlockData.class);
        when(structureVoid.getMaterial()).thenReturn(Material.STRUCTURE_VOID);
        bukkit.when(() -> Bukkit.createBlockData(Material.STRUCTURE_VOID)).thenReturn(structureVoid);
        scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
    }

    @AfterClass
    public static void closeBukkit() {
        bukkit.close();
    }

    @Before
    public void scheduler() {
        reset(scheduler);
        cancelled = false;
        tick = null;
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(42);
        when(scheduler.runTaskTimer(nullable(Plugin.class), any(Runnable.class), eq(1L), eq(1L)))
                .thenAnswer(invocation -> {
                    tick = invocation.getArgument(1);
                    return task;
                });
        doAnswer(invocation -> { cancelled = true; return null; }).when(scheduler).cancelTask(42);
    }

    @After
    public void restoreGlobals() {
        SchematicCommand.noPhys = false;
        NMSHandler.blockHelper = null;
    }

    private class Paste extends CuboidBlockSet {
        int attempted;
        int failAt = -1;
        RuntimeException failure = new NullPointerException("missing block entity fixture");
        FullBlockData realBlock;
        Block destination;
        int entitiesPasted;

        Paste() {
            x_width = 3;
            y_length = z_height = 1;
            blocks = new FullBlockData[3];
        }

        @Override
        public void setBlockSingle(FullBlockData block, int x, int y, int z, InputParams input) {
            assertTrue(SchematicCommand.noPhys);
            int current = attempted++;
            if (current == failAt) {
                if (realBlock != null) {
                    realBlock.set(destination, false);
                }
                throw failure;
            }
        }

        @Override
        public void pasteEntities(LocationTag location) {
            entitiesPasted++;
        }
    }

    private void nextTick() {
        if (!cancelled) {
            tick.run();
        }
    }

    @Test
    public void delayedFailureCancelsOnceAndRestoresBothPreviousPhysicsStates() {
        for (boolean previous : new boolean[] { false, true }) {
            scheduler();
            SchematicCommand.noPhys = previous;
            Paste paste = new Paste();
            paste.failAt = 1;
            AtomicInteger success = new AtomicInteger();
            AtomicInteger cleanup = new AtomicInteger();
            BlockSet.InputParams input = new BlockSet.InputParams();
            input.onError = () -> {
                assertEquals(previous, SchematicCommand.noPhys);
                cleanup.incrementAndGet();
            };
            paste.setBlocksDelayed(success::incrementAndGet, input, Long.MAX_VALUE);
            assertSame(paste.failure, assertThrows(NullPointerException.class, this::nextTick));
            for (int i = 0; i < 5; i++) {
                nextTick();
            }
            assertEquals(2, paste.attempted);
            assertEquals(0, success.get());
            assertEquals(1, cleanup.get());
            assertTrue(cancelled);
            assertEquals(previous, SchematicCommand.noPhys);
        }
    }

    @Test
    public void fullBlockNbtFailureStopsBeforeRemainingBlocks() {
        Paste paste = new Paste();
        paste.failAt = 0;
        BlockData data = mock(BlockData.class);
        paste.realBlock = new FullBlockData(data, CompoundBinaryTag.empty(), null);
        paste.destination = mock(Block.class);
        NMSHandler.blockHelper = mock(BlockHelper.class);
        doThrow(paste.failure).when(NMSHandler.blockHelper).setNbtData(paste.destination, paste.realBlock.tileEntityData);
        BlockSet.InputParams input = new BlockSet.InputParams();
        AtomicInteger cleanup = new AtomicInteger();
        input.onError = cleanup::incrementAndGet;
        paste.setBlocksDelayed(() -> fail("success/entity callback after NBT failure"), input, Long.MAX_VALUE);
        assertSame(paste.failure, assertThrows(NullPointerException.class, this::nextTick));
        nextTick();
        assertEquals(1, paste.attempted);
        assertEquals(1, cleanup.get());
        verify(paste.destination).setBlockData(data, false);
        assertFalse(SchematicCommand.noPhys);
    }

    @Test
    public void delayedSuccessPreservesBothPhysicsStatesAndCallsSuccessOnce() {
        for (boolean previous : new boolean[] { false, true }) {
            scheduler();
            SchematicCommand.noPhys = previous;
            Paste paste = new Paste();
            AtomicInteger success = new AtomicInteger();
            BlockSet.InputParams input = new BlockSet.InputParams();
            input.onError = () -> fail("failure cleanup on success");
            paste.setBlocksDelayed(success::incrementAndGet, input, Long.MAX_VALUE);
            nextTick();
            nextTick();
            assertEquals(3, paste.attempted);
            assertEquals(1, success.get());
            assertEquals(previous, SchematicCommand.noPhys);
        }
    }

    @Test
    public void yieldedPasteRestoresPhysicsBetweenTicksAndResumes() {
        SchematicCommand.noPhys = true;
        Paste paste = new Paste();
        AtomicInteger success = new AtomicInteger();
        paste.setBlocksDelayed(success::incrementAndGet, new BlockSet.InputParams(), -1);
        for (int i = 1; i <= 3; i++) {
            nextTick();
            assertEquals(i, paste.attempted);
            assertTrue(SchematicCommand.noPhys);
            assertEquals(0, success.get());
        }
        nextTick();
        assertTrue(cancelled);
        assertEquals(1, success.get());
    }

    @Test
    public void failureWithoutOptionalCleanupStillCancels() {
        Paste paste = new Paste();
        paste.failAt = 0;
        paste.setBlocksDelayed(null, new BlockSet.InputParams(), Long.MAX_VALUE);
        assertThrows(NullPointerException.class, this::nextTick);
        assertTrue(cancelled);
        assertFalse(SchematicCommand.noPhys);
    }

    @Test
    public void successCallbackFailureDoesNotRunFailureCleanupAgain() {
        Paste paste = new Paste();
        BlockSet.InputParams input = new BlockSet.InputParams();
        input.onError = () -> fail("double cleanup");
        paste.setBlocksDelayed(() -> { throw new IllegalStateException("entity callback fixture"); }, input, Long.MAX_VALUE);
        assertThrows(IllegalStateException.class, this::nextTick);
        assertTrue(cancelled);
        assertFalse(SchematicCommand.noPhys);
    }

    @Test
    public void immediateFailureRestoresBothPreviousPhysicsStates() {
        for (boolean previous : new boolean[] { false, true }) {
            SchematicCommand.noPhys = previous;
            Paste paste = new Paste();
            paste.failAt = 1;
            assertSame(paste.failure, assertThrows(NullPointerException.class, () -> paste.setBlocks(new BlockSet.InputParams())));
            assertEquals(2, paste.attempted);
            assertEquals(previous, SchematicCommand.noPhys);
        }
    }

    @Test
    public void immediateSuccessRestoresPreviousPhysicsState() {
        SchematicCommand.noPhys = true;
        Paste paste = new Paste();
        paste.setBlocks(new BlockSet.InputParams());
        assertEquals(3, paste.attempted);
        assertTrue(SchematicCommand.noPhys);
    }

    private ScriptEntry commandEntry(Paste paste) {
        ScriptEntry entry = mock(ScriptEntry.class);
        ElementTag type = mock(ElementTag.class);
        when(type.asString()).thenReturn("PASTE");
        when(entry.getElement("type")).thenReturn(type);
        ElementTag name = mock(ElementTag.class);
        when(name.asString()).thenReturn("fixture");
        when(entry.requiredArgForPrefixAsElement("name")).thenReturn(name);
        ElementTag delay = mock(ElementTag.class);
        when(delay.asLong()).thenReturn(Long.MAX_VALUE);
        when(entry.argForPrefixAsElement("max_delay_ms", "50")).thenReturn(delay);
        when(entry.argAsBoolean("delayed")).thenReturn(true);
        when(entry.argAsBoolean("entities")).thenReturn(true);
        // Mockito's default empty list would select fake paste and reject entities.
        when(entry.argForPrefixList("fake_to", PlayerTag.class, true)).thenReturn(null);
        when(entry.getObjectTag("location")).thenReturn(mock(LocationTag.class));
        when(entry.argForPrefix("fake_duration", DurationTag.class, true)).thenReturn(mock(DurationTag.class));
        SchematicCommand.schematics = new HashMap<>();
        SchematicCommand.schematics.put("FIXTURE", paste);
        return entry;
    }

    @Test
    public void commandFailureReleasesReaderAndWaitableWithoutPastingEntities() {
        Paste paste = new Paste();
        paste.failAt = 1;
        ScriptEntry entry = commandEntry(paste);
        mock(SchematicCommand.class, CALLS_REAL_METHODS).execute(entry);
        assertEquals(1, paste.readingProcesses);
        assertThrows(NullPointerException.class, this::nextTick);
        nextTick();
        assertEquals(0, paste.readingProcesses);
        assertEquals(0, paste.entitiesPasted);
        verify(entry, times(1)).setFinished(true);
    }

    @Test
    public void commandSuccessStillPastesEntitiesAndReleasesReaderOnce() {
        Paste paste = new Paste();
        ScriptEntry entry = commandEntry(paste);
        mock(SchematicCommand.class, CALLS_REAL_METHODS).execute(entry);
        assertEquals(1, paste.readingProcesses);
        nextTick();
        nextTick();
        assertEquals(0, paste.readingProcesses);
        assertEquals(1, paste.entitiesPasted);
        verify(entry, times(1)).setFinished(true);
    }

    @Test
    public void schedulingFailureReleasesReaderWithoutPlacingBlocksOrEntities() {
        Paste paste = new Paste();
        ScriptEntry entry = commandEntry(paste);
        when(scheduler.runTaskTimer(nullable(Plugin.class), any(Runnable.class), eq(1L), eq(1L)))
                .thenThrow(new IllegalStateException("scheduler fixture"));
        try (MockedStatic<Debug> debug = mockStatic(Debug.class)) {
            mock(SchematicCommand.class, CALLS_REAL_METHODS).execute(entry);
        }
        assertEquals(0, paste.readingProcesses);
        assertEquals(0, paste.attempted);
        assertEquals(0, paste.entitiesPasted);
        verify(entry, atLeastOnce()).setFinished(true);
    }
}
