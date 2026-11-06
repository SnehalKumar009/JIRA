import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In development the SPA runs on Vite's server and proxies the API to a locally running backend
// (./mvnw spring-boot:test-run). In the image, the built files are served by the backend itself,
// on the same origin as the API (ADR-0010).
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
  build: {
    outDir: "dist",
    sourcemap: false,
    // React + Mantine are most of the bundle (~170 kB gzipped). Code-split once pages multiply.
    chunkSizeWarningLimit: 700,
  },
});
