package com.scriptcraft.script.parser;

import com.scriptcraft.script.runtime.Action;

import java.util.List;
import java.util.Locale;

/** function Name(param: type): — имя, параметры и тело. Ищется без учёта регистра. */
public record FunctionDefinition(String name, List<Param> params, List<Action> actions, String file) {
    public record Param(String name, FuncType type) {
        public String key() {
            return name.toLowerCase(Locale.ROOT);
        }
    }

    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }
}
