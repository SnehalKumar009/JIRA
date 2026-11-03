import { useEffect, useState } from "react";

type SystemInfo = {
  name: string;
  version: string;
  installationId: string;
};

type State =
  | { kind: "loading" }
  | { kind: "ready"; info: SystemInfo }
  | { kind: "error"; message: string };

// M0 placeholder: proves the build path and same-origin API access. The real app shell arrives in M1.
export function App() {
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/v1/system/info", { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`API returned ${response.status}`);
        }
        setState({ kind: "ready", info: (await response.json()) as SystemInfo });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setState({ kind: "error", message: error instanceof Error ? error.message : String(error) });
        }
      });
    return () => controller.abort();
  }, []);

  return (
    <main className="status">
      <h1>Tracker</h1>
      {state.kind === "loading" && <p>Checking the platform…</p>}
      {state.kind === "error" && <p className="error">Platform check failed: {state.message}</p>}
      {state.kind === "ready" && (
        <dl>
          <dt>Version</dt>
          <dd>{state.info.version}</dd>
          <dt>Installation</dt>
          <dd>
            <code>{state.info.installationId}</code>
          </dd>
        </dl>
      )}
    </main>
  );
}
