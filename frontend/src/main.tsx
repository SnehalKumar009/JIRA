import "@mantine/core/styles.css";
import "./styles.css";

import { MantineProvider, createTheme } from "@mantine/core";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router";
import { ApiError } from "./api";
import { App } from "./App";

// The CSP only allows <style> elements carrying this request's nonce (ADR-0021).
const nonce = document.querySelector<HTMLMetaElement>('meta[name="csp-nonce"]')?.content ?? "";

const theme = createTheme({
  primaryColor: "indigo",
  defaultRadius: "md",
});

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Don't retry what retrying can't fix.
      retry: (failures, error) =>
        !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failures < 2,
      refetchOnWindowFocus: false,
    },
  },
});

const root = document.getElementById("root");
if (!root) {
  throw new Error("Missing #root element");
}

createRoot(root).render(
  <StrictMode>
    <MantineProvider theme={theme} defaultColorScheme="auto" getStyleNonce={() => nonce}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </MantineProvider>
  </StrictMode>,
);
