package com.scriptcraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Картинка по id или пути из скрипта: из resources сервера или из кэша клиента. */
@OnlyIn(Dist.CLIENT)
public final class TexturePreview {
    public record Image(ResourceLocation id, int width, int height) {
    }

    private static final Map<String, Image> FOUND = new HashMap<>();
    private static final Map<String, Boolean> MISSING = new HashMap<>();
    private static int seq;

    private TexturePreview() {
    }

    public static Image lookup(String token) {
        if (token == null || FOUND.containsKey(token)) {
            return FOUND.get(token);
        }
        if (MISSING.containsKey(token)) {
            return null;
        }
        Path file = resolve(token);
        if (file == null || !Files.isRegularFile(file)) {
            MISSING.put(token, Boolean.TRUE);
            return null;
        }
        try {
            NativeImage image;
            try (var in = Files.newInputStream(file)) {
                image = NativeImage.read(in);
            }
            ResourceLocation id = new ResourceLocation(ScriptCraft.MODID, "preview/" + (seq++));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            Image info = new Image(id, image.getWidth(), image.getHeight());
            FOUND.put(token, info);
            return info;
        } catch (Exception e) {
            MISSING.put(token, Boolean.TRUE);
            return null;
        }
    }

    private static Path resolve(String token) {
        String raw = token.replace('\\', '/').replace("\"", "");
        if (raw.startsWith("assets/")) {
            raw = raw.substring("assets/".length());
        }
        String ns;
        String path;
        int colon = raw.indexOf(':');
        if (colon > 0 && colon < 32) {
            ns = raw.substring(0, colon);
            path = raw.substring(colon + 1);
        } else if (raw.contains("/")) {
            int slash = raw.indexOf('/');
            ns = raw.substring(0, slash);
            path = raw.substring(slash + 1);
        } else {
            return null;
        }
        if (!path.endsWith(".png")) {
            return null;
        }
        Path local = ModPaths.resources().resolve("assets").resolve(ns).resolve(path);
        if (Files.isRegularFile(local)) {
            return local;
        }
        Path cached = ModPaths.cache().resolve("assets").resolve(ns).resolve(path);
        return Files.isRegularFile(cached) ? cached : local;
    }
}
