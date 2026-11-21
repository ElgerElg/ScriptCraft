package com.scriptcraft.script.runtime;

import com.scriptcraft.lang.Messages;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Правки LuckPerms. Цель — любой набор игроков, в том числе параметр функции и оффлайн-имя. */
public final class LuckPermsActions {
    private LuckPermsActions() {
    }

    public enum Kind {
        SET_PERM, ADD_PERM, REMOVE_PERM, CLEAR_PERMS,
        ADD_GROUP, REMOVE_GROUP, SET_GROUP, CLEAR_GROUPS,
        SET_PREFIX, ADD_PREFIX, REMOVE_PREFIX, CLEAR_PREFIX,
        SET_SUFFIX, ADD_SUFFIX, REMOVE_SUFFIX, CLEAR_SUFFIX,
        SET_META, REMOVE_META, CLEAR_META
    }

    public record Change(Kind kind, @Nullable Target target, @Nullable Expression groupTarget,
                         @Nullable Expression a, @Nullable Expression b,
                         @Nullable Expression time, int secondsPerUnit, @Nullable Expression priority,
                         boolean prioritySet) implements Action {
        public Change(Kind kind, Target target, @Nullable Expression a, @Nullable Expression b,
                      @Nullable Expression time, int secondsPerUnit, @Nullable Expression priority,
                      boolean prioritySet) {
            this(kind, target, null, a, b, time, secondsPerUnit, priority, prioritySet);
        }

        @Override
        public void execute(ExecutionContext ctx) {
            if (!LuckPermsLink.available()) {
                throw new ScriptRuntimeException(Messages.get("lp.missing"));
            }
            String first = a == null ? "" : text(a, ctx);
            String second = b == null ? "" : text(b, ctx);
            boolean flag = b != null && Effects.bool(b, ctx);
            long seconds = time == null ? 0 : Math.max(0, Math.round(Effects.number(time, ctx) * secondsPerUnit));
            int weight = !prioritySet || priority == null ? 100 : (int) Math.round(Effects.number(priority, ctx));
            if (groupTarget != null) {
                String gName = text(groupTarget, ctx);
                LuckPermsLink.editGroup(gName, grp -> apply(grp, first, second, flag, seconds, weight));
                return;
            }
            if (target == null) {
                return;
            }
            List<ServerPlayer> online = target.resolve(ctx);
            if (!online.isEmpty()) {
                for (ServerPlayer player : online) {
                    LuckPermsLink.edit(player, user -> apply(user, first, second, flag, seconds, weight));
                }
                return;
            }
            String name = Effects.storedName(target, ctx);
            if (name == null) {
                return;
            }
            UUID id = LuckPermsLink.profileId(ctx, name);
            if (id == null) {
                throw new ScriptRuntimeException(Messages.get("lp.no_profile", name));
            }
            LuckPermsLink.edit(id, null, user -> apply(user, first, second, flag, seconds, weight));
        }

        private void apply(Object user, String first, String second, boolean flag, long seconds, int weight) {
            switch (kind) {
                case SET_PERM -> LuckPermsLink.setPermission(user, first, flag, seconds);
                case ADD_PERM -> LuckPermsLink.setPermission(user, first, true, seconds);
                case REMOVE_PERM -> LuckPermsLink.removePermission(user, first);
                case CLEAR_PERMS -> LuckPermsLink.clearPermissions(user);
                case ADD_GROUP -> LuckPermsLink.addGroup(user, first, seconds);
                case REMOVE_GROUP -> LuckPermsLink.removeGroup(user, first);
                case SET_GROUP -> LuckPermsLink.setPrimaryGroup(user, first, seconds);
                case CLEAR_GROUPS -> LuckPermsLink.clearGroups(user);
                case SET_PREFIX -> {
                    LuckPermsLink.clearPrefix(user, weight, prioritySet);
                    LuckPermsLink.setPrefix(user, first, weight, seconds, false);
                }
                case ADD_PREFIX -> LuckPermsLink.setPrefix(user, first, weight, seconds, false);
                case REMOVE_PREFIX -> LuckPermsLink.clearPrefix(user, weight, prioritySet);
                case CLEAR_PREFIX -> LuckPermsLink.clearPrefix(user, 0, false);
                case SET_SUFFIX -> {
                    LuckPermsLink.clearSuffix(user, weight, prioritySet);
                    LuckPermsLink.setSuffix(user, first, weight, seconds, false);
                }
                case ADD_SUFFIX -> LuckPermsLink.setSuffix(user, first, weight, seconds, false);
                case REMOVE_SUFFIX -> LuckPermsLink.clearSuffix(user, weight, prioritySet);
                case CLEAR_SUFFIX -> LuckPermsLink.clearSuffix(user, 0, false);
                case SET_META -> LuckPermsLink.setMeta(user, first, second);
                case REMOVE_META -> LuckPermsLink.removeMeta(user, first);
                case CLEAR_META -> LuckPermsLink.clearMeta(user);
            }
        }
    }

    private static String text(Expression expression, ExecutionContext ctx) {
        String value = ctx.format(Expression.toText(expression.evaluate(ctx))).strip();
        if (value.isEmpty() || value.equals("<none>")) {
            throw new ScriptRuntimeException(Messages.get("lp.empty"));
        }
        return value;
    }
}
