package com.scriptcraft.client;

import com.scriptcraft.network.InputPacket;
import com.scriptcraft.network.ModNetwork;
import com.scriptcraft.screen.ButtonElement;
import com.scriptcraft.screen.ImageElement;
import com.scriptcraft.screen.InputElement;
import com.scriptcraft.screen.Layout;
import com.scriptcraft.screen.ListElement;
import com.scriptcraft.screen.ScreenDescription;
import com.scriptcraft.screen.ScreenElement;
import com.scriptcraft.screen.ScreenMeta;
import com.scriptcraft.screen.SliderElement;
import com.scriptcraft.screen.SlotElement;
import com.scriptcraft.screen.TextElement;
import com.scriptcraft.screen.ToggleElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Экран, построенный по описанию из скрипта.
 * Поддерживает:
 * - динамическое обновление элементов на лету без пересоздания экрана (applyUpdate);
 * - интерактивные контролы: input (текстовое поле), slider (ползунок), toggle (тумблер/чекбокс);
 * - тултипы (tooltip "...") для любых элементов;
 * - keep open, горячие клавиши элементов (key w / hotkey space) и клавиатурные события (press / release / hold).
 */
@OnlyIn(Dist.CLIENT)
public class ScriptScreen extends Screen {
    private ScreenDescription description;
    private List<ScreenElement> ordered;
    private final Map<ScreenElement, Button> buttons = new IdentityHashMap<>();
    private final Map<ScreenElement, EditBox> inputs = new IdentityHashMap<>();
    private final Map<String, String> inputDrafts = new LinkedHashMap<>();
    private final Map<String, Double> sliderValues = new LinkedHashMap<>();
    private final Map<String, Boolean> toggleValues = new LinkedHashMap<>();
    private SliderElement draggingSlider;
    private ListElement draggingListScroll;
    private long lastSliderSendMs;
    private final Map<ListElement, Integer> scroll = new IdentityHashMap<>();
    private final Map<Integer, String> heldKeys = new LinkedHashMap<>();
    private final Map<String, ActiveTween> tweens = new LinkedHashMap<>();
    private boolean serverClosed;

    private record ActiveTween(float fromX, float fromY, float toX, float toY, long startMs, int durationMs) {
        float progress(long nowMs) {
            if (durationMs <= 0) return 1f;
            float t = Math.max(0f, Math.min(1f, (nowMs - startMs) / (float) durationMs));
            return durationMs <= 200 ? t : 1f - (1f - t) * (1f - t);
        }

        float curX(long nowMs) {
            return fromX + (toX - fromX) * progress(nowMs);
        }

        float curY(long nowMs) {
            return fromY + (toY - fromY) * progress(nowMs);
        }

        boolean done(long nowMs) {
            return nowMs - startMs >= durationMs;
        }
    }

    public ScriptScreen(ScreenDescription description) {
        super(Component.literal(description.title()));
        this.description = description;
        this.ordered = ElementRenderer.sortedByLayer(description.elements(), e -> e.layout().layer());
        seedLocalControls(description.elements(), true);
    }

    private void seedLocalControls(List<ScreenElement> elements, boolean overwrite) {
        for (ScreenElement e : elements) {
            String id = ScreenMeta.elementId(e);
            if (id.isEmpty()) continue;
            if (e instanceof InputElement in && (overwrite || !inputDrafts.containsKey(id))) {
                inputDrafts.put(id, in.value());
            } else if (e instanceof SliderElement sl && (overwrite || !sliderValues.containsKey(id))) {
                sliderValues.put(id, sl.value());
            } else if (e instanceof ToggleElement tg && (overwrite || !toggleValues.containsKey(id))) {
                toggleValues.put(id, tg.value());
            }
        }
    }

    public String titleText() {
        return description.title();
    }

    public void markServerClosed() {
        this.serverClosed = true;
    }

    /**
     * Быстрое дельта-обновление: заменяет только пришедшие по id элементы без пересылки всего экрана
     * и без пересоздания кнопок/полей ввода, если они не менялись.
     */
    public void applyDelta(List<ScreenElement> delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        List<ScreenElement> merged = new ArrayList<>(this.description.elements());
        Map<String, Integer> idxById = new LinkedHashMap<>(merged.size() * 2);
        for (int i = 0; i < merged.size(); i++) {
            String eid = ScreenMeta.elementId(merged.get(i));
            if (!eid.isEmpty()) {
                idxById.putIfAbsent(eid.toLowerCase(Locale.ROOT), i);
            }
        }
        long nowMs = System.currentTimeMillis();
        boolean widgetsAffected = false;
        boolean layerChanged = false;
        for (ScreenElement nextEl : delta) {
            String eid = ScreenMeta.elementId(nextEl);
            if (eid.isEmpty()) {
                continue;
            }
            String key = eid.toLowerCase(Locale.ROOT);
            Integer idx = idxById.get(key);
            if (idx != null) {
                ScreenElement oldEl = merged.get(idx);
                if (oldEl.layout().layer() != nextEl.layout().layer()) {
                    layerChanged = true;
                }
                int durMs = ScreenMeta.tweenMs(nextEl);
                if (durMs > 0) {
                    ActiveTween active = tweens.get(eid);
                    float fromX = (active != null && !active.done(nowMs)) ? active.curX(nowMs) : oldEl.layout().x();
                    float fromY = (active != null && !active.done(nowMs)) ? active.curY(nowMs) : oldEl.layout().y();
                    tweens.put(eid, new ActiveTween(fromX, fromY, nextEl.layout().x(), nextEl.layout().y(), nowMs, durMs));
                }
                merged.set(idx, nextEl);
                if (oldEl instanceof ButtonElement || nextEl instanceof ButtonElement
                        || oldEl instanceof InputElement || nextEl instanceof InputElement) {
                    widgetsAffected = true;
                }
            } else {
                idxById.put(key, merged.size());
                merged.add(nextEl);
                layerChanged = true;
                if (nextEl instanceof ButtonElement || nextEl instanceof InputElement) {
                    widgetsAffected = true;
                }
            }
            if (nextEl instanceof InputElement in) {
                inputDrafts.put(eid, in.value());
            } else if (nextEl instanceof SliderElement sl) {
                if (draggingSlider == null || !eid.equals(ScreenMeta.elementId(draggingSlider))) {
                    sliderValues.put(eid, sl.value());
                }
            } else if (nextEl instanceof ToggleElement tg) {
                toggleValues.put(eid, tg.value());
            }
        }
        this.description = new ScreenDescription(this.description.title(), merged);
        if (layerChanged) {
            this.ordered = ElementRenderer.sortedByLayer(merged, e -> e.layout().layer());
        } else {
            // Быстрая замена на месте в ordered без пересортировки
            Map<String, ScreenElement> deltaByKey = new LinkedHashMap<>(delta.size() * 2);
            for (ScreenElement e : delta) {
                String eid = ScreenMeta.elementId(e);
                if (!eid.isEmpty()) {
                    deltaByKey.put(eid.toLowerCase(Locale.ROOT), e);
                }
            }
            List<ScreenElement> nextOrdered = new ArrayList<>(this.ordered.size());
            for (ScreenElement cur : this.ordered) {
                String eid = ScreenMeta.elementId(cur);
                ScreenElement rep = eid.isEmpty() ? null : deltaByKey.get(eid.toLowerCase(Locale.ROOT));
                nextOrdered.add(rep != null ? rep : cur);
            }
            this.ordered = nextOrdered;
        }
        if (widgetsAffected) {
            String focusedInputId = null;
            int focusedCursor = 0;
            for (Map.Entry<ScreenElement, EditBox> entry : inputs.entrySet()) {
                if (entry.getValue().isFocused()) {
                    focusedInputId = ScreenMeta.elementId(entry.getKey());
                    focusedCursor = entry.getValue().getCursorPosition();
                    break;
                }
            }
            rebuildWidgets(focusedInputId, focusedCursor);
        }
    }

    public void applyUpdate(ScreenDescription next) {
        String focusedInputId = null;
        int focusedCursor = 0;
        for (Map.Entry<ScreenElement, EditBox> entry : inputs.entrySet()) {
            if (entry.getValue().isFocused()) {
                focusedInputId = ScreenMeta.elementId(entry.getKey());
                focusedCursor = entry.getValue().getCursorPosition();
                break;
            }
        }
        Map<String, Integer> prevScrollByKey = new LinkedHashMap<>();
        Map<String, Integer> prevRowsByKey = new LinkedHashMap<>();
        for (ScreenElement element : ordered) {
            if (element instanceof ListElement list) {
                String k = listKey(list);
                prevRowsByKey.put(k, list.rows().size());
                if (scroll.containsKey(list)) {
                    prevScrollByKey.put(k, scroll.get(list));
                }
            }
        }
        long nowMs = System.currentTimeMillis();
        Map<String, float[]> prevPosById = new LinkedHashMap<>();
        for (ScreenElement element : ordered) {
            String eid = ScreenMeta.elementId(element);
            if (!eid.isEmpty()) {
                ActiveTween active = tweens.get(eid);
                if (active != null && !active.done(nowMs)) {
                    prevPosById.put(eid, new float[]{active.curX(nowMs), active.curY(nowMs)});
                } else {
                    prevPosById.put(eid, new float[]{element.layout().x(), element.layout().y()});
                }
            }
        }
        this.description = next;
        this.ordered = ElementRenderer.sortedByLayer(next.elements(), e -> e.layout().layer());
        for (ScreenElement e : next.elements()) {
            String eid = ScreenMeta.elementId(e);
            if (eid.isEmpty()) continue;
            int durMs = ScreenMeta.tweenMs(e);
            if (durMs > 0) {
                float[] prev = prevPosById.get(eid);
                if (prev != null) {
                    tweens.put(eid, new ActiveTween(prev[0], prev[1], e.layout().x(), e.layout().y(), nowMs, durMs));
                }
            }
        }
        // Обновляем локальные значения для изменённых с сервера контролов, кроме активного поля ввода
        for (ScreenElement e : next.elements()) {
            String id = ScreenMeta.elementId(e);
            if (id.isEmpty()) continue;
            if (e instanceof InputElement in) {
                if (!id.equals(focusedInputId)) {
                    inputDrafts.put(id, in.value());
                }
            } else if (e instanceof SliderElement sl) {
                if (draggingSlider == null || !id.equals(ScreenMeta.elementId(draggingSlider))) {
                    sliderValues.put(id, sl.value());
                }
            } else if (e instanceof ToggleElement tg) {
                toggleValues.put(id, tg.value());
            }
        }
        scroll.clear();
        for (ScreenElement element : ordered) {
            if (element instanceof ListElement list) {
                String k = listKey(list);
                Integer prevRows = prevRowsByKey.get(k);
                int max = ElementRenderer.listMaxScroll(this.font, list, this.width, this.height);
                if (prevRows != null && list.rows().size() > prevRows) {
                    // При добавлении новых строк (например, в чате) прокручиваем список к концу
                    scroll.put(list, max);
                } else {
                    Integer s = prevScrollByKey.get(k);
                    if (s != null) {
                        scroll.put(list, Math.max(0, Math.min(max, s)));
                    }
                }
            }
        }
        rebuildWidgets(focusedInputId, focusedCursor);
    }

    private static String listKey(ListElement list) {
        Layout l = list.layout();
        return ScreenMeta.elementId(list) + ":" + l.x() + ":" + l.y() + ":" + l.anchor().name();
    }

    @Override
    protected void init() {
        rebuildWidgets(null, 0);
    }

    private void rebuildWidgets(String restoreFocusId, int restoreCursor) {
        clearWidgets();
        buttons.clear();
        inputs.clear();
        for (ScreenElement element : description.elements()) {
            if (ScreenMeta.isHidden(element)) {
                continue;
            }
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
            } else if (element instanceof InputElement in) {
                Layout l = in.layout();
                int w = l.width() > 0 ? l.width() : InputElement.DEFAULT_WIDTH;
                int h = l.height() > 0 ? l.height() : InputElement.DEFAULT_HEIGHT;
                int x = l.anchor().resolveX(this.width, w, l.x());
                int y = l.anchor().resolveY(this.height, h, l.y());
                String id = ScreenMeta.elementId(in);
                EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(in.placeholder()));
                box.setMaxLength(Math.max(1, Math.min(1024, in.maxLength())));
                String val = !id.isEmpty() && inputDrafts.containsKey(id) ? inputDrafts.get(id) : in.value();
                box.setValue(val);
                if (!in.placeholder().isEmpty()) {
                    box.setHint(Component.literal(in.placeholder()));
                }
                if (l.color() != 0xFFFFFFFF && l.color() != 0) {
                    box.setTextColor(l.color() & 0x00FFFFFF);
                }
                box.setResponder(nextText -> {
                    if (!id.isEmpty()) {
                        inputDrafts.put(id, nextText);
                        ModNetwork.sendToServer(new InputPacket(InputPacket.GUI_INPUT, id + "\0" + nextText));
                    }
                });
                if (!id.isEmpty() && id.equals(restoreFocusId)) {
                    box.setFocused(true);
                    box.setCursorPosition(Math.min(restoreCursor, val.length()));
                    setFocused(box);
                }
                inputs.put(element, addWidget(box));
            }
        }
    }

    private boolean hasFocusedInput() {
        for (EditBox box : inputs.values()) {
            if (box.isFocused()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        for (EditBox box : inputs.values()) {
            box.tick();
        }
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

        ItemStack hoveredItem = ItemStack.EMPTY;
        String hoveredTooltip = "";
        long nowMs = System.currentTimeMillis();
        float z = 0f;
        for (ScreenElement rawElement : ordered) {
            if (ScreenMeta.isHidden(rawElement)) {
                continue;
            }
            ScreenElement element = rawElement;
            String tweenId = ScreenMeta.elementId(rawElement);
            if (!tweenId.isEmpty()) {
                ActiveTween tw = tweens.get(tweenId);
                if (tw != null) {
                    if (tw.done(nowMs)) {
                        tweens.remove(tweenId);
                    } else {
                        element = ScreenMeta.withPosition(rawElement, Math.round(tw.curX(nowMs)), Math.round(tw.curY(nowMs)));
                    }
                }
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0f, 0f, z);
            Button btn = buttons.get(rawElement);
            EditBox box = inputs.get(rawElement);
            if (btn != null) {
                if (element != rawElement) {
                    Layout l = element.layout();
                    int bw = l.width() > 0 ? l.width() : 100;
                    int bh = l.height() > 0 ? l.height() : 20;
                    btn.setX(l.anchor().resolveX(this.width, bw, l.x()));
                    btn.setY(l.anchor().resolveY(this.height, bh, l.y()));
                }
                btn.render(graphics, mouseX, mouseY, partialTick);
            } else if (box != null) {
                if (element != rawElement) {
                    Layout l = element.layout();
                    int bw = l.width() > 0 ? l.width() : 120;
                    int bh = l.height() > 0 ? l.height() : 18;
                    box.setX(l.anchor().resolveX(this.width, bw, l.x()));
                    box.setY(l.anchor().resolveY(this.height, bh, l.y()));
                }
                box.render(graphics, mouseX, mouseY, partialTick);
            } else if (element instanceof SliderElement sl) {
                renderSlider(graphics, sl, mouseX, mouseY);
            } else if (element instanceof ToggleElement tg) {
                renderToggle(graphics, tg, mouseX, mouseY);
            } else {
                int listScroll = rawElement instanceof ListElement list ? scroll.getOrDefault(list, 0) : 0;
                ItemStack stack = ElementRenderer.render(graphics, this.font, element, this.width, this.height, mouseX, mouseY, listScroll);
                if (!stack.isEmpty()) {
                    hoveredItem = stack;
                }
            }
            graphics.pose().popPose();
            if (ElementRenderer.drawsItem(element)) {
                z = Math.min(z + ElementRenderer.ITEM_Z_STEP, ElementRenderer.MAX_Z);
            }
            String tip = ScreenMeta.elementTooltip(element);
            if (!tip.isEmpty() && ElementRenderer.isHovered(this.font, element, this.width, this.height, mouseX, mouseY)) {
                hoveredTooltip = tip;
            }
        }

        // Клиентские GUI-частицы и всплывающие цифры урона поверх экрана
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, z + 30f);
        ClientGuiFx.render(graphics, this.font, this.width, this.height);
        graphics.pose().popPose();

        if (!hoveredTooltip.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0f, 0f, z + 50f);
            List<Component> lines = new ArrayList<>();
            for (String raw : hoveredTooltip.replace("%nl%", "\n").replace("%newline%", "\n").replace("\\n", "\n").split("\n")) {
                lines.add(Component.literal(raw.replace('&', '\u00a7')));
            }
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            graphics.pose().popPose();
        } else if (!hoveredItem.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0f, 0f, z + 50f);
            graphics.renderTooltip(this.font, hoveredItem, mouseX, mouseY);
            graphics.pose().popPose();
        }
    }

    private void renderSlider(GuiGraphics g, SliderElement sl, int mouseX, int mouseY) {
        Layout l = sl.layout();
        int w = l.width() > 0 ? l.width() : SliderElement.DEFAULT_WIDTH;
        int h = l.height() > 0 ? l.height() : SliderElement.DEFAULT_HEIGHT;
        int x = l.anchor().resolveX(this.width, w, l.x());
        int y = l.anchor().resolveY(this.height, h, l.y());
        String id = ScreenMeta.elementId(sl);
        double val = !id.isEmpty() && sliderValues.containsKey(id) ? sliderValues.get(id) : sl.value();
        double range = sl.max() - sl.min();
        double norm = range > 0 ? Math.max(0.0, Math.min(1.0, (val - sl.min()) / range)) : 0.0;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int accent = (l.color() == 0xFFFFFFFF || l.color() == 0) ? 0xFF3B82F6 : l.color();

        g.fill(x, y, x + w, y + h, 0xCC1E1E24);
        g.fill(x, y, x + w, y + 1, hover ? 0xFF60A5FA : 0xFF45475A);
        g.fill(x, y + h - 1, x + w, y + h, hover ? 0xFF60A5FA : 0xFF45475A);
        g.fill(x, y, x + 1, y + h, hover ? 0xFF60A5FA : 0xFF45475A);
        g.fill(x + w - 1, y, x + w, y + h, hover ? 0xFF60A5FA : 0xFF45475A);

        int fillW = (int) Math.round((w - 2) * norm);
        if (fillW > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, (accent & 0x00FFFFFF) | 0x99000000);
        }
        int knobX = x + 1 + (int) Math.round((w - 8) * norm);
        g.fill(knobX, y + 1, knobX + 6, y + h - 1, hover || draggingSlider == sl ? 0xFFFFFFFF : 0xFFE2E8F0);

        String numText = formatSliderNum(val);
        String txt = sl.label().isEmpty() ? numText : (sl.label() + ": " + numText);
        g.drawCenteredString(this.font, txt, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    private void renderToggle(GuiGraphics g, ToggleElement tg, int mouseX, int mouseY) {
        Layout l = tg.layout();
        int w = l.width() > 0 ? l.width() : ToggleElement.DEFAULT_WIDTH;
        int h = l.height() > 0 ? l.height() : ToggleElement.DEFAULT_HEIGHT;
        int x = l.anchor().resolveX(this.width, w, l.x());
        int y = l.anchor().resolveY(this.height, h, l.y());
        String id = ScreenMeta.elementId(tg);
        boolean on = !id.isEmpty() && toggleValues.containsKey(id) ? toggleValues.get(id) : tg.value();
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int onColor = (l.color() == 0xFFFFFFFF || l.color() == 0) ? 0xFF22C55E : l.color();

        g.fill(x, y, x + w, y + h, hover ? 0xCC2A2B36 : 0xB0181920);
        g.fill(x, y, x + w, y + 1, 0xFF45475A);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF45475A);
        g.fill(x, y, x + 1, y + h, 0xFF45475A);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF45475A);

        int swW = 24;
        int swH = Math.min(12, h - 4);
        int swX = x + 5;
        int swY = y + (h - swH) / 2;
        g.fill(swX, swY, swX + swW, swY + swH, on ? onColor : 0xFF52525B);
        int knob = swH - 2;
        int kx = on ? (swX + swW - knob - 1) : (swX + 1);
        g.fill(kx, swY + 1, kx + knob, swY + 1 + knob, 0xFFFFFFFF);

        String label = tg.label().isEmpty() ? (on ? "ON" : "OFF") : tg.label();
        g.drawString(this.font, label, swX + swW + 6, y + (h - 8) / 2, on ? 0xFFFFFFFF : 0xFFB4B4C2, true);
    }

    private static String formatSliderNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) {
            return Long.toString(Math.round(v));
        }
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void updateSliderFromMouse(SliderElement sl, double mouseX, boolean forceSend) {
        Layout l = sl.layout();
        int w = l.width() > 0 ? l.width() : SliderElement.DEFAULT_WIDTH;
        int x = l.anchor().resolveX(this.width, w, l.x());
        double norm = Math.max(0.0, Math.min(1.0, (mouseX - x) / Math.max(1.0, w)));
        double raw = sl.min() + norm * (sl.max() - sl.min());
        SliderElement snapped = sl.withValue(raw);
        String id = ScreenMeta.elementId(sl);
        if (!id.isEmpty()) {
            Double prev = sliderValues.put(id, snapped.value());
            long now = System.currentTimeMillis();
            if (forceSend || ((prev == null || Math.abs(prev - snapped.value()) > 1e-9) && now - lastSliderSendMs >= 60)) {
                lastSliderSendMs = now;
                ModNetwork.sendToServer(new InputPacket(InputPacket.GUI_SLIDER, id + "\0" + formatSliderNum(snapped.value())));
            }
        }
    }

    private void onButton(ButtonElement button) {
        activate(button.action());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Сначала проверяем EditBox-ы, чтобы снимать фокус при клике мимо
        boolean hitInput = false;
        for (EditBox box : inputs.values()) {
            if (box.mouseClicked(mouseX, mouseY, button)) {
                setFocused(box);
                hitInput = true;
            } else {
                box.setFocused(false);
            }
        }
        if (hitInput) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (int i = ordered.size() - 1; i >= 0; i--) {
            ScreenElement element = ordered.get(i);
            if (element instanceof ButtonElement || element instanceof InputElement || ScreenMeta.isHidden(element)) {
                continue;
            }
            if (element instanceof SliderElement sl && ElementRenderer.isHovered(this.font, sl, this.width, this.height, mouseX, mouseY)) {
                draggingSlider = sl;
                updateSliderFromMouse(sl, mouseX, true);
                return true;
            }
            if (element instanceof ToggleElement tg && ElementRenderer.isHovered(this.font, tg, this.width, this.height, mouseX, mouseY)) {
                String id = ScreenMeta.elementId(tg);
                boolean cur = !id.isEmpty() && toggleValues.containsKey(id) ? toggleValues.get(id) : tg.value();
                boolean next = !cur;
                if (!id.isEmpty()) {
                    toggleValues.put(id, next);
                    ModNetwork.sendToServer(new InputPacket(InputPacket.GUI_TOGGLE, id + "\0" + next));
                }
                return true;
            }
            if (element instanceof ListElement list) {
                int barScroll = ElementRenderer.listScrollbarDrag(this.font, list, this.width, this.height, mouseX, mouseY, false);
                if (barScroll >= 0) {
                    draggingListScroll = list;
                    scroll.put(list, barScroll);
                    return true;
                }
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSlider != null && button == 0) {
            updateSliderFromMouse(draggingSlider, mouseX, false);
            return true;
        }
        if (draggingListScroll != null && button == 0) {
            int barScroll = ElementRenderer.listScrollbarDrag(this.font, draggingListScroll, this.width, this.height, mouseX, mouseY, true);
            if (barScroll >= 0) {
                scroll.put(draggingListScroll, barScroll);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSlider != null && button == 0) {
            updateSliderFromMouse(draggingSlider, mouseX, true);
            draggingSlider = null;
            return true;
        }
        if (draggingListScroll != null && button == 0) {
            draggingListScroll = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
    public boolean charTyped(char codePoint, int modifiers) {
        if (hasFocusedInput()) {
            return super.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (hasFocusedInput()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                for (EditBox box : inputs.values()) {
                    box.setFocused(false);
                }
                setFocused(null);
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
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
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (hasFocusedInput()) {
            return super.keyReleased(key, scan, modifiers);
        }
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
