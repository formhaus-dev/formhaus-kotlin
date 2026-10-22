# Security policy

## Supported versions

Security fixes go to the latest minor release of `formhaus-kotlin`. Older minors do not receive patches.

## Scope

`formhaus-core` runs on servers: `validateSubmission`, `checkDefinition`, the JSON parser and the regex engine take untrusted input. A way to make them accept values the definition rejects, crash, or run without bounds is a security issue.

Other bugs are not security issues. Open a regular issue for them.

## Reporting a vulnerability

Report vulnerabilities privately through [GitHub security advisories](https://github.com/formhaus-dev/formhaus-kotlin/security/advisories/new). Do not open a public issue.

Include the version, a minimal definition or code that reproduces the problem, and the impact you expect.
