package com.denizenscript.denizen.nms.v26_2.helpers;

import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;

/** Lossless archive for the bed block entity removed in 26.2. Not a replacement bed PDC API. */
final class LegacyBedNbt {

    static void archive(Block block, CompoundBinaryTag data) {
        String id = data.getString("id", data.getString("Id"));
        if (!Tag.BEDS.isTagged(block.getType()) || !(id.equals("minecraft:bed") || id.equals("bed"))) {
            throw new IllegalStateException("Cannot apply block entity NBT: destination has no block entity");
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            BinaryTagIO.writer().writeNamed(Map.entry("", data), output, BinaryTagIO.Compression.GZIP);
            byte[] bytes = output.toByteArray();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hash = new StringBuilder(64);
            for (byte value : digest) {
                hash.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
            }
            // Content-addressed records keep earlier payloads when another schematic uses this location.
            NamespacedKey key = new NamespacedKey("denizen", "legacy_bed_nbt/"
                    + block.getX() + "_" + block.getY() + "_" + block.getZ() + "/" + hash);
            PersistentDataContainer archive = block.getChunk().getPersistentDataContainer();
            if (archive.has(key) && !Arrays.equals(bytes, archive.get(key, PersistentDataType.BYTE_ARRAY))) {
                throw new IllegalStateException("Conflicting legacy bed NBT archive; refusing to overwrite");
            }
            archive.set(key, PersistentDataType.BYTE_ARRAY, bytes);
            if (!Arrays.equals(bytes, archive.get(key, PersistentDataType.BYTE_ARRAY))) {
                throw new IllegalStateException("Legacy bed NBT archive verification failed");
            }
        }
        catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("Could not preserve legacy bed NBT", error);
        }
    }
}
