import { Badge, Card, Container, Group, SimpleGrid, Stack, Text, Title } from "@mantine/core";
import type { Me } from "../auth";

export function HomePage({ me }: { me: Me }) {
  return (
    <Container size="md">
      <Stack>
        <Title order={2}>Welcome, {me.displayName}</Title>
        <Text c="dimmed">Projects and issues arrive in the next milestones.</Text>
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <Card withBorder>
            <Stack gap="xs">
              <Text fw={600}>Your account</Text>
              <Text size="sm">Username: {me.username}</Text>
              <Text size="sm">Email: {me.email ?? "—"}</Text>
              {me.admin && (
                <Group>
                  <Badge color="grape">Administrator</Badge>
                </Group>
              )}
            </Stack>
          </Card>
          <Card withBorder>
            <Stack gap="xs">
              <Text fw={600}>Groups</Text>
              <Text size="xs" c="dimmed">
                Managed in your identity provider.
              </Text>
              <Group gap="xs">
                {me.groups.length === 0 ? (
                  <Text size="sm">None</Text>
                ) : (
                  me.groups.map((group) => (
                    <Badge key={group} variant="light">
                      {group}
                    </Badge>
                  ))
                )}
              </Group>
            </Stack>
          </Card>
        </SimpleGrid>
      </Stack>
    </Container>
  );
}
