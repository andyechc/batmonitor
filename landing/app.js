/**
 * BatMonitor · landing
 *
 * Lee `api/version.json` y rellena la versión, el changelog y los enlaces de
 * descarga. Si el fetch falla (por ejemplo al abrir el HTML con file://, o si
 * la API aún no está publicada) se usan los mismos valores por defecto escritos
 * aquí abajo, así que la página nunca se queda a medias.
 */
(function () {
  'use strict';

  /* Mismos valores que api/version.json. Mantener ambos sincronizados. */
  var DEFAULTS = {
    versionName: '1.0',
    versionCode: 1,
    notes: {
      es: 'Versión inicial: lectura BLE, 24 celdas, temas claro y oscuro.',
      en: 'Initial release: BLE reading, 24 cells, light and dark themes.'
    },
    uptodownUrl: 'https://batmonitor.uptodown.com/android',
    apkUrl: 'https://github.com/andyechc/batmonitor/releases/latest/download/batmonitor.apk'
  };

  var VERSION_URL = './api/version.json';

  /* ---------- Utilidades ---------- */

  function str(value, fallback) {
    return typeof value === 'string' && value.trim() !== '' ? value.trim() : fallback;
  }

  function httpUrl(value, fallback) {
    var url = str(value, fallback);
    return /^https?:\/\//i.test(url) ? url : fallback;
  }

  function int(value, fallback) {
    var n = Number(value);
    return Number.isFinite(n) && n >= 0 ? Math.trunc(n) : fallback;
  }

  /** Normaliza lo que devuelve la API, campo a campo, sobre los valores por defecto. */
  function normalize(raw) {
    var data = raw && typeof raw === 'object' ? raw : {};
    var notes = data.notes && typeof data.notes === 'object' ? data.notes : {};

    return {
      versionName: str(data.versionName, DEFAULTS.versionName),
      versionCode: int(data.versionCode, DEFAULTS.versionCode),
      notes: {
        es: str(notes.es, DEFAULTS.notes.es),
        en: str(notes.en, DEFAULTS.notes.en)
      },
      uptodownUrl: httpUrl(data.uptodownUrl, DEFAULTS.uptodownUrl),
      apkUrl: httpUrl(data.apkUrl, DEFAULTS.apkUrl)
    };
  }

  function setText(selector, text) {
    document.querySelectorAll(selector).forEach(function (node) {
      node.textContent = text;
    });
  }

  function setHref(selector, href) {
    document.querySelectorAll(selector).forEach(function (node) {
      node.setAttribute('href', href);
    });
  }

  /** Changelog: una viñeta por línea; si nota es de una sola línea, un párrafo. */
  function renderChangelog(container, text) {
    if (!container) return;

    var lines = String(text)
      .split(/\r?\n/)
      .map(function (line) {
        return line.replace(/^[-•*]\s*/, '').trim();
      })
      .filter(Boolean);

    container.textContent = '';
    if (!lines.length) return;

    if (lines.length === 1) {
      var p = document.createElement('p');
      p.textContent = lines[0];
      container.appendChild(p);
      return;
    }

    var list = document.createElement('ul');
    lines.forEach(function (line) {
      var item = document.createElement('li');
      item.textContent = line;
      list.appendChild(item);
    });
    container.appendChild(list);
  }

  /* ---------- Pintar ---------- */

  function apply(version, live) {
    setText('[data-version-badge]', 'v' + version.versionName);
    setText('[data-version-name]', version.versionName);
    setText('[data-version-code]', String(version.versionCode));

    renderChangelog(document.querySelector('[data-changelog]'), version.notes.es);

    // El botón principal y cualquier otro CTA apuntan siempre al APK de la API.
    setHref('[data-apk-link]', version.apkUrl);
    setHref('[data-uptodown-link]', version.uptodownUrl);

    var note = document.querySelector('[data-api-note]');
    if (note) {
      note.textContent = live
        ? 'Leído ahora desde api/version.json.'
        : 'No se pudo consultar api/version.json: se muestra la versión incluida en esta página.';
    }
  }

  /* ---------- Arranque ---------- */

  apply(normalize(DEFAULTS), false);

  fetch(VERSION_URL, { cache: 'no-cache', headers: { Accept: 'application/json' } })
    .then(function (response) {
      if (!response.ok) throw new Error('HTTP ' + response.status);
      return response.json();
    })
    .then(function (data) {
      apply(normalize(data), true);
    })
    .catch(function () {
      // Silencioso a propósito: los valores por defecto ya están pintados.
      apply(normalize(DEFAULTS), false);
    });

  /* ---------- Animación de aparición ---------- */

  var revealables = document.querySelectorAll('[data-reveal]');

  if (!('IntersectionObserver' in window)) {
    revealables.forEach(function (el) { el.classList.add('is-visible'); });
  } else {
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target);
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.08 });

    revealables.forEach(function (el) { observer.observe(el); });
  }

  /* ---------- Año del pie ---------- */

  setText('[data-current-year]', String(new Date().getFullYear()));
})();
