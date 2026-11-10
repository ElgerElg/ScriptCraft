package com.scriptcraft.script.parser;

import com.scriptcraft.script.runtime.ArgType;
import org.jetbrains.annotations.Nullable;

/**
 * Один кусок команды: тип в &lt;скобках&gt; или [скобках], либо слово как есть (take).
 * Слово не попадает в %arg-1% — это часть пути команды.
 */
public record ArgSpec(@Nullable ArgType type, boolean optional, @Nullable String literal) {
    public ArgSpec(ArgType type, boolean optional) {
        this(type, optional, null);
    }

    public static ArgSpec literal(String word) {
        return new ArgSpec(null, false, word);
    }

    public boolean isLiteral() {
        return literal != null;
    }
}
