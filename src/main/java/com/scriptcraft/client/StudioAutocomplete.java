package com.scriptcraft.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Контекстные подсказки автодополнения для .sc скриптов в редакторе студии.
 */
@OnlyIn(Dist.CLIENT)
public final class StudioAutocomplete {
    private static final List<String> TOP_LEVEL = List.of(
            "on join:", "on quit:", "on first join:", "on chat:", "on death:", "on respawn:",
            "on break:", "on place:", "on right click:", "on left click:", "on damage:",
            "on kill:", "on attack:", "on shoot:", "on projectile hit:", "on consume:",
            "on craft:", "on fish:", "on sneak:", "on sprint:", "on jump:", "on command:",
            "on drop:", "on pickup:", "on receive:", "on item break:", "on key :", "on open :",
            "on world change:", "on gamemode change:", "on load:", "on unload:",
            "command /", "every 5 seconds:", "function ", "define item ", "define block "
    );

    private static final List<String> BODY = List.of(
            "if ", "else if ", "else:", "loop all players:", "loop 10 times:", "while ",
            "wait 1 second", "chance of 50%:", "stop", "cancel event",
            "send \"\" to player", "message \"\" to player", "broadcast \"\"", "reply \"\"",
            "send title \"\" with subtitle \"\" to player", "send actionbar \"\" to player",
            "set {} to ", "add 1 to {}", "remove 1 from {}", "delete {}",
            "give player ", "heal player", "damage player by ", "feed player", "kill player",
            "teleport player to ", "set gamemode of player to ", "set flight of player to true",
            "set walk speed of player to ", "set fly speed of player to ",
            "give player effect speed for 10 seconds", "clear effects of player",
            "play sound entity.player.levelup to player",
            "play sound entity.experience_orb.pickup at player for all players",
            "spawn particle flame at player count 20 speed 0.05",
            "spawn entity \"minecraft:zombie\" at player named \"&cBoss\" with health 50",
            "push player upwards with force 1.2", "set velocity of player to 0 1 0",
            "strike lightning at player", "strike fake lightning at player",
            "create explosion at player with power 2", "create safe explosion at player with power 2",
            "set scoreboard title \"&6Stats\" for player",
            "set scoreboard line 1 to \"&7Online: %online%\" for player",
            "clear scoreboard of player",
            "open screen \"Menu\" to player:", "create hud \"stats\" for player:",
            "set block at targeted block to ", "execute console command \"\"",
            "make player execute command \"\"", "kick player due to \"\"", "log \"\""
    );

    private static final List<String> EXPR_AND_PH = List.of(
            "%player%", "%online%", "%arg-1%", "%arg-2%", "%args%", "%message%",
            "%victim%", "%attacker%", "%cause%", "%block%", "%item%", "%amount%",
            "%x%", "%y%", "%z%", "%world%", "%from%", "%gamemode%", "%command%",
            "%loop-number%", "%loop-player%", "%sneaking%", "%sprinting%", "%projectile%",
            "clicked entity is set", "clicked player is set", "clicked block is set",
            "targeted block", "targeted entity", "targeted player", "location of player",
            "location of targeted block", "location at ", "health of player", "food of player",
            "random integer between 1 and 10", "random number between 0 and 1",
            "random element out of {}", "round(", "floor(", "ceil(", "abs(", "sqrt(", "min(", "max(", "clamp("
    );

    private StudioAutocomplete() {
    }

    public record Match(int replaceFrom, List<String> items) {
    }

    public static Match suggest(String line, int col, boolean manual) {
        if (line == null || col < 0 || col > line.length()) {
            return null;
        }
        String before = line.substring(0, col);
        String trimmed = before.stripLeading();
        boolean indented = before.length() > trimmed.length();

        // Определяем начало текущего токена (или начала строки для многословных шаблонов)
        int wordStart = col;
        while (wordStart > 0) {
            char c = before.charAt(wordStart - 1);
            if (Character.isWhitespace(c) || c == '(' || c == ',' || c == ':') {
                break;
            }
            wordStart--;
        }
        String word = before.substring(wordStart).toLowerCase(Locale.ROOT);
        String linePrefix = trimmed.toLowerCase(Locale.ROOT);

        if (!manual && word.length() < 2 && !word.startsWith("%")) {
            return null;
        }

        List<String> result = new ArrayList<>();
        if (word.startsWith("%")) {
            for (String s : EXPR_AND_PH) {
                if (s.startsWith("%") && s.toLowerCase(Locale.ROOT).startsWith(word)) {
                    result.add(s);
                }
            }
            return result.isEmpty() ? null : new Match(wordStart, result.subList(0, Math.min(7, result.size())));
        }

        List<String> pool = indented ? BODY : TOP_LEVEL;
        // Сначала пробуем совпадение по началу всей команды (напр. "spawn p" -> "spawn particle ...")
        int lineStart = before.length() - trimmed.length();
        if (!linePrefix.isEmpty()) {
            for (String s : pool) {
                if (s.toLowerCase(Locale.ROOT).startsWith(linePrefix) && !s.equalsIgnoreCase(trimmed)) {
                    result.add(s);
                }
            }
        }
        if (!result.isEmpty()) {
            return new Match(lineStart, result.subList(0, Math.min(7, result.size())));
        }

        // Иначе совпадение по последнему слову (выражения, условия, функции)
        if (!word.isEmpty() || manual) {
            for (String s : EXPR_AND_PH) {
                if (!s.startsWith("%") && s.toLowerCase(Locale.ROOT).startsWith(word) && !s.equalsIgnoreCase(word)) {
                    result.add(s);
                }
            }
            if (result.isEmpty() && manual) {
                for (String s : pool) {
                    if (s.toLowerCase(Locale.ROOT).startsWith(word)) {
                        result.add(s);
                    }
                }
            }
        }
        return result.isEmpty() ? null : new Match(wordStart, result.subList(0, Math.min(7, result.size())));
    }
}
