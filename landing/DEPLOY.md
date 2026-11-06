# Publicar la landing de BatMonitor

La carpeta `landing/` es un sitio estático completo: **no hay build step**, no hay dependencias y no hay `package.json`. Se sube tal cual a cualquier hosting estático.

> **Estado actual: en vivo en https://andyechc.is-a.dev/batmonitor/** mediante el workflow `.github/workflows/pages.yml` (despliega `landing/` en cada push a `main`). La app Android consulta `https://andyechc.is-a.dev/batmonitor/api/version.json` (ver `UpdateCheck.kt`).

```
landing/
├── index.html            # página única
├── styles.css            # estilos (tema oscuro de la app, acento #2EE6A6)
├── app.js                # lee api/version.json y rellena versión, changelog y enlaces
├── api/version.json      # API de versión (contrato fijo, ver abajo)
└── assets/               # capturas reales del emulador (5 PNG)
```

---

## 1. Antes de publicar: 3 cosas que hay que sustituir

### 1.1 `uptodownUrl` es provisional

En `api/version.json` el campo `uptodownUrl` apunta a `https://batmonitor.uptodown.com/android`, que **todavía no es un listado real**. Cuando el APK esté publicado en Uptodown, cambia ese valor por la URL definitiva de la ficha:

```json
"uptodownUrl": "https://batmonitor.uptodown.com/android"
```

Es el único sitio donde está la URL: `app.js` la lee de la API (o de su bloque `DEFAULTS`) y la aplica a todos los enlaces con `data-uptodown-link`. No hay URLs de Uptodown escritas a mano en el HTML.

> Repite el cambio en el bloque `DEFAULTS` de `app.js` para que el fallback sin fetch siga siendo correcto.

### 1.2 El APK debe llamarse `batmonitor.apk` en el Release

`apkUrl` usa la forma corta de GitHub:

```
https://github.com/andyechc/batmonitor/releases/latest/download/batmonitor.apk
```

Esa URL **solo funciona si existe un release publicado** (no borrador, no pre-release) con un asset cuyo nombre sea **exactamente** `batmonitor.apk`. Si el asset se llama `app-release.apk`, `BatMonitor-1.0.apk` o `batmonitor-v1.0.apk`, `latest/download` devuelve 404 y el botón de descarga se rompe.

Al crear el release:

```bash
# renombra el APK antes de adjuntarlo
cp android/app/build/outputs/apk/release/app-release.apk batmonitor.apk
gh release create v1.0 batmonitor.apk \
  --title "BatMonitor 1.0" \
  --notes "Versión inicial: lectura BLE, 24 celdas, temas claro y oscuro."
```

Alternativa estable si algún día quieres fijar una versión concreta: `.../releases/download/v1.0/batmonitor.apk`. Entonces cambia `apkUrl` en la API (y en `DEFAULTS`) cada vez que publiques.

### 1.3 Sube la versión en los tres sitios

Al publicar una versión nueva hay que tocar:

| Sitio | Campo |
| --- | --- |
| `android/app/build.gradle.kts` | `versionCode` (entero, siempre sube) y `versionName` |
| `landing/api/version.json` | `versionName`, `versionCode`, `notes.es` (y `notes.en`) |
| `landing/app.js` → `DEFAULTS` | los mismos valores, como fallback |

---

## 2. Contrato de `api/version.json`

```json
{
  "versionName": "1.0",
  "versionCode": 1,
  "notes": {
    "es": "Versión inicial: lectura BLE, 24 celdas, temas claro y oscuro.",
    "en": "Initial release: BLE reading, 24 cells, light and dark themes."
  },
  "uptodownUrl": "https://batmonitor.uptodown.com/android",
  "apkUrl": "https://github.com/andyechc/batmonitor/releases/latest/download/batmonitor.apk"
}
```

`app.js` rellena con estos datos:

- el badge de versión (`v1.0`) del hero, de la cabecera de actualizaciones y del cierre;
- `versionName` / `versionCode` de la sección **Actualizaciones**;
- el changelog, usando **`notes.es`** (las líneas separadas por `\n` se convierten en viñetas; una sola línea se muestra como párrafo);
- el `href` de todos los botones **Descargar APK** (`apkUrl`);
- el `href` de todos los enlaces a **Uptodown** (`uptodownUrl`).

Detalles de robustez:

- Cada campo se valida por separado. Si la API trae un campo vacío, del tipo equivocado o una URL que no sea `http(s)`, se conserva el valor de `DEFAULTS`.
- Si el fetch falla (abrir el HTML con `file://`, API caída, bloqueo de red), la página se queda con los valores por defecto ya pintados en el HTML; el aviso de la sección Actualizaciones lo indica.
- El contenido de la API se inserta con `textContent`, nunca con `innerHTML`.

`notes.en` está en el contrato pero la landing solo muestra el español, porque el sitio es en español.

---

## 3. Probar en local (obligatorio antes de publicar)

`fetch('./api/version.json')` no funciona con `file://`, así que hay que servirlo por HTTP:

```bash
python3 -m http.server 8000 --directory landing
# abre http://localhost:8000
```

Comprobación rápida de que la API responde:

```bash
curl -i http://localhost:8000/api/version.json   # 200 + Content-Type: application/json
```

Si al abrir el `index.html` directamente con doble clic ves `v1.0` y el texto por defecto, es el comportamiento esperado del fallback.

---

## 4. Publicar en GitHub Pages

Todas las rutas de la landing son **relativas** (`./`, `assets/…`, `./api/version.json`), así que funciona igual en la raíz de un dominio que en un subdirectorio (`/landing/`).

### Opción A — servir `landing/` desde `main` con `/docs`

1. Mueve o copia la carpeta: `git mv landing docs` (o mantén `landing/` y usa la opción B).
2. GitHub → **Settings → Pages** → *Build and deployment* → **Deploy from a branch** → rama `main`, carpeta `/docs` → **Save**.
3. En 1–2 minutos queda en `https://andyechc.is-a.dev/batmonitor/` (el dominio personalizado ya está configurado en el repositorio).

### Opción B — publicar `landing/` con GitHub Actions (recomendada si quieres mantener la carpeta)

`.github/workflows/pages.yml`:

```yaml
name: Deploy landing
on:
  push:
    branches: [main]
    paths: ['landing/**']
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: true

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/configure-pages@v5
      - uses: actions/upload-pages-artifact@v3
        with:
          path: landing
      - id: deployment
        uses: actions/deploy-pages@v4
```

Luego GitHub → **Settings → Pages** → *Source*: **GitHub Actions**.

### Opción C — rama `gh-pages` solo con el contenido

```bash
git subtree push --prefix landing origin gh-pages
```

O con `gh-pages`:

```bash
npx gh-pages -d landing
```

Y en **Settings → Pages**: rama `gh-pages`, carpeta `/ (root)`.

---

## 5. Publicar en Netlify

### Arrastrar y soltar

1. Entra en <https://app.netlify.com/drop>.
2. Arrastra la carpeta `landing/` completa (no el repo).
3. Listo: obtienes una URL `*.netlify.app`. Renómbrala en **Site configuration → Site details → Change site name**.

### Conectado al repo (deploy automático)

New site → *Import an existing project* → elige el repo de GitHub y configura:

| Ajuste | Valor |
| --- | --- |
| Base directory | *(vacío)* |
| Build command | *(vacío)* |
| Publish directory | `landing` |

Netlify no necesita build. Si quieres fijarlo en el repo, un `netlify.toml` en la raíz sirve:

```toml
[build]
  publish = "landing"
```

---

## 6. Publicar en Vercel

### CLI, sin repo

```bash
cd landing
npx vercel --prod
```

Al preguntar por el framework, elige **Other**; build command y output directory vacíos.

### Conectado al repo

1. <https://vercel.com/new> → importa el repo.
2. **Root Directory** → `landing`.
3. **Framework Preset** → *Other*. Build command y Output directory vacíos.
4. Deploy. Cada push a `main` republica.

---

## 7. Cualquier otro hosting estático

Sirve para Cloudflare Pages, Firebase Hosting, S3 + CloudFront, un VPS con nginx, un Synology… El requisito es solo servir ficheros:

- Sube el **contenido** de `landing/` (que `index.html` quede en la raíz del sitio o del subdirectorio elegido).
- Mantén la estructura `api/version.json` junto al `index.html`: la ruta es relativa a la página.
- El servidor debe devolver `application/json` para `.json` (nginx: `include mime.types;`, que lo trae; Apache: `mime_module`). Si lo sirve como `text/plain`, el `fetch` con `response.json()` fallará y la página usará el fallback.
- No hace falta reescritura de rutas ni SPA fallback: es una sola página sin router.

Cloudflare Pages, por ejemplo: *Workers & Pages → Create → Pages → Connect to Git*, y como *Build output directory* `landing`, sin build command.

---

## 8. Dominio propio

Necesitas el dominio (o subdominio) apuntando al hosting y HTTPS activado en el panel.

**Raíz del dominio** (`batmonitor.app`) — usa el registro A que indique el hosting:

| Tipo | Nombre | Valor |
| --- | --- | --- |
| A | `@` | IP del hosting (p. ej. `185.199.108.153` en GitHub Pages) |
| A | `@` | segunda IP si el hosting da varias |

**Subdominio** (`www`, `app`, …) — un CNAME:

| Tipo | Nombre | Valor |
| --- | --- | --- |
| CNAME | `www` | `andyechc.github.io` / `tu-sitio.netlify.app` / `cname.vercel-dns.com` |

Por hosting:

- **GitHub Pages**: escribe el dominio en **Settings → Pages → Custom domain** (crea el `CNAME` en la rama publicada) y marca **Enforce HTTPS**. Añade un `CNAME` con el dominio dentro de la carpeta publicada si prefieres versionarlo.
- **Netlify**: *Domain management → Add a domain*, sigue las instrucciones DNS y deja el certificado Let's Encrypt automático. Configura el dominio primario y redirige el otro.
- **Vercel**: *Settings → Domains → Add*, con los registros que indique. Los certificados se emiten solos.
- **Cloudflare Pages**: *Custom domains → Set up*, con el dominio en la misma cuenta de Cloudflare es un CNAME interno.

Tras cambiar el DNS, espera la propagación (de minutos a unas horas) y comprueba que `https://tu-dominio/` y `https://tu-dominio/api/version.json` responden 200.

Recuerda actualizar `canonical` y `og:image` en `index.html` cuando cambies de dominio (`og:image` es relativa; algunos clientes de redes sociales prefieren una URL absoluta).

---

## 9. Flujo completo de una release

```
1. Android      versionCode/versionName en android/app/build.gradle.kts
                → build de release firmado
2. GitHub       gh release create vX.Y batmonitor.apk   (asset con nombre EXACTO)
3. Landing      subir versionName/versionCode/notes.es en api/version.json + DEFAULTS de app.js
                → commit y push: el hosting estático publica solo
4. Uptodown     subir el mismo APK y crear/actualizar la ficha
                → copiar la URL de la ficha en uptodownUrl (API + DEFAULTS)
5. Verificar    el botón Descargar APK baja el asset del release;
                el badge muestra la versión nueva y el changelog coincide
```

Orden de dependencias:

- **Uptodown es el canal de descubrimiento**, no la fuente del binario. En su ficha, el enlace de descarga debe apuntar al APK del release de GitHub (o a la landing) para no duplicar binarios. Mientras la ficha no exista, el enlace de la landing da 404: es lo esperado y está documentado arriba.
- **La landing es la página de aterrizaje**: describe la app, muestra capturas y descarga el APK del Release.
- **`apkUrl` del Release de GitHub es la única fuente del binario.** No alojes copias del APK en la carpeta `landing/` (rompería el límite de tamaño de Pages y descentralizaría las actualizaciones).

### Caché después de publicar

- `app.js` pide el JSON con `cache: 'no-cache'`, así que el navegador revalida en cada visita.
- Aun así, GitHub Pages y los CDN de Netlify/Vercel pueden cachear unos minutos (GitHub Pages: ~10 min). Si acabas de publicar una versión y ves la anterior, espera o fuerza recarga (`Cmd/Ctrl + Shift + R`). Para evitarlo en pruebas, abre `…/api/version.json?v=2`.
- No añadas `?v=` fijo en `app.js`: dejaría de actualizarse.

---

## 10. Checklist final

- [ ] `python3 -m http.server` sirve `/` y `/api/version.json` con 200.
- [ ] El release tiene el asset `batmonitor.apk` (minúsculas, sin sufijo de versión).
- [ ] `uptodownUrl` apunta a la ficha real (hoy es provisional).
- [ ] `versionName`, `versionCode` y `notes.es` coinciden en `api/version.json` y en `DEFAULTS` de `app.js`.
- [ ] El enlace del pie a GitHub apunta a `https://github.com/andyechc/batmonitor`.
- [ ] Las 5 capturas de `assets/` se ven (y el sitio carga sin JS con los valores por defecto).
- [ ] Dominio propio con HTTPS si se usa uno, y `canonical` actualizado.
