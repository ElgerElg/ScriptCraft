package com.scriptcraft.resources;

import com.scriptcraft.ScriptCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Клиентский скрытый пак: файлы scriptcraft/cache/ (ресурсы, скачанные с сервера) плюс сгенерированные
 * blockstates/модели для слотов пула (scriptcraft:dyn_block_N / dyn_item_N), указывающие на модели и
 * текстуры из ресурсов скрипта. Сгенерированные файлы важнее одноимённых файлов кэша.
 * Для настоящих id (example:ruby_block) генерируются только недостающие blockstate/модели — файлы автора важнее.
 */
public class GeneratedAssetsPack extends PathPackResources {
    public GeneratedAssetsPack(String name, Path root) {
        super(name, root, true);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> namespaces = new HashSet<>(super.getNamespaces(type));
        if (type == PackType.CLIENT_RESOURCES) {
            namespaces.add(ScriptCraft.MODID);
            namespaces.addAll(AssetBindings.realNamespaces());
        }
        return namespaces;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type == PackType.CLIENT_RESOURCES && ScriptCraft.MODID.equals(location.getNamespace())) {
            byte[] generated = AssetBindings.generate(location.getPath());
            if (generated != null) {
                return () -> new ByteArrayInputStream(generated);
            }
        }
        if (type == PackType.CLIENT_RESOURCES) {
            IoSupplier<InputStream> own = super.getResource(type, location);
            if (own != null) {
                return own; // файл автора из кэша важнее сгенерированного
            }
            byte[] real = AssetBindings.generateReal(location.getNamespace(), location.getPath());
            return real == null ? null : () -> new ByteArrayInputStream(real);
        }
        return super.getResource(type, location);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        boolean ours = type == PackType.CLIENT_RESOURCES && ScriptCraft.MODID.equals(namespace);
        Set<String> generated = ours ? new HashSet<>(AssetBindings.generatedPaths()) : Set.of();
        super.listResources(type, namespace, path, (location, supplier) -> {
            if (!generated.contains(location.getPath())) {
                output.accept(location, supplier);
            }
        });
        String prefix = path.endsWith("/") ? path : path + "/";
        if (type == PackType.CLIENT_RESOURCES && !ScriptCraft.MODID.equals(namespace)) {
            // Настоящие id: недостающие blockstate/модели (существующие в кэше уже выданы выше).
            for (String p : AssetBindings.generatedRealPaths(namespace)) {
                if (p.startsWith(prefix)) {
                    ResourceLocation location = ResourceLocation.tryBuild(namespace, p);
                    byte[] data = AssetBindings.generateReal(namespace, p);
                    if (location != null && data != null) {
                        output.accept(location, () -> new ByteArrayInputStream(data));
                    }
                }
            }
        }
        if (ours) {
            for (String p : AssetBindings.generatedPaths()) {
                if (p.startsWith(prefix)) {
                    ResourceLocation location = ResourceLocation.tryBuild(ScriptCraft.MODID, p);
                    if (location != null) {
                        output.accept(location, () -> new ByteArrayInputStream(AssetBindings.generate(p)));
                    }
                }
            }
        }
    }
}
