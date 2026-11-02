# Security policy

## Supported versions

Only the latest release (the `latest` images of the registry, built from `main`) receives security fixes.

## Reporting a vulnerability

Please do **not** open a public issue. Report it privately through
[GitHub private vulnerability reporting](https://github.com/Bergamof/bergacast/security/advisories/new), with the
affected version, the steps to reproduce and the impact you expect.

You will get an acknowledgement within a week. Once a fix is released, the advisory is published with credit to the
reporter, unless you prefer to stay anonymous.

## What the project checks

Every pull request, and every night for `main`, is checked for known vulnerabilities in the dependencies and images
(Trivy, `npm audit`), leaked secrets (gitleaks) and code issues (CodeQL); see the « Intégration continue » section of
the [README](README.md).
