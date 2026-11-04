/* ScriptCraft Docs — страница «Примеры»: большие скрипты из window.SC_DOCS.examples. */
(function () {
  "use strict";
  var SC = window.SC;
  var EX = window.SC_DOCS.examples;
  var byId = {};
  window.SC_DOCS.syntax.entries.forEach(function (e) { byId[e.id] = e; });

  function render() {
    document.getElementById("ex-toc").innerHTML = EX.map(function (x) {
      return '<a class="chip" href="#' + x.id + '">' + SC.esc(SC.tr(x.title)) + "</a>";
    }).join("");
    document.getElementById("ex-list").innerHTML = EX.map(function (x) {
      var notes = (x.notes && x.notes[SC.lang]) || [];
      var rel = (x.related || []).filter(function (r) { return byId[r]; }).map(function (r) {
        return '<a href="index.html#' + r + '">' + SC.esc(SC.tr(byId[r].name)) + "</a>";
      }).join("");
      return '<section class="ex" id="' + x.id + '">' +
        '<h2>' + SC.esc(SC.tr(x.title)) + ' <a class="perma" href="#' + x.id + '">#</a></h2>' +
        '<div class="desc">' + SC.paras(SC.tr(x.intro)) + "</div>" +
        x.blocks.map(function (b) { return SC.codeBlock(b.code, b.lang, SC.tr(b.title)); }).join("") +
        (notes.length ? '<div class="label">' + SC.esc(SC.t("ex.notes")) + "</div><ul>" +
          notes.map(function (n) { return "<li>" + SC.inline(n) + "</li>"; }).join("") + "</ul>" : "") +
        (rel ? '<div class="related"><span>' + SC.esc(SC.t("ex.related")) + "</span>" + rel + "</div>" : "") +
        "</section>";
    }).join("");
  }

  document.addEventListener("DOMContentLoaded", function () {
    render();
    if (location.hash) {
      var el = document.getElementById(decodeURIComponent(location.hash.slice(1)));
      if (el) setTimeout(function () { el.scrollIntoView(); }, 0);
    }
  });
  document.addEventListener("sc:lang", render);
})();
