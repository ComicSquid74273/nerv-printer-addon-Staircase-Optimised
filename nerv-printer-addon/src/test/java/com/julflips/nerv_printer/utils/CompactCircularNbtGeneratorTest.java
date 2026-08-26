package com.julflips.nerv_printer.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactCircularNbtGeneratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void transformsCopiesAndTransactionallyReloadValidatesTheNbt() throws Exception {
        CompoundTag source = flatSourceNbt();
        CompoundTag untouchedSource = source.copy();

        CompactCircularNbtGenerator.GeneratedNbt generated =
            CompactCircularNbtGenerator.generate(source);

        assertEquals(untouchedSource, source);
        assertNotEquals(source, generated.root());
        assertEquals(2, generated.root().getList("palette").orElseThrow().size());
        assertEquals(16_512, generated.root().getList("blocks").orElseThrow().size());
        assertEquals(128, generated.plan().sizeX());
        assertEquals(1, generated.plan().sizeY());
        assertEquals(129, generated.plan().sizeZ());
        CompoundTag marker = generated.root()
            .getCompound("nerv_printer:compact_circular_u")
            .orElseThrow();
        assertEquals("compact_circular_u", marker.getString("format").orElseThrow());
        assertEquals(1, marker.getInt("schema_version").orElseThrow());
        assertEquals(1, marker.getInt("geometry_version").orElseThrow());

        Path destination = temporaryDirectory.resolve("flat_compact.nbt");
        CompactCircularNbtGenerator.writeValidated(generated, destination);
        CompoundTag reloaded = NbtIo.readCompressed(
            destination,
            new NbtAccounter(0x20000000L, 100)
        );
        CompactCircularNbtGenerator.verifyGeneratedNbt(generated, reloaded);
        assertEquals(generated.root(), reloaded);
    }

    @Test
    void loadsMarkedAndLegacyGeneratedNbtWithoutGeneratingConnectorsTwice() {
        CompoundTag source = steppedSourceNbt();
        CompactCircularNbtGenerator.LoadedNbt sourceLoad =
            CompactCircularNbtGenerator.loadOrGenerate(source);

        assertEquals(CompactCircularNbtGenerator.InputKind.SOURCE, sourceLoad.inputKind());
        assertEquals(132, sourceLoad.generated().plan().sizeZ());
        assertEquals(7, sourceLoad.generated().plan().globalYShift());
        assertTrue(sourceLoad.generated().plan().connectorBlocks().size() > 0);

        CompactCircularNbtGenerator.LoadedNbt markedLoad =
            CompactCircularNbtGenerator.loadOrGenerate(sourceLoad.generated().root());
        assertEquals(
            CompactCircularNbtGenerator.InputKind.MARKED_COMPACT,
            markedLoad.inputKind()
        );
        assertEquals(sourceLoad.generated().root(), markedLoad.generated().root());
        assertEquals(
            sourceLoad.generated().plan().connectorBlocks().size(),
            markedLoad.generated().plan().connectorBlocks().size()
        );

        CompoundTag legacy = sourceLoad.generated().root().copy();
        legacy.remove("nerv_printer:compact_circular_u");
        CompactCircularNbtGenerator.LoadedNbt legacyLoad =
            CompactCircularNbtGenerator.loadOrGenerate(legacy);
        assertEquals(
            CompactCircularNbtGenerator.InputKind.LEGACY_COMPACT,
            legacyLoad.inputKind()
        );
        assertEquals(sourceLoad.generated().root(), legacyLoad.generated().root());
    }

    @Test
    void rejectsMarkedCompactNbtWhoseConnectorWasChanged() {
        CompoundTag corrupt = CompactCircularNbtGenerator.generate(steppedSourceNbt())
            .root()
            .copy();
        ListTag blocks = corrupt.getList("blocks").orElseThrow();
        boolean changed = false;
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index).orElseThrow();
            ListTag position = block.getList("pos").orElseThrow();
            if (position.getInt(2).orElseThrow() > CompactCircularNbtPlan.FAR_Z) {
                block.putInt("state", 0);
                changed = true;
                break;
            }
        }
        assertTrue(changed);
        assertThrows(
            IllegalArgumentException.class,
            () -> CompactCircularNbtGenerator.loadOrGenerate(corrupt)
        );
    }

    @Test
    void treatsAnUnmarkedCanonicalFixedPointAsLegacyCompact() {
        CompoundTag canonicalFlat = CompactCircularNbtGenerator.generate(flatSourceNbt())
            .root()
            .copy();
        canonicalFlat.remove("nerv_printer:compact_circular_u");

        CompactCircularNbtGenerator.LoadedNbt loaded =
            CompactCircularNbtGenerator.loadOrGenerate(canonicalFlat);

        assertEquals(
            CompactCircularNbtGenerator.InputKind.LEGACY_COMPACT,
            loaded.inputKind()
        );
        assertEquals(129, loaded.generated().plan().sizeZ());
    }

    @Test
    void rejectsEntitiesAndBlockEntitiesInsteadOfSilentlyDroppingTheirCoordinates() {
        CompoundTag withEntity = flatSourceNbt();
        withEntity.getList("entities").orElseThrow().add(new CompoundTag());
        assertThrows(
            IllegalArgumentException.class,
            () -> CompactCircularNbtGenerator.generate(withEntity)
        );

        CompoundTag withBlockEntity = flatSourceNbt();
        CompoundTag firstBlock = withBlockEntity.getList("blocks")
            .orElseThrow()
            .getCompound(0)
            .orElseThrow();
        firstBlock.put("nbt", new CompoundTag());
        assertThrows(
            IllegalArgumentException.class,
            () -> CompactCircularNbtGenerator.generate(withBlockEntity)
        );

        CompoundTag withInvalidEntitiesTag = flatSourceNbt();
        withInvalidEntitiesTag.put("entities", new CompoundTag());
        assertThrows(
            IllegalArgumentException.class,
            () -> CompactCircularNbtGenerator.generate(withInvalidEntitiesTag)
        );
    }

    private static CompoundTag flatSourceNbt() {
        CompoundTag root = new CompoundTag();
        root.putString("author", "compact-test");

        ListTag size = new ListTag();
        size.add(IntTag.valueOf(128));
        size.add(IntTag.valueOf(1));
        size.add(IntTag.valueOf(129));
        root.put("size", size);

        ListTag palette = new ListTag();
        CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        palette.add(stone);
        root.put("palette", palette);

        ListTag blocks = new ListTag();
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 129; z++) {
                CompoundTag block = new CompoundTag();
                ListTag position = new ListTag();
                position.add(IntTag.valueOf(x));
                position.add(IntTag.valueOf(0));
                position.add(IntTag.valueOf(z));
                block.put("pos", position);
                block.putInt("state", 0);
                blocks.add(block);
            }
        }
        root.put("blocks", blocks);
        root.put("entities", new ListTag());
        return root;
    }

    private static CompoundTag steppedSourceNbt() {
        CompoundTag root = flatSourceNbt();
        ListTag blocks = root.getList("blocks").orElseThrow();
        for (int z = 1; z < CompactCircularNbtPlan.SOURCE_Z_SIZE; z++) {
            int index = CompactCircularNbtPlan.SOURCE_Z_SIZE + z;
            CompoundTag block = blocks.getCompound(index).orElseThrow();
            block.getList("pos")
                .orElseThrow()
                .set(1, IntTag.valueOf(Math.min(z, 6)));
        }
        CompoundTag deepBlock = new CompoundTag();
        ListTag deepPosition = new ListTag();
        deepPosition.add(IntTag.valueOf(0));
        deepPosition.add(IntTag.valueOf(-7));
        deepPosition.add(IntTag.valueOf(0));
        deepBlock.put("pos", deepPosition);
        deepBlock.putInt("state", 0);
        blocks.add(deepBlock);
        return root;
    }
}
