package com.scriptcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.scriptcraft.screen.ButtonElement;
import com.scriptcraft.screen.ImageElement;
import com.scriptcraft.screen.Layout;
import com.scriptcraft.screen.ScreenDescription;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.SlotElement;
import com.scriptcraft.screen.TextElement;
import com.scriptcraft.script.runtime.ItemResolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Экран, построенный по описанию из скрипта.
 * Кнопки — настоящие виджеты Minecraft (создаются в init), текст/слоты/картинки рисуются в render.
 * Скелет: без прокрутки, полей ввода, вкладок — см. TODO в README.
 */
@OnlyIn(Dist.CLIENT)
public class ScriptScreen extends Screen {
    private static final int SLOT_SIZE = 18;

    private final ScreenDescription description;

    public ScriptScreen(ScreenDescription description) {
        super(Component.literal(description.title()));
        this.description = description;
    }

    @Override
    protected void init() {
        // init() вызывается и при изменении размера окна, поэтому позиции всегда пересчитываются.
        for (ScreenElement element : description.elements()) {
            if (element instanceof ButtonElement button) {
                Layout l = button.layout();
                int w = l.width() > 0 ? l.width() : 100;
                int h = l.height() > 0 ? l.height() : 20;
                int x = l.anchor().resolveX(this.width, w, l.x());
                int y = l.anchor().resolveY(this.height, h, l.y());
                addRenderableWidget(Button.builder(Component.literal(button.label()), b -> onButton(button))
                        .bounds(x, y, w, h)
                        .build());
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // В 1.20.1 renderBackground принимает только GuiGraphics (в 1.20.2+ сигнатура другая).
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        ItemStack hovered = ItemStack.EMPTY;
        for (ScreenElement element : description.elements()) {
            if (element instanceof TextElement text) {
                renderText(graphics, text);
            } else if (element instanceof ImageElement image) {
                renderImage(graphics, image);
            } else if (element instanceof SlotElement slot) {
                ItemStack stack = renderSlot(graphics, slot, mouseX, mouseY);
                if (!stack.isEmpty()) {
                    hovered = stack;
                }
            }
        }

        // Виджеты (кнопки) рисует базовый класс.
        super.render(graphics, mouseX, mouseY, partialTick);

        if (!hovered.isEmpty()) {
            graphics.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
    }

    private void renderText(GuiGraphics graphics, TextElement text) {
        Layout l = text.layout();
        int w = this.font.width(text.text());
        int h = this.font.lineHeight;
        int x = l.anchor().resolveX(this.width, w, l.x());
        int y = l.anchor().resolveY(this.height, h, l.y());
        graphics.drawString(this.font, text.text(), x, y, l.color(), text.shadow());
    }

    private void renderImage(GuiGraphics graphics, ImageElement image) {
        ResourceLocation texture = ResourceLocation.tryParse(image.texture());
        if (texture == null) {
            return;
        }
        Layout l = image.layout();
        int w = l.width() > 0 ? l.width() : 16;
        int h = l.height() > 0 ? l.height() : 16;
        int texW = image.textureWidth() > 0 ? image.textureWidth() : w;
        int texH = image.textureHeight() > 0 ? image.textureHeight() : h;
        int x = l.anchor().resolveX(this.width, w, l.x());
        int y = l.anchor().resolveY(this.height, h, l.y());

        // Цвет элемента используется как оттенок картинки (белый = без изменений).
        int c = l.color();
        float a = ((c >>> 24) & 0xFF) / 255f;
        if (a == 0f) {
            a = 1f;
        }
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, a);
        // Рисуем всю текстуру (texW x texH), растянутую до w x h.
        graphics.blit(texture, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Рисует слот. Возвращает предмет, если на него наведена мышь (для подсказки). */
    private ItemStack renderSlot(GuiGraphics graphics, SlotElement slot, int mouseX, int mouseY) {
        Layout l = slot.layout();
        int x = l.anchor().resolveX(this.width, SLOT_SIZE, l.x());
        int y = l.anchor().resolveY(this.height, SLOT_SIZE, l.y());

        // Простая "ванильная" рамка слота.
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + SLOT_SIZE, y + SLOT_SIZE, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF8B8B8B);

        // Тот же поиск предмета, что и на сервере: "diamond" -> minecraft:diamond, иначе scriptcraft:<id>.
        Item item = ItemResolver.resolve(slot.itemId()).orElse(null);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, slot.count()));
        graphics.renderItem(stack, x + 1, y + 1);
        graphics.renderItemDecorations(this.font, stack, x + 1, y + 1);

        boolean hover = mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
        return hover ? stack : ItemStack.EMPTY;
    }

    private void onButton(ButtonElement button) {
        String action = button.action();
        if (action.startsWith("/") && this.minecraft != null && this.minecraft.player != null) {
            // Команда уходит на сервер как обычная команда игрока — сервер сам проверит права.
            this.minecraft.player.connection.sendCommand(action.substring(1));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
