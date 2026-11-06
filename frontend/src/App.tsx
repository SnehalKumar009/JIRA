import { Alert, AppShell, Center, Loader } from "@mantine/core";
import { Navigate, Route, Routes } from "react-router";
import { useMe } from "./auth";
import { Header } from "./Header";
import { HomePage } from "./pages/HomePage";
import { SignInPage } from "./pages/SignInPage";
import { TokensPage } from "./pages/TokensPage";

export function App() {
  const me = useMe();

  if (me.isPending) {
    return (
      <Center h="100vh">
        <Loader />
      </Center>
    );
  }
  if (me.isError) {
    return (
      <Center h="100vh" p="md">
        <Alert color="red" title="Tracker is unavailable">
          {me.error.message}
        </Alert>
      </Center>
    );
  }
  if (me.data === null) {
    return <SignInPage />;
  }

  // Routes here must also be listed in the backend's SpaController.
  return (
    <AppShell header={{ height: 56 }} padding="md">
      <AppShell.Header>
        <Header me={me.data} />
      </AppShell.Header>
      <AppShell.Main>
        <Routes>
          <Route path="/" element={<HomePage me={me.data} />} />
          <Route path="/settings/tokens" element={<TokensPage me={me.data} />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AppShell.Main>
    </AppShell>
  );
}
