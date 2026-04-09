import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  define: {
    // sockjs-client references the Node.js global — polyfill it for browsers
    global: 'globalThis',
  },
  server: {
    port: 3000,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    environmentOptions: {
      jsdom: {
        // Enable requestAnimationFrame, which rc-motion requires for animation
        // step transitions (e.g. closing an Ant Design Modal).
        pretendToBeVisual: true,
      },
    },
    setupFiles: ['./src/test/setup.ts'],
    alias: {
      // Redirect keycloak-js to a lightweight stub that works in jsdom
      'keycloak-js': path.resolve(__dirname, 'src/test/mocks/keycloak.ts'),
    },
  },
});
