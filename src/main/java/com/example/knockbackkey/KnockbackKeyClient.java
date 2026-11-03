package com.example.knockbackkey;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class KnockbackKeyClient implements ClientModInitializer {
    private static KeyMapping hitKey;

    private static boolean relative = true;
    private static double angle = 90.0;
    private static double minCharge = 0.9;
    private static boolean restoreView = true;

    @Override
    public void onInitializeClient() {
        loadConfig();

        hitKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.knockbackkey.hit",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_0,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(KnockbackKeyClient::onTick);
    }

    private static void onTick(Minecraft mc) {
        while (hitKey.consumeClick()) {
            LocalPlayer player = mc.player;
            if (player == null || mc.gameMode == null || mc.screen != null) {
                continue;
            }
            HitResult hit = mc.hitResult;
            if (!(hit instanceof EntityHitResult entityHit)) {
                continue;
            }
            if (minCharge > 0 && player.getAttackStrengthScale(0.5F) < minCharge) {
                continue;
            }
            doDisplacementHit(mc, player, entityHit.getEntity());
        }
    }

    private static void doDisplacementHit(Minecraft mc, LocalPlayer player, Entity target) {
        float originalYaw = player.getYRot();
        float originalPitch = player.getXRot();
        float snappedYaw = relative ? (float) (originalYaw + angle) : (float) angle;

        player.setYRot(snappedYaw);
        sendRotation(player, snappedYaw, originalPitch);

        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);

        if (restoreView) {
            player.setYRot(originalYaw);
            sendRotation(player, originalYaw, originalPitch);
        }
    }

    private static void sendRotation(LocalPlayer player, float yaw, float pitch) {
        player.connection.send(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, player.onGround(), player.horizontalCollision));
    }

    private static void loadConfig() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("knockbackkey.properties");
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException ignored) {
            }
        }
        relative = !"absolute".equalsIgnoreCase(props.getProperty("mode", "relative").trim());
        angle = parse(props.getProperty("angle"), angle);
        minCharge = Math.max(0, Math.min(1, parse(props.getProperty("minCharge"), minCharge)));
        restoreView = !"false".equalsIgnoreCase(props.getProperty("restoreView", "true").trim());

        Properties out = new Properties();
        out.setProperty("mode", relative ? "relative" : "absolute");
        out.setProperty("angle", Double.toString(angle));
        out.setProperty("minCharge", Double.toString(minCharge));
        out.setProperty("restoreView", Boolean.toString(restoreView));
        try (OutputStream os = Files.newOutputStream(path)) {
            out.store(os, "Knockback Key. mode: relative or absolute. angle in degrees. minCharge 0-1. restoreView true/false.");
        } catch (IOException ignored) {
        }
    }

    private static double parse(String s, double fallback) {
        if (s == null) return fallback;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
