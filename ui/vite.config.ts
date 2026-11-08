import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const backend = process.env.LEDGER_API ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: backend,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      '/ws': {
        target: backend.replace(/^http/, 'ws'),
        ws: true,
      },
    },
  },
});
