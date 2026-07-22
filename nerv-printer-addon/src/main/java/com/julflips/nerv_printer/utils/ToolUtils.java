package com.julflips.nerv_printer.utils;

import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;

import java.util.Set;

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
                if (tool.isIn(ItemTags.PICKAXES)
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
        float baseSpeed = tool.getMiningSpeedMultiplier(targetBlock);
        double effectiveSpeed = baseSpeed;
        if (baseSpeed > 1.0F && efficiency > 0) {
            effectiveSpeed += efficiency * efficiency + 1.0;
        }

        // Vanilla divides unsuitable required-tool progress by 100 instead of
        // 30. Encoding that ratio keeps cross-item selection aligned with
        // actual breaking progress without depending on the currently held
        // stack.
        if (targetBlock.isToolRequired()
            && !tool.isSuitableFor(targetBlock)) {
            effectiveSpeed *= 0.3;
        }
        return effectiveSpeed;
    }

    public static int getEfficiencyLevel(ItemStack stack) {
        for (var entry : EnchantmentHelper.getEnchantments(stack)
            .getEnchantmentEntries()) {
            if (entry.getKey().getKey().isPresent()
                && entry.getKey().getKey().get().getValue().equals(
                    Enchantments.EFFICIENCY.getValue()
                )) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    public static boolean isTool(ItemStack itemStack) {
        if (itemStack.isIn(ItemTags.PICKAXES)
            || itemStack.isIn(ItemTags.AXES)
            || itemStack.isIn(ItemTags.SHOVELS)
            || itemStack.isIn(ItemTags.HOES)
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
            candidate.getMiningSpeedMultiplier(targetBlock),
            current.getMiningSpeedMultiplier(targetBlock)
        );
        if (speedComparison != 0) return speedComparison > 0;

        return Registries.ITEM.getId(candidate.getItem()).toString()
            .compareTo(
                Registries.ITEM.getId(current.getItem()).toString()
            ) < 0;
    }
}
