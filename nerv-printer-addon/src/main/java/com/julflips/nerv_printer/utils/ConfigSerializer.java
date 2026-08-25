package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import com.julflips.nerv_printer.utils.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class ConfigSerializer {

    private static JsonObject blockPosToJson(BlockPos pos) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", pos.getX());
        obj.addProperty("y", pos.getY());
        obj.addProperty("z", pos.getZ());
        return obj;
    }

    private static JsonObject vec3dToJson(Vec3 vec) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", vec.x);
        obj.addProperty("y", vec.y);
        obj.addProperty("z", vec.z);
        return obj;
    }

    private static JsonObject blockPosVecPairToJson(Tuple<BlockPos, Vec3> pair) {
        JsonObject obj = new JsonObject();
        obj.add("blockPos", blockPosToJson(pair.getA()));
        obj.add("openPos", vec3dToJson(pair.getB()));
        return obj;
    }

    public static void writeToJson(
        Path file,
        String type,
        Tuple<BlockPos, Vec3> reset,
        Tuple<BlockPos, Vec3> cartographyTable,
        Tuple<BlockPos, Vec3> finishedMapChest,
        ArrayList<Tuple<BlockPos, Vec3>> mapMaterialChests,
        Tuple<Vec3, Tuple<Float, Float>> dumpStation,
        BlockPos mapCorner,
        HashMap<Item, ArrayList<Tuple<BlockPos, Vec3>>> materialDict
    ) throws IOException {
        writeToJson(file, type, reset, cartographyTable, finishedMapChest, null, null,
            mapMaterialChests, dumpStation, mapCorner, materialDict, null,
            null, null, null, null, null);
    }

    public static void writeToJson(
        Path file,
        String type,
        Tuple<BlockPos, Vec3> cartographyTable,
        Tuple<BlockPos, Vec3> finishedMapChest,
        Tuple<BlockPos, Vec3> usedToolChest,
        Tuple<BlockPos, Vec3> bed,
        ArrayList<Tuple<BlockPos, Vec3>> mapMaterialChests,
        Tuple<Vec3, Tuple<Float, Float>> dumpStation,
        BlockPos mapCorner,
        HashMap<Item, ArrayList<Tuple<BlockPos, Vec3>>> materialDict,
        Set<ItemStack> toolSet,
        Tuple<BlockPos, Vec3> anvil,
        Tuple<BlockPos, Vec3> enderChest,
        Tuple<BlockPos, Vec3> craftingTable,
        HashMap<Item, Tuple<BlockPos, Vec3>> usedToolChests
    ) throws IOException {
        writeToJson(file, type, null, cartographyTable, finishedMapChest, usedToolChest, bed,
            mapMaterialChests, dumpStation, mapCorner, materialDict, toolSet,
            anvil, enderChest, craftingTable, usedToolChests, null);
    }

    public static void writeToJson(
        Path file,
        String type,
        Tuple<BlockPos, Vec3> cartographyTable,
        Tuple<BlockPos, Vec3> finishedMapChest,
        Tuple<BlockPos, Vec3> usedToolChest,
        Tuple<BlockPos, Vec3> bed,
        ArrayList<Tuple<BlockPos, Vec3>> mapMaterialChests,
        Tuple<Vec3, Tuple<Float, Float>> dumpStation,
        BlockPos mapCorner,
        HashMap<Item, ArrayList<Tuple<BlockPos, Vec3>>> materialDict,
        Set<ItemStack> toolSet,
        Tuple<BlockPos, Vec3> anvil,
        Tuple<BlockPos, Vec3> enderChest,
        Tuple<BlockPos, Vec3> craftingTable,
        HashMap<Item, Tuple<BlockPos, Vec3>> usedToolChests,
        Map<Item, Integer> toolMinimumEfficiency
    ) throws IOException {
        writeToJson(file, type, null, cartographyTable, finishedMapChest, usedToolChest, bed,
            mapMaterialChests, dumpStation, mapCorner, materialDict, toolSet,
            anvil, enderChest, craftingTable, usedToolChests,
            toolMinimumEfficiency);
    }

    public static void writeToJson(
        Path file,
        String type,
        Tuple<BlockPos, Vec3> reset,
        Tuple<BlockPos, Vec3> cartographyTable,
        Tuple<BlockPos, Vec3> finishedMapChest,
        Tuple<BlockPos, Vec3> usedToolChest,
        Tuple<BlockPos, Vec3> bed,
        ArrayList<Tuple<BlockPos, Vec3>> mapMaterialChests,
        Tuple<Vec3, Tuple<Float, Float>> dumpStation,
        BlockPos mapCorner,
        HashMap<Item, ArrayList<Tuple<BlockPos, Vec3>>> materialDict,
        Set<ItemStack> toolSet,
        Tuple<BlockPos, Vec3> anvil,
        Tuple<BlockPos, Vec3> enderChest,
        Tuple<BlockPos, Vec3> craftingTable,
        HashMap<Item, Tuple<BlockPos, Vec3>> usedToolChests,
        Map<Item, Integer> toolMinimumEfficiency
    ) throws IOException {
        JsonObject root = new JsonObject();

        root.addProperty("type", type);
        if (reset != null) root.add("reset", blockPosVecPairToJson(reset));
        if (cartographyTable != null) root.add("cartographyTable", blockPosVecPairToJson(cartographyTable));
        if (finishedMapChest != null) root.add("finishedMapChest", blockPosVecPairToJson(finishedMapChest));
        if (usedToolChest != null) root.add("usedToolChest", blockPosVecPairToJson(usedToolChest));
        if (bed != null) root.add("bed", blockPosVecPairToJson(bed));
        if (anvil != null) root.add("anvil", blockPosVecPairToJson(anvil));
        if (enderChest != null) root.add("enderChest", blockPosVecPairToJson(enderChest));
        if (craftingTable != null) root.add("craftingTable", blockPosVecPairToJson(craftingTable));

        if (mapMaterialChests != null) {
            JsonArray materialChestsArray = new JsonArray();
            for (Tuple<BlockPos, Vec3> pair : mapMaterialChests) {
                materialChestsArray.add(blockPosVecPairToJson(pair));
            }
            root.add("mapMaterialChests", materialChestsArray);
        }

        if (dumpStation != null) {
            JsonObject dumpStationObj = new JsonObject();
            dumpStationObj.add("pos", vec3dToJson(dumpStation.getA()));
            dumpStationObj.addProperty("yaw", dumpStation.getB().getA());
            dumpStationObj.addProperty("pitch", dumpStation.getB().getB());
            root.add("dumpStation", dumpStationObj);
        }

        if (mapCorner != null) root.add("mapCorner", blockPosToJson(mapCorner));

        if (materialDict != null) {
            JsonObject materialDictObj = new JsonObject();
            for (Map.Entry<Item, ArrayList<Tuple<BlockPos, Vec3>>> entry : materialDict.entrySet()) {
                String blockId = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();

                JsonArray chestArray = new JsonArray();
                for (Tuple<BlockPos, Vec3> pair : entry.getValue()) {
                    chestArray.add(blockPosVecPairToJson(pair));
                }

                materialDictObj.add(blockId, chestArray);
            }
            root.add("materialDict", materialDictObj);
        }

        if (toolSet != null) {
            JsonArray toolSetArray = new JsonArray();
            for (ItemStack stack : toolSet) {
                JsonObject stackObj = new JsonObject();
                stackObj.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                stackObj.addProperty(
                    "efficiency",
                    Math.max(
                        ToolUtils.getEfficiencyLevel(stack),
                        toolMinimumEfficiency == null
                            ? 0
                            : toolMinimumEfficiency.getOrDefault(
                                stack.getItem(),
                                0
                            )
                    )
                );
                toolSetArray.add(stackObj);
            }
            root.add("toolSet", toolSetArray);
        }

        if (usedToolChests != null) {
            JsonObject usedToolChestsObj = new JsonObject();
            for (Map.Entry<Item, Tuple<BlockPos, Vec3>> entry : usedToolChests.entrySet()) {
                String itemId = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
                usedToolChestsObj.add(itemId, blockPosVecPairToJson(entry.getValue()));
            }
            root.add("usedToolChests", usedToolChestsObj);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = Files.newBufferedWriter(file)) {
            gson.toJson(root, writer);
        }
    }
}
