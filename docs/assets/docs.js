/*
 * ScriptCraft Docs — страница синтаксиса: поиск, фильтр по категориям, карточки, постоянные ссылки (#id).
 * Данные: window.SC_DOCS.syntax из data/syntax.js.
 */
(function () {
  "use strict";
  var SC = window.SC;
  var DATA = window.SC_DOCS.syntax;
  var byId = {};
  var catById = {};
  DATA.categories.forEach(function (c, i) { c.order = i; catById[c.id] = c; });
  DATA.entries.forEach(function (e, i) { e.order = i; byId[e.id] = e; });

  var state = { q: "", cats: {} };
  var els = {};

  function norm(s) { return String(s).toLowerCase().replace(/ё/g, "е"); }

  // Поисковые поля строятся сразу для обоих языков: русский запрос находит запись и в EN-интерфейсе.
  DATA.entries.forEach(function (e) {
    e.h = {
      name: norm([e.id, e.name.ru, e.name.en].join(" ")),
      syntax: norm(e.syntax.join(" ")),
      desc: norm([e.desc.ru, e.desc.en].join(" ")),
      example: norm(e.example)
    };
  });

  /** Буквы запроса идут по порядку и кучно (опечатки вроде «opn scren»). */
  function subseq(needle, hay) {
    var limit = needle.length * 2;
    for (var start = hay.indexOf(needle[0]); start >= 0; start = hay.indexOf(needle[0], start + 1)) {
      var i = 1;
      for (var j = start + 1; j < hay.length && j - start < limit && i < needle.length; j++) if (hay[j] === needle[i]) i++;
      if (i === needle.length) return true;
    }
    return false;
  }

  /** Очки совпадения: каждое слово запроса должно где-то найтись; 0 — не подходит. */
  function score(e, terms) {
    var total = 0;
    for (var i = 0; i < terms.length; i++) {
      var t = terms[i], s = 0;
      if (e.h.name.indexOf(t) >= 0) s = e.h.name.indexOf(t) === 0 || e.h.name.indexOf(" " + t) >= 0 ? 12 : 10;
      else if (e.h.syntax.indexOf(t) >= 0) s = 6;
      else if (e.h.desc.indexOf(t) >= 0) s = 2;
      else if (e.h.example.indexOf(t) >= 0) s = 1;
      else if (t.length >= 3 && subseq(t, e.h.name)) s = 0.5; // «нечёткий» поиск
      if (!s) return 0;
      total += s;
    }
    return total;
  }

  function terms() {
    return norm(state.q).split(/\s+/).filter(Boolean);
  }

  /** Экранирует текст и выделяет совпадения <mark>. */
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
      var s = ts.length ? score(e, ts) : 1;
      if (s > 0) list.push({ e: e, s: s });
    });
    list.sort(function (a, b) {
      if (ts.length && b.s !== a.s) return b.s - a.s;
      return catById[a.e.category].order - catById[b.e.category].order || a.e.order - b.e.order;
    });
    return list.map(function (x) { return x.e; });
  }

  function card(e, ts) {
    var cat = catById[e.category];
    var related = (e.related || []).filter(function (r) { return byId[r]; }).map(function (r) {
      return '<a href="#' + r + '">' + SC.esc(SC.tr(byId[r].name)) + "</a>";
    }).join("");
    return '<article class="card" id="' + e.id + '">' +
      '<div class="card-h"><h3>' + mark(SC.tr(e.name), ts) + "</h3>" +
      '<span class="badge c-' + e.category + '">' + SC.esc(SC.tr(cat)) + "</span>" +
      '<span class="since">' + SC.esc(SC.t("card.since", { v: e.since })) + "</span>" +
      '<a class="perma" href="#' + e.id + '" title="' + SC.esc(SC.t("card.link")) + '">#</a></div>' +
      '<div class="syntax">' + e.syntax.map(function (s) { return "<code>" + mark(s, ts) + "</code>"; }).join("") + "</div>" +
      '<div class="desc">' + SC.paras(SC.tr(e.desc)) + "</div>" +
      SC.codeBlock(e.example, e.category === "config" ? "toml" : "sc", SC.t("card.example") + (e.category === "config" ? " · scriptcraft/config.toml" : "")) +
      (related ? '<div class="related"><span>' + SC.esc(SC.t("card.related")) + "</span>" + related + "</div>" : "") +
      "</article>";
  }

  function renderChips() {
    var counts = {};
    DATA.entries.forEach(function (e) { counts[e.category] = (counts[e.category] || 0) + 1; });
    var anyCat = Object.keys(state.cats).some(function (k) { return state.cats[k]; });
    els.chips.innerHTML = DATA.categories.map(function (c) {
      return '<button type="button" class="chip' + (state.cats[c.id] ? " on" : "") + '" data-cat="' + c.id + '" aria-pressed="' + !!state.cats[c.id] + '">' +
        SC.esc(SC.tr(c)) + '<span class="n">' + (counts[c.id] || 0) + "</span></button>";
    }).join("") + (anyCat || state.q ? '<button type="button" class="chip clear" data-clear="1">✕ ' + SC.esc(SC.t("chips.all")) + "</button>" : "");
  }

  function render() {
    var ts = terms();
    var list = visible();
    renderChips();
    els.status.textContent = list.length === DATA.entries.length
      ? SC.t("status.all", { n: list.length })
      : SC.t("status.found", { n: list.length, total: DATA.entries.length });

    var html = "", lastCat = null;
    list.forEach(function (e) {
      if (!ts.length && e.category !== lastCat) {
        var c = catById[e.category];
        var n = list.filter(function (x) { return x.category === c.id; }).length;
        html += '<h2 class="cat-title">' + SC.esc(SC.tr(c)) + "<small>" + n + "</small></h2>";
      }
      lastCat = e.category;
      html += card(e, ts);
    });
    els.list.innerHTML = html || '<div class="empty">' + SC.esc(SC.t("empty")) + "</div>";
    renderSide(list, ts);
    SC.measure();
  }

  // ------------------------------------------------------------ сайдбар со сворачиваемыми категориями
  var SIDE_KEY = "sc-side-collapsed";
  var collapsed = {};      // сохранённое состояние (localStorage)
  var searchClosed = {};   // при поиске группы открыты; здесь — те, что свернули вручную во время поиска
  try { (JSON.parse(localStorage.getItem(SIDE_KEY)) || []).forEach(function (c) { collapsed[c] = true; }); } catch (e) { /* нет localStorage */ }

  function saveCollapsed() {
    try { localStorage.setItem(SIDE_KEY, JSON.stringify(Object.keys(collapsed).filter(function (k) { return collapsed[k]; }))); } catch (e) { /* ignore */ }
  }
  function isOpen(cat) { return terms().length ? !searchClosed[cat] : !collapsed[cat]; }

  var CHEVRON = '<svg class="chev" viewBox="0 0 16 16" aria-hidden="true"><path d="M4.5 6l3.5 3.5L11.5 6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>';

  /** Группы по категориям; при поиске порядок групп — по самой релевантной записи. */
  function renderSide(list, ts) {
    var groups = [], byCat = {};
    list.forEach(function (e) {
      if (!byCat[e.category]) { byCat[e.category] = []; groups.push(e.category); }
      byCat[e.category].push(e);
    });
    if (!ts.length) groups.sort(function (a, b) { return catById[a].order - catById[b].order; });
    var tools = groups.length > 1
      ? '<div class="side-tools"><span>' + SC.esc(SC.t("side.sections")) + "</span>" +
        '<button type="button" class="side-all" data-side="expand" title="' + SC.esc(SC.t("side.expand")) + '" aria-label="' + SC.esc(SC.t("side.expand")) + '">' +
        '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M4.5 6.5L8 3l3.5 3.5M4.5 9.5L8 13l3.5-3.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg></button>' +
        '<button type="button" class="side-all" data-side="collapse" title="' + SC.esc(SC.t("side.collapse")) + '" aria-label="' + SC.esc(SC.t("side.collapse")) + '">' +
        '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M4.5 3L8 6.5 11.5 3M4.5 13L8 9.5l3.5 3.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg></button></div>'
      : "";
    els.side.innerHTML = tools + groups.map(function (cat) {
      var open = isOpen(cat);
      return '<div class="side-group' + (open ? "" : " collapsed") + '" data-cat="' + cat + '">' +
        '<button type="button" class="side-cat" aria-expanded="' + open + '" aria-controls="side-' + cat + '">' + CHEVRON +
        '<span class="side-name">' + SC.esc(SC.tr(catById[cat])) + '</span><span class="side-n">' + byCat[cat].length + "</span></button>" +
        '<div class="side-items" id="side-' + cat + '"><div class="side-inner">' +
        byCat[cat].map(function (e) { return '<a href="#' + e.id + '" data-id="' + e.id + '"' + (open ? "" : ' tabindex="-1"') + ">" + SC.esc(SC.tr(e.name)) + "</a>"; }).join("") +
        "</div></div></div>";
    }).join("");
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

  /** Подсветить пункт сайдбара и прокрутить к нему саму панель (не страницу). */
  function markSide(id) {
    var e = byId[id];
    if (e && !isOpen(e.category)) setOpen(e.category, true);
    els.side.querySelectorAll("a.cur").forEach(function (a) { a.classList.remove("cur"); });
    var link = els.side.querySelector('a[data-id="' + id + '"]');
    if (!link) return;
    link.classList.add("cur");
    setTimeout(function () { // после анимации раскрытия
      var top = link.offsetTop, h = els.side.clientHeight; // .side — sticky, он и есть offsetParent
      if (top < els.side.scrollTop || top > els.side.scrollTop + h - 30) els.side.scrollTo({ top: Math.max(0, top - h / 3), behavior: "smooth" });
    }, 260);
  }

  /** Переход по постоянной ссылке: если запись скрыта фильтром — сбрасываем фильтры. */
  function goTo(id, smooth) {
    var e = byId[id];
    if (!e) return;
    if (!document.getElementById(id)) {
      state.q = ""; state.cats = {}; els.input.value = "";
      render();
    }
    var el = document.getElementById(id);
    el.scrollIntoView({ behavior: smooth ? "smooth" : "auto", block: "start" });
    el.classList.remove("flash");
    void el.offsetWidth;
    el.classList.add("flash");
    markSide(id);
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

    // ?q=... и ?cat=effects,targets в адресе — для ссылок на подборки.
    var params = new URLSearchParams(location.search);
    if (params.get("q")) { state.q = params.get("q"); els.input.value = state.q; }
    (params.get("cat") || "").split(",").forEach(function (c) { if (catById[c]) state.cats[c] = true; });

    els.input.addEventListener("input", function () { state.q = els.input.value; searchClosed = {}; render(); window.scrollTo(0, 0); });
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
      if (ev.target.closest("a")) setNav(false);
      var head = ev.target.closest(".side-cat");
      if (head) {
        var cat = head.parentNode.getAttribute("data-cat");
        setOpen(cat, head.getAttribute("aria-expanded") !== "true");
        return;
      }
      var all = ev.target.closest(".side-all");
      if (all) {
        var open = all.getAttribute("data-side") === "expand";
        els.side.querySelectorAll(".side-group").forEach(function (g) { setOpen(g.getAttribute("data-cat"), open); });
      }
    });
    els.chips.addEventListener("click", function (ev) {
      var b = ev.target.closest("button");
      if (!b) return;
      if (b.getAttribute("data-clear")) { state.cats = {}; state.q = ""; els.input.value = ""; }
      else { var c = b.getAttribute("data-cat"); state.cats[c] = !state.cats[c]; }
      render();
      window.scrollTo(0, 0);
    });
    document.addEventListener("keydown", function (ev) {
      if (ev.key === "Escape" && document.documentElement.classList.contains("nav-open")) { setNav(false); return; }
      if (ev.key === "/" && document.activeElement !== els.input) { ev.preventDefault(); els.input.focus(); els.input.select(); }
      else if (ev.key === "Escape" && document.activeElement === els.input) { els.input.value = ""; state.q = ""; render(); }
    });
    // Клик по ссылке на текущий же #id не вызывает hashchange — обрабатываем сами.
    document.addEventListener("click", function (ev) {
      var a = ev.target.closest && ev.target.closest('a[href^="#"]');
      if (!a) return;
      var id = a.getAttribute("href").slice(1);
      if (byId[id] && id === currentHash()) { ev.preventDefault(); goTo(id, true); }
    });
    window.addEventListener("hashchange", function () { goTo(currentHash(), true); });
    document.addEventListener("sc:lang", render);

    render();
    if (byId[currentHash()]) setTimeout(function () { goTo(currentHash(), false); }, 0);
  });
})();
