package com.julflips.nerv_printer.utils;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Strict Minecraft-NBT adapter for {@link CompactCircularNbtPlan}.
 */
public final class CompactCircularNbtGenerator {
    private static final long MAX_NBT_BYTES = 0x20000000L;
    private static final int MAX_NBT_DEPTH = 100;
    private static final String MARKER_KEY = "nerv_printer:compact_circular_u";
    private static final String MARKER_FORMAT = "compact_circular_u";
    private static final int MARKER_SCHEMA_VERSION = 1;
    private static final int MARKER_GEOMETRY_VERSION = 1;

    private CompactCircularNbtGenerator() {
    }

    public enum InputKind {
        SOURCE,
        MARKED_COMPACT,
        LEGACY_COMPACT
    }

    public record GeneratedNbt(
        NbtCompound root,
        CompactCircularNbtPlan.Result plan,
        List<String> paletteNames
    ) {
        public GeneratedNbt {
            paletteNames = List.copyOf(paletteNames);
        }
    }

    public record LoadedNbt(GeneratedNbt generated, InputKind inputKind) {
        public boolean compactInput() {
            return inputKind != InputKind.SOURCE;
        }
    }

    public static GeneratedNbt generate(NbtCompound sourceRoot) {
        if (sourceRoot != null && sourceRoot.get(MARKER_KEY) != null) {
            throw fail("A marked compact NBT must be loaded with loadOrGenerate().");
        }
        return generateCanonical(sourceRoot);
    }

    /**
     * Accepts either a raw 128x129 source or an exact compact file previously
     * produced by this generator. Compact inputs are reconstructed and
     * canonical-regenerated before use; the marker or filename is never trusted
     * as proof that their geometry is valid.
     */
    public static LoadedNbt loadOrGenerate(NbtCompound inputRoot) {
        if (inputRoot == null) throw fail("The structure root is missing.");
        if (inputRoot.get(MARKER_KEY) != null) {
            validateMarker(inputRoot);
            return new LoadedNbt(
                loadCanonicalCompact(inputRoot, true),
                InputKind.MARKED_COMPACT
            );
        }

        IllegalArgumentException sourceFailure;
        try {
            GeneratedNbt generated = generateCanonical(inputRoot);
            NbtCompound legacyForm = withoutMarker(generated.root());
            if (legacyForm.equals(inputRoot)) {
                return new LoadedNbt(generated, InputKind.LEGACY_COMPACT);
            }
            return new LoadedNbt(generated, InputKind.SOURCE);
        } catch (IllegalArgumentException exception) {
            sourceFailure = exception;
        }

        try {
            return new LoadedNbt(
                loadCanonicalCompact(inputRoot, false),
                InputKind.LEGACY_COMPACT
            );
        } catch (IllegalArgumentException compactFailure) {
            throw fail(
                "Input is neither a valid 128x129 source nor an exact generated compact NBT. "
                    + "Source check: " + validationMessage(sourceFailure)
                    + " Compact check: " + validationMessage(compactFailure)
            );
        }
    }

    private static GeneratedNbt generateCanonical(NbtCompound sourceRoot) {
        if (sourceRoot == null) throw fail("The structure root is missing.");
        if (sourceRoot.get(MARKER_KEY) != null) validateMarker(sourceRoot);
        NbtList sourceSize = requiredList(sourceRoot, "size");
        NbtList sourcePalette = requiredList(sourceRoot, "palette");
        NbtList sourceBlockList = requiredList(sourceRoot, "blocks");
        if (sourceSize.size() != 3) throw fail("The structure size tag must contain exactly three integers.");
        requiredInt(sourceSize, 0, "size X");
        requiredInt(sourceSize, 1, "size Y");
        requiredInt(sourceSize, 2, "size Z");
        if (sourcePalette.isEmpty()) throw fail("The structure palette is empty.");
        if (sourceBlockList.isEmpty()) throw fail("The structure contains no blocks.");

        NbtElement entitiesElement = sourceRoot.get("entities");
        if (entitiesElement != null) {
            if (!(entitiesElement instanceof NbtList entities)) {
                throw fail("The entities tag must be a list.");
            }
            if (!entities.isEmpty()) throw fail("Entities are not supported.");
        }

        List<String> paletteNames = readPaletteNames(sourcePalette);
        List<CompactCircularNbtPlan.SourceBlock> sourceBlocks = readSourceBlocks(sourceBlockList);
        CompactCircularNbtPlan.Result plan = CompactCircularNbtPlan.generate(sourceBlocks, paletteNames);

        NbtCompound outputRoot = sourceRoot.copy();
        NbtList outputPalette = requiredList(outputRoot, "palette");
        if (plan.cobblestoneState() == outputPalette.size()) {
            NbtCompound cobblestone = new NbtCompound();
            cobblestone.putString("Name", CompactCircularNbtPlan.COBBLESTONE_NAME);
            outputPalette.add(cobblestone);
            paletteNames = new ArrayList<>(paletteNames);
            paletteNames.add(CompactCircularNbtPlan.COBBLESTONE_NAME);
        }
        if (plan.cobblestoneState() >= outputPalette.size()) {
            throw fail("Could not resolve the cobblestone palette state.");
        }

        NbtList outputBlocks = requiredList(outputRoot, "blocks");
        if (outputBlocks.size() != sourceBlocks.size()) {
            throw fail("The output working copy changed block count before transformation.");
        }

        for (CompactCircularNbtPlan.GeneratedBlock generated : plan.generatedBlocks()) {
            if (generated.connector()) continue;
            int sourceIndex = generated.sourceIndex();
            NbtCompound block = outputBlocks.getCompound(sourceIndex)
                .orElseThrow(() -> fail("Output block " + sourceIndex + " is not a compound."));
            NbtList position = requiredList(block, "pos");
            if (position.size() != 3) {
                throw fail("Output block " + sourceIndex + " has an invalid position.");
            }
            CompactCircularNbtPlan.Position expectedOriginal = sourceBlocks.get(sourceIndex).position();
            if (!readPosition(position).equals(expectedOriginal)) {
                throw fail("The output working-copy block order changed before transformation.");
            }
            position.set(1, NbtInt.of(generated.position().y()));
            block.putInt("state", generated.state());
        }

        for (CompactCircularNbtPlan.GeneratedBlock connector : plan.connectorBlocks()) {
            NbtCompound block = new NbtCompound();
            NbtList position = new NbtList();
            position.add(NbtInt.of(connector.position().x()));
            position.add(NbtInt.of(connector.position().y()));
            position.add(NbtInt.of(connector.position().z()));
            block.put("pos", position);
            block.putInt("state", connector.state());
            outputBlocks.add(block);
        }

        NbtList outputSize = requiredList(outputRoot, "size");
        outputSize.set(0, NbtInt.of(plan.sizeX()));
        outputSize.set(1, NbtInt.of(plan.sizeY()));
        outputSize.set(2, NbtInt.of(plan.sizeZ()));
        installMarker(outputRoot, plan);

        GeneratedNbt generated = new GeneratedNbt(outputRoot, plan, paletteNames);
        verifyGeneratedNbt(generated, outputRoot);
        return generated;
    }

    private static GeneratedNbt loadCanonicalCompact(
        NbtCompound compactRoot,
        boolean marked
    ) {
        if (marked) {
            validateMarker(compactRoot);
        } else if (compactRoot.get(MARKER_KEY) != null) {
            throw fail("Legacy compact validation received a marked NBT.");
        }

        NbtCompound reconstructedSource = reconstructSource(compactRoot);
        GeneratedNbt canonical = generateCanonical(reconstructedSource);
        if (marked) {
            verifyGeneratedNbt(canonical, compactRoot);
        } else {
            NbtCompound upgraded = compactRoot.copy();
            NbtCompound marker = requiredCompound(canonical.root(), MARKER_KEY);
            upgraded.put(MARKER_KEY, marker.copy());
            verifyGeneratedNbt(canonical, upgraded);
        }
        return canonical;
    }

    private static NbtCompound reconstructSource(NbtCompound compactRoot) {
        List<String> paletteNames = readPaletteNames(requiredList(compactRoot, "palette"));
        int cobblestoneState = paletteNames.indexOf(CompactCircularNbtPlan.COBBLESTONE_NAME);
        if (cobblestoneState < 0) {
            throw fail("A generated compact NBT has no cobblestone palette state.");
        }

        NbtList compactBlocks = requiredList(compactRoot, "blocks");
        if (compactBlocks.isEmpty()) throw fail("The structure contains no blocks.");
        int[] startingTopY = new int[CompactCircularNbtPlan.MAP_WIDTH];
        int[] startingTopState = new int[CompactCircularNbtPlan.MAP_WIDTH];
        boolean[] startingTopPresent = new boolean[CompactCircularNbtPlan.MAP_WIDTH];

        for (int index = 0; index < compactBlocks.size(); index++) {
            int blockIndex = index;
            NbtCompound block = compactBlocks.getCompound(index)
                .orElseThrow(() -> fail("Generated block " + blockIndex + " is not a compound."));
            if (block.get("nbt") != null) {
                throw fail("The generated structure unexpectedly contains a block entity.");
            }
            CompactCircularNbtPlan.Position position = readPosition(requiredList(block, "pos"));
            int state = block.getInt("state")
                .orElseThrow(() -> fail("Generated block " + blockIndex + " has no state."));
            if (state < 0 || state >= paletteNames.size()) {
                throw fail("Generated block " + blockIndex + " has an invalid palette state.");
            }
            if (position.z() != CompactCircularNbtPlan.START_Z
                || position.x() < 0
                || position.x() >= CompactCircularNbtPlan.MAP_WIDTH) {
                continue;
            }
            if (!startingTopPresent[position.x()]
                || position.y() > startingTopY[position.x()]) {
                startingTopPresent[position.x()] = true;
                startingTopY[position.x()] = position.y();
                startingTopState[position.x()] = state;
            }
        }

        for (int x = 0; x < CompactCircularNbtPlan.MAP_WIDTH; x++) {
            if (!startingTopPresent[x]) {
                throw fail("A generated compact NBT is missing its Z=0 surface at X=" + x + ".");
            }
            if (startingTopY[x] != startingTopY[0]) {
                throw fail("A generated compact NBT has a non-flat Z=0 surface.");
            }
            if (startingTopState[x] != cobblestoneState) {
                throw fail("A generated compact NBT has a non-cobblestone Z=0 surface.");
            }
        }

        int globalYShift = startingTopY[0];
        NbtCompound source = compactRoot.copy();
        NbtList sourceBlocks = new NbtList();
        for (int index = 0; index < compactBlocks.size(); index++) {
            NbtCompound compactBlock = compactBlocks.getCompound(index).orElseThrow();
            CompactCircularNbtPlan.Position position =
                readPosition(requiredList(compactBlock, "pos"));
            if (position.z() > CompactCircularNbtPlan.FAR_Z) continue;

            NbtCompound sourceBlock = compactBlock.copy();
            NbtList sourcePosition = requiredList(sourceBlock, "pos");
            sourcePosition.set(1, NbtInt.of(position.y() - globalYShift));
            sourceBlocks.add(sourceBlock);
        }
        source.put("blocks", sourceBlocks);

        NbtList sourceSize = new NbtList();
        sourceSize.add(NbtInt.of(CompactCircularNbtPlan.MAP_WIDTH));
        sourceSize.add(NbtInt.of(1));
        sourceSize.add(NbtInt.of(CompactCircularNbtPlan.SOURCE_Z_SIZE));
        source.put("size", sourceSize);
        return source;
    }

    private static void installMarker(
        NbtCompound root,
        CompactCircularNbtPlan.Result plan
    ) {
        NbtCompound marker = new NbtCompound();
        marker.putString("format", MARKER_FORMAT);
        marker.putInt("schema_version", MARKER_SCHEMA_VERSION);
        marker.putInt("geometry_version", MARKER_GEOMETRY_VERSION);
        marker.putInt("source_width", CompactCircularNbtPlan.MAP_WIDTH);
        marker.putInt("source_depth", CompactCircularNbtPlan.SOURCE_Z_SIZE);
        marker.putInt("maximum_extension", plan.maximumExtension());
        root.put(MARKER_KEY, marker);
    }

    private static void validateMarker(NbtCompound root) {
        NbtCompound marker = requiredCompound(root, MARKER_KEY);
        String format = marker.getString("format")
            .orElseThrow(() -> fail("The compact marker has no format."));
        int schemaVersion = marker.getInt("schema_version")
            .orElseThrow(() -> fail("The compact marker has no schema version."));
        int geometryVersion = marker.getInt("geometry_version")
            .orElseThrow(() -> fail("The compact marker has no geometry version."));
        int sourceWidth = marker.getInt("source_width")
            .orElseThrow(() -> fail("The compact marker has no source width."));
        int sourceDepth = marker.getInt("source_depth")
            .orElseThrow(() -> fail("The compact marker has no source depth."));
        int maximumExtension = marker.getInt("maximum_extension")
            .orElseThrow(() -> fail("The compact marker has no maximum extension."));

        if (!MARKER_FORMAT.equals(format)) throw fail("The compact marker format is unsupported.");
        if (schemaVersion != MARKER_SCHEMA_VERSION) {
            throw fail("Unsupported compact marker schema version " + schemaVersion + ".");
        }
        if (geometryVersion != MARKER_GEOMETRY_VERSION) {
            throw fail("Unsupported compact geometry version " + geometryVersion + ".");
        }
        if (sourceWidth != CompactCircularNbtPlan.MAP_WIDTH
            || sourceDepth != CompactCircularNbtPlan.SOURCE_Z_SIZE) {
            throw fail("The compact marker source dimensions are invalid.");
        }
        if (maximumExtension < 0
            || maximumExtension > CompactCircularNbtPlan.MAX_OUTSIDE_EXTENSION) {
            throw fail("The compact marker maximum extension is invalid.");
        }
    }

    private static NbtCompound withoutMarker(NbtCompound root) {
        NbtCompound copy = root.copy();
        copy.remove(MARKER_KEY);
        return copy;
    }

    private static String validationMessage(IllegalArgumentException exception) {
        String message = exception.getMessage();
        String prefix = "Compact circular NBT validation failed: ";
        return message != null && message.startsWith(prefix)
            ? message.substring(prefix.length())
            : String.valueOf(message);
    }

    /**
     * Writes through a sibling temporary file, reloads the exact bytes, verifies
     * them, and only then replaces the destination.
     */
    public static void writeValidated(GeneratedNbt generated, Path destination) throws IOException {
        if (destination == null) throw new IOException("No compact NBT destination was provided.");
        Path absoluteDestination = destination.toAbsolutePath().normalize();
        Path parent = absoluteDestination.getParent();
        if (parent == null) throw new IOException("Compact NBT destination has no parent directory.");
        Files.createDirectories(parent);

        Path temporary = Files.createTempFile(
            parent,
            absoluteDestination.getFileName() + ".",
            ".tmp"
        );
        try {
            NbtIo.writeCompressed(generated.root(), temporary);
            NbtCompound written = NbtIo.readCompressed(
                temporary,
                new NbtSizeTracker(MAX_NBT_BYTES, MAX_NBT_DEPTH)
            );
            verifyGeneratedNbt(generated, written);
            try {
                Files.move(
                    temporary,
                    absoluteDestination,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absoluteDestination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void verifyGeneratedNbt(GeneratedNbt expected, NbtCompound actualRoot) {
        List<String> actualPaletteNames = readPaletteNames(requiredList(actualRoot, "palette"));
        if (!actualPaletteNames.equals(expected.paletteNames())) {
            throw fail("The generated palette changed while writing.");
        }

        NbtList size = requiredList(actualRoot, "size");
        if (size.size() != 3
            || requiredInt(size, 0, "size X") != expected.plan().sizeX()
            || requiredInt(size, 1, "size Y") != expected.plan().sizeY()
            || requiredInt(size, 2, "size Z") != expected.plan().sizeZ()) {
            throw fail("The generated structure size changed while writing.");
        }

        NbtElement entitiesElement = actualRoot.get("entities");
        if (entitiesElement != null) {
            if (!(entitiesElement instanceof NbtList entities)) {
                throw fail("The generated entities tag is not a list.");
            }
            if (!entities.isEmpty()) {
                throw fail("The generated structure unexpectedly contains entities.");
            }
        }

        NbtList actualBlocks = requiredList(actualRoot, "blocks");
        if (actualBlocks.size() != expected.plan().generatedBlocks().size()) {
            throw fail("The generated block count changed while writing.");
        }

        Map<CompactCircularNbtPlan.Position, Integer> expectedRecords = new HashMap<>();
        for (CompactCircularNbtPlan.GeneratedBlock block : expected.plan().generatedBlocks()) {
            if (expectedRecords.put(block.position(), block.state()) != null) {
                throw fail("The expected generated plan contains duplicate positions.");
            }
        }

        Map<CompactCircularNbtPlan.Position, Integer> actualRecords = new HashMap<>();
        for (int index = 0; index < actualBlocks.size(); index++) {
            int blockIndex = index;
            NbtCompound block = actualBlocks.getCompound(index)
                .orElseThrow(() -> fail("Generated block " + blockIndex + " is not a compound."));
            if (block.get("nbt") != null) {
                throw fail("The generated structure unexpectedly contains a block entity.");
            }
            CompactCircularNbtPlan.Position position = readPosition(requiredList(block, "pos"));
            int state = block.getInt("state")
                .orElseThrow(() -> fail("Generated block " + blockIndex + " has no state."));
            if (state < 0 || state >= actualPaletteNames.size()) {
                throw fail("Generated block " + index + " has an invalid palette state.");
            }
            if (actualRecords.put(position, state) != null) {
                throw fail("The generated structure contains duplicate block position " + position + ".");
            }
        }
        if (!actualRecords.equals(expectedRecords)) {
            throw fail("The generated block records changed while writing.");
        }
        if (!actualRoot.equals(expected.root())) {
            throw fail("A generated NBT tag or palette property changed while writing.");
        }
    }

    private static List<String> readPaletteNames(NbtList palette) {
        List<String> names = new ArrayList<>(palette.size());
        for (int index = 0; index < palette.size(); index++) {
            int paletteIndex = index;
            NbtCompound entry = palette.getCompound(index)
                .orElseThrow(() -> fail("Palette entry " + paletteIndex + " is not a compound."));
            String name = entry.getString("Name")
                .orElseThrow(() -> fail("Palette entry " + paletteIndex + " has no Name."));
            names.add(name);
        }
        return names;
    }

    private static List<CompactCircularNbtPlan.SourceBlock> readSourceBlocks(NbtList blocks) {
        List<CompactCircularNbtPlan.SourceBlock> result = new ArrayList<>(blocks.size());
        for (int index = 0; index < blocks.size(); index++) {
            int blockIndex = index;
            NbtCompound block = blocks.getCompound(index)
                .orElseThrow(() -> fail("Block " + blockIndex + " is not a compound."));
            if (block.get("nbt") != null) {
                throw fail("Block entities are not supported (block " + index + ").");
            }
            int state = block.getInt("state")
                .orElseThrow(() -> fail("Block " + blockIndex + " has no state."));
            CompactCircularNbtPlan.Position position = readPosition(requiredList(block, "pos"));
            result.add(new CompactCircularNbtPlan.SourceBlock(index, position, state));
        }
        return result;
    }

    private static CompactCircularNbtPlan.Position readPosition(NbtList position) {
        if (position.size() != 3) throw fail("A block position must contain exactly three integers.");
        return new CompactCircularNbtPlan.Position(
            requiredInt(position, 0, "block X"),
            requiredInt(position, 1, "block Y"),
            requiredInt(position, 2, "block Z")
        );
    }

    private static int requiredInt(NbtList list, int index, String label) {
        Optional<Integer> value = list.getInt(index);
        return value.orElseThrow(() -> fail("Missing or invalid " + label + "."));
    }

    private static NbtList requiredList(NbtCompound compound, String key) {
        return compound.getList(key)
            .orElseThrow(() -> fail("Missing or invalid " + key + " tag."));
    }

    private static NbtCompound requiredCompound(NbtCompound compound, String key) {
        return compound.getCompound(key)
            .orElseThrow(() -> fail("Missing or invalid " + key + " tag."));
    }

    private static IllegalArgumentException fail(String message) {
        return new IllegalArgumentException("Compact circular NBT validation failed: " + message);
    }
}
