/* ScriptCraft Docs — страница «Примеры» в стиле Варианта A (IDE сайдбар + карточки). */
(function () {
  "use strict";
  var SC = window.SC;
  var EX = window.SC_DOCS.examples;
  var byId = {};
  window.SC_DOCS.syntax.entries.forEach(function (e) { byId[e.id] = e; });

  var state = { q: "", active: EX.length ? EX[0].id : null };
  var els = {};

  function norm(s) { return String(s || "").toLowerCase().replace(/ё/g, "е"); }

  function filtered() {
    var q = norm(state.q).trim();
    if (!q) return EX;
    return EX.filter(function (x) {
      var hay = norm([x.id, x.title.ru, x.title.en, x.intro.ru, x.intro.en].join(" ") + " " +
        x.blocks.map(function (b) { return b.code; }).join(" "));
      return hay.indexOf(q) >= 0;
    });
  }

  function renderSide(list) {
    els.side.innerHTML =
      '<div class="side-tree">' +
      '<div class="side-group"><div class="side-cat" style="cursor:default">' +
      '<span class="side-ico">✦</span><span class="side-name">' + SC.esc(SC.t("ex.title")) + '</span>' +
      '<span class="side-n">' + list.length + '</span></div>' +
      '<div class="side-items"><div class="side-inner">' +
      list.map(function (x) {
        var on = x.id === state.active;
        return '<a href="#' + x.id + '" data-ex="' + x.id + '"' + (on ? ' class="cur"' : "") + ">" + SC.esc(SC.tr(x.title)) + "</a>";
      }).join("") +
      "</div></div></div></div>" +
      '<div class="side-ex"><div class="side-ex-h">' + SC.esc(SC.t("nav.docs")) + '</div>' +
      '<a class="side-ex-all" href="index.html">← ' + SC.esc(SC.t("docs.title")) + "</a></div>";
  }

  function render() {
    var list = filtered();
    if (!list.some(function (x) { return x.id === state.active; })) {
      state.active = list.length ? list[0].id : null;
    }
    renderSide(list);
    if (!list.length) {
      els.list.innerHTML = '<div class="empty">' + SC.esc(SC.t("empty")) + "</div>";
      return;
    }
    els.list.innerHTML = list.map(function (x) {
      var notes = (x.notes && x.notes[SC.lang]) || [];
      var rel = (x.related || []).filter(function (r) { return byId[r]; }).map(function (r) {
        return '<a href="index.html#' + r + '">' + SC.esc(SC.tr(byId[r].name)) + "</a>";
      }).join("");
      return '<section class="ex' + (x.id === state.active ? " on" : "") + '" id="' + x.id + '">' +
        '<h2>' + SC.esc(SC.tr(x.title)) + ' <a class="perma" href="#' + x.id + '">#</a></h2>' +
        '<div class="desc">' + SC.paras(SC.tr(x.intro)) + "</div>" +
        x.blocks.map(function (b) { return SC.codeBlock(b.code, b.lang, SC.tr(b.title)); }).join("") +
        (notes.length ? '<div class="label">' + SC.esc(SC.t("ex.notes")) + "</div><ul>" +
          notes.map(function (n) { return "<li>" + SC.inline(n) + "</li>"; }).join("") + "</ul>" : "") +
        (rel ? '<div class="related"><span>' + SC.esc(SC.t("ex.related")) + "</span>" + rel + "</div>" : "") +
        "</section>";
    }).join("");
  }

  function jump(id, smooth) {
    if (!id) return;
    state.active = id;
    renderSide(filtered());
    var el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: smooth ? "smooth" : "auto", block: "start" });
      el.classList.remove("flash");
      void el.offsetWidth;
      el.classList.add("flash");
    }
  }

  document.addEventListener("DOMContentLoaded", function () {
    els.side = document.getElementById("ex-side");
    els.list = document.getElementById("ex-list");
    els.input = document.getElementById("ex-q");

    var navBtn = document.getElementById("nav-toggle");
    var shade = document.getElementById("nav-shade");
    function setNav(open) {
      document.documentElement.classList.toggle("nav-open", open);
      if (navBtn) navBtn.setAttribute("aria-expanded", open ? "true" : "false");
    }
    if (navBtn) navBtn.addEventListener("click", function () { setNav(!document.documentElement.classList.contains("nav-open")); });
    if (shade) shade.addEventListener("click", function () { setNav(false); });

    if (els.input) {
      els.input.addEventListener("input", function () { state.q = els.input.value; render(); });
    }
    els.side.addEventListener("click", function (ev) {
      var a = ev.target.closest("a[data-ex]");
      if (!a) return;
      ev.preventDefault();
      var id = a.getAttribute("data-ex");
      history.replaceState(null, "", "#" + id);
      jump(id, true);
      setNav(false);
    });
    document.addEventListener("keydown", function (ev) {
      if (ev.key === "Escape" && document.documentElement.classList.contains("nav-open")) { setNav(false); return; }
      if (((ev.key === "/" && document.activeElement !== els.input) || ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === "k")) && els.input) {
        ev.preventDefault(); els.input.focus(); els.input.select();
      } else if (ev.key === "Escape" && document.activeElement === els.input) {
        els.input.value = ""; state.q = ""; render();
      }
    });

    if (location.hash) state.active = decodeURIComponent(location.hash.slice(1));
    render();
    if (location.hash) setTimeout(function () { jump(state.active, false); }, 0);
  });
  document.addEventListener("sc:lang", render);
})();
