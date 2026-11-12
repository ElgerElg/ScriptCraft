package com.scriptcraft.script.runtime;

import com.scriptcraft.lang.Phrase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Предмет скрипта: id и необязательный SNBT сразу за ним.
 * {@code tacz:modern_kinetic_gun{GunId:"tacz:glock_17"}}.
 * Если двоеточие перед кавычкой пропущено ({@code GunId"tacz:glock_17"}), оно подставляется.
 */
public final class ItemStacks {
    private ItemStacks() {
    }

    public record Spec(String id, @Nullable CompoundTag tag) {
    }

    /** Id без nbt, в нижнем регистре. Пустая строка, если разобрать нечего. */
    public static String idOf(String raw) {
        String text = raw == null ? "" : raw.strip();
        int brace = text.indexOf('{');
        String id = (brace <= 0 ? text : text.substring(0, brace)).strip();
        return id.toLowerCase(Locale.ROOT);
    }

    public static Spec parse(String raw) {
        String text = raw == null ? "" : raw.strip();
        int brace = text.indexOf('{');
        if (brace <= 0) {
            return new Spec(text.toLowerCase(Locale.ROOT), null);
        }
        String id = text.substring(0, brace).strip().toLowerCase(Locale.ROOT);
        return new Spec(id, readTag(text.substring(brace).strip()));
    }

    public static ItemStack create(String raw, int count) {
        Spec spec = parse(raw);
        Item item = ItemResolver.resolve(spec.id())
                .orElseThrow(() -> new ScriptRuntimeException(com.scriptcraft.lang.Messages.get("effect.unknown_item", spec.id())));
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        if (spec.tag() != null) {
            stack.setTag(spec.tag().copy());
        }
        return stack;
    }

    /** Для клиента: битый id или nbt даёт пустой стек, а не падение отрисовки. */
    public static ItemStack tryCreate(String raw, int count) {
        try {
            if (raw == null || raw.isBlank() || idOf(raw).isEmpty()) {
                return ItemStack.EMPTY;
            }
            return create(raw, count);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    /** Предмет совпадает. Если в записи есть nbt, каждый его ключ должен быть на стеке. */
    public static boolean matches(ItemStack stack, String raw) {
        if (stack == null || stack.isEmpty() || raw == null || raw.isBlank()) {
            return false;
        }
        Spec spec;
        try {
            spec = parse(raw);
        } catch (RuntimeException e) {
            return false;
        }
        Item item = ItemResolver.resolve(spec.id()).orElse(null);
        if (item == null || !stack.is(item)) {
            return false;
        }
        return spec.tag() == null || covers(stack.getTag(), spec.tag());
    }

    private static boolean covers(@Nullable CompoundTag have, CompoundTag want) {
        if (have == null) {
            return false;
        }
        for (String key : want.getAllKeys()) {
            Tag expected = want.get(key);
            Tag actual = have.get(key);
            if (expected instanceof CompoundTag nested && actual instanceof CompoundTag got) {
                if (!covers(got, nested)) {
                    return false;
                }
            } else if (actual == null || !actual.equals(expected)) {
                return false;
            }
        }
        return true;
    }

    private static CompoundTag readTag(String snbt) {
        try {
            return TagParser.parseTag(snbt);
        } catch (Exception first) {
            String fixed = snbt.replaceAll("([A-Za-z0-9_]+)\"", "$1:\"");
            if (!fixed.equals(snbt)) {
                try {
                    return TagParser.parseTag(fixed);
                } catch (Exception ignored) {
                    // остаётся сообщение первого разбора
                }
            }
            String message = first.getMessage() == null ? first.toString() : first.getMessage();
            throw new ScriptRuntimeException(Phrase.pick("bad item nbt: " + message, "неверный nbt предмета: " + message));
        }
    }
}
