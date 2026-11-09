package com.scriptcraft.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Подсветка-маркер: события жёлтым, пути текстур и id синим. */
@OnlyIn(Dist.CLIENT)
public final class ScriptMarkup {
    public record Mark(int from, int to, boolean event, String token) {
    }

    private static final Pattern HEADER = Pattern.compile("(?i)^(\\s*)((?:on|every|command)\\b[^:\\n]*:|define\\b)");
    private static final Pattern RESOURCE = Pattern.compile("(?i)(?:[a-z0-9_.-]+:[a-z0-9_./-]+)|(?:[a-z0-9_.-]+(?:/[a-z0-9_.-]+)+\\.png)");

    private ScriptMarkup() {
    }

    public static List<Mark> of(String line) {
        List<Mark> marks = new ArrayList<>();
        Matcher header = HEADER.matcher(line);
        if (header.find()) {
            marks.add(new Mark(header.start(2), header.end(2), true, header.group(2)));
        }
        Matcher resource = RESOURCE.matcher(line);
        while (resource.find()) {
            int from = resource.start();
            int to = resource.end();
            boolean overlap = false;
            for (Mark mark : marks) {
                if (from < mark.to && to > mark.from) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) {
                marks.add(new Mark(from, to, false, resource.group()));
            }
        }
        return marks;
    }
}
