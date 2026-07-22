package com.julflips.nerv_printer.utils;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
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
        NbtCompound source = flatSourceNbt();
        NbtCompound untouchedSource = source.copy();

        CompactCircularNbtGenerator.GeneratedNbt generated =
            CompactCircularNbtGenerator.generate(source);

        assertEquals(untouchedSource, source);
        assertNotEquals(source, generated.root());
        assertEquals(2, generated.root().getList("palette").orElseThrow().size());
        assertEquals(16_512, generated.root().getList("blocks").orElseThrow().size());
        assertEquals(128, generated.plan().sizeX());
        assertEquals(1, generated.plan().sizeY());
        assertEquals(129, generated.plan().sizeZ());
        NbtCompound marker = generated.root()
            .getCompound("nerv_printer:compact_circular_u")
            .orElseThrow();
        assertEquals("compact_circular_u", marker.getString("format").orElseThrow());
        assertEquals(1, marker.getInt("schema_version").orElseThrow());
        assertEquals(1, marker.getInt("geometry_version").orElseThrow());

        Path destination = temporaryDirectory.resolve("flat_compact.nbt");
        CompactCircularNbtGenerator.writeValidated(generated, destination);
        NbtCompound reloaded = NbtIo.readCompressed(
            destination,
            new NbtSizeTracker(0x20000000L, 100)
        );
        CompactCircularNbtGenerator.verifyGeneratedNbt(generated, reloaded);
        assertEquals(generated.root(), reloaded);
    }

    @Test
    void loadsMarkedAndLegacyGeneratedNbtWithoutGeneratingConnectorsTwice() {
        NbtCompound source = steppedSourceNbt();
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

        NbtCompound legacy = sourceLoad.generated().root().copy();
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
        NbtCompound corrupt = CompactCircularNbtGenerator.generate(steppedSourceNbt())
            .root()
            .copy();
        NbtList blocks = corrupt.getList("blocks").orElseThrow();
        boolean changed = false;
        for (int index = 0; index < blocks.size(); index++) {
            NbtCompound block = blocks.getCompound(index).orElseThrow();
            NbtList position = block.getList("pos").orElseThrow();
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
        NbtCompound canonicalFlat = CompactCircularNbtGenerator.generate(flatSourceNbt())
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
        NbtCompound withEntity = flatSourceNbt();
        withEntity.getList("entities").orElseThrow().add(new NbtCompound());
        assertThrows(
            IllegalArgumentException.class,
            () -> CompactCircularNbtGenerator.generate(withEntity)
        );

        NbtCompound withBlockEntity = flatSourceNbt();
        NbtCompound firstBlock = withBlockEntity.getList("blocks")
            .orElseThrow()
            .getCompound(0)
            .orElseThrow();
        firstBlock.put("nbt", new NbtCompound());
        assertThrows(
            IllegalArgumentException.class,
            () -> CompactCircularNbtGenerator.generate(withBlockEntity)
        );

        NbtCompound withInvalidEntitiesTag = flatSourceNbt();
        withInvalidEntitiesTag.put("entities", new NbtCompound());
        assertThrows(
            IllegalArgumentException.class,
            () -> CompactCircularNbtGenerator.generate(withInvalidEntitiesTag)
        );
    }

    private static NbtCompound flatSourceNbt() {
        NbtCompound root = new NbtCompound();
        root.putString("author", "compact-test");

        NbtList size = new NbtList();
        size.add(NbtInt.of(128));
        size.add(NbtInt.of(1));
        size.add(NbtInt.of(129));
        root.put("size", size);

        NbtList palette = new NbtList();
        NbtCompound stone = new NbtCompound();
        stone.putString("Name", "minecraft:stone");
        palette.add(stone);
        root.put("palette", palette);

        NbtList blocks = new NbtList();
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 129; z++) {
                NbtCompound block = new NbtCompound();
                NbtList position = new NbtList();
                position.add(NbtInt.of(x));
                position.add(NbtInt.of(0));
                position.add(NbtInt.of(z));
                block.put("pos", position);
                block.putInt("state", 0);
                blocks.add(block);
            }
        }
        root.put("blocks", blocks);
        root.put("entities", new NbtList());
        return root;
    }

    private static NbtCompound steppedSourceNbt() {
        NbtCompound root = flatSourceNbt();
        NbtList blocks = root.getList("blocks").orElseThrow();
        for (int z = 1; z < CompactCircularNbtPlan.SOURCE_Z_SIZE; z++) {
            int index = CompactCircularNbtPlan.SOURCE_Z_SIZE + z;
            NbtCompound block = blocks.getCompound(index).orElseThrow();
            block.getList("pos")
                .orElseThrow()
                .set(1, NbtInt.of(Math.min(z, 6)));
        }
        NbtCompound deepBlock = new NbtCompound();
        NbtList deepPosition = new NbtList();
        deepPosition.add(NbtInt.of(0));
        deepPosition.add(NbtInt.of(-7));
        deepPosition.add(NbtInt.of(0));
        deepBlock.put("pos", deepPosition);
        deepBlock.putInt("state", 0);
        blocks.add(deepBlock);
        return root;
    }
}
