/*
 * ScriptCraft Docs — общий код для всех страниц:
 * строки интерфейса (RU/EN), переключатели языка и темы, подсветка кода, кнопки «Копировать».
 * Без внешних зависимостей; работает и с file://.
 */
(function () {
  "use strict";
  var SC = (window.SC = window.SC || {});

  // ------------------------------------------------------------ строки интерфейса
  SC.strings = {
    ru: {
      "nav.docs": "Документация", "nav.examples": "Примеры", "nav.start": "Начало",
      "lang.btn": "EN", "lang.title": "Switch to English",
      "theme.title": "Светлая / тёмная тема", "github": "GitHub",
      "docs.title": "Синтаксис ScriptCraft",
      "docs.sub": "Всё, что умеет язык: события, эффекты, условия, выражения, цели, экраны, команды мода и конфиг. У каждой строки — пример.",
      "search.ph": "Поиск: heal, переменная, radius, open screen…", "search.hint": "/ — поиск, Esc — очистить",
      "chips.all": "Сбросить", "status.all": "Записей: {n}", "status.found": "Найдено: {n} из {total}",
      "empty": "Ничего не найдено. Попробуйте другое слово или сбросьте фильтры.",
      "card.syntax": "Синтаксис", "card.example": "Пример", "card.related": "См. также:", "card.since": "с версии {v}",
      "card.link": "Ссылка на эту запись",
      "copy": "Копировать", "copied": "Скопировано!", "copy.fail": "Не удалось",
      "ex.title": "Примеры скриптов",
      "ex.sub": "Готовые скрипты целиком: скопируйте в scriptcraft/scripts/ и выполните /sc reload.",
      "ex.notes": "Важно знать", "ex.related": "Синтаксис:",
      "foot": "ScriptCraft — скриптовый мод для Minecraft Forge 1.20.1. Документация к версии {v}."
    },
    en: {
      "nav.docs": "Docs", "nav.examples": "Examples", "nav.start": "Getting started",
      "lang.btn": "RU", "lang.title": "Переключить на русский",
      "theme.title": "Light / dark theme", "github": "GitHub",
      "docs.title": "ScriptCraft syntax",
      "docs.sub": "Everything the language can do: events, effects, conditions, expressions, targets, screens, mod commands and config. Every line has an example.",
      "search.ph": "Search: heal, variable, radius, open screen…", "search.hint": "/ to search, Esc to clear",
      "chips.all": "Reset", "status.all": "Entries: {n}", "status.found": "Found: {n} of {total}",
      "empty": "Nothing found. Try another word or reset the filters.",
      "card.syntax": "Syntax", "card.example": "Example", "card.related": "See also:", "card.since": "since {v}",
      "card.link": "Link to this entry",
      "copy": "Copy", "copied": "Copied!", "copy.fail": "Failed",
      "ex.title": "Script examples",
      "ex.sub": "Complete scripts: copy into scriptcraft/scripts/ and run /sc reload.",
      "ex.notes": "Good to know", "ex.related": "Syntax:",
      "foot": "ScriptCraft is a scripting mod for Minecraft Forge 1.20.1. Docs for version {v}."
    }
  };

  function store(key, value) {
    try {
      if (value === undefined) return localStorage.getItem(key);
      localStorage.setItem(key, value);
    } catch (e) { /* file:// в некоторых браузерах без localStorage */ }
    return null;
  }

  SC.lang = store("sc-lang") === "en" ? "en" : "ru";
  SC.t = function (key, vars) {
    var s = (SC.strings[SC.lang] && SC.strings[SC.lang][key]) || SC.strings.ru[key] || key;
    if (vars) for (var k in vars) s = s.split("{" + k + "}").join(vars[k]);
    return s;
  };
  /** Выбор RU/EN из объекта {ru, en}. */
  SC.tr = function (obj) { return obj ? (obj[SC.lang] || obj.ru || "") : ""; };

  var root = document.documentElement;
  var prefersLight = window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches;
  root.setAttribute("data-theme", store("sc-theme") || (prefersLight ? "light" : "dark"));
  root.setAttribute("data-lang", SC.lang);
  root.lang = SC.lang;

  SC.applyStrings = function () {
    document.querySelectorAll("[data-i18n]").forEach(function (el) { el.textContent = SC.t(el.getAttribute("data-i18n")); });
    document.querySelectorAll("[data-i18n-ph]").forEach(function (el) { el.placeholder = SC.t(el.getAttribute("data-i18n-ph")); });
    document.querySelectorAll("[data-i18n-title]").forEach(function (el) { el.title = SC.t(el.getAttribute("data-i18n-title")); });
    document.querySelectorAll("button.copy").forEach(function (el) { el.textContent = SC.t("copy"); });
    var v = window.SC_DOCS && window.SC_DOCS.syntax ? window.SC_DOCS.syntax.version : "";
    document.querySelectorAll("[data-i18n-foot]").forEach(function (el) { el.textContent = SC.t("foot", { v: v }); });
  };

  SC.setLang = function (lang) {
    SC.lang = lang;
    store("sc-lang", lang);
    root.setAttribute("data-lang", lang);
    root.lang = lang;
    SC.applyStrings();
    document.dispatchEvent(new CustomEvent("sc:lang"));
  };

  // ------------------------------------------------------------ экранирование и мини-разметка
  SC.esc = function (s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  };
  /** `код` и **жирный** в описаниях; абзацы — по пустой строке. */
  SC.inline = function (text) {
    return SC.esc(text)
      .replace(/`([^`]+)`/g, "<code>$1</code>")
      .replace(/\*\*([^*]+)\*\*/g, "<b>$1</b>");
  };
  SC.paras = function (text) {
    return String(text).split(/\n\s*\n/).map(function (p) { return "<p>" + SC.inline(p.trim()) + "</p>"; }).join("");
  };

  // ------------------------------------------------------------ подсветка
  var KW = "on|command|permission|if|else|loop|define|block|item|namespace|modid|open|screen|to|from|of|in|radius|is|not|set|for|seconds|at|size|texture|anchor|color|shadow|run|close|all|other|players";
  var FN = "send|broadcast|reply|title|subtitle|action|bar|play|sound|give|teleport|tp|heal|damage|feed|kill|ignite|extinguish|clear|inventory|gamemode|flying|effect|remove|add|delete|xp|levels|level|experience|execute|console|make|text|button|slot|image|health|food|hunger|fire";
  var TG = "player|targeted|loop-player";
  var SK_RE = new RegExp(
    '("(?:[^"\\\\]|\\\\.)*")' +                 // 1 строка
    "|(\\{[^}\\s]*\\})" +                        // 2 переменная
    "|(%[a-z-]+%)" +                             // 3 плейсхолдер
    "|(@[aprse](?:\\[[^\\]]*\\])?)" +            // 4 селектор
    "|(#[0-9A-Fa-f]{6,8}\\b)" +                  // 5 цвет
    "|(-?\\b\\d+(?:\\.\\d+)?(?:x\\d+)?\\b)" +    // 6 число / размер 120x20
    "|(\\b(?:" + TG + ")(?:'s)?(?![\\w-]))" +    // 7 цель
    "|(\\b(?:" + KW + ")\\b)" +                  // 8 ключевое слово
    "|(\\b(?:" + FN + ")\\b)" +                  // 9 эффект
    "|(/[a-z0-9_]+)",                            // 10 /команда
    "g");
  var CLS = [null, "str", "var", "ph", "tg", "col", "num", "tg", "kw", "fn", "fn"];

  function span(cls, html) { return '<span class="hl-' + cls + '">' + html + "</span>"; }

  function hlString(s) {
    // Внутри строки подсвечиваем {переменные}, %плейсхолдеры% и &-цвета.
    return SC.esc(s).replace(/(\{[^}\s]*\})|(%[a-z-]+%)|(&amp;[0-9a-fk-or])/g, function (m, v, p, c) {
      return v ? span("var", m) : p ? span("ph", m) : span("col", m);
    });
  }

  function hlSkLine(line) {
    if (/^\s*#/.test(line)) return span("com", SC.esc(line));
    var out = "", last = 0, m;
    SK_RE.lastIndex = 0;
    while ((m = SK_RE.exec(line))) {
      out += SC.esc(line.slice(last, m.index));
      var g = 1;
      while (g < CLS.length && m[g] === undefined) g++;
      out += g === 1 ? span("str", hlString(m[0])) : span(CLS[g], SC.esc(m[0]));
      last = SK_RE.lastIndex;
    }
    return out + SC.esc(line.slice(last));
  }

  function hlData(line) {
    // JSON и TOML: комментарии, ключи, строки, числа, true/false.
    if (/^\s*#/.test(line)) return span("com", SC.esc(line));
    var re = /("(?:[^"\\]|\\.)*")(\s*:)?|(\[[^\]"]*\])|(\b-?\d+(?:\.\d+)?\b)|(\btrue\b|\bfalse\b)|^(\s*[A-Za-z_][\w.]*)(?=\s*=)/g;
    var out = "", last = 0, m;
    while ((m = re.exec(line))) {
      out += SC.esc(line.slice(last, m.index));
      if (m[1]) out += span(m[2] ? "key" : "str", SC.esc(m[1])) + (m[2] ? SC.esc(m[2]) : "");
      else if (m[3]) out += span("kw", SC.esc(m[3]));
      else if (m[4]) out += span("num", SC.esc(m[4]));
      else if (m[5]) out += span("kw", SC.esc(m[5]));
      else if (m[6]) out += span("key", SC.esc(m[6]));
      last = re.lastIndex;
      if (m[0] === "") re.lastIndex++;
    }
    return out + SC.esc(line.slice(last));
  }

  SC.highlight = function (code, lang) {
    var lines = String(code).split("\n");
    if (lang === "text") return lines.map(function (l) {
      return /^\s*#/.test(l) ? span("com", SC.esc(l)) : SC.esc(l).replace(/(\([^)]*\))$/, function (x) { return span("com", x); });
    }).join("\n");
    var fn = lang === "json" || lang === "toml" ? hlData : hlSkLine;
    return lines.map(fn).join("\n");
  };

  /** HTML блока кода с заголовком и кнопкой «Копировать». Исходник хранится в data-code. */
  SC.codeBlock = function (code, lang, title) {
    return '<div class="code"><div class="code-h"><span>' + SC.esc(title || lang || "") + "</span>" +
      '<button class="btn copy" type="button" data-code="' + SC.esc(code) + '">' + SC.esc(SC.t("copy")) + "</button></div>" +
      "<pre><code>" + SC.highlight(code, lang || "sk") + "</code></pre></div>";
  };

  // ------------------------------------------------------------ копирование
  function fallbackCopy(text) {
    var ta = document.createElement("textarea");
    ta.value = text;
    ta.style.position = "fixed"; ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    var ok = false;
    try { ok = document.execCommand("copy"); } catch (e) { ok = false; }
    document.body.removeChild(ta);
    return ok;
  }
  SC.copy = function (text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text).then(function () { return true; }, function () { return fallbackCopy(text); });
    }
    return Promise.resolve(fallbackCopy(text));
  };
  document.addEventListener("click", function (ev) {
    var btn = ev.target.closest && ev.target.closest("button.copy");
    if (!btn) return;
    SC.copy(btn.getAttribute("data-code")).then(function (ok) {
      btn.textContent = SC.t(ok ? "copied" : "copy.fail");
      btn.classList.toggle("ok", ok);
      setTimeout(function () { btn.textContent = SC.t("copy"); btn.classList.remove("ok"); }, 1500);
    });
  });

  // ------------------------------------------------------------ шапка
  SC.measure = function () {
    var top = document.querySelector(".top");
    var sb = document.querySelector(".searchbar");
    if (top) root.style.setProperty("--hh", top.offsetHeight + "px");
    root.style.setProperty("--sbh", sb && getComputedStyle(sb).position === "sticky" ? sb.offsetHeight + "px" : "0px");
  };

  document.addEventListener("DOMContentLoaded", function () {
    var langBtn = document.getElementById("lang-toggle");
    if (langBtn) langBtn.addEventListener("click", function () { SC.setLang(SC.lang === "ru" ? "en" : "ru"); });
    var themeBtn = document.getElementById("theme-toggle");
    if (themeBtn) themeBtn.addEventListener("click", function () {
      var next = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
      root.setAttribute("data-theme", next);
      store("sc-theme", next);
    });
    // Подсветка статических блоков кода (страница «Начало»).
    document.querySelectorAll("pre[data-lang]").forEach(function (pre) {
      var code = pre.textContent.replace(/^\n/, "").replace(/\s+$/, "");
      var wrap = document.createElement("div");
      wrap.innerHTML = SC.codeBlock(code, pre.getAttribute("data-lang"), pre.getAttribute("data-title"));
      pre.parentNode.replaceChild(wrap.firstChild, pre);
    });
    SC.applyStrings();
    SC.measure();
    window.addEventListener("resize", SC.measure);
  });
})();
