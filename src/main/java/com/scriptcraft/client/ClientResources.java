package com.scriptcraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.scriptcraft.ModPaths;
import com.scriptcraft.ScriptCraft;
import com.scriptcraft.config.ScriptCraftConfig;
import com.scriptcraft.lang.Messages;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.network.ResourceChunkPacket;
import com.scriptcraft.network.ResourceManifestPacket;
import com.scriptcraft.network.ResourceRequestPacket;
import com.scriptcraft.resources.ResourceIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Клиентская часть синхронизации ресурсов.
 *
 * Кэш: scriptcraft/cache/assets/... — он же содержимое скрытого встроенного ресурспака мода
 * ({@link com.scriptcraft.resources.ResourcePacks}), который подключается ещё при запуске игры.
 * Поэтому если кэш уже актуален, при входе на сервер ничего не скачивается и ничего не перезагружается.
 *
 * Применение изменений:
 * - assets/<ns>/textures/gui/**.png — регистрируются сразу через DynamicTexture (без перезагрузки);
 * - всё остальное (модели, blockstates, текстуры блоков/предметов, lang) попадает в атлас/модели только
 *   при перезагрузке ресурсов, поэтому делается ОДНА Minecraft.reloadResourcePacks() — и только если
 *   такие файлы действительно изменились.
 * Устаревшие файлы из кэша не удаляются (чтобы при переходе между серверами не перекачивать и не
 * перезагружать лишний раз); кэш можно очистить вручную, удалив папку scriptcraft/cache.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientResources {
    /** Ожидаемые файлы: путь -> запись манифеста. */
    private static final Map<String, ResourceManifestPacket.Entry> PENDING = new HashMap<>();
    /** Собираемые файлы. */
    private static final Map<String, byte[]> BUFFERS = new HashMap<>();
    private static final Map<String, Integer> RECEIVED = new HashMap<>();
    /** Изменённые в кэше файлы, которые ещё не применены. */
    private static final Set<String> CHANGED = new LinkedHashSet<>();
    /** GUI-картинки, которые уже точно есть в TextureManager (проверять больше не нужно). */
    private static final Set<ResourceLocation> READY = new HashSet<>();
    /** Когда снова проверить картинку, которой пока нет ни в ресурсах, ни в кэше (мс). */
    private static final Map<ResourceLocation, Long> RETRY_AT = new HashMap<>();
    /**
     * Нужна перезагрузка ресурсов из-за новой раскладки слотов пула. Не сбрасывается при выходе: таблица уже
     * сохранена, и если перезагрузка не успела пройти, она выполнится при следующем входе.
     */
    private static boolean bindingsReload = false;

    private ClientResources() {
    }

    /** Манифест с сервера: сравниваем с кэшем и запрашиваем недостающее. */
    public static void onManifest(List<ResourceManifestPacket.Entry> entries) {
        // Недокачанное по старому манифесту забываем; уже записанные, но не применённые файлы (CHANGED) — нет.
        PENDING.clear();
        BUFFERS.clear();
        RECEIVED.clear();
        long total = 0;
        for (ResourceManifestPacket.Entry e : entries) {
            total += Math.max(0, e.size());
        }
        long limit = ScriptCraftConfig.maxResourceSizeMb() * 1024L * 1024L;
        if (total > limit) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Ресурсы сервера ({} КБ) больше лимита maxResourceSizeMb, не скачиваю", total / 1024);
            ClientToasts.push(Messages.toast("resources.too_big", total / 1024 / 1024, ScriptCraftConfig.maxResourceSizeMb()));
            return;
        }
        Path cache = ModPaths.cache();
        List<String> request = new ArrayList<>();
        for (ResourceManifestPacket.Entry e : entries) {
            if (!ResourceIndex.isSafePath(e.path()) || e.size() < 0) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] Сервер прислал недопустимый путь ресурса, пропущен: {}", e.path());
                continue;
            }
            Path file = cache.resolve(e.path());
            if (!file.normalize().startsWith(cache.normalize())) {
                continue; // защита от выхода за пределы кэша
            }
            if (!isUpToDate(file, e)) {
                PENDING.put(e.path(), e);
                request.add(e.path());
            }
        }
        if (request.isEmpty()) {
            ScriptCraft.LOGGER.info("[ScriptCraft] Кэш ресурсов актуален ({} файлов), скачивать нечего", entries.size());
            apply(); // вдруг остались применённые не до конца файлы от прерванной передачи
            return;
        }
        ScriptCraft.LOGGER.info("[ScriptCraft] Запрашиваю у сервера {} файлов ресурсов", request.size());
        ModNetwork.sendToServer(new ResourceRequestPacket(request));
    }

    /** Кусок файла: собираем, проверяем SHA-1, пишем в кэш. */
    public static void onChunk(ResourceChunkPacket p) {
        ResourceManifestPacket.Entry expected = PENDING.get(p.path());
        if (expected == null || !expected.sha1().equals(p.sha1()) || p.total() != expected.size()
                || p.offset() < 0 || p.offset() + p.data().length > p.total()) {
            return; // не запрашивали или устаревший кусок
        }
        byte[] buffer = BUFFERS.computeIfAbsent(p.path(), k -> new byte[p.total()]);
        System.arraycopy(p.data(), 0, buffer, p.offset(), p.data().length);
        int received = RECEIVED.merge(p.path(), p.data().length, Integer::sum);
        if (received < p.total()) {
            return;
        }
        PENDING.remove(p.path());
        BUFFERS.remove(p.path());
        RECEIVED.remove(p.path());
        if (!ResourceIndex.sha1(buffer).equals(expected.sha1())) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Ресурс {} пришёл повреждённым (SHA-1 не совпал)", p.path());
        } else {
            try {
                Path file = ModPaths.cache().resolve(p.path());
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.write(tmp, buffer);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                CHANGED.add(p.path());
            } catch (IOException e) {
                ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось записать ресурс {} в кэш: {}", p.path(), e.toString());
            }
        }
        if (PENDING.isEmpty()) {
            apply();
        }
    }

    /** Выход из мира: забываем недокачанное (уже записанные файлы остаются в кэше). */
    public static void reset() {
        PENDING.clear();
        BUFFERS.clear();
        RECEIVED.clear();
        CHANGED.clear();
        READY.clear();
        RETRY_AT.clear();
    }

    /**
     * Вызывается перед отрисовкой GUI-картинки HUD/экрана. Чинит случай, когда текстура не подхватилась:
     * - картинку уже пытались нарисовать до докачки -> под id лежит "missing" (фиолетово-чёрная клетка);
     * - кэш был актуален уже при входе, а ресурспак-кэш ещё не знает этот namespace -> текстуры нет вовсе.
     * Если файл есть в scriptcraft/cache, он регистрируется как DynamicTexture — без перезахода и перезагрузки.
     * Нет файла — повторная проверка не чаще раза в секунду (он может ещё докачиваться).
     */
    public static void ensureGuiTexture(ResourceLocation id) {
        if (READY.contains(id)) {
            return;
        }
        String path = "assets/" + id.getNamespace() + "/" + id.getPath();
        if (!isGuiTexture(path)) {
            READY.add(id); // не наша забота: атлас блоков/предметов и ванильные картинки
            return;
        }
        long now = System.currentTimeMillis();
        Long retry = RETRY_AT.get(id);
        if (retry != null && now < retry) {
            return;
        }
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        // Версия getTexture с запасным значением НЕ загружает текстуру, только смотрит, что зарегистрировано.
        AbstractTexture current = textures.getTexture(id, null);
        if (current != null && current != MissingTextureAtlasSprite.getTexture()) {
            READY.add(id);
            return;
        }
        if (Files.isRegularFile(ModPaths.cache().resolve(path)) && registerDynamic(path)) {
            READY.add(id);
            RETRY_AT.remove(id);
        } else {
            // Пусть пока рисуется как есть (ресурспак или "missing"); проверим ещё раз позже.
            RETRY_AT.put(id, now + 1000L);
        }
    }

    /**
     * Пришла таблица привязок. layoutChanged — модели слотов пула указывают на другие id: их нужно "запечь"
     * перезагрузкой ресурсов. Если файлы ещё докачиваются — перезагрузка будет одна, после докачки.
     */
    public static void onBindings(boolean layoutChanged) {
        if (layoutChanged) {
            bindingsReload = true;
        }
        if (bindingsReload && PENDING.isEmpty()) {
            apply();
        }
    }

    private static void apply() {
        if (CHANGED.isEmpty() && !bindingsReload) {
            return;
        }
        boolean needReload = bindingsReload;
        bindingsReload = false;
        int gui = 0;
        for (String path : CHANGED) {
            if (isGuiTexture(path)) {
                if (registerDynamic(path)) {
                    String[] parts = path.split("/", 3);
                    ResourceLocation id = ResourceLocation.tryBuild(parts[1], parts[2]);
                    if (id != null) {
                        READY.add(id);
                        RETRY_AT.remove(id);
                    }
                    gui++;
                    continue;
                }
            }
            needReload = true;
        }
        ScriptCraft.LOGGER.info("[ScriptCraft] Ресурсы обновлены: {} файлов, GUI-картинок без перезагрузки: {}", CHANGED.size(), gui);
        CHANGED.clear();
        if (needReload) {
            // Атлас текстур блоков/предметов и запечённые модели собираются только при перезагрузке ресурсов.
            ClientToasts.push(Messages.toast("resources.reloading"));
            Minecraft.getInstance().reloadResourcePacks();
        }
    }

    /** assets/<ns>/textures/gui/... .png — картинки для экранов скриптов. */
    private static boolean isGuiTexture(String path) {
        String[] parts = path.split("/", 4);
        return parts.length == 4 && parts[2].equals("textures") && parts[3].startsWith("gui/") && path.endsWith(".png");
    }

    /** Регистрирует картинку как DynamicTexture под её обычным id (ns:textures/gui/...png). */
    private static boolean registerDynamic(String path) {
        String[] parts = path.split("/", 3); // assets, ns, остальное
        ResourceLocation id = ResourceLocation.tryBuild(parts[1], parts[2]);
        if (id == null) {
            return false;
        }
        try {
            NativeImage image = NativeImage.read(Files.readAllBytes(ModPaths.cache().resolve(path)));
            // register() сам закрывает старую текстуру с тем же id.
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            return true;
        } catch (IOException | RuntimeException e) {
            ScriptCraft.LOGGER.warn("[ScriptCraft] Не удалось загрузить картинку {}: {}", path, e.toString());
            return false;
        }
    }

    private static boolean isUpToDate(Path file, ResourceManifestPacket.Entry e) {
        try {
            return Files.isRegularFile(file) && Files.size(file) == e.size()
                    && ResourceIndex.sha1(Files.readAllBytes(file)).equals(e.sha1());
        } catch (IOException ex) {
            return false;
        }
    }

}
