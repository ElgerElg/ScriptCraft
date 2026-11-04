package com.scriptcraft.resources;

import com.scriptcraft.registry.BindingStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Серверный пак scriptcraft/resources/data: при чтении JSON (теги, лут-таблицы, рецепты, достижения)
 * id из скриптов в кавычках заменяются на технические id слотов: "example:ruby_block" -> "scriptcraft:dyn_block_0".
 * Поэтому data-файлы пишутся с обычными id скриптов и работают как раньше (mineable/pickaxe, дроп, крафт).
 * Ссылки на теги ("#example:...") и пути лут-таблиц (example:blocks/...) не трогаются — их нет в таблице.
 * Чужие датапаки (папка datapacks мира) НЕ перезаписываются — там нужны технические id.
 */
public class RewritingDataPack extends PathPackResources {
    private static final Pattern QUOTED_ID = Pattern.compile("\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"");

    public RewritingDataPack(String name, Path root) {
        super(name, root, true);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        IoSupplier<InputStream> supplier = super.getResource(type, location);
        return supplier == null ? null : wrap(location, supplier);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        super.listResources(type, namespace, path, (location, supplier) -> output.accept(location, wrap(location, supplier)));
    }

    private static IoSupplier<InputStream> wrap(ResourceLocation location, IoSupplier<InputStream> supplier) {
        if (!location.getPath().endsWith(".json")) {
            return supplier;
        }
        return () -> {
            Map<String, String> map = BindingStore.dataRewrite();
            if (map.isEmpty()) {
                return supplier.get();
            }
            String text;
            try (InputStream in = supplier.get()) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return new ByteArrayInputStream(rewrite(text, map).getBytes(StandardCharsets.UTF_8));
        };
    }

    static String rewrite(String text, Map<String, String> map) {
        Matcher m = QUOTED_ID.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (m.find()) {
            String replacement = map.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(replacement == null ? m.group() : "\"" + replacement + "\""));
        }
        m.appendTail(out);
        return out.toString();
    }
}
