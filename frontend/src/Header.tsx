import { Avatar, Group, Menu, Text, UnstyledButton } from "@mantine/core";
import { useState } from "react";
import { Link } from "react-router";
import { signOut } from "./api";
import type { Me } from "./auth";

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

export function Header({ me }: { me: Me }) {
  const [signingOut, setSigningOut] = useState(false);

  return (
    <Group h="100%" px="md" justify="space-between">
      <Text component={Link} to="/" fw={700} size="lg" c="inherit" td="none">
        Tracker
      </Text>
      <Menu position="bottom-end" width={240}>
        <Menu.Target>
          <UnstyledButton aria-label="Account menu">
            <Group gap="xs">
              <Avatar color="indigo" radius="xl" size="sm">
                {initials(me.displayName)}
              </Avatar>
              <Text size="sm" visibleFrom="sm">
                {me.displayName}
              </Text>
            </Group>
          </UnstyledButton>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Label>
            {me.username}
            {me.email ? ` · ${me.email}` : ""}
          </Menu.Label>
          <Menu.Item component={Link} to="/settings/tokens">
            Access tokens
          </Menu.Item>
          <Menu.Divider />
          <Menu.Item
            color="red"
            disabled={signingOut}
            onClick={() => {
              setSigningOut(true);
              signOut().catch(() => setSigningOut(false));
            }}
          >
            Sign out
          </Menu.Item>
        </Menu.Dropdown>
      </Menu>
    </Group>
  );
}
