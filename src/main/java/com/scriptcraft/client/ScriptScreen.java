package com.scriptcraft.client;

import com.scriptcraft.screen.ButtonElement;
import com.scriptcraft.screen.Layout;
import com.scriptcraft.screen.ScreenDescription;
import com.scriptcraft.screen.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Экран, построенный по описанию из скрипта.
 * Кнопки — настоящие виджеты Minecraft (создаются в init), остальное рисует {@link ElementRenderer}.
 *
 * Слои: все элементы (и кнопки тоже) рисуются по возрастанию layer, при равном слое — в порядке объявления.
 * Поэтому кнопки добавляются через addWidget (только ввод), а рисуются вручную в общем цикле.
 * Скелет: без прокрутки, полей ввода, вкладок — см. TODO в README.
 */
@OnlyIn(Dist.CLIENT)
public class ScriptScreen extends Screen {
    private final ScreenDescription description;
    /** Элементы в порядке отрисовки (по слою). */
    private final List<ScreenElement> ordered;
    /** Кнопка-виджет для каждого ButtonElement (по ссылке: одинаковые кнопки — разные элементы). */
    private final Map<ScreenElement, Button> buttons = new IdentityHashMap<>();

    public ScriptScreen(ScreenDescription description) {
        super(Component.literal(description.title()));
        this.description = description;
        this.ordered = ElementRenderer.sortedByLayer(description.elements(), e -> e.layout().layer());
    }

    @Override
    protected void init() {
        // init() вызывается и при изменении размера окна (clearWidgets перед этим делает Screen), поэтому
        // позиции всегда пересчитываются.
        buttons.clear();
        for (ScreenElement element : description.elements()) {
            if (element instanceof ButtonElement button) {
                Layout l = button.layout();
                int w = l.width() > 0 ? l.width() : 100;
                int h = l.height() > 0 ? l.height() : 20;
                int x = l.anchor().resolveX(this.width, w, l.x());
                int y = l.anchor().resolveY(this.height, h, l.y());
                Button widget = Button.builder(Component.literal(button.label()), b -> onButton(button))
                        .bounds(x, y, w, h)
                        .build();
                buttons.put(element, addWidget(widget));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // В 1.20.1 renderBackground принимает только GuiGraphics (в 1.20.2+ сигнатура другая).
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        ItemStack hovered = ItemStack.EMPTY;
        float z = 0f;
        for (ScreenElement element : ordered) {
            graphics.pose().pushPose();
            graphics.pose().translate(0f, 0f, z);
            Button widget = buttons.get(element);
            if (widget != null) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            } else {
                ItemStack stack = ElementRenderer.render(graphics, this.font, element, this.width, this.height, mouseX, mouseY);
                if (!stack.isEmpty()) {
                    hovered = stack;
                }
            }
            graphics.pose().popPose();
            if (ElementRenderer.drawsItem(element)) {
                z = Math.min(z + ElementRenderer.ITEM_Z_STEP, ElementRenderer.MAX_Z);
            }
        }

        if (!hovered.isEmpty()) {
            // Подсказка — поверх всех слоёв.
            graphics.pose().pushPose();
            graphics.pose().translate(0f, 0f, z);
            graphics.renderTooltip(this.font, hovered, mouseX, mouseY);
            graphics.pose().popPose();
        }
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
