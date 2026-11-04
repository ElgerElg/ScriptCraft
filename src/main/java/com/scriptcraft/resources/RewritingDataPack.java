package com.scriptcraft.resources;

import com.scriptcraft.registry.BindingStore;
import com.scriptcraft.registry.IdRewriter;
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

/**
 * Серверный пак scriptcraft/resources/data: при чтении JSON (теги, лут-таблицы, рецепты, достижения)
 * и функций .mcfunction (целые id-токены в командах) id из скриптов заменяются на технические id слотов:
 * "example:ruby_block" -> "scriptcraft:dyn_block_0".
 * Поэтому data-файлы пишутся с обычными id скриптов и работают как раньше (mineable/pickaxe, дроп, крафт).
 * Ссылки на теги ("#example:...") и пути лут-таблиц (example:blocks/...) не трогаются — их нет в таблице.
 * Чужие датапаки (папка datapacks мира) НЕ перезаписываются — там нужны технические id.
 */
public class RewritingDataPack extends PathPackResources {
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
        boolean json = location.getPath().endsWith(".json");
        boolean function = location.getPath().endsWith(".mcfunction");
        if (!json && !function) {
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
            String rewritten = json ? IdRewriter.rewriteJson(text, map) : IdRewriter.rewriteFunction(text, map);
            return new ByteArrayInputStream(rewritten.getBytes(StandardCharsets.UTF_8));
        };
    }
}
