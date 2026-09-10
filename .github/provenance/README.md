# Provenance pilot

The `Provenance pilot` workflow builds the shaded plugin, checks the
Keystone/Adventure relocations, and validates the committed configuration on
every pull request and main push. Manual dispatch defaults to build-only. Enable
`submit` only after the project is connected and the production API has an
explicit GitHub Actions OIDC policy for this repository and workflow.

Repository variables required for submission:

- `PROVENANCE_PROJECT_ID`: UUID of the project connected to `bwmp-dev/Sigil`.
- `PROVENANCE_AUDIENCE`: exact audience selected by the platform OIDC policy.

No long-lived platform token is needed. The backend must validate repository,
installation, source ref and workflow identity before issuing its scoped grant.
Do not treat these variables alone as server authorization.

`provenance.yml` selects five exact Paper builds, from 1.20.6 through 26.1.2,
using Java 21 or 25. The platform and hosted runner must support those exact
environments. This is a smoke-test matrix, not coverage of every server/version
Sigil supports. The `apiFloor: 1.20.6` deliberately narrows the pilot's floor;
Sigil declares an actual plugin API floor of 1.18. Runtime and runner availability
must be verified separately before submitting; this configuration is not evidence
that either is available.

The test requires plugin enablement, the bundled `sigil:ember_wand` item before
and after reload, and clean shutdown. Networking is disabled. Release mode is
`test-only` with no publishing targets. Player interactions, abilities, recipes,
and Folia-specific behavior need later scenarios.

`prepare.py` reads the release-managed Maven version, checks the final shaded
JAR, prints its SHA-256, and rejects stale configuration artifact identity.
For manual submission, `pilot.py` derives `provenance-pilot.yml` from that file,
changing only `artifact.version` to a unique build-version/run-ID/attempt label.
The candidate uses the identical label: dispatch requires these two identities
to agree. The JAR bytes/path, test matrix, network policy and test-only release
mode do not change. Reruns create distinct candidates rather than overwriting
the history of an earlier test.
Release-please maintains its annotated version/path alongside the Maven versions.
The unconditional validator uses the shared configuration package at the same
pinned Provenance commit as the Action, with frozen dependencies. Only the
separate, dispatch-gated submission job has OIDC permission; it downloads the
JAR produced by the successful build job in the same run.

Submission success means the API accepted a candidate; inspect its
matrix and assertions in the console before claiming a passing test. This pilot
does not report a green GitHub compatibility status merely for submission.

Local build:

```sh
mvn -B -ntp -s .github/provenance/maven-settings.xml verify
python3 .github/provenance/prepare.py
# After building the pinned shared config-schema package:
node .github/provenance/validate.mjs /path/to/provenance/packages/config-schema/dist/index.js
```
