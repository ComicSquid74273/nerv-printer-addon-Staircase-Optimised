package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import com.julflips.nerv_printer.utils.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class ConfigDeserializer {

    private static BlockPos jsonToBlockPos(JsonObject obj) {
        return new BlockPos(
            obj.get("x").getAsInt(),
            obj.get("y").getAsInt(),
            obj.get("z").getAsInt()
        );
    }

    private static Vec3 jsonToVec3d(JsonObject obj) {
        return new Vec3(
            obj.get("x").getAsDouble(),
            obj.get("y").getAsDouble(),
            obj.get("z").getAsDouble()
        );
    }

    private static Tuple<BlockPos, Vec3> jsonToBlockPosVecPair(JsonObject obj) {
        BlockPos pos = jsonToBlockPos(obj.getAsJsonObject("blockPos"));
        Vec3 openPos = jsonToVec3d(obj.getAsJsonObject("openPos"));
        return new Tuple<>(pos, openPos);
    }

    /**
     * Data container for config values
     */
    public static class ConfigData {
        public String type;
        public Tuple<BlockPos, Vec3> reset;
        public Tuple<BlockPos, Vec3> cartographyTable;
        public Tuple<BlockPos, Vec3> finishedMapChest;
        public Tuple<BlockPos, Vec3> usedToolChest;
        public Tuple<BlockPos, Vec3> bed;
        public Tuple<BlockPos, Vec3> anvil;
        public Tuple<BlockPos, Vec3> enderChest;
        public Tuple<BlockPos, Vec3> craftingTable;
        public ArrayList<Tuple<BlockPos, Vec3>> mapMaterialChests;
        public Tuple<Vec3, Tuple<Float, Float>> dumpStation;
        public BlockPos mapCorner;
        public HashMap<Item, ArrayList<Tuple<BlockPos, Vec3>>> materialDict;
        public HashMap<Item, Tuple<BlockPos, Vec3>> usedToolChests;
        public Set<ItemStack> toolSet;
        public HashMap<Item, Integer> toolMinimumEfficiency;
    }

    private static JsonObject getObj(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject()
            ? root.getAsJsonObject(key)
            : null;
    }

    public static ConfigData readFromJson(Path file) throws IOException {
        Gson gson = new Gson();

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            ConfigData data = new ConfigData();

            data.type = root.get("type").getAsString();

            JsonObject obj;
            obj = getObj(root, "reset");
            data.reset = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "cartographyTable");
            data.cartographyTable = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "finishedMapChest");
            data.finishedMapChest = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "usedToolChest");
            data.usedToolChest = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "bed");
            data.bed = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "anvil");
            data.anvil = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "enderChest");
            data.enderChest = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "craftingTable");
            data.craftingTable = obj != null ? jsonToBlockPosVecPair(obj) : null;

            data.mapMaterialChests = new ArrayList<>();
            if (root.has("mapMaterialChests")) {
                for (JsonElement e : root.getAsJsonArray("mapMaterialChests")) {
                    data.mapMaterialChests.add(
                        jsonToBlockPosVecPair(e.getAsJsonObject())
                    );
                }
            }

            if (root.has("dumpStation")) {
                JsonObject dump = root.getAsJsonObject("dumpStation");

                Vec3 pos = jsonToVec3d(dump.getAsJsonObject("pos"));
                float yaw = dump.get("yaw").getAsFloat();
                float pitch = dump.get("pitch").getAsFloat();

                data.dumpStation = new Tuple<>(pos, new Tuple<>(yaw, pitch));
            } else {
                data.dumpStation = null;
            }

            data.mapCorner = jsonToBlockPos(root.getAsJsonObject("mapCorner"));

            data.materialDict = new HashMap<>();
            if (root.has("materialDict")) {
                JsonObject materialDictObj = root.getAsJsonObject("materialDict");
                for (String key : materialDictObj.keySet()) {
                    Identifier id = Identifier.parse(key);
                    Item item = BuiltInRegistries.ITEM.getValue(id);
                    ArrayList<Tuple<BlockPos, Vec3>> list = new ArrayList<>();
                    for (JsonElement e : materialDictObj.getAsJsonArray(key)) {
                        list.add(jsonToBlockPosVecPair(e.getAsJsonObject()));
                    }
                    data.materialDict.put(item, list);
                }
            }

            data.toolSet = new HashSet<>();
            data.toolMinimumEfficiency = new HashMap<>();
            if (root.has("toolSet")) {
                for (JsonElement e : root.getAsJsonArray("toolSet")) {
                    JsonObject o = e.getAsJsonObject();
                    Identifier id = Identifier.parse(o.get("item").getAsString());
                    Item item = BuiltInRegistries.ITEM.getValue(id);
                    data.toolSet.add(
                        new ItemStack(item)
                    );
                    data.toolMinimumEfficiency.merge(
                        item,
                        o.has("efficiency")
                            ? Math.max(0, o.get("efficiency").getAsInt())
                            : 0,
                        Math::max
                    );
                }
            }

            data.usedToolChests = new HashMap<>();
            if (root.has("usedToolChests")) {
                JsonObject usedToolChestsObj = root.getAsJsonObject("usedToolChests");
                for (String key : usedToolChestsObj.keySet()) {
                    Identifier id = Identifier.parse(key);
                    Item item = BuiltInRegistries.ITEM.getValue(id);
                    data.usedToolChests.put(
                        item,
                        jsonToBlockPosVecPair(usedToolChestsObj.getAsJsonObject(key))
                    );
                }
            }

            return data;
        }
    }
}
