package com.julflips.nerv_printer.utils;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

public final class ToolUtils {

    public static ItemStack getBestTool(Set<ItemStack> tools, BlockState targetBlock) {
        double bestScore = 1.0;
        ItemStack bestStack = null;
        for (ItemStack tool : tools) {
            double score = getEffectiveMiningScore(tool, targetBlock);
            if (score > bestScore
                || (Double.compare(score, bestScore) == 0
                    && isDeterministicallyBetter(
                        tool,
                        bestStack,
                        targetBlock
                    ))) {
                bestScore = score;
                bestStack = tool;
            }
        }

        // Default to the strongest registered pickaxe if no registered tool
        // improves on hand mining for this block.
        if (bestStack == null) {
            for (ItemStack tool : tools) {
                if (tool.is(ItemTags.PICKAXES)
                    && isDeterministicallyBetter(
                        tool,
                        bestStack,
                        targetBlock
                    )) {
                    bestStack = tool;
                }
            }
        }
        return bestStack;
    }

    public static double getEffectiveMiningScore(
        ItemStack tool,
        BlockState targetBlock
    ) {
        return getEffectiveMiningScore(
            tool,
            targetBlock,
            getEfficiencyLevel(tool)
        );
    }

    public static double getEffectiveMiningScore(
        ItemStack tool,
        BlockState targetBlock,
        int efficiency
    ) {
        if (efficiency < 0) {
            throw new IllegalArgumentException(
                "Efficiency level cannot be negative."
            );
        }
        float baseSpeed = tool.getDestroySpeed(targetBlock);
        double effectiveSpeed = baseSpeed;
        if (baseSpeed > 1.0F && efficiency > 0) {
            effectiveSpeed += efficiency * efficiency + 1.0;
        }

        // Vanilla divides unsuitable required-tool progress by 100 instead of
        // 30. Encoding that ratio keeps cross-item selection aligned with
        // actual breaking progress without depending on the currently held
        // stack.
        if (targetBlock.requiresCorrectToolForDrops()
            && !tool.isCorrectToolForDrops(targetBlock)) {
            effectiveSpeed *= 0.3;
        }
        return effectiveSpeed;
    }

    public static int getEfficiencyLevel(ItemStack stack) {
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack)
            .entrySet()) {
            if (entry.getKey().unwrapKey().isPresent()
                && entry.getKey().unwrapKey().get().identifier().equals(
                    Enchantments.EFFICIENCY.identifier()
                )) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    public static boolean isTool(ItemStack itemStack) {
        if (itemStack.is(ItemTags.PICKAXES)
            || itemStack.is(ItemTags.AXES)
            || itemStack.is(ItemTags.SHOVELS)
            || itemStack.is(ItemTags.HOES)
            || itemStack.getItem() instanceof ShearsItem) {
            return true;
        }
        return false;
    }

    private static boolean isDeterministicallyBetter(
        ItemStack candidate,
        ItemStack current,
        BlockState targetBlock
    ) {
        if (current == null) return true;

        int scoreComparison = Double.compare(
            getEffectiveMiningScore(candidate, targetBlock),
            getEffectiveMiningScore(current, targetBlock)
        );
        if (scoreComparison != 0) return scoreComparison > 0;

        int efficiencyComparison = Integer.compare(
            getEfficiencyLevel(candidate),
            getEfficiencyLevel(current)
        );
        if (efficiencyComparison != 0) return efficiencyComparison > 0;

        int speedComparison = Float.compare(
            candidate.getDestroySpeed(targetBlock),
            current.getDestroySpeed(targetBlock)
        );
        if (speedComparison != 0) return speedComparison > 0;

        return BuiltInRegistries.ITEM.getKey(candidate.getItem()).toString()
            .compareTo(
                BuiltInRegistries.ITEM.getKey(current.getItem()).toString()
            ) < 0;
    }
}
