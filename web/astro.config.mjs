import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';

// Placeholder production URL: canonical, OG and the sitemap depend on it.
export default defineConfig({
  site: 'https://metahelper.yinkavaughan.me',
  integrations: [sitemap()],
  prefetch: { prefetchAll: true, defaultStrategy: 'hover' },
  build: { inlineStylesheets: 'auto' },
});
