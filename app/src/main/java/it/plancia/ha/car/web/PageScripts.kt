package it.plancia.ha.car.web

/** JavaScript che Plancia aggiunge al frontend di Home Assistant. */
internal object PageScripts {

    /**
     * Gira prima degli script della pagina, o appena caricata dove la WebView non lo permette.
     * - La barra laterale di Home Assistant resta sempre nascosta: le plance si scelgono dal menu di Plancia.
     * - Il pulsante ☰ di Home Assistant apre il menu di Plancia invece della barra laterale.
     * - Lo scorrimento chiesto dall'auto scorre l'elemento giusto, anche dentro i dialoghi.
     */
    const val SETUP = """
(function() {
  if (window.__plancia) return;
  window.__plancia = true;
  try { localStorage.setItem('dockedSidebar', JSON.stringify('always_hidden')); } catch (e) {}

  window.addEventListener('hass-toggle-menu', function(ev) {
    ev.stopImmediatePropagation();
    if (window.plancia) window.plancia.openMenu();
  }, true);

  function composedParent(el) {
    if (el.assignedSlot) return el.assignedSlot;
    if (el.parentElement) return el.parentElement;
    var root = el.getRootNode ? el.getRootNode() : null;
    return root && root.host ? root.host : null;
  }

  function deepest(x, y) {
    var el = document.elementFromPoint(x, y);
    while (el && el.shadowRoot) {
      var inner = el.shadowRoot.elementFromPoint(x, y);
      if (!inner || inner === el) break;
      el = inner;
    }
    return el;
  }

  function canScroll(el, vertical, delta) {
    var style = getComputedStyle(el);
    var overflow = vertical ? style.overflowY : style.overflowX;
    if (overflow !== 'auto' && overflow !== 'scroll' && overflow !== 'overlay') return false;
    if (vertical) return delta > 0 ? el.scrollTop + el.clientHeight < el.scrollHeight - 1 : el.scrollTop > 0;
    return delta > 0 ? el.scrollLeft + el.clientWidth < el.scrollWidth - 1 : el.scrollLeft > 0;
  }

  window.__planciaScroll = function(dx, dy, x, y, smooth) {
    var vertical = Math.abs(dy) >= Math.abs(dx);
    var delta = vertical ? dy : dx;
    var options = { left: vertical ? 0 : dx, top: vertical ? dy : 0, behavior: smooth ? 'smooth' : 'auto' };
    for (var el = deepest(x, y); el && el.nodeType === 1; el = composedParent(el)) {
      if (el === document.documentElement || el === document.body) break;
      if (canScroll(el, vertical, delta)) { el.scrollBy(options); return; }
    }
    (document.scrollingElement || document.documentElement).scrollBy(options);
  };
})();
"""

    /**
     * Se la pagina è partita prima che SETUP potesse nascondere la barra laterale,
     * la chiude dall'interno, con lo stesso evento che usa Home Assistant.
     */
    const val HIDE_SIDEBAR = """
(function() {
  var tries = 0;
  var timer = setInterval(function() {
    if (++tries > 40) { clearInterval(timer); return; }
    var app = document.querySelector('home-assistant');
    if (!app || !app.hass) return;
    clearInterval(timer);
    if (app.hass.dockedSidebar === 'always_hidden') return;
    var ev = new Event('hass-dock-sidebar', { bubbles: true, composed: true });
    ev.detail = { dock: 'always_hidden' };
    app.dispatchEvent(ev);
  }, 500);
})();
"""
}
