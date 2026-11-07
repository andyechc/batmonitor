/**
 * BatMonitor · landing
 *
 * Reads `api/version.json` and fills in the version, changelog and download
 * links. If the fetch fails (for example when opening the HTML via file://, or
 * if the API is not published yet) the defaults below are used, so the page
 * never renders half-empty.
 */
(function () {
  'use strict';

  /* Same values as api/version.json. Keep both in sync. */
  var DEFAULTS = {
    versionName: '1.3',
    versionCode: 4,
    notes: {
      es: 'Nuevo proveedor de publicidad (bicea.org, iframe 728x90) en la app y en la web. Sin cambios funcionales.',
      en: 'New advertising provider (bicea.org, 728x90 iframe) in the app and on the website. No functional changes.'
    },
    uptodownUrl: 'https://github.com/andyechc/batmonitor/releases/latest/download/batmonitor.apk',
    apkUrl: 'https://github.com/andyechc/batmonitor/releases/latest/download/batmonitor.apk'
  };

  var VERSION_URL = './api/version.json';

  /* ---------- Utilities ---------- */

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

  /** Normalizes whatever the API returns, field by field, over the defaults. */
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

  /** Changelog: one bullet per line; a single-line note becomes a paragraph. */
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

  /* ---------- Render ---------- */

  function apply(version, live) {
    setText('[data-version-badge]', 'v' + version.versionName);
    setText('[data-version-name]', version.versionName);
    setText('[data-version-code]', String(version.versionCode));

    renderChangelog(document.querySelector('[data-changelog]'), version.notes.en);

    // The main button and any other CTA always point at the API's APK.
    setHref('[data-apk-link]', version.apkUrl);
    setHref('[data-uptodown-link]', version.uptodownUrl);

    var note = document.querySelector('[data-api-note]');
    if (note) {
      note.textContent = live
        ? 'Read just now from api/version.json.'
        : 'Could not fetch api/version.json: showing the version bundled in this page.';
    }
  }

  /* ---------- Boot ---------- */

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
      // Silent on purpose: the defaults are already rendered.
      apply(normalize(DEFAULTS), false);
    });

  /* ---------- Reveal animation ---------- */

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

  /* ---------- Footer year ---------- */

  setText('[data-current-year]', String(new Date().getFullYear()));
})();
