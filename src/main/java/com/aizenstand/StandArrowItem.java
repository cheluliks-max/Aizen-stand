package com.aizenstand;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Right-click it to be pierced by the arrow and awaken the stand. */
public class StandArrowItem extends Item {
    public StandArrowItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }
        if (StandManager.hasStand(player)) {
            player.sendMessage(Text.translatable("message.aizen_stand.already_awakened"), true);
            return TypedActionResult.fail(stack);
        }
        StandManager.awaken(player);
        if (!player.isCreative()) {
            stack.decrement(1);
        }
        return TypedActionResult.success(stack);
    }
}
