package com.julflips.nerv_printer.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
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
    private static final int MARKER_LIGHTING_VERSION = 1;
    private static final String LIGHT_BLOCK_MARKER =
        "nerv_printer:generated_end_rod";

    private CompactCircularNbtGenerator() {
    }

    public enum InputKind {
        SOURCE,
        MARKED_COMPACT,
        LEGACY_COMPACT
    }

    public record GeneratedNbt(
        CompoundTag root,
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

    public static GeneratedNbt generate(CompoundTag sourceRoot) {
        if (sourceRoot != null && sourceRoot.get(MARKER_KEY) != null) {
            throw fail("A marked compact NBT must be loaded with loadOrGenerate().");
        }
        return generateCanonical(sourceRoot, true);
    }

    /**
     * Accepts either a raw contiguous map-grid source or an exact compact file
     * previously produced by this generator. Compact inputs are reconstructed
     * and canonical-regenerated before use; the marker or filename is never
     * trusted as proof that their geometry is valid.
     */
    public static LoadedNbt loadOrGenerate(CompoundTag inputRoot) {
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
            GeneratedNbt generated = generateCanonical(inputRoot, true);
            CompoundTag legacyForm = withoutMarker(generated.root());
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
                "Input is neither a valid contiguous map-grid source nor an exact generated compact NBT. "
                    + "Source check: " + validationMessage(sourceFailure)
                    + " Compact check: " + validationMessage(compactFailure)
            );
        }
    }

    private static GeneratedNbt generateCanonical(
        CompoundTag sourceRoot,
        boolean includeLighting
    ) {
        if (sourceRoot == null) throw fail("The structure root is missing.");
        if (sourceRoot.get(MARKER_KEY) != null) validateMarker(sourceRoot);
        sourceRoot = normalizeExactMapGridDepth(sourceRoot);
        ListTag sourceSize = requiredList(sourceRoot, "size");
        ListTag sourcePalette = requiredList(sourceRoot, "palette");
        ListTag sourceBlockList = requiredList(sourceRoot, "blocks");
        if (sourceSize.size() != 3) throw fail("The structure size tag must contain exactly three integers.");
        int sourceWidth = requiredInt(sourceSize, 0, "size X");
        int sourceHeight = requiredInt(sourceSize, 1, "size Y");
        int sourceDepth = requiredInt(sourceSize, 2, "size Z");
        if (sourceHeight <= 0) throw fail("The structure size Y must be positive.");
        CompactCircularNbtPlan.dimensions(sourceWidth, sourceDepth);
        if (sourcePalette.isEmpty()) throw fail("The structure palette is empty.");
        if (sourceBlockList.isEmpty()) throw fail("The structure contains no blocks.");

        Tag entitiesElement = sourceRoot.get("entities");
        if (entitiesElement != null) {
            if (!(entitiesElement instanceof ListTag entities)) {
                throw fail("The entities tag must be a list.");
            }
            if (!entities.isEmpty()) throw fail("Entities are not supported.");
        }

        List<String> paletteNames = readPaletteNames(sourcePalette);
        List<CompactCircularNbtPlan.SourceBlock> sourceBlocks = readSourceBlocks(sourceBlockList);
        int uprightEndRodState = uprightEndRodState(sourcePalette);
        CompactCircularNbtPlan.Result plan = CompactCircularNbtPlan.generate(
            sourceBlocks,
            paletteNames,
            sourceWidth,
            sourceDepth,
            uprightEndRodState,
            includeLighting
        );

        CompoundTag outputRoot = sourceRoot.copy();
        ListTag outputPalette = requiredList(outputRoot, "palette");
        if (plan.cobblestoneState() == outputPalette.size()) {
            CompoundTag cobblestone = new CompoundTag();
            cobblestone.putString("Name", CompactCircularNbtPlan.COBBLESTONE_NAME);
            outputPalette.add(cobblestone);
            paletteNames = new ArrayList<>(paletteNames);
            paletteNames.add(CompactCircularNbtPlan.COBBLESTONE_NAME);
        }
        if (plan.cobblestoneState() >= outputPalette.size()) {
            throw fail("Could not resolve the cobblestone palette state.");
        }
        if (plan.lightingEnabled()
            && plan.endRodState() == outputPalette.size()) {
            CompoundTag endRod = new CompoundTag();
            endRod.putString("Name", CompactCircularNbtPlan.END_ROD_NAME);
            CompoundTag properties = new CompoundTag();
            properties.putString("facing", "up");
            endRod.put("Properties", properties);
            outputPalette.add(endRod);
            paletteNames = new ArrayList<>(paletteNames);
            paletteNames.add(CompactCircularNbtPlan.END_ROD_NAME);
        }
        if (plan.lightingEnabled()
            && plan.endRodState() >= outputPalette.size()) {
            throw fail("Could not resolve the upright end-rod palette state.");
        }

        ListTag outputBlocks = requiredList(outputRoot, "blocks");
        if (outputBlocks.size() != sourceBlocks.size()) {
            throw fail("The output working copy changed block count before transformation.");
        }

        for (CompactCircularNbtPlan.GeneratedBlock generated : plan.generatedBlocks()) {
            if (generated.connector() || generated.lighting()) continue;
            int sourceIndex = generated.sourceIndex();
            CompoundTag block = outputBlocks.getCompound(sourceIndex)
                .orElseThrow(() -> fail("Output block " + sourceIndex + " is not a compound."));
            ListTag position = requiredList(block, "pos");
            if (position.size() != 3) {
                throw fail("Output block " + sourceIndex + " has an invalid position.");
            }
            CompactCircularNbtPlan.Position expectedOriginal = sourceBlocks.get(sourceIndex).position();
            if (!readPosition(position).equals(expectedOriginal)) {
                throw fail("The output working-copy block order changed before transformation.");
            }
            position.set(1, IntTag.valueOf(generated.position().y()));
            block.putInt("state", generated.state());
        }

        for (CompactCircularNbtPlan.GeneratedBlock connector : plan.connectorBlocks()) {
            CompoundTag block = new CompoundTag();
            ListTag position = new ListTag();
            position.add(IntTag.valueOf(connector.position().x()));
            position.add(IntTag.valueOf(connector.position().y()));
            position.add(IntTag.valueOf(connector.position().z()));
            block.put("pos", position);
            block.putInt("state", connector.state());
            outputBlocks.add(block);
        }
        for (CompactCircularNbtPlan.GeneratedBlock light : plan.lightingBlocks()) {
            CompoundTag block = generatedBlockTag(light);
            block.putBoolean(LIGHT_BLOCK_MARKER, true);
            outputBlocks.add(block);
        }

        ListTag outputSize = requiredList(outputRoot, "size");
        outputSize.set(0, IntTag.valueOf(plan.sizeX()));
        outputSize.set(1, IntTag.valueOf(plan.sizeY()));
        outputSize.set(2, IntTag.valueOf(plan.sizeZ()));
        installMarker(outputRoot, plan);

        GeneratedNbt generated = new GeneratedNbt(outputRoot, plan, paletteNames);
        verifyGeneratedNbt(generated, outputRoot);
        return generated;
    }

    /**
     * Converts an exact C*128 by R*128 map mosaic into the canonical source
     * form used by the staircase planner. The first visible row is retained
     * at Z=1 and copied to Z=0 as a synthetic northern reference row.
     */
    private static CompoundTag normalizeExactMapGridDepth(
        CompoundTag sourceRoot
    ) {
        ListTag sourceSize = requiredList(sourceRoot, "size");
        if (sourceSize.size() != 3) {
            throw fail(
                "The structure size tag must contain exactly three integers."
            );
        }
        int sourceWidth = requiredInt(sourceSize, 0, "size X");
        int sourceDepth = requiredInt(sourceSize, 2, "size Z");
        MapGridLayout.Detected detected =
            MapGridLayout.detectRawStructure(
                sourceWidth,
                sourceDepth
            );
        if (detected.includesNorthernReferenceRow()) {
            return sourceRoot;
        }

        CompoundTag normalized = sourceRoot.copy();
        ListTag inputBlocks = requiredList(sourceRoot, "blocks");
        ListTag normalizedBlocks = new ListTag();
        boolean copiedReferenceShaft = false;
        for (int index = 0; index < inputBlocks.size(); index++) {
            int blockIndex = index;
            CompoundTag input = inputBlocks.getCompound(index)
                .orElseThrow(() -> fail(
                    "Block " + blockIndex + " is not a compound."
                ));
            ListTag inputPosition = requiredList(input, "pos");
            if (inputPosition.size() != 3) {
                throw fail(
                    "Block " + index + " has an invalid position."
                );
            }
            int z = requiredInt(
                inputPosition,
                2,
                "block " + index + " Z"
            );
            if (z < 0 || z >= sourceDepth) {
                throw fail(
                    "Block " + index
                        + " lies outside the declared source depth."
                );
            }
            if (z == 0) {
                normalizedBlocks.add(input.copy());
                copiedReferenceShaft = true;
            }

            CompoundTag shifted = input.copy();
            ListTag shiftedPosition = requiredList(shifted, "pos");
            shiftedPosition.set(2, IntTag.valueOf(z + 1));
            normalizedBlocks.add(shifted);
        }
        if (!copiedReferenceShaft) {
            throw fail(
                "An exact-size map-grid NBT has no Z=0 blocks from which "
                    + "to synthesize its northern reference row."
            );
        }

        normalized.put("blocks", normalizedBlocks);
        requiredList(normalized, "size").set(
            2,
            IntTag.valueOf(Math.addExact(sourceDepth, 1))
        );
        return normalized;
    }

    private static GeneratedNbt loadCanonicalCompact(
        CompoundTag compactRoot,
        boolean marked
    ) {
        if (marked) {
            validateMarker(compactRoot);
        } else if (compactRoot.get(MARKER_KEY) != null) {
            throw fail("Legacy compact validation received a marked NBT.");
        }

        CompoundTag reconstructedSource = reconstructSource(compactRoot);
        boolean hasLighting = hasGeneratedLighting(compactRoot);
        GeneratedNbt canonical = generateCanonical(
            reconstructedSource,
            true
        );
        GeneratedNbt validationCanonical = hasLighting
            ? canonical
            : generateCanonical(reconstructedSource, false);
        if (marked) {
            verifyGeneratedNbt(validationCanonical, compactRoot);
        } else {
            CompoundTag upgraded = compactRoot.copy();
            CompoundTag marker = requiredCompound(
                validationCanonical.root(),
                MARKER_KEY
            );
            upgraded.put(MARKER_KEY, marker.copy());
            verifyGeneratedNbt(validationCanonical, upgraded);
        }
        return canonical;
    }

    private static CompoundTag reconstructSource(CompoundTag compactRoot) {
        List<String> paletteNames = readPaletteNames(requiredList(compactRoot, "palette"));
        int cobblestoneState = paletteNames.indexOf(CompactCircularNbtPlan.COBBLESTONE_NAME);
        if (cobblestoneState < 0) {
            throw fail("A generated compact NBT has no cobblestone palette state.");
        }

        ListTag compactBlocks = requiredList(compactRoot, "blocks");
        if (compactBlocks.isEmpty()) throw fail("The structure contains no blocks.");
        int sourceWidth = CompactCircularNbtPlan.MAP_WIDTH;
        int sourceDepth = CompactCircularNbtPlan.SOURCE_Z_SIZE;
        CompoundTag marker = compactRoot.getCompound(MARKER_KEY).orElse(null);
        if (marker != null) {
            sourceWidth = marker.getInt("source_width")
                .orElseThrow(() -> fail("The compact marker has no source width."));
            sourceDepth = marker.getInt("source_depth")
                .orElseThrow(() -> fail("The compact marker has no source depth."));
        }
        CompactCircularNbtPlan.Dimensions dimensions =
            CompactCircularNbtPlan.dimensions(sourceWidth, sourceDepth);
        int[] startingTopY = new int[dimensions.mapWidth()];
        int[] startingTopState = new int[dimensions.mapWidth()];
        boolean[] startingTopPresent = new boolean[dimensions.mapWidth()];

        for (int index = 0; index < compactBlocks.size(); index++) {
            int blockIndex = index;
            CompoundTag block = compactBlocks.getCompound(index)
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
                || position.x() >= dimensions.mapWidth()) {
                continue;
            }
            if (!startingTopPresent[position.x()]
                || position.y() > startingTopY[position.x()]) {
                startingTopPresent[position.x()] = true;
                startingTopY[position.x()] = position.y();
                startingTopState[position.x()] = state;
            }
        }

        for (int x = 0; x < dimensions.mapWidth(); x++) {
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
        CompoundTag source = compactRoot.copy();
        ListTag sourceBlocks = new ListTag();
        for (int index = 0; index < compactBlocks.size(); index++) {
            CompoundTag compactBlock = compactBlocks.getCompound(index).orElseThrow();
            if (compactBlock.getBooleanOr(
                LIGHT_BLOCK_MARKER,
                false
            )) {
                continue;
            }
            CompactCircularNbtPlan.Position position =
                readPosition(requiredList(compactBlock, "pos"));
            if (position.z() > dimensions.visibleRows()) continue;

            CompoundTag sourceBlock = compactBlock.copy();
            ListTag sourcePosition = requiredList(sourceBlock, "pos");
            sourcePosition.set(1, IntTag.valueOf(position.y() - globalYShift));
            sourceBlocks.add(sourceBlock);
        }
        source.put("blocks", sourceBlocks);

        ListTag sourceSize = new ListTag();
        sourceSize.add(IntTag.valueOf(dimensions.mapWidth()));
        sourceSize.add(IntTag.valueOf(1));
        sourceSize.add(IntTag.valueOf(dimensions.sourceDepth()));
        source.put("size", sourceSize);
        return source;
    }

    private static void installMarker(
        CompoundTag root,
        CompactCircularNbtPlan.Result plan
    ) {
        CompoundTag marker = new CompoundTag();
        marker.putString("format", MARKER_FORMAT);
        marker.putInt("schema_version", MARKER_SCHEMA_VERSION);
        marker.putInt("geometry_version", MARKER_GEOMETRY_VERSION);
        marker.putInt("source_width", plan.mapWidth());
        marker.putInt("source_depth", plan.sourceDepth());
        marker.putInt("maximum_extension", plan.maximumExtension());
        if (plan.lightingEnabled()) {
            marker.putInt(
                "lighting_version",
                MARKER_LIGHTING_VERSION
            );
            marker.putInt(
                "minimum_surface_block_light",
                plan.minimumGuaranteedSurfaceLight()
            );
            marker.putInt(
                "end_rod_height_above_surface",
                EndRodLightingPlan.HEIGHT_ABOVE_SURFACE
            );
        }
        root.put(MARKER_KEY, marker);
    }

    private static void validateMarker(CompoundTag root) {
        CompoundTag marker = requiredCompound(root, MARKER_KEY);
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
        Integer lightingVersion = marker.getInt("lighting_version")
            .orElse(null);

        if (!MARKER_FORMAT.equals(format)) throw fail("The compact marker format is unsupported.");
        if (schemaVersion != MARKER_SCHEMA_VERSION) {
            throw fail("Unsupported compact marker schema version " + schemaVersion + ".");
        }
        if (geometryVersion != MARKER_GEOMETRY_VERSION) {
            throw fail("Unsupported compact geometry version " + geometryVersion + ".");
        }
        CompactCircularNbtPlan.dimensions(sourceWidth, sourceDepth);
        if (maximumExtension < 0
            || maximumExtension > CompactCircularNbtPlan.MAX_OUTSIDE_EXTENSION) {
            throw fail("The compact marker maximum extension is invalid.");
        }
        if (lightingVersion != null) {
            if (lightingVersion != MARKER_LIGHTING_VERSION
                || marker.getInt("minimum_surface_block_light")
                    .orElse(-1)
                    < EndRodLightingPlan.MINIMUM_SURFACE_LIGHT
                || marker.getInt("end_rod_height_above_surface")
                    .orElse(-1)
                    != EndRodLightingPlan.HEIGHT_ABOVE_SURFACE) {
                throw fail("The compact marker lighting contract is invalid.");
            }
        }
    }

    private static CompoundTag withoutMarker(CompoundTag root) {
        CompoundTag copy = root.copy();
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
            CompoundTag written = NbtIo.readCompressed(
                temporary,
                new NbtAccounter(MAX_NBT_BYTES, MAX_NBT_DEPTH)
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

    public static void verifyGeneratedNbt(GeneratedNbt expected, CompoundTag actualRoot) {
        List<String> actualPaletteNames = readPaletteNames(requiredList(actualRoot, "palette"));
        if (!actualPaletteNames.equals(expected.paletteNames())) {
            throw fail("The generated palette changed while writing.");
        }

        ListTag size = requiredList(actualRoot, "size");
        if (size.size() != 3
            || requiredInt(size, 0, "size X") != expected.plan().sizeX()
            || requiredInt(size, 1, "size Y") != expected.plan().sizeY()
            || requiredInt(size, 2, "size Z") != expected.plan().sizeZ()) {
            throw fail("The generated structure size changed while writing.");
        }

        Tag entitiesElement = actualRoot.get("entities");
        if (entitiesElement != null) {
            if (!(entitiesElement instanceof ListTag entities)) {
                throw fail("The generated entities tag is not a list.");
            }
            if (!entities.isEmpty()) {
                throw fail("The generated structure unexpectedly contains entities.");
            }
        }

        ListTag actualBlocks = requiredList(actualRoot, "blocks");
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
            CompoundTag block = actualBlocks.getCompound(index)
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

    private static CompoundTag generatedBlockTag(
        CompactCircularNbtPlan.GeneratedBlock generated
    ) {
        CompoundTag block = new CompoundTag();
        ListTag position = new ListTag();
        position.add(IntTag.valueOf(generated.position().x()));
        position.add(IntTag.valueOf(generated.position().y()));
        position.add(IntTag.valueOf(generated.position().z()));
        block.put("pos", position);
        block.putInt("state", generated.state());
        return block;
    }

    private static boolean hasGeneratedLighting(CompoundTag root) {
        ListTag blocks = requiredList(root, "blocks");
        for (int index = 0; index < blocks.size(); index++) {
            int blockIndex = index;
            CompoundTag block = blocks.getCompound(index)
                .orElseThrow(() -> fail(
                    "Generated block " + blockIndex
                        + " is not a compound."
                ));
            if (block.getBooleanOr(LIGHT_BLOCK_MARKER, false)) {
                return true;
            }
        }
        return false;
    }

    private static int uprightEndRodState(ListTag palette) {
        for (int index = palette.size() - 1; index >= 0; index--) {
            int paletteIndex = index;
            CompoundTag entry = palette.getCompound(index)
                .orElseThrow(() -> fail(
                    "Palette entry " + paletteIndex
                        + " is not a compound."
                ));
            if (!CompactCircularNbtPlan.END_ROD_NAME.equals(
                entry.getStringOr("Name", "")
            )) {
                continue;
            }
            CompoundTag properties = entry.getCompound("Properties")
                .orElse(null);
            if (properties != null
                && "up".equals(properties.getStringOr("facing", ""))) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> readPaletteNames(ListTag palette) {
        List<String> names = new ArrayList<>(palette.size());
        for (int index = 0; index < palette.size(); index++) {
            int paletteIndex = index;
            CompoundTag entry = palette.getCompound(index)
                .orElseThrow(() -> fail("Palette entry " + paletteIndex + " is not a compound."));
            String name = entry.getString("Name")
                .orElseThrow(() -> fail("Palette entry " + paletteIndex + " has no Name."));
            names.add(name);
        }
        return names;
    }

    private static List<CompactCircularNbtPlan.SourceBlock> readSourceBlocks(ListTag blocks) {
        List<CompactCircularNbtPlan.SourceBlock> result = new ArrayList<>(blocks.size());
        for (int index = 0; index < blocks.size(); index++) {
            int blockIndex = index;
            CompoundTag block = blocks.getCompound(index)
                .orElseThrow(() -> fail("Block " + blockIndex + " is not a compound."));
            if (block.get("nbt") != null) {
                throw fail("Block entities are not supported (block " + index + ").");
            }
            if (block.get(LIGHT_BLOCK_MARKER) != null) {
                throw fail(
                    "Source block " + index
                        + " uses a reserved generated-light marker."
                );
            }
            int state = block.getInt("state")
                .orElseThrow(() -> fail("Block " + blockIndex + " has no state."));
            CompactCircularNbtPlan.Position position = readPosition(requiredList(block, "pos"));
            result.add(new CompactCircularNbtPlan.SourceBlock(index, position, state));
        }
        return result;
    }

    private static CompactCircularNbtPlan.Position readPosition(ListTag position) {
        if (position.size() != 3) throw fail("A block position must contain exactly three integers.");
        return new CompactCircularNbtPlan.Position(
            requiredInt(position, 0, "block X"),
            requiredInt(position, 1, "block Y"),
            requiredInt(position, 2, "block Z")
        );
    }

    private static int requiredInt(ListTag list, int index, String label) {
        Optional<Integer> value = list.getInt(index);
        return value.orElseThrow(() -> fail("Missing or invalid " + label + "."));
    }

    private static ListTag requiredList(CompoundTag compound, String key) {
        return compound.getList(key)
            .orElseThrow(() -> fail("Missing or invalid " + key + " tag."));
    }

    private static CompoundTag requiredCompound(CompoundTag compound, String key) {
        return compound.getCompound(key)
            .orElseThrow(() -> fail("Missing or invalid " + key + " tag."));
    }

    private static IllegalArgumentException fail(String message) {
        return new IllegalArgumentException("Compact circular NBT validation failed: " + message);
    }
}
