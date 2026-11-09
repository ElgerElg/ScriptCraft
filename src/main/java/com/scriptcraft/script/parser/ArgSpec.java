package com.scriptcraft.script.parser;

import com.scriptcraft.script.runtime.ArgType;

/** Один аргумент команды. В квадратных скобках он необязательный. */
public record ArgSpec(ArgType type, boolean optional) {
}
