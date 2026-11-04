package com.scriptcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.scriptcraft.hud.HudEntry;
import com.scriptcraft.hud.HudOp;
import com.scriptcraft.hud.VanillaHudPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HUD из скриптов на клиенте: хранит элементы, присланные сервером ({@link HudOp}), и рисует их
 * двумя оверлеями Forge (регистрация — {@link ClientModEvents}):
 * - "hud_below" — ПОД всем ванильным HUD: элементы с отрицательным слоем;
 * - "hud" — ПОВЕРХ всего: элементы со слоем 0 и выше.
 * Внутри оверлея элементы рисуются по возрастанию слоя.
 *
 * Скрытие частей ванильного HUD — {@link #isHidden(ResourceLocation)} в {@link ClientEvents}.
 * Всё вызывается только из главного потока клиента.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientHud {
    private static final LinkedHashMap<String, HudEntry> ENTRIES = new LinkedHashMap<>();
    /** Отсортированный по слою список видимых элементов; пересобирается только после изменений. */
    private static List<HudEntry> sorted = List.of();
    private static boolean dirty = false;
    private static int hiddenMask = 0;

    /** Оверлей Forge -> часть HUD из скриптов. */
    private static final Map<ResourceLocation, VanillaHudPart> VANILLA = new HashMap<>();

    static {
        VANILLA.put(VanillaGuiOverlay.HOTBAR.id(), VanillaHudPart.HOTBAR);
        VANILLA.put(VanillaGuiOverlay.PLAYER_HEALTH.id(), VanillaHudPart.HEALTH);
        VANILLA.put(VanillaGuiOverlay.FOOD_LEVEL.id(), VanillaHudPart.FOOD);
        VANILLA.put(VanillaGuiOverlay.ARMOR_LEVEL.id(), VanillaHudPart.ARMOR);
        VANILLA.put(VanillaGuiOverlay.AIR_LEVEL.id(), VanillaHudPart.AIR);
        VANILLA.put(VanillaGuiOverlay.EXPERIENCE_BAR.id(), VanillaHudPart.EXPERIENCE);
        VANILLA.put(VanillaGuiOverlay.MOUNT_HEALTH.id(), VanillaHudPart.MOUNT_HEALTH);
        VANILLA.put(VanillaGuiOverlay.JUMP_BAR.id(), VanillaHudPart.JUMP_BAR);
        VANILLA.put(VanillaGuiOverlay.CROSSHAIR.id(), VanillaHudPart.CROSSHAIR);
        VANILLA.put(VanillaGuiOverlay.POTION_ICONS.id(), VanillaHudPart.EFFECTS);
        VANILLA.put(VanillaGuiOverlay.ITEM_NAME.id(), VanillaHudPart.ITEM_NAME);
        VANILLA.put(VanillaGuiOverlay.SCOREBOARD.id(), VanillaHudPart.SCOREBOARD);
        VANILLA.put(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(), VanillaHudPart.BOSSBAR);
    }

    private ClientHud() {
    }

    /** Применяет пачку изменений от сервера. */
    public static void apply(List<HudOp> ops) {
        for (HudOp op : ops) {
            if (op instanceof HudOp.Put put) {
                for (HudEntry e : put.entries()) {
                    // remove + put: заменённый элемент встаёт в конец (порядок объявления при равных слоях).
                    ENTRIES.remove(e.id());
                    ENTRIES.put(e.id(), e);
                }
            } else if (op instanceof HudOp.Remove remove) {
                ENTRIES.remove(remove.id());
            } else if (op instanceof HudOp.RemoveGroup group) {
                ENTRIES.values().removeIf(e -> e.group().equals(group.group()));
            } else if (op instanceof HudOp.Clear) {
                ENTRIES.clear();
            } else if (op instanceof HudOp.Value value) {
                ENTRIES.computeIfPresent(value.id(), (id, e) -> {
                    HudEntry updated = e.withValues(value.value(), value.max());
                    return updated != null ? updated : e;
                });
            } else if (op instanceof HudOp.Text text) {
                ENTRIES.computeIfPresent(text.id(), (id, e) -> {
                    HudEntry updated = e.withText(text.text());
                    return updated != null ? updated : e;
                });
            } else if (op instanceof HudOp.Visible visible) {
                ENTRIES.computeIfPresent(visible.id(), (id, e) -> e.withVisible(visible.visible()));
            } else if (op instanceof HudOp.Vanilla vanilla) {
                hiddenMask = vanilla.hiddenMask();
            }
        }
        dirty = true;
    }

    /** Отключение от сервера: HUD и скрытие ванильных частей сбрасываются (сервер пришлёт всё заново при входе). */
    public static void reset() {
        ENTRIES.clear();
        sorted = List.of();
        dirty = false;
        hiddenMask = 0;
    }

    /** Скрыт ли этот ванильный оверлей скриптом (hide vanilla ...). */
    public static boolean isHidden(ResourceLocation overlayId) {
        if (hiddenMask == 0) {
            return false;
        }
        VanillaHudPart part = VANILLA.get(overlayId);
        return part != null && (hiddenMask & part.bit()) != 0;
    }

    /** Оверлей "под всем": элементы с отрицательным слоем. */
    public static void renderBelow(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        render(graphics, width, height, true);
    }

    /** Оверлей "поверх всего": элементы со слоем 0 и выше. */
    public static void renderAbove(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        render(graphics, width, height, false);
    }

    private static void render(GuiGraphics graphics, int width, int height, boolean below) {
        Minecraft mc = Minecraft.getInstance();
        if (ENTRIES.isEmpty() || mc.player == null || mc.options.hideGui) {
            return;
        }
        if (dirty) {
            sorted = ElementRenderer.sortedByLayer(ENTRIES.values().stream().filter(HudEntry::visible).toList(),
                    e -> e.element().layout().layer());
            dirty = false;
        }
        float z = 0f;
        for (HudEntry entry : sorted) {
            boolean negative = entry.element().layout().layer() < 0;
            if (negative != below) {
                continue;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0f, 0f, z);
            ElementRenderer.render(graphics, mc.font, entry.element(), width, height, -1, -1);
            graphics.pose().popPose();
            if (ElementRenderer.drawsItem(entry.element())) {
                z = Math.min(z + ElementRenderer.ITEM_Z_STEP, ElementRenderer.MAX_Z);
            }
        }
        if (z > 0f) {
            // Наши элементы подняты по z: сбрасываем глубину, чтобы следующие оверлеи (чат, подсказки)
            // не оказались "под" ними.
            graphics.flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
    }
}
