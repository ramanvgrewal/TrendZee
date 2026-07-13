import { defineConfig } from "@tanstack/react-start/config";

const apiBaseUrl = process.env.VITE_API_BASE_URL || "http://localhost:8080";

export default defineConfig({
  server: {
    preset: "vercel",
    routeRules: {
      "/api/**": { proxy: `${apiBaseUrl}/api/**` },
    },
  },
});
