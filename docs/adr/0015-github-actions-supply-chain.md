# ADR-0015: GitHub + GitHub Actions, supply-chain scanning

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
CI must block merges on failure and scan dependencies, images, and secrets.

## Decision
- Private GitHub repository; branch protection on `main` requiring CI and review.
- GitHub Actions: build, unit + integration tests, frontend build, E2E on the Compose stack, image build and tag.
- Scanning: Trivy (dependencies and images), gitleaks (secrets), Renovate (dependency and image updates).
- Images and base images pinned by digest.

## Alternatives considered
- **GitLab CI** — equivalent capability; GitHub chosen.
- **Local-only CI** — no enforcement on merges.

## Consequences
- Creating the GitHub repository is a manual step for the owner.
- CI minutes needed for integration and E2E runs.
