# Provenance pilot

The manually dispatched `Provenance pilot` workflow builds the shaded plugin and
checks the Keystone/Adventure relocations. Its default is build-only. Enable
`submit` only after the project is connected and the production API has an
explicit GitHub Actions OIDC policy for this repository and workflow.

Repository variables required for submission:

- `PROVENANCE_PROJECT_ID`: UUID of the project connected to `bwmp-dev/Sigil`.
- `PROVENANCE_AUDIENCE`: exact audience selected by the platform OIDC policy.

No long-lived platform token is needed. The backend must validate repository,
installation, source ref and workflow identity before issuing its scoped grant.
Do not treat these variables alone as server authorization.

`provenance.yml` starts with Paper 1.20.6 build 151 and Java 21. The platform
catalog and hosted runner must have that exact environment available. This is a
first smoke test, not coverage of every server/version Sigil supports.

The test requires plugin enablement, the bundled `sigil:ember_wand` item before
and after reload, and clean shutdown. Networking is disabled. Release mode is
`test-only` with no publishing targets. Player interactions, abilities, recipes,
and Folia-specific behavior need later scenarios.

`prepare.py` reads the release-managed Maven version, checks the final shaded
JAR, prints its SHA-256, and writes the resolved configuration into the build
directory. Submission success means the API accepted a candidate; inspect its
matrix and assertions in the console before claiming a passing test. This pilot
does not report a green GitHub compatibility status merely for submission.

Local build:

```sh
mvn -B -ntp -s .github/provenance/maven-settings.xml verify
python3 .github/provenance/prepare.py
```
