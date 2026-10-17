# Security Policy

## Supported Versions

Only the latest release version on the `main` branch receives security updates.

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability in NexAuth, do not report it through public GitHub issues or public discussions.

Please disclose vulnerabilities through one of the following methods:

1. **GitHub Private Vulnerability Reporting**: Submit an advisory via the **Security** tab of this repository by clicking **Report a vulnerability**.
2. **Email**: Send vulnerability details to `security@nexauth.dev` (or the repository maintainers).

Include the following information in your report:
- Type of issue (e.g., authentication bypass, injection, replay vulnerability, DoS).
- Component affected (Velocity plugin, Paper companion, or Common library).
- Step-by-step reproduction instructions or proof-of-concept.
- Potential impact and mitigation ideas if available.

### Response Timeline
- **Initial acknowledgment**: Within 48 hours.
- **Assessment and validation**: Within 5 business days.
- **Remediation release**: As soon as a patch is tested and verified.

## Operational Security Requirements

When deploying NexAuth in production:
- Ensure the `hmacSecret` in `velocity-config.json` and backend `config.yml` matches, contains at least 32 characters of high-entropy randomness, and is never shared publicly.
- Use Velocity modern player forwarding on all backend Paper servers (`player-info-forwarding-mode = "modern"` in `velocity.toml` and `settings.velocity-support.online-mode = true` in Paper configuration).
- Firewall backend Paper ports so players cannot bypass the Velocity proxy.
