package com.scriptcraft.script.engine;

import com.scriptcraft.script.ScriptSource;
import com.scriptcraft.script.parser.ParseResult;
import com.scriptcraft.script.parser.ScriptParser;

import java.util.List;

/** Встроенный движок: собственный парсер Skript-подобного синтаксиса + интерпретатор с белым списком действий. */
public final class SimpleScriptEngine implements ScriptEngine {
    @Override
    public String id() {
        return "simple";
    }

    @Override
    public ScriptProgram compile(List<ScriptSource> sources, List<String> errors) {
        ParseResult result = new ScriptParser().parse(sources);
        errors.addAll(result.errors());
        return new SimpleProgram(result);
    }
}
