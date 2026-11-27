/*
 * ScriptCraft Docs — Вариант A: 3-колоночный IDE-справочник.
 * Слева: сворачиваемое дерево категорий + быстрые примеры.
 * По центру: раскрывающиеся на месте карточки (синтаксис, описание, пример, связанные — без прыжка наверх!).
 * Справа: закреплённый редактор «Quick Example & Preview» с нумерацией строк и кнопкой копирования.
 */
(function () {
  "use strict";
  var SC = window.SC;
  var DATA = window.SC_DOCS.syntax;
  var EXAMPLES = Array.isArray(window.SC_DOCS.examples)
    ? window.SC_DOCS.examples
    : ((window.SC_DOCS.examples && window.SC_DOCS.examples.items) || []);
  var byId = {};
  var catById = {};
  DATA.categories.forEach(function (c, i) { c.order = i; catById[c.id] = c; });
  DATA.entries.forEach(function (e, i) { e.order = i; byId[e.id] = e; });

  var state = { q: "", cats: {}, onlyNew: false, selected: null, open: {} };
  var els = {};

  var CAT_ICONS = {
    events: "⚡",
    structures: "❖",
    effects: "✦",
    conditions: "◈",
    expressions: "λ",
    targets: "◎",
    screen: "▣",
    hud: "▤",
    commands: "⌘",
    config: "⚙"
  };

  function norm(s) { return String(s).toLowerCase().replace(/ё/g, "е"); }

  DATA.entries.forEach(function (e) {
    e.h = {
      name: norm([e.id, e.name.ru, e.name.en].join(" ")),
      syntax: norm(e.syntax.join(" ")),
      desc: norm([e.desc.ru, e.desc.en].join(" ")),
      example: norm(e.example)
    };
  });

  function subseq(needle, hay) {
    var limit = needle.length * 2;
    for (var start = hay.indexOf(needle[0]); start >= 0; start = hay.indexOf(needle[0], start + 1)) {
      var i = 1;
      for (var j = start + 1; j < hay.length && j - start < limit && i < needle.length; j++) if (hay[j] === needle[i]) i++;
      if (i === needle.length) return true;
    }
    return false;
  }

  function score(e, terms) {
    var total = 0;
    for (var i = 0; i < terms.length; i++) {
      var t = terms[i], s = 0;
      if (e.h.name.indexOf(t) >= 0) s = e.h.name.indexOf(t) === 0 || e.h.name.indexOf(" " + t) >= 0 ? 12 : 10;
      else if (e.h.syntax.indexOf(t) >= 0) s = 6;
      else if (e.h.desc.indexOf(t) >= 0) s = 2;
      else if (e.h.example.indexOf(t) >= 0) s = 1;
      else if (t.length >= 3 && subseq(t, e.h.name)) s = 0.5;
      if (!s) return 0;
      total += s;
    }
    return total;
  }

  function terms() {
    return norm(state.q).split(/\s+/).filter(Boolean);
  }

  function mark(text, ts) {
    var src = String(text);
    if (!ts.length) return SC.esc(src);
    var low = norm(src), hits = [];
    ts.forEach(function (t) {
      var at = low.indexOf(t);
      while (at >= 0) { hits.push([at, at + t.length]); at = low.indexOf(t, at + t.length); }
    });
    if (!hits.length) return SC.esc(src);
    hits.sort(function (a, b) { return a[0] - b[0]; });
    var out = "", pos = 0;
    hits.forEach(function (h) {
      if (h[0] < pos) h[0] = pos;
      if (h[1] <= h[0]) return;
      out += SC.esc(src.slice(pos, h[0])) + "<mark>" + SC.esc(src.slice(h[0], h[1])) + "</mark>";
      pos = h[1];
    });
    return out + SC.esc(src.slice(pos));
  }

  function visible() {
    var ts = terms();
    var anyCat = Object.keys(state.cats).some(function (k) { return state.cats[k]; });
    var list = [];
    DATA.entries.forEach(function (e) {
      if (anyCat && !state.cats[e.category]) return;
      if (state.onlyNew && e.since !== DATA.version) return;
      var s = ts.length ? score(e, ts) : 1;
      if (s > 0) list.push({ e: e, s: s });
    });
    list.sort(function (a, b) {
      if (ts.length && b.s !== a.s) return b.s - a.s;
      return catById[a.e.category].order - catById[b.e.category].order || a.e.order - b.e.order;
    });
    return list.map(function (x) { return x.e; });
  }

  /** Раскрывающаяся на месте карточка: заголовок + основной синтаксис; по клику раскрываются все формы, описание, пример и связи. */
  function accCard(e, ts) {
    var cat = catById[e.category];
    var isOpen = !!state.open[e.id];
    var isSel = state.selected === e.id;
    var related = (e.related || []).filter(function (r) { return byId[r]; });
    var body = "";
    if (isOpen) {
      body =
        '<div class="ide-acc-body">' +
        (e.syntax.length > 1
          ? '<div class="ide-syn-list">' + e.syntax.map(function (s, i) {
              return '<div class="ide-syn-row"><span class="ide-syn-idx">' + (i + 1) + '</span><code>' + mark(s, ts) + '</code>' +
                '<button class="syntax-copy" type="button" data-copy-syntax="' + SC.esc(s) + '" title="' + SC.esc(SC.t("copy")) + '">⧉</button></div>';
            }).join("") + '</div>'
          : "") +
        '<div class="desc ide-focus-desc">' + SC.readable(SC.tr(e.desc)) + "</div>" +
        SC.codeBlock(e.example, e.category === "config" ? "toml" : "sc", SC.t("card.example") + (e.category === "config" ? " · scriptcraft/config.toml" : "")) +
        (related.length
          ? '<div class="related"><span>' + SC.esc(SC.t("card.related")) + ":</span>" + related.map(function (r) {
              return '<a href="#' + r + '" data-pick="' + r + '">' + SC.esc(SC.tr(byId[r].name)) + "</a>";
            }).join("") + "</div>"
          : "") +
        "</div>";
    }
    return '<article class="ide-acc' + (isOpen ? " open" : "") + (isSel ? " on" : "") + '" id="' + e.id + '" data-entry="' + e.id + '">' +
      '<div class="ide-acc-head" data-toggle="' + e.id + '">' +
      '<span class="ide-acc-chev">' + (isOpen ? "▾" : "▸") + '</span>' +
      '<h3>' + mark(SC.tr(e.name), ts) + '</h3>' +
      '<span class="badge c-' + e.category + '">' + (CAT_ICONS[e.category] || "") + " " + SC.esc(SC.tr(cat)) + '</span>' +
      '<span class="since' + (e.since === DATA.version ? " is-new" : "") + '">' + SC.esc(SC.t("card.since", { v: e.since })) + '</span>' +
      '<button class="syntax-copy ide-head-copy" type="button" data-copy-syntax="' + SC.esc(e.syntax[0]) + '" title="' + SC.esc(SC.t("copy")) + '">⧉</button>' +
      '</div>' +
      '<div class="ide-acc-syn" data-toggle="' + e.id + '"><code>' + mark(e.syntax[0], ts) + '</code></div>' +
      body +
      '</article>';
  }

  /** Правая панель: «Quick Example & Preview» с нумерацией строк и зелёной кнопкой копирования. */
  function renderPreview() {
    if (!els.preview) return;
    var e = state.selected && byId[state.selected];
    if (!e) {
      els.preview.innerHTML = '<div class="ide-prev-wrap"><h2 class="ide-prev-h">' + SC.esc(SC.t("preview.title")) +
        '</h2><p class="preview-empty">' + SC.esc(SC.t("preview.empty")) + "</p></div>";
      return;
    }
    var fileLabel = e.category === "config" ? "config.toml" : (e.id.replace(/^(event|eff|cond|expr|struct|target|screen|hud|cmd|cfg)-/, "") + ".sc");
    var lang = e.category === "config" ? "toml" : "sc";
    var lines = String(e.example || "").replace(/\r\n/g, "\n").split("\n");
    var nums = lines.map(function (_, i) { return "<span>" + (i + 1) + ":</span>"; }).join("");
    var hl = SC.highlight ? SC.highlight(e.example, lang) : SC.esc(e.example);

    els.preview.innerHTML =
      '<div class="ide-prev-wrap">' +
      '<h2 class="ide-prev-h">' + SC.esc(SC.t("preview.title")) + '</h2>' +
      '<div class="ide-prev-sub">' + SC.esc(SC.tr(e.name)) + '</div>' +
      '<div class="ide-editor-box">' +
      '<div class="ide-editor-tabs"><span class="ide-editor-tab">' + SC.esc(fileLabel) + '</span></div>' +
      '<div class="ide-editor-body"><div class="ide-gutter">' + nums + '</div><pre class="ide-pre">' + hl + '</pre></div>' +
      '</div>' +
      '<button type="button" class="ide-run-btn" id="preview-copy-btn">' + SC.esc(SC.t("preview.copy_btn")) + '</button>' +
      '<div class="ide-output-box" id="preview-output">' + SC.esc(SC.t("preview.output")) + '</div>' +
      '</div>';
  }

  function renderChips() {
    var newCount = 0;
    DATA.entries.forEach(function (e) { if (e.since === DATA.version) newCount++; });
    var anyCat = Object.keys(state.cats).some(function (k) { return state.cats[k]; });
    var chips = (newCount ? '<button type="button" class="chip chip-new' + (state.onlyNew ? " on" : "") + '" data-new="1">★ ' + SC.esc(SC.t("chips.new")) + '<span class="n">' + newCount + "</span></button>" : "");
    DATA.categories.forEach(function (c) {
      if (state.cats[c.id]) {
        chips += '<button type="button" class="chip on" data-cat="' + c.id + '">' + (CAT_ICONS[c.id] || "") + " " + SC.esc(SC.tr(c)) + " ✕</button>";
      }
    });
    if (anyCat || state.q || state.onlyNew) {
      chips += '<button type="button" class="chip clear" data-clear="1">✕ ' + SC.esc(SC.t("chips.all")) + "</button>";
    }
    els.chips.innerHTML = chips;
  }

  function render() {
    var ts = terms();
    var list = visible();
    if (!state.selected || !list.some(function (x) { return x.id === state.selected; })) {
      state.selected = list.length ? list[0].id : null;
      if (state.selected) state.open[state.selected] = true;
    }
    renderChips();
    els.status.textContent = list.length === DATA.entries.length
      ? SC.t("status.all", { n: list.length })
      : SC.t("status.found", { n: list.length, total: DATA.entries.length });

    var html = "", lastCat = null;
    list.forEach(function (e) {
      if (!ts.length && e.category !== lastCat) {
        var c = catById[e.category];
        var n = list.filter(function (x) { return x.category === c.id; }).length;
        html += '<h2 class="cat-title"><span class="cat-ico">' + (CAT_ICONS[c.id] || "") + "</span> " + SC.esc(SC.tr(c)) + "<small>" + n + "</small></h2>";
      }
      lastCat = e.category;
      html += accCard(e, ts);
    });
    els.list.innerHTML = html || '<div class="empty">' + SC.esc(SC.t("empty")) + "</div>";
    renderSide(list, ts);
    if (state.selected) markSide(state.selected);
  }

  // ------------------------------------------------------------ левое дерево категорий
  var SIDE_KEY = "sc-side-collapsed";
  var collapsed = {};
  var searchClosed = {};
  try {
    var saved = JSON.parse(localStorage.getItem(SIDE_KEY));
    if (Array.isArray(saved)) saved.forEach(function (c) { collapsed[c] = true; });
    else DATA.categories.forEach(function (c, idx) { if (idx > 0) collapsed[c.id] = true; });
  } catch (e) {
    DATA.categories.forEach(function (c, idx) { if (idx > 0) collapsed[c.id] = true; });
  }

  function saveCollapsed() {
    try { localStorage.setItem(SIDE_KEY, JSON.stringify(Object.keys(collapsed).filter(function (k) { return collapsed[k]; }))); } catch (e) { /* ignore */ }
  }
  function isOpen(cat) { return terms().length ? !searchClosed[cat] : !collapsed[cat]; }

  function renderSide(list, ts) {
    var groups = [], byCat = {};
    list.forEach(function (e) {
      if (!byCat[e.category]) { byCat[e.category] = []; groups.push(e.category); }
      byCat[e.category].push(e);
    });
    if (!ts.length) groups.sort(function (a, b) { return catById[a].order - catById[b].order; });
    var exBox = EXAMPLES.length
      ? '<div class="side-ex"><div class="side-ex-h">' + SC.esc(SC.t("side.examples")) + '</div>' +
        EXAMPLES.slice().reverse().slice(0, 5).map(function (ex) {
          return '<a class="side-ex-link" href="examples.html#' + SC.esc(ex.id) + '">' + SC.esc(SC.tr(ex.title)) + "</a>";
        }).join("") +
        '<a class="side-ex-all" href="examples.html">' + SC.esc(SC.t("side.all_examples")) + "</a></div>"
      : "";
    els.side.innerHTML =
      '<div class="side-tree">' +
      groups.map(function (cat) {
        var open = isOpen(cat);
        return '<div class="side-group' + (open ? "" : " collapsed") + '" data-cat="' + cat + '">' +
          '<button type="button" class="side-cat" aria-expanded="' + open + '" aria-controls="side-' + cat + '">' +
          '<span class="side-ico">' + (CAT_ICONS[cat] || "") + '</span>' +
          '<span class="side-name">' + SC.esc(SC.tr(catById[cat])) + '</span><span class="side-n">' + byCat[cat].length + "</span></button>" +
          '<div class="side-items" id="side-' + cat + '"><div class="side-inner">' +
          byCat[cat].map(function (e) {
            var active = e.id === state.selected;
            return '<a href="#' + e.id + '" data-id="' + e.id + '"' + (active ? ' class="cur"' : "") + (open ? "" : ' tabindex="-1"') + ">" + SC.esc(SC.tr(e.name)) + "</a>";
          }).join("") +
          "</div></div></div>";
      }).join("") +
      "</div>" + exBox;
  }

  function setOpen(cat, open, remember) {
    var g = els.side.querySelector('.side-group[data-cat="' + cat + '"]');
    if (terms().length) searchClosed[cat] = !open;
    else if (remember !== false) { collapsed[cat] = !open; saveCollapsed(); }
    if (!g) return;
    g.classList.toggle("collapsed", !open);
    g.querySelector(".side-cat").setAttribute("aria-expanded", String(open));
    g.querySelectorAll(".side-inner a").forEach(function (a) { if (open) a.removeAttribute("tabindex"); else a.setAttribute("tabindex", "-1"); });
  }

  function markSide(id) {
    var e = byId[id];
    if (e && !isOpen(e.category)) setOpen(e.category, true);
    els.side.querySelectorAll("a.cur").forEach(function (a) { a.classList.remove("cur"); });
    var link = els.side.querySelector('a[data-id="' + id + '"]');
    if (link) link.classList.add("cur");
  }

  /** Раскрывает карточку на месте без прокрутки страницы наверх. */
  function toggleCardInPlace(id) {
    if (!id || !byId[id]) return;
    var wasOpen = !!state.open[id];
    state.open[id] = !wasOpen;
    state.selected = id;
    var scrollPos = els.center ? els.center.scrollTop : window.scrollY;
    render();
    if (els.center) els.center.scrollTop = scrollPos;
  }

  /** Переход из сайдбара или по #id: раскрывает карточку и плавно прокручивает к ней. */
  function jumpToEntry(id, smooth) {
    if (!id || !byId[id]) return;
    if (!visible().some(function (x) { return x.id === id; })) {
      state.q = ""; state.cats = {}; state.onlyNew = false; els.input.value = "";
    }
    state.selected = id;
    state.open[id] = true;
    render();
    var el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: smooth ? "smooth" : "auto", block: "nearest" });
      el.classList.remove("flash");
      void el.offsetWidth;
      el.classList.add("flash");
    }
  }

  function currentHash() {
    try { return decodeURIComponent(location.hash.slice(1)); } catch (e) { return ""; }
  }

  document.addEventListener("DOMContentLoaded", function () {
    els.input = document.getElementById("q");
    els.chips = document.getElementById("chips");
    els.status = document.getElementById("status");
    els.list = document.getElementById("list");
    els.side = document.getElementById("side");
    els.center = document.getElementById("center");

    var params = new URLSearchParams(location.search);
    if (params.get("q")) { state.q = params.get("q"); els.input.value = state.q; }
    (params.get("cat") || "").split(",").forEach(function (c) { if (catById[c]) state.cats[c] = true; });

    els.input.addEventListener("input", function () { state.q = els.input.value; searchClosed = {}; render(); });
    var navBtn = document.getElementById("nav-toggle");
    var shade = document.getElementById("nav-shade");
    function setNav(open) {
      document.documentElement.classList.toggle("nav-open", open);
      if (navBtn) navBtn.setAttribute("aria-expanded", open ? "true" : "false");
    }
    if (navBtn) navBtn.addEventListener("click", function () { setNav(!document.documentElement.classList.contains("nav-open")); });
    if (shade) shade.addEventListener("click", function () { setNav(false); });
    window.addEventListener("resize", function () { if (window.innerWidth > 900) setNav(false); });

    els.side.addEventListener("click", function (ev) {
      var a = ev.target.closest("a[data-id]");
      if (a) {
        ev.preventDefault();
        var id = a.getAttribute("data-id");
        history.replaceState(null, "", "#" + id);
        jumpToEntry(id, true);
        setNav(false);
        return;
      }
      var head = ev.target.closest(".side-cat");
      if (head) {
        var cat = head.parentNode.getAttribute("data-cat");
        setOpen(cat, head.getAttribute("aria-expanded") !== "true");
      }
    });

    els.list.addEventListener("click", function (ev) {
      var copy = ev.target.closest("button[data-copy-syntax]");
      if (copy) {
        ev.stopPropagation();
        SC.copy(copy.getAttribute("data-copy-syntax")).then(function (ok) {
          var prev = copy.textContent;
          copy.textContent = ok ? "✓" : "!";
          copy.classList.toggle("ok", ok);
          setTimeout(function () { copy.textContent = prev; copy.classList.remove("ok"); }, 1200);
        });
        return;
      }
      var rel = ev.target.closest("a[data-pick]");
      if (rel) {
        ev.preventDefault();
        var rid = rel.getAttribute("data-pick");
        history.replaceState(null, "", "#" + rid);
        jumpToEntry(rid, true);
        return;
      }
      var toggle = ev.target.closest("[data-toggle]");
      if (toggle) {
        var tid = toggle.getAttribute("data-toggle");
        history.replaceState(null, "", "#" + tid);
        toggleCardInPlace(tid);
      }
    });

    if (els.preview) {
      els.preview.addEventListener("click", function (ev) {
        var btn = ev.target.closest("#preview-copy-btn");
        if (!btn) return;
        var e = state.selected && byId[state.selected];
        if (!e) return;
        SC.copy(e.example).then(function (ok) {
          var out = document.getElementById("preview-output");
          btn.textContent = ok ? SC.t("preview.copied_btn") : SC.t("copy.fail");
          if (out && ok) out.textContent = SC.t("preview.copied_btn");
          setTimeout(function () {
            btn.textContent = SC.t("preview.copy_btn");
            if (out) out.textContent = SC.t("preview.output");
          }, 1500);
        });
      });
    }

    els.chips.addEventListener("click", function (ev) {
      var b = ev.target.closest("button");
      if (!b) return;
      if (b.getAttribute("data-clear")) { state.cats = {}; state.q = ""; state.onlyNew = false; els.input.value = ""; }
      else if (b.getAttribute("data-new")) { state.onlyNew = !state.onlyNew; }
      else { var c = b.getAttribute("data-cat"); state.cats[c] = !state.cats[c]; }
      render();
    });

    document.addEventListener("keydown", function (ev) {
      if (ev.key === "Escape" && document.documentElement.classList.contains("nav-open")) { setNav(false); return; }
      if ((ev.key === "/" && document.activeElement !== els.input) || ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === "k")) {
        ev.preventDefault(); els.input.focus(); els.input.select();
      } else if (ev.key === "Escape" && document.activeElement === els.input) {
        els.input.value = ""; state.q = ""; render();
      }
    });

    window.addEventListener("hashchange", function () {
      var h = currentHash();
      if (byId[h]) jumpToEntry(h, true);
    });
    document.addEventListener("sc:lang", render);

    if (byId[currentHash()]) {
      state.selected = currentHash();
      state.open[state.selected] = true;
    }
    render();
    if (byId[currentHash()]) setTimeout(function () { jumpToEntry(currentHash(), false); }, 0);
  });
})();
