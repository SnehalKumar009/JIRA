import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Code,
  Container,
  CopyButton,
  Group,
  Modal,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { useDisclosure } from "@mantine/hooks";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "../api";
import type { Me } from "../auth";

type Token = {
  id: string;
  name: string;
  displayPrefix: string;
  scopes: string[];
  createdAt: string;
  expiresAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
  status: "active" | "expired" | "revoked";
};

type CreatedToken = { id: string; name: string; token: string; scopes: string[]; expiresAt: string };

const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: "medium" });
const formatDate = (value: string | null) => (value ? dateFormat.format(new Date(value)) : "—");

const statusColor = { active: "green", expired: "gray", revoked: "red" } as const;

export function TokensPage({ me }: { me: Me }) {
  const queryClient = useQueryClient();
  const tokens = useQuery({ queryKey: ["tokens"], queryFn: () => api<Token[]>("/api/v1/me/tokens") });
  const revoke = useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/me/tokens/${id}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["tokens"] }),
  });
  const [createOpen, createModal] = useDisclosure(false);

  return (
    <Container size="lg">
      <Stack>
        <Group justify="space-between">
          <div>
            <Title order={2}>Access tokens</Title>
            <Text c="dimmed" size="sm">
              For scripts and integrations. Send as <Code>Authorization: Bearer &lt;token&gt;</Code>.
            </Text>
          </div>
          <Button onClick={createModal.open}>New token</Button>
        </Group>

        {tokens.isError && <Alert color="red">{tokens.error.message}</Alert>}
        {revoke.isError && <Alert color="red">{revoke.error.message}</Alert>}

        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th>Token</Table.Th>
              <Table.Th>Scopes</Table.Th>
              <Table.Th>Expires</Table.Th>
              <Table.Th>Last used</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {tokens.data?.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={7}>
                  <Text c="dimmed" size="sm">
                    No tokens yet.
                  </Text>
                </Table.Td>
              </Table.Tr>
            )}
            {tokens.data?.map((token) => (
              <Table.Tr key={token.id}>
                <Table.Td>{token.name}</Table.Td>
                <Table.Td>
                  <Code>{token.displayPrefix}…</Code>
                </Table.Td>
                <Table.Td>
                  <Group gap={4}>
                    {token.scopes.map((scope) => (
                      <Badge key={scope} variant="light" size="sm">
                        {scope}
                      </Badge>
                    ))}
                  </Group>
                </Table.Td>
                <Table.Td>{formatDate(token.expiresAt)}</Table.Td>
                <Table.Td>{formatDate(token.lastUsedAt)}</Table.Td>
                <Table.Td>
                  <Badge color={statusColor[token.status]} variant="dot">
                    {token.status}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  {token.status === "active" && (
                    <Button
                      size="xs"
                      variant="subtle"
                      color="red"
                      loading={revoke.isPending && revoke.variables === token.id}
                      onClick={() => {
                        if (window.confirm(`Revoke "${token.name}"? Anything using it stops working.`)) {
                          revoke.mutate(token.id);
                        }
                      }}
                    >
                      Revoke
                    </Button>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Stack>

      <Modal opened={createOpen} onClose={createModal.close} title="New access token" size="lg">
        <CreateTokenForm
          admin={me.admin}
          onCreated={() => queryClient.invalidateQueries({ queryKey: ["tokens"] })}
          onDone={createModal.close}
        />
      </Modal>
    </Container>
  );
}

function CreateTokenForm({
  admin,
  onCreated,
  onDone,
}: {
  admin: boolean;
  onCreated: () => void;
  onDone: () => void;
}) {
  const [name, setName] = useState("");
  const [scopes, setScopes] = useState<string[]>(["read"]);
  const [expiresInDays, setExpiresInDays] = useState<string | null>("90");
  const create = useMutation({
    mutationFn: () =>
      api<CreatedToken>("/api/v1/me/tokens", {
        method: "POST",
        body: JSON.stringify({ name, scopes, expiresInDays: Number(expiresInDays) }),
      }),
    onSuccess: onCreated,
  });

  if (create.data) {
    return (
      <Stack>
        <Alert color="yellow" title="Copy it now">
          This is the only time the token is shown. Store it somewhere safe.
        </Alert>
        <Code block className="token-value">
          {create.data.token}
        </Code>
        <Group justify="flex-end">
          <CopyButton value={create.data.token}>
            {({ copied, copy }) => (
              <Button variant="light" onClick={copy}>
                {copied ? "Copied" : "Copy"}
              </Button>
            )}
          </CopyButton>
          <Button onClick={onDone}>Done</Button>
        </Group>
      </Stack>
    );
  }

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        create.mutate();
      }}
    >
      <Stack>
        <TextInput
          label="Name"
          description="What the token is for, e.g. the script that uses it"
          required
          maxLength={100}
          value={name}
          onChange={(event) => setName(event.currentTarget.value)}
        />
        <Checkbox.Group label="Scopes" required value={scopes} onChange={setScopes}>
          <Stack gap="xs" mt="xs">
            <Checkbox value="read" label="read — view data" />
            <Checkbox value="write" label="write — change data (includes read)" />
            {admin && <Checkbox value="admin" label="admin — administration API" />}
          </Stack>
        </Checkbox.Group>
        <Select
          label="Expires after"
          data={[
            { value: "7", label: "7 days" },
            { value: "30", label: "30 days" },
            { value: "90", label: "90 days" },
            { value: "365", label: "1 year" },
          ]}
          value={expiresInDays}
          onChange={setExpiresInDays}
          allowDeselect={false}
        />
        {create.isError && <Alert color="red">{create.error.message}</Alert>}
        <Group justify="flex-end">
          <Button variant="default" onClick={onDone}>
            Cancel
          </Button>
          <Button type="submit" loading={create.isPending} disabled={!name.trim() || scopes.length === 0}>
            Create token
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
