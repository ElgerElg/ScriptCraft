package com.scriptcraft.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.client.ClientToasts;
import com.scriptcraft.client.StudioSession;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Защищает клиент от падения при загрузке несовместимой/битой анимации PlayerAnimator
 * (например, Emotecraft version 4 при библиотеке v3): пробует понизить версию до 3,
 * а если не удаётся — пропускает файл, пишет в журнал Студии и показывает уведомление.
 */
@Mixin(value = PlayerAnimationRegistry.class, remap = false)
public abstract class PlayerAnimationRegistryMixin {

    @Redirect(
            method = "resourceLoaderCallback",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/kosmx/playerAnim/core/data/gson/AnimationSerializing;deserializeAnimation(Ljava/io/InputStream;)Ljava/util/List;"
            ),
            require = 0
    )
    private static List<KeyframeAnimation> scriptcraft$safeDeserializeAnimation(InputStream stream) {
        byte[] raw;
        try {
            raw = stream.readAllBytes();
        } catch (Throwable t) {
            return List.of();
        }
        try {
            return AnimationSerializing.deserializeAnimation(new ByteArrayInputStream(raw));
        } catch (Throwable firstErr) {
            // Пробуем автоматически понизить version > 3 -> 3 для совместимости с PlayerAnimator 1.0.2
            try {
                String text = new String(raw, StandardCharsets.UTF_8);
                JsonElement el = JsonParser.parseString(text);
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    if (obj.has("version") && obj.get("version").isJsonPrimitive()
                            && obj.get("version").getAsNumber().intValue() > 3) {
                        obj.addProperty("version", 3);
                        byte[] patched = obj.toString().getBytes(StandardCharsets.UTF_8);
                        List<KeyframeAnimation> retry = AnimationSerializing.deserializeAnimation(new ByteArrayInputStream(patched));
                        ScriptCraft.LOGGER.warn("[ScriptCraft] Анимация PlayerAnimator была в формате v{}, автоматически адаптирована до v3",
                                firstErr.getMessage());
                        return retry;
                    }
                }
            } catch (Throwable ignored) {
            }

            String reason = firstErr.getMessage() == null ? firstErr.toString() : firstErr.getMessage();
            ScriptCraft.LOGGER.warn("[ScriptCraft] Пропущена некорректная анимация PlayerAnimator (клиент не крашнут): {}", reason);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    StudioSession.remember("[PlayerAnimator] " + reason);
                    ClientToasts.push("§e[PlayerAnimator] §f" + reason);
                });
            }
            return List.of();
        }
    }
}
