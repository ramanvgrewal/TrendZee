import { defineConfig } from "vite";
import { tanstackStart } from "@tanstack/react-start/plugin/vite";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [
    tanstackStart({
      server: {
        preset: 'vercel',
      }
    }),
    react(),
    tailwindcss(),
  ],
  resolve: {
    // Native tsconfig paths support in Vite
    tsconfigPaths: true
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false,
      }
    }
  }
});


