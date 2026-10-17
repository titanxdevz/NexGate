# Contributing to NexAuth

Thank you for your interest in contributing to NexAuth. This document outlines the technical standards, build procedures, and pull request workflow for contributors.

## Prerequisites

- **Java Development Kit (JDK)**: Version 21 or newer.
- **Git**: Version 2.30 or newer.
- **Gradle**: Uses the included Gradle Wrapper (`./gradlew`).

## Development Workflow

1. **Fork and Clone**
   ```bash
   git clone https://github.com/<your-username>/NexGate.git
   cd NexGate
   ```

2. **Create a Feature Branch**
   ```bash
   git checkout -b feat/your-feature-name
   # or
   git checkout -b fix/your-bug-fix
   ```

3. **Build and Test Locally**
   Before submitting code, verify that all unit and integration tests pass:
   ```bash
   ./gradlew test check shadowJar
   ```

4. **Commit Guidelines**
   Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:
   - `feat:` Adds a new user- or operator-facing feature.
   - `fix:` Fixes a bug or vulnerability.
   - `docs:` Documentation additions or updates.
   - `ci:` Changes to CI/CD workflows or tooling scripts.
   - `refactor:` Code restructuring without functional behavior changes.
   - `test:` Adds or updates automated test coverage.
   - `chore:` Dependency updates, configuration tweaks, or maintenance tasks.

   Examples:
   ```text
   feat(auth): add configurable IP cooldown after repeated failed kicks
   fix(messaging): enforce constant-time signature verification
   docs(readme): clarify proxy-only network deployment model
   ```

5. **Submitting a Pull Request**
   - Push your branch to your fork.
   - Open a pull request against `main`.
   - Complete the pull request template checklist.
   - Ensure all CI workflow checks pass.

## Code Standards

- **Java Version**: Target Java 21 bytecode (`options.release.set(21)`).
- **Asynchronous Execution**: Network, database, and cryptographic operations must never block proxy or server main threads. Use `CompletableFuture` or Velocity continuation tasks.
- **Security-First**:
  - Always validate user input (usernames, passwords, tokens).
  - Use `MessageDigest.isEqual` for constant-time comparisons of cryptographic signatures.
  - Never commit credentials, production database files, or private keys.
- **Test Coverage**: Any functional change or security patch must include corresponding unit tests in the appropriate module (`src/test/java`).
