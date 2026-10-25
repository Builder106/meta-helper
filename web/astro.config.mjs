import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';

// Production URL: canonical, OG and the sitemap read it. Also in src/data/site.ts
// and public/robots.txt.
export default defineConfig({
  site: 'https://meta-helper.vercel.app',
  integrations: [sitemap()],
  prefetch: { prefetchAll: true, defaultStrategy: 'hover' },
  build: { inlineStylesheets: 'auto' },
});
