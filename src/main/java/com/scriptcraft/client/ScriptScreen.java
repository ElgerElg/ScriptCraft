package com.scriptcraft.client;

import com.scriptcraft.network.InputPacket;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.screen.ButtonElement;
import com.scriptcraft.screen.ImageElement;
import com.scriptcraft.screen.Layout;
import com.scriptcraft.screen.ListElement;
import com.scriptcraft.screen.ScreenDescription;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.ScreenMeta;
import com.scriptcraft.screen.SlotElement;
import com.scriptcraft.screen.TextElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Экран, построенный по описанию из скрипта.
 * Поддерживает:
 * - динамическое обновление элементов на лету без пересоздания экрана (applyUpdate);
 * - keep open (кнопки и элементы выполняют команды/функции, не закрывая экран);
 * - горячие клавиши элементов (key w / hotkey space) и клавиатурные события (press / release / hold)
 *   независимо от раскладки клавиатуры (физические коды GLFW);
 * - автоматическую очистку зажатых клавиш и уведомление сервера при закрытии экрана.
 */
@OnlyIn(Dist.CLIENT)
public class ScriptScreen extends Screen {
    private ScreenDescription description;
    /** Элементы в порядке отрисовки (по слою). */
    private List<ScreenElement> ordered;
    /** Кнопка-виджет для каждого ButtonElement (по ссылке: одинаковые кнопки — разные элементы). */
    private final Map<ScreenElement, Button> buttons = new IdentityHashMap<>();
    /** Сдвиг списков колёсиком, в пикселях. */
    private final Map<ListElement, Integer> scroll = new IdentityHashMap<>();
    /** Зажатые сейчас физические клавиши в этом экране: GLFW-код -> имя клавиши. */
    private final Map<Integer, String> heldKeys = new LinkedHashMap<>();
    private boolean serverClosed;

    public ScriptScreen(ScreenDescription description) {
        super(Component.literal(description.title()));
        this.description = description;
        this.ordered = ElementRenderer.sortedByLayer(description.elements(), e -> e.layout().layer());
    }

    public String titleText() {
        return description.title();
    }

    public void markServerClosed() {
        this.serverClosed = true;
    }

    /** Обновляет элементы уже открытого экрана на лету без пересоздания Screen и без сброса состояния. */
    public void applyUpdate(ScreenDescription next) {
        Map<String, Integer> prevScrollByKey = new LinkedHashMap<>();
        for (Map.Entry<ListElement, Integer> entry : scroll.entrySet()) {
            prevScrollByKey.put(listKey(entry.getKey()), entry.getValue());
        }
        this.description = next;
        this.ordered = ElementRenderer.sortedByLayer(next.elements(), e -> e.layout().layer());
        scroll.clear();
        for (ScreenElement element : ordered) {
            if (element instanceof ListElement list) {
                Integer s = prevScrollByKey.get(listKey(list));
                if (s != null) {
                    scroll.put(list, s);
                }
            }
        }
        rebuildButtons();
    }

    private static String listKey(ListElement list) {
        Layout l = list.layout();
        return ScreenMeta.elementId(list) + ":" + l.x() + ":" + l.y() + ":" + l.anchor().name();
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        buttons.clear();
        for (ScreenElement element : description.elements()) {
            if (element instanceof ButtonElement button && !ScreenMeta.isHidden(button)) {
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
    public void tick() {
        super.tick();
        if (heldKeys.isEmpty() || this.minecraft == null) {
            return;
        }
        long window = this.minecraft.getWindow().getWindow();
        Iterator<Map.Entry<Integer, String>> it = heldKeys.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, String> entry = it.next();
            int code = entry.getKey();
            String name = entry.getValue();
            if (GLFW.glfwGetKey(window, code) != GLFW.GLFW_PRESS) {
                it.remove();
                if (ClientInput.watchesRelease(name)) {
                    ModNetwork.sendToServer(new InputPacket(InputPacket.KEY_RELEASE, name));
                }
            } else if (ClientInput.watchesHold(name)) {
                ModNetwork.sendToServer(new InputPacket(InputPacket.KEY_HOLD, name));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        ItemStack hovered = ItemStack.EMPTY;
        float z = 0f;
        for (ScreenElement element : ordered) {
            if (ScreenMeta.isHidden(element)) {
                continue;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0f, 0f, z);
            Button widget = buttons.get(element);
            if (widget != null) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            } else {
                int listScroll = element instanceof ListElement list ? scroll.getOrDefault(list, 0) : 0;
                ItemStack stack = ElementRenderer.render(graphics, this.font, element, this.width, this.height, mouseX, mouseY, listScroll);
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
            graphics.pose().pushPose();
            graphics.pose().translate(0f, 0f, z);
            graphics.renderTooltip(this.font, hovered, mouseX, mouseY);
            graphics.pose().popPose();
        }
    }

    private void onButton(ButtonElement button) {
        activate(button.action());
    }

    /** ЛКМ по тексту, картинке, слоту или строке списка. Верхний слой перекрывает нижний. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (int i = ordered.size() - 1; i >= 0; i--) {
            ScreenElement element = ordered.get(i);
            if (element instanceof ButtonElement || ScreenMeta.isHidden(element)) {
                continue;
            }
            int listScroll = element instanceof ListElement list ? scroll.getOrDefault(list, 0) : 0;
            String action = ElementRenderer.clickAction(this.font, element, this.width, this.height, mouseX, mouseY, listScroll);
            if (action != null) {
                if (!ScreenMeta.rawAction(action).isEmpty()) {
                    activate(action);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (int i = ordered.size() - 1; i >= 0; i--) {
            if (!(ordered.get(i) instanceof ListElement list) || ScreenMeta.isHidden(list)) {
                continue;
            }
            if (ElementRenderer.clickAction(this.font, list, this.width, this.height, mouseX, mouseY, 0) == null) {
                continue;
            }
            int max = ElementRenderer.listMaxScroll(this.font, list, this.width, this.height);
            int next = scroll.getOrDefault(list, 0) - (int) Math.signum(delta) * ListElement.ROW;
            scroll.put(list, Math.max(0, Math.min(max, next)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        String name = ClientKeys.name(key);
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (ClientInput.cancels(com.scriptcraft.script.InputSync.ESC_TOKEN)) {
                if (name != null && !heldKeys.containsKey(key)) {
                    heldKeys.put(key, name);
                    if (ClientInput.watchesKey(name)) {
                        ModNetwork.sendToServer(new InputPacket(InputPacket.KEY, name));
                    }
                }
                return true;
            }
            this.onClose();
            return true;
        }
        if (name != null) {
            boolean firstPress = !heldKeys.containsKey(key);
            if (firstPress) {
                heldKeys.put(key, name);
                if (ClientInput.watchesKey(name)) {
                    ModNetwork.sendToServer(new InputPacket(InputPacket.KEY, name));
                }
                // Проверяем горячие клавиши элементов (key w / hotkey space) от верхнего слоя к нижнему
                for (int i = ordered.size() - 1; i >= 0; i--) {
                    ScreenElement element = ordered.get(i);
                    if (ScreenMeta.isHidden(element)) {
                        continue;
                    }
                    String packed = elementAction(element);
                    if (packed != null) {
                        ScreenMeta meta = ScreenMeta.unpack(packed);
                        if (name.equals(meta.hotkey()) && !meta.rawAction().isEmpty()) {
                            activate(packed);
                            break;
                        }
                    }
                }
            }
            // Поглощаем нажатие, чтобы стандартное управление Minecraft (E, хотбар, чат) не срабатывало в GUI
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        String name = heldKeys.remove(key);
        if (name == null) {
            name = ClientKeys.name(key);
        }
        if (name != null) {
            if (ClientInput.watchesRelease(name)) {
                ModNetwork.sendToServer(new InputPacket(InputPacket.KEY_RELEASE, name));
            }
            return true;
        }
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    public void removed() {
        for (String name : heldKeys.values()) {
            if (ClientInput.watchesRelease(name)) {
                ModNetwork.sendToServer(new InputPacket(InputPacket.KEY_RELEASE, name));
            }
        }
        heldKeys.clear();
        if (!serverClosed) {
            ModNetwork.sendToServer(new InputPacket(InputPacket.SCREEN_CLOSE, description.title()));
        }
        super.removed();
    }

    private static String elementAction(ScreenElement element) {
        if (element instanceof ButtonElement b) return b.action();
        if (element instanceof TextElement t) return t.action();
        if (element instanceof ImageElement i) return i.action();
        if (element instanceof SlotElement s) return s.action();
        return null;
    }

    private void activate(String packedAction) {
        ScreenMeta meta = ScreenMeta.unpack(packedAction);
        String raw = meta.rawAction();
        if (raw.startsWith("/") && this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand(raw.substring(1));
        } else if (raw.startsWith("fn:")) {
            ModNetwork.sendToServer(new InputPacket(InputPacket.FUNC, raw.substring(3)));
        }
        if ("close".equals(raw) || !meta.keepOpen()) {
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
