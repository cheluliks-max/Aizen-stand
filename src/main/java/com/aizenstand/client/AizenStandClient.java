package com.aizenstand.client;

import com.aizenstand.AizenStandMod;
import com.aizenstand.StandManager;
import com.aizenstand.net.KeyActionPayload;
import com.aizenstand.net.StateSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AizenStandClient implements ClientModInitializer {
    public static KeyBinding KEY_SUMMON;     // G
    public static KeyBinding KEY_HADO_90;    // R
    public static KeyBinding KEY_HADO_99;    // V

    @Override
    public void onInitializeClient() {
        KEY_SUMMON = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aizen_stand.summon", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.aizen_stand"));
        KEY_HADO_90 = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aizen_stand.hado90", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.aizen_stand"));
        KEY_HADO_99 = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aizen_stand.hado99", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.aizen_stand"));

        EntityModelLayerRegistry.registerModelLayer(AizenStandModel.LAYER, AizenStandModel::getTexturedModelData);
        EntityRendererRegistry.register(AizenStandMod.AIZEN_STAND, AizenStandRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(StateSyncPayload.ID,
                (payload, context) -> ClientState.update(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                return;
            }
            while (KEY_SUMMON.wasPressed()) {
                ClientPlayNetworking.send(new KeyActionPayload(KeyActionPayload.SUMMON));
            }
            while (KEY_HADO_90.wasPressed()) {
                ClientPlayNetworking.send(new KeyActionPayload(KeyActionPayload.HADO_90));
            }
            while (KEY_HADO_99.wasPressed()) {
                ClientPlayNetworking.send(new KeyActionPayload(KeyActionPayload.HADO_99));
            }
        });

        HudRenderCallback.EVENT.register(AizenStandClient::renderHud);
    }

    private static void renderHud(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || !ClientState.hasStand) {
            return;
        }
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        int barW = 100;
        int x = sw - barW - 10;
        int y = sh - 20;

        // reiatsu bar
        ctx.fill(x - 1, y - 1, x + barW + 1, y + 9, 0xCC000000);
        ctx.fill(x, y, x + barW, y + 8, 0xFF2A1646);
        int filled = Math.max(0, Math.min(barW, ClientState.reiatsu * barW / StandManager.MAX_REIATSU));
        ctx.fill(x, y, x + filled, y + 8, 0xFF9A5CFF);
        ctx.fill(x, y, x + filled, y + 3, 0xFFD2A8FF);
        ctx.drawText(mc.textRenderer, Text.translatable("hud.aizen_stand.reiatsu", ClientState.reiatsu), x, y - 11, 0xFFE0C8FF, true);

        // key hints
        Text state = Text.translatable(ClientState.summoned ? "hud.aizen_stand.on" : "hud.aizen_stand.off");
        ctx.drawText(mc.textRenderer, Text.translatable("hud.aizen_stand.summon", KEY_SUMMON.getBoundKeyLocalizedText(), state),
                x, y - 22, 0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, hadoLine("hud.aizen_stand.hado90", KEY_HADO_90, ClientState.cooldown90), x, y - 33,
                ClientState.cooldown90 > 0 ? 0xFF999999 : 0xFFFFE08A, true);
        ctx.drawText(mc.textRenderer, hadoLine("hud.aizen_stand.hado99", KEY_HADO_99, ClientState.cooldown99), x, y - 44,
                ClientState.cooldown99 > 0 ? 0xFF999999 : 0xFFFFE08A, true);
    }

    private static Text hadoLine(String key, KeyBinding binding, int cooldownTicks) {
        Text status = cooldownTicks > 0
                ? Text.literal((cooldownTicks + 19) / 20 + "s")
                : Text.translatable("hud.aizen_stand.ready");
        return Text.translatable(key, binding.getBoundKeyLocalizedText(), status);
    }
}
