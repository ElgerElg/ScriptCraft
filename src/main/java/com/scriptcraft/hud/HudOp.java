package com.scriptcraft.hud;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Одна операция над HUD клиента. Пакет несёт пачку операций — так сервер отправляет только изменения
 * (новое значение полосы, новый текст, видимость), а не весь HUD заново.
 */
public sealed interface HudOp {
    /** Защита от слишком больших пакетов. */
    int MAX_ENTRIES = 256;

    Kind kind();

    void writeData(FriendlyByteBuf buf);

    /** Порядок важен для сети — новые операции добавлять в конец. */
    enum Kind {
        PUT, REMOVE, REMOVE_GROUP, CLEAR, VALUE, TEXT, VISIBLE, VANILLA
    }

    /** Добавить или заменить элементы (по id). */
    record Put(List<HudEntry> entries) implements HudOp {
        public Kind kind() {
            return Kind.PUT;
        }

        public void writeData(FriendlyByteBuf buf) {
            buf.writeVarInt(entries.size());
            for (HudEntry e : entries) {
                e.write(buf);
            }
        }
    }

    /** Удалить элемент по id. */
    record Remove(String id) implements HudOp {
        public Kind kind() {
            return Kind.REMOVE;
        }

        public void writeData(FriendlyByteBuf buf) {
            buf.writeUtf(id);
        }
    }

    /** Удалить все элементы группы. */
    record RemoveGroup(String group) implements HudOp {
        public Kind kind() {
            return Kind.REMOVE_GROUP;
        }

        public void writeData(FriendlyByteBuf buf) {
            buf.writeUtf(group);
        }
    }

    /** Удалить всё (кроме настроек ванильного HUD — они отдельной операцией). */
    record Clear() implements HudOp {
        public Kind kind() {
            return Kind.CLEAR;
        }

        public void writeData(FriendlyByteBuf buf) {
        }
    }

    /** Новые value/max полосы. */
    record Value(String id, String value, String max) implements HudOp {
        public Kind kind() {
            return Kind.VALUE;
        }

        public void writeData(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(value);
            buf.writeUtf(max);
        }
    }

    /** Новый текст (текстовый элемент, надпись на полосе, текстовый слот). */
    record Text(String id, String text) implements HudOp {
        public Kind kind() {
            return Kind.TEXT;
        }

        public void writeData(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(text);
        }
    }

    /** Показать/скрыть элемент. */
    record Visible(String id, boolean visible) implements HudOp {
        public Kind kind() {
            return Kind.VISIBLE;
        }

        public void writeData(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeBoolean(visible);
        }
    }

    /** Полная маска скрытых частей ванильного HUD ({@link VanillaHudPart}). */
    record Vanilla(int hiddenMask) implements HudOp {
        public Kind kind() {
            return Kind.VANILLA;
        }

        public void writeData(FriendlyByteBuf buf) {
            buf.writeVarInt(hiddenMask);
        }
    }

    static void write(HudOp op, FriendlyByteBuf buf) {
        buf.writeEnum(op.kind());
        op.writeData(buf);
    }

    static HudOp read(FriendlyByteBuf buf) {
        Kind kind = buf.readEnum(Kind.class);
        return switch (kind) {
            case PUT -> {
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Слишком много элементов HUD: " + n);
                }
                List<HudEntry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(HudEntry.read(buf));
                }
                yield new Put(list);
            }
            case REMOVE -> new Remove(buf.readUtf());
            case REMOVE_GROUP -> new RemoveGroup(buf.readUtf());
            case CLEAR -> new Clear();
            case VALUE -> new Value(buf.readUtf(), buf.readUtf(), buf.readUtf());
            case TEXT -> new Text(buf.readUtf(), buf.readUtf());
            case VISIBLE -> new Visible(buf.readUtf(), buf.readBoolean());
            case VANILLA -> new Vanilla(buf.readVarInt());
        };
    }
}
