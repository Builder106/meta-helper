# Whiteboard website

The public website for MetaHelper: an interactive whiteboard to explore how photos of code turn into spoken answers, with a plain-text transcript at `/transcript`. It is a static [Astro](https://astro.build) site, live at <https://meta-helper.vercel.app>.

## Build

Run dependencies and builds on a dedicated build host (or verifier):

```sh
npm ci
npm run build
npm run preview
```

The build outputs static HTML, assets, and sitemaps to `dist/`.

## Deploy

The site is hosted on Vercel under the `sankofa-forge` team:

- **Project:** `meta-helper`
- **Root Directory:** `web`
- **Framework Preset:** Astro
- **Production URL:** <https://meta-helper.vercel.app>

Deploys are managed via the Vercel CLI:

```sh
vercel deploy --prod
```

`web/vercel.json` configures clean URLs and cache headers for static assets under `/_astro/`, `/fonts/`, and `/img/`.

## Artwork

The whiteboard and glasses artwork are authored with Blender and Glaxnimate. See [`DESIGN.md`](DESIGN.md) for art direction and design notes.

