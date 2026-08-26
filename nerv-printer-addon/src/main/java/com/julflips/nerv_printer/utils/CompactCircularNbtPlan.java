package com.julflips.nerv_printer.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure geometry implementation of COMPACT_CIRCULAR_U_NBT_GENERATION_SPEC.md.
 *
 * <p>This class deliberately has no Minecraft dependencies. The NBT adapter and
 * the printer both consume the same validated result, preventing their geometry
 * implementations from drifting apart.</p>
 */
public final class CompactCircularNbtPlan {
    /** Legacy one-map defaults retained for callers that size 1x1 buffers. */
    public static final int MAP_WIDTH = 128;
    public static final int START_Z = 0;
    public static final int FAR_Z = 128;
    public static final int SOURCE_Z_SIZE = 129;
    public static final int VISIBLE_ROWS = 128;
    public static final int PAIR_COUNT = 64;
    public static final int MAX_OUTSIDE_EXTENSION = 5;
    public static final int OPTIMIZED_HELIX_EXTENSION = 3;
    public static final String COBBLESTONE_NAME = "minecraft:cobblestone";
    public static final String END_ROD_NAME = "minecraft:end_rod";

    private CompactCircularNbtPlan() {
    }

    /**
     * Validated source geometry for a contiguous grid of scale-zero maps.
     * The source has one global northern reference row, followed by one or
     * more complete 128-row map bands.
     */
    public record Dimensions(
        int mapWidth,
        int visibleRows,
        int sourceDepth,
        int pairCount,
        int mapColumns,
        int mapRows
    ) {
    }

    public static Dimensions dimensions(int mapWidth, int sourceDepth) {
        if (mapWidth <= 0 || mapWidth % MAP_WIDTH != 0) {
            throw fail("Source width must be a positive multiple of 128.");
        }
        if ((mapWidth & 1) != 0) {
            throw fail("Source width must be even so every column has a U-pair partner.");
        }
        if (sourceDepth <= 1) {
            throw fail("Source depth must contain a northern reference row and visible rows.");
        }
        int visibleRows = sourceDepth - 1;
        if (visibleRows % VISIBLE_ROWS != 0) {
            throw fail("Visible source depth (size Z minus one) must be a positive multiple of 128.");
        }
        return new Dimensions(
            mapWidth,
            visibleRows,
            sourceDepth,
            mapWidth / 2,
            mapWidth / MAP_WIDTH,
            visibleRows / VISIBLE_ROWS
        );
    }

    public record Position(int x, int y, int z) {
        public Position addY(int offset) {
            return new Position(x, y + offset, z);
        }
    }

    public record SourceBlock(int index, Position position, int state) {
    }

    public record GeneratedBlock(
        Position position,
        int state,
        int sourceIndex,
        boolean connector,
        boolean lighting
    ) {
    }

    public record PairRoute(
        int pairIndex,
        int outboundX,
        int returnX,
        int outboundFarYRelative,
        int returnFarYRelative,
        int heightDifference,
        int minimumEdges,
        String routeFamily,
        int fullCircularTurns,
        int maximumExtension,
        List<Position> relativePath,
        List<Position> absolutePath
    ) {
        public PairRoute {
            relativePath = List.copyOf(relativePath);
            absolutePath = List.copyOf(absolutePath);
        }

        public List<Position> relativeInterior() {
            return relativePath.subList(1, relativePath.size() - 1);
        }
    }

    public static final class Result {
        private final Dimensions dimensions;
        private final List<GeneratedBlock> generatedBlocks;
        private final List<GeneratedBlock> connectorBlocks;
        private final List<GeneratedBlock> lightingBlocks;
        private final List<PairRoute> pairRoutes;
        private final int[][] sourceTopY;
        private final int[][] sourceTopState;
        private final int[][] targetSurfaceY;
        private final int cobblestoneState;
        private final int endRodState;
        private final int minimumGuaranteedSurfaceLight;
        private final int globalYShift;
        private final int sizeY;
        private final int sizeZ;
        private final int maximumExtension;

        private Result(
            Dimensions dimensions,
            List<GeneratedBlock> generatedBlocks,
            List<GeneratedBlock> connectorBlocks,
            List<GeneratedBlock> lightingBlocks,
            List<PairRoute> pairRoutes,
            int[][] sourceTopY,
            int[][] sourceTopState,
            int[][] targetSurfaceY,
            int cobblestoneState,
            int endRodState,
            int minimumGuaranteedSurfaceLight,
            int globalYShift,
            int sizeY,
            int sizeZ,
            int maximumExtension
        ) {
            this.dimensions = dimensions;
            this.generatedBlocks = List.copyOf(generatedBlocks);
            this.connectorBlocks = List.copyOf(connectorBlocks);
            this.lightingBlocks = List.copyOf(lightingBlocks);
            this.pairRoutes = List.copyOf(pairRoutes);
            this.sourceTopY = copyGrid(sourceTopY);
            this.sourceTopState = copyGrid(sourceTopState);
            this.targetSurfaceY = copyGrid(targetSurfaceY);
            this.cobblestoneState = cobblestoneState;
            this.endRodState = endRodState;
            this.minimumGuaranteedSurfaceLight =
                minimumGuaranteedSurfaceLight;
            this.globalYShift = globalYShift;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.maximumExtension = maximumExtension;
        }

        public List<GeneratedBlock> generatedBlocks() {
            return generatedBlocks;
        }

        public List<GeneratedBlock> connectorBlocks() {
            return connectorBlocks;
        }

        public List<GeneratedBlock> lightingBlocks() {
            return lightingBlocks;
        }

        public List<PairRoute> pairRoutes() {
            return pairRoutes;
        }

        public int sourceTopY(int x, int z) {
            return sourceTopY[x][z];
        }

        public int sourceTopState(int x, int z) {
            return sourceTopState[x][z];
        }

        public int targetSurfaceY(int x, int z) {
            return targetSurfaceY[x][z];
        }

        public int cobblestoneState() {
            return cobblestoneState;
        }

        public int endRodState() {
            return endRodState;
        }

        public int minimumGuaranteedSurfaceLight() {
            return minimumGuaranteedSurfaceLight;
        }

        public boolean lightingEnabled() {
            return endRodState >= 0;
        }

        public int globalYShift() {
            return globalYShift;
        }

        public int startingY() {
            return globalYShift;
        }

        public int sizeX() {
            return dimensions.mapWidth();
        }

        public int mapWidth() {
            return dimensions.mapWidth();
        }

        public int visibleRows() {
            return dimensions.visibleRows();
        }

        public int sourceDepth() {
            return dimensions.sourceDepth();
        }

        public int pairCount() {
            return dimensions.pairCount();
        }

        public int mapColumns() {
            return dimensions.mapColumns();
        }

        public int mapRows() {
            return dimensions.mapRows();
        }

        public int sizeY() {
            return sizeY;
        }

        public int sizeZ() {
            return sizeZ;
        }

        public int maximumExtension() {
            return maximumExtension;
        }

        private static int[][] copyGrid(int[][] source) {
            int[][] copy = new int[source.length][];
            for (int i = 0; i < source.length; i++) copy[i] = source[i].clone();
            return copy;
        }
    }

    private record HorizontalPosition(int x, int z) {
    }

    private record HorizontalRoute(
        List<HorizontalPosition> positions,
        int fullTurns,
        String family
    ) {
    }

    private record RelativePairRoute(
        int pairIndex,
        int outboundX,
        int returnX,
        int outboundFarY,
        int returnFarY,
        int heightDifference,
        int minimumEdges,
        String routeFamily,
        int fullCircularTurns,
        int maximumExtension,
        List<Position> path
    ) {
    }

    public static Result generate(
        List<SourceBlock> sourceBlocks,
        List<String> paletteNames
    ) {
        Objects.requireNonNull(sourceBlocks, "sourceBlocks");
        if (sourceBlocks.isEmpty()) throw fail("The NBT contains no blocks.");
        int maximumX = sourceBlocks.stream()
            .mapToInt(block -> block.position().x())
            .max()
            .orElseThrow();
        int maximumZ = sourceBlocks.stream()
            .mapToInt(block -> block.position().z())
            .max()
            .orElseThrow();
        if (maximumX == Integer.MAX_VALUE || maximumZ == Integer.MAX_VALUE) {
            throw fail("Source dimensions exceed the supported integer coordinate range.");
        }
        return generate(
            sourceBlocks,
            paletteNames,
            maximumX + 1,
            maximumZ + 1,
            lastPaletteState(paletteNames, END_ROD_NAME),
            true
        );
    }

    public static Result generate(
        List<SourceBlock> sourceBlocks,
        List<String> paletteNames,
        int mapWidth,
        int sourceDepth
    ) {
        return generate(
            sourceBlocks,
            paletteNames,
            mapWidth,
            sourceDepth,
            lastPaletteState(paletteNames, END_ROD_NAME),
            true
        );
    }

    static Result generate(
        List<SourceBlock> sourceBlocks,
        List<String> paletteNames,
        int mapWidth,
        int sourceDepth,
        int uprightEndRodState,
        boolean includeLighting
    ) {
        Objects.requireNonNull(sourceBlocks, "sourceBlocks");
        Objects.requireNonNull(paletteNames, "paletteNames");
        Dimensions dimensions = dimensions(mapWidth, sourceDepth);
        if (sourceBlocks.isEmpty()) throw fail("The NBT contains no blocks.");
        if (paletteNames.isEmpty()) throw fail("The NBT palette is empty.");

        int cobblestoneState = paletteNames.indexOf(COBBLESTONE_NAME);
        int nextGeneratedState = paletteNames.size();
        if (cobblestoneState < 0) {
            cobblestoneState = nextGeneratedState++;
        }
        if (uprightEndRodState < -1
            || uprightEndRodState >= paletteNames.size()
            || uprightEndRodState >= 0
                && !END_ROD_NAME.equals(
                    paletteNames.get(uprightEndRodState)
                )) {
            throw fail("The requested upright end-rod palette state is invalid.");
        }
        int endRodState = includeLighting
            ? uprightEndRodState >= 0
                ? uprightEndRodState
                : nextGeneratedState
            : -1;

        int[][] topY = new int[dimensions.mapWidth()][dimensions.sourceDepth()];
        int[][] topState = new int[dimensions.mapWidth()][dimensions.sourceDepth()];
        int[][] topIndex = new int[dimensions.mapWidth()][dimensions.sourceDepth()];
        boolean[][] hasTop = new boolean[dimensions.mapWidth()][dimensions.sourceDepth()];
        Set<Position> sourcePositions = new HashSet<>();

        for (int expectedIndex = 0; expectedIndex < sourceBlocks.size(); expectedIndex++) {
            SourceBlock block = sourceBlocks.get(expectedIndex);
            if (block.index() != expectedIndex) {
                throw fail("Source block indices must match NBT list order.");
            }
            if (block.state() < 0 || block.state() >= paletteNames.size()) {
                throw fail("Block " + expectedIndex + " references invalid palette state " + block.state() + ".");
            }
            if (!sourcePositions.add(block.position())) {
                throw fail("Duplicate block position: " + block.position());
            }

            Position position = block.position();
            if (position.x() < 0 || position.x() >= dimensions.mapWidth()
                || position.z() < 0 || position.z() >= dimensions.sourceDepth()) {
                throw fail(
                    "Block " + expectedIndex + " lies outside declared X/Z source bounds."
                );
            }

            if (!hasTop[position.x()][position.z()]
                || position.y() > topY[position.x()][position.z()]) {
                hasTop[position.x()][position.z()] = true;
                topY[position.x()][position.z()] = position.y();
                topState[position.x()][position.z()] = block.state();
                topIndex[position.x()][position.z()] = block.index();
            } else if (position.y() == topY[position.x()][position.z()]) {
                throw fail("Multiple blocks occupy the highest position at " + position + ".");
            }
        }

        for (int x = 0; x < dimensions.mapWidth(); x++) {
            for (int z = 0; z < dimensions.sourceDepth(); z++) {
                if (!hasTop[x][z]) {
                    throw fail("The source contains an empty X/Z shaft at X=" + x + ", Z=" + z + ".");
                }
            }
        }

        int[][] targetSurface = generateTargetSurface(topY, dimensions);
        EndRodLightingPlan.Result lightingPlan = includeLighting
            ? generateLightingPlan(targetSurface, dimensions)
            : null;
        List<RelativePairRoute> relativeRoutes = new ArrayList<>(dimensions.pairCount());
        List<Position> connectorPositionsRelative = new ArrayList<>();
        Set<Position> occupiedConnectorPositions = new HashSet<>();
        Map<HorizontalPosition, Integer> shaftOwners = new HashMap<>();
        int maximumExtension = 0;

        for (int pairIndex = 0; pairIndex < dimensions.pairCount(); pairIndex++) {
            int firstX = pairIndex * 2;
            int secondX = firstX + 1;
            RelativePairRoute route = generateOptimalConnector(
                pairIndex,
                firstX,
                secondX,
                dimensions.visibleRows(),
                targetSurface[firstX][dimensions.visibleRows()],
                targetSurface[secondX][dimensions.visibleRows()]
            );
            relativeRoutes.add(route);
            maximumExtension = Math.max(maximumExtension, route.maximumExtension());

            for (Position position : route.path().subList(1, route.path().size() - 1)) {
                if (position.z() <= dimensions.visibleRows()) {
                    throw fail("Pair " + pairIndex + " placed an interior connector inside the map.");
                }
                if (!occupiedConnectorPositions.add(position)) {
                    throw fail("Connector collision at " + position + ".");
                }
                HorizontalPosition shaft = new HorizontalPosition(position.x(), position.z());
                Integer owner = shaftOwners.putIfAbsent(shaft, pairIndex);
                if (owner != null && owner != pairIndex) {
                    throw fail("Pairs " + owner + " and " + pairIndex + " share connector shaft " + shaft + ".");
                }
                connectorPositionsRelative.add(position);
            }
        }

        if (maximumExtension > OPTIMIZED_HELIX_EXTENSION
            || maximumExtension > MAX_OUTSIDE_EXTENSION) {
            throw fail("Generated connector exceeds the permitted outside extension.");
        }

        List<GeneratedBlock> transformedRelative = new ArrayList<>(sourceBlocks.size());
        for (SourceBlock block : sourceBlocks) {
            Position old = block.position();
            int surfaceShift = targetSurface[old.x()][old.z()] - topY[old.x()][old.z()];
            int state = block.state();
            if (old.z() == START_Z && block.index() == topIndex[old.x()][START_Z]) {
                state = cobblestoneState;
            }
            transformedRelative.add(new GeneratedBlock(
                new Position(old.x(), old.y() + surfaceShift, old.z()),
                state,
                block.index(),
                false,
                false
            ));
        }

        int minimumRelativeY = Integer.MAX_VALUE;
        for (GeneratedBlock block : transformedRelative) {
            minimumRelativeY = Math.min(minimumRelativeY, block.position().y());
        }
        for (Position connector : connectorPositionsRelative) {
            minimumRelativeY = Math.min(minimumRelativeY, connector.y());
        }
        int globalYShift = -minimumRelativeY;

        List<GeneratedBlock> generatedBlocks = new ArrayList<>(
            transformedRelative.size()
                + connectorPositionsRelative.size()
                + (lightingPlan == null ? 0 : lightingPlan.rods().size())
        );
        List<GeneratedBlock> connectorBlocks = new ArrayList<>(connectorPositionsRelative.size());
        List<GeneratedBlock> lightingBlocks = new ArrayList<>(
            lightingPlan == null ? 0 : lightingPlan.rods().size()
        );
        Set<Position> generatedPositions = new HashSet<>();
        int maximumY = Integer.MIN_VALUE;

        for (GeneratedBlock relative : transformedRelative) {
            GeneratedBlock absolute = new GeneratedBlock(
                relative.position().addY(globalYShift),
                relative.state(),
                relative.sourceIndex(),
                false,
                false
            );
            addGeneratedBlock(absolute, generatedBlocks, generatedPositions);
            maximumY = Math.max(maximumY, absolute.position().y());
        }
        for (Position relative : connectorPositionsRelative) {
            GeneratedBlock absolute = new GeneratedBlock(
                relative.addY(globalYShift),
                cobblestoneState,
                -1,
                true,
                false
            );
            addGeneratedBlock(absolute, generatedBlocks, generatedPositions);
            connectorBlocks.add(absolute);
            maximumY = Math.max(maximumY, absolute.position().y());
        }
        if (lightingPlan != null) {
            for (EndRodLightingPlan.Rod rod : lightingPlan.rods()) {
                GeneratedBlock absolute = new GeneratedBlock(
                    new Position(
                        rod.x(),
                        rod.y() + globalYShift,
                        rod.z() + 1
                    ),
                    endRodState,
                    -1,
                    false,
                    true
                );
                addGeneratedBlock(
                    absolute,
                    generatedBlocks,
                    generatedPositions
                );
                lightingBlocks.add(absolute);
                maximumY = Math.max(
                    maximumY,
                    absolute.position().y()
                );
            }
        }

        List<PairRoute> pairRoutes = new ArrayList<>(dimensions.pairCount());
        for (RelativePairRoute relative : relativeRoutes) {
            List<Position> absolutePath = relative.path().stream()
                .map(position -> position.addY(globalYShift))
                .toList();
            pairRoutes.add(new PairRoute(
                relative.pairIndex(),
                relative.outboundX(),
                relative.returnX(),
                relative.outboundFarY(),
                relative.returnFarY(),
                relative.heightDifference(),
                relative.minimumEdges(),
                relative.routeFamily(),
                relative.fullCircularTurns(),
                relative.maximumExtension(),
                relative.path(),
                absolutePath
            ));
        }

        Result result = new Result(
            dimensions,
            generatedBlocks,
            connectorBlocks,
            lightingBlocks,
            pairRoutes,
            topY,
            topState,
            targetSurface,
            cobblestoneState,
            endRodState,
            lightingPlan == null
                ? 0
                : lightingPlan.minimumGuaranteedLight(),
            globalYShift,
            maximumY + 1,
            dimensions.visibleRows() + maximumExtension + 1,
            maximumExtension
        );
        validateGenerated(result, sourceBlocks, paletteNames.size(), generatedPositions);
        return result;
    }

    private static int[][] generateTargetSurface(
        int[][] sourceTopY,
        Dimensions dimensions
    ) {
        int[][] target = new int[dimensions.mapWidth()][dimensions.sourceDepth()];
        for (int x = 0; x < dimensions.mapWidth(); x++) {
            target[x][START_Z] = 0;
            for (int z = 1; z < dimensions.sourceDepth(); z++) {
                target[x][z] = target[x][z - 1]
                    + Integer.signum(sourceTopY[x][z] - sourceTopY[x][z - 1]);
            }
        }
        return target;
    }

    private static EndRodLightingPlan.Result generateLightingPlan(
        int[][] targetSurface,
        Dimensions dimensions
    ) {
        int[][] visibleSurface = new int[
            dimensions.mapWidth()
        ][
            dimensions.visibleRows()
        ];
        for (int x = 0; x < dimensions.mapWidth(); x++) {
            System.arraycopy(
                targetSurface[x],
                1,
                visibleSurface[x],
                0,
                dimensions.visibleRows()
            );
        }
        return EndRodLightingPlan.generate(visibleSurface);
    }

    private static int lastPaletteState(
        List<String> paletteNames,
        String blockName
    ) {
        Objects.requireNonNull(paletteNames, "paletteNames");
        for (int index = paletteNames.size() - 1; index >= 0; index--) {
            if (blockName.equals(paletteNames.get(index))) return index;
        }
        return -1;
    }

    private static RelativePairRoute generateOptimalConnector(
        int pairIndex,
        int firstX,
        int secondX,
        int farZ,
        int firstY,
        int secondY
    ) {
        if (secondX != firstX + 1) throw fail("Connector columns must be adjacent.");
        int difference = secondY - firstY;
        int magnitude = Math.abs(difference);
        int minimumEdges = smallestOddAtLeast(magnitude);
        HorizontalRoute horizontal = minimumEdges <= 5
            ? generateSimpleU(firstX, secondX, farZ, minimumEdges)
            : generateCircular(firstX, secondX, farZ, minimumEdges);

        if (horizontal.positions().size() - 1 != minimumEdges) {
            throw fail("Pair " + pairIndex + " produced the wrong number of edges.");
        }

        List<Position> path = new ArrayList<>(horizontal.positions().size());
        int currentY = firstY;
        int direction = Integer.signum(difference);
        HorizontalPosition first = horizontal.positions().getFirst();
        path.add(new Position(first.x(), currentY, first.z()));
        for (int edgeIndex = 0; edgeIndex < horizontal.positions().size() - 1; edgeIndex++) {
            HorizontalPosition position = horizontal.positions().get(edgeIndex + 1);
            if (edgeIndex < magnitude) currentY += direction;
            path.add(new Position(position.x(), currentY, position.z()));
        }

        if (currentY != secondY) throw fail("Pair " + pairIndex + " ended at the wrong Y.");
        int extension = validateConnectorPath(
            path,
            pairIndex,
            firstX,
            secondX,
            farZ,
            minimumEdges
        );
        if (extension > OPTIMIZED_HELIX_EXTENSION) {
            throw fail("Pair " + pairIndex + " exceeded the optimized helix footprint.");
        }

        return new RelativePairRoute(
            pairIndex,
            firstX,
            secondX,
            firstY,
            secondY,
            difference,
            minimumEdges,
            horizontal.family(),
            horizontal.fullTurns(),
            extension,
            List.copyOf(path)
        );
    }

    private static HorizontalRoute generateSimpleU(
        int firstX,
        int secondX,
        int farZ,
        int edgeCount
    ) {
        if (edgeCount != 1 && edgeCount != 3 && edgeCount != 5) {
            throw fail("Simple U edge count must be 1, 3, or 5.");
        }
        int extension = (edgeCount - 1) / 2;
        List<HorizontalPosition> positions = new ArrayList<>();
        positions.add(new HorizontalPosition(firstX, farZ));
        for (int distance = 1; distance <= extension; distance++) {
            positions.add(new HorizontalPosition(firstX, farZ + distance));
        }
        positions.add(new HorizontalPosition(secondX, farZ + extension));
        for (int distance = extension - 1; distance >= 0; distance--) {
            positions.add(new HorizontalPosition(secondX, farZ + distance));
        }
        return new HorizontalRoute(List.copyOf(positions), 0, "simple_u");
    }

    private static HorizontalRoute generateCircular(
        int firstX,
        int secondX,
        int farZ,
        int edgeCount
    ) {
        if (edgeCount < 7 || edgeCount % 2 == 0) {
            throw fail("Circular connector edge count must be odd and at least seven.");
        }

        HorizontalPosition a = new HorizontalPosition(firstX, farZ);
        HorizontalPosition p = new HorizontalPosition(firstX, farZ + 1);
        HorizontalPosition r = new HorizontalPosition(firstX, farZ + 2);
        HorizontalPosition s = new HorizontalPosition(firstX, farZ + 3);
        HorizontalPosition t = new HorizontalPosition(secondX, farZ + 3);
        HorizontalPosition u = new HorizontalPosition(secondX, farZ + 2);
        HorizontalPosition q = new HorizontalPosition(secondX, farZ + 1);
        HorizontalPosition b = new HorizontalPosition(secondX, farZ);
        List<HorizontalPosition> oneTurn = List.of(s, t, u, r);
        List<HorizontalPosition> positions = new ArrayList<>(List.of(a, p, r));

        int fullTurns;
        String family;
        if (edgeCount % 4 == 1) {
            fullTurns = (edgeCount - 5) / 4;
            if (fullTurns < 1) throw fail("Invalid circular 5+4k route.");
            for (int i = 0; i < fullTurns; i++) positions.addAll(oneTurn);
            positions.addAll(List.of(p, q, b));
            family = "5_plus_4k";
        } else {
            fullTurns = (edgeCount - 7) / 4;
            for (int i = 0; i < fullTurns; i++) positions.addAll(oneTurn);
            positions.addAll(List.of(s, t, u, q, b));
            family = "7_plus_4k";
        }
        return new HorizontalRoute(List.copyOf(positions), fullTurns, family);
    }

    private static int validateConnectorPath(
        List<Position> path,
        int pairIndex,
        int firstX,
        int secondX,
        int farZ,
        int requiredEdges
    ) {
        if (path.size() - 1 != requiredEdges) throw fail("Pair " + pairIndex + " is not edge-optimal.");
        if (!path.getFirst().equals(new Position(firstX, path.getFirst().y(), farZ))
            || !path.getLast().equals(new Position(secondX, path.getLast().y(), farZ))) {
            throw fail("Pair " + pairIndex + " has invalid endpoints.");
        }

        Set<Position> positions = new HashSet<>();
        Map<HorizontalPosition, List<Integer>> shaftHeights = new LinkedHashMap<>();
        int maximumExtension = 0;
        for (Position position : path) {
            if (position.x() != firstX && position.x() != secondX) {
                throw fail("Pair " + pairIndex + " left its assigned pair lane.");
            }
            int extension = position.z() - farZ;
            if (extension < 0 || extension > MAX_OUTSIDE_EXTENSION) {
                throw fail("Pair " + pairIndex + " exceeded its connector bounds.");
            }
            maximumExtension = Math.max(maximumExtension, extension);
            if (!positions.add(position)) throw fail("Pair " + pairIndex + " self-collides at " + position + ".");
            shaftHeights.computeIfAbsent(
                new HorizontalPosition(position.x(), position.z()),
                ignored -> new ArrayList<>()
            ).add(position.y());
        }

        for (int i = 0; i < path.size() - 1; i++) {
            Position current = path.get(i);
            Position next = path.get(i + 1);
            int horizontalDistance = Math.abs(next.x() - current.x()) + Math.abs(next.z() - current.z());
            if (horizontalDistance != 1 || Math.abs(next.y() - current.y()) > 1) {
                throw fail("Pair " + pairIndex + " contains an unwalkable connector edge.");
            }
        }

        for (List<Integer> heights : shaftHeights.values()) {
            heights.sort(Comparator.naturalOrder());
            for (int i = 0; i < heights.size() - 1; i++) {
                if (heights.get(i + 1) - heights.get(i) < 3) {
                    throw fail("Pair " + pairIndex + " has insufficient repeated-shaft headroom.");
                }
            }
        }
        for (Position position : path) {
            if (positions.contains(position.addY(1)) || positions.contains(position.addY(2))) {
                throw fail("Pair " + pairIndex + " contains a block inside connector headroom.");
            }
        }
        return maximumExtension;
    }

    private static void addGeneratedBlock(
        GeneratedBlock block,
        List<GeneratedBlock> generatedBlocks,
        Set<Position> positions
    ) {
        if (!positions.add(block.position())) {
            throw fail("Generated blocks collided at " + block.position() + ".");
        }
        generatedBlocks.add(block);
    }

    private static void validateGenerated(
        Result result,
        List<SourceBlock> sourceBlocks,
        int originalPaletteSize,
        Set<Position> generatedPositions
    ) {
        if (result.pairRoutes().size() != result.pairCount()) {
            throw fail("Generated connector-pair count is invalid.");
        }
        if (result.sizeX() != result.mapWidth()
            || result.sizeZ() != result.visibleRows() + result.maximumExtension() + 1) {
            throw fail("Generated structure dimensions are invalid.");
        }
        int minimumY = result.generatedBlocks().stream()
            .mapToInt(block -> block.position().y())
            .min()
            .orElseThrow();
        int maximumY = result.generatedBlocks().stream()
            .mapToInt(block -> block.position().y())
            .max()
            .orElseThrow();
        if (minimumY != 0 || result.sizeY() != maximumY + 1) {
            throw fail("Generated Y bounds are invalid.");
        }

        Map<Position, GeneratedBlock> byPosition = new HashMap<>();
        int[][] generatedTopY = new int[result.mapWidth()][result.sourceDepth()];
        int[][] generatedTopState = new int[result.mapWidth()][result.sourceDepth()];
        boolean[][] generatedTopPresent = new boolean[result.mapWidth()][result.sourceDepth()];
        for (GeneratedBlock block : result.generatedBlocks()) {
            byPosition.put(block.position(), block);
            if (block.lighting()) continue;
            Position position = block.position();
            if (position.x() < 0 || position.x() >= result.mapWidth()
                || position.z() < 0 || position.z() >= result.sourceDepth()) {
                continue;
            }
            if (!generatedTopPresent[position.x()][position.z()]
                || position.y() > generatedTopY[position.x()][position.z()]) {
                generatedTopPresent[position.x()][position.z()] = true;
                generatedTopY[position.x()][position.z()] = position.y();
                generatedTopState[position.x()][position.z()] = block.state();
            }
        }

        for (int x = 0; x < result.mapWidth(); x++) {
            for (int z = 0; z < result.sourceDepth(); z++) {
                if (!generatedTopPresent[x][z]) throw fail("Generated map is missing a surface shaft.");
                int expectedY = result.targetSurfaceY(x, z) + result.globalYShift();
                if (generatedTopY[x][z] != expectedY) throw fail("Generated surface height changed.");
                int expectedState = z == START_Z ? result.cobblestoneState() : result.sourceTopState(x, z);
                if (generatedTopState[x][z] != expectedState) throw fail("Generated visible surface state changed.");
                if (z > 0) {
                    int generatedStep = generatedTopY[x][z] - generatedTopY[x][z - 1];
                    int sourceStep = Integer.signum(result.sourceTopY(x, z) - result.sourceTopY(x, z - 1));
                    if (generatedStep != sourceStep || Math.abs(generatedStep) > 1) {
                        throw fail("Generated shade direction changed.");
                    }
                }
            }
        }

        for (PairRoute route : result.pairRoutes()) {
            if (route.absolutePath().size() - 1 != route.minimumEdges()) {
                throw fail("Generated route is not edge-optimal.");
            }
            for (int i = 1; i < route.absolutePath().size() - 1; i++) {
                GeneratedBlock connector = byPosition.get(route.absolutePath().get(i));
                if (connector == null || !connector.connector() || connector.state() != result.cobblestoneState()) {
                    throw fail("Generated connector block is missing or is not cobblestone.");
                }
            }
            for (Position position : route.absolutePath()) {
                if (generatedPositions.contains(position.addY(1))
                    || generatedPositions.contains(position.addY(2))) {
                    throw fail("Generated output contains a block inside route headroom at " + position + ".");
                }
            }
        }

        if (result.lightingEnabled()) {
            if (result.lightingBlocks().isEmpty()
                || result.minimumGuaranteedSurfaceLight()
                    < EndRodLightingPlan.MINIMUM_SURFACE_LIGHT) {
                throw fail("Generated end-rod lighting is incomplete.");
            }
            for (GeneratedBlock light : result.lightingBlocks()) {
                Position position = light.position();
                if (!light.lighting()
                    || light.connector()
                    || light.sourceIndex() != -1
                    || light.state() != result.endRodState()
                    || position.z() < 1
                    || position.z() > result.visibleRows()
                    || position.y()
                        != result.targetSurfaceY(
                            position.x(),
                            position.z()
                        )
                            + result.globalYShift()
                            + EndRodLightingPlan.HEIGHT_ABOVE_SURFACE) {
                    throw fail("Generated end-rod target is invalid.");
                }
            }
        } else if (!result.lightingBlocks().isEmpty()) {
            throw fail("Lighting-disabled output contains end rods.");
        }

        if (result.generatedBlocks().size()
            != sourceBlocks.size()
                + result.connectorBlocks().size()
                + result.lightingBlocks().size()) {
            throw fail("Generated block count is invalid.");
        }
        int paletteLimit = originalPaletteSize;
        if (result.cobblestoneState() == paletteLimit) paletteLimit++;
        if (result.endRodState() == paletteLimit) paletteLimit++;
        for (GeneratedBlock block : result.generatedBlocks()) {
            if (block.state() < 0 || block.state() >= paletteLimit) {
                throw fail("Generated block references an invalid palette state.");
            }
        }
    }

    public static int smallestOddAtLeast(int value) {
        if (value <= 1) return 1;
        return value % 2 == 1 ? value : value + 1;
    }

    private static IllegalArgumentException fail(String message) {
        return new IllegalArgumentException("Compact circular NBT validation failed: " + message);
    }
}
