import { Alert, Button, Card, Center, Stack, Text, Title } from "@mantine/core";
import { useSearchParams } from "react-router";
import { signInUrl } from "../api";

const loginErrors: Record<string, string> = {
  account_disabled: "This account has been deactivated. Ask an administrator if you need access again.",
  idp_session_ended: "Your session ended. Sign in again.",
};

export function SignInPage() {
  const [params] = useSearchParams();
  const error = params.get("loginError");

  return (
    <Center h="100vh" p="md">
      <Card withBorder shadow="sm" padding="xl" w={380}>
        <Stack>
          <Title order={2}>Tracker</Title>
          <Text c="dimmed" size="sm">
            Sign in with your organisation account.
          </Text>
          {error && (
            <Alert color="red" title="Sign-in failed">
              {loginErrors[error] ?? "Sign-in did not complete. Try again."}
            </Alert>
          )}
          <Button component="a" href={signInUrl} fullWidth>
            Sign in
          </Button>
        </Stack>
      </Card>
    </Center>
  );
}
