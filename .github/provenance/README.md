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

## Explicit operational hash-rejection exercise

Manual dispatch also provides `hash_mismatch_probe`, default false and mutually
exclusive with `submit`. After the ordinary build/tests pass, its separate OIDC
job uploads a tiny fixed synthetic payload with a deliberately different declared
digest and requires HTTP422 plus durable rejected-artifact readback. It never
creates a candidate, executes a plugin or publishes a release. Ordinary pushes,
pull requests and normal submission retain their existing behavior.

The probe binds the real grant to this repository, main ref, workflow, project and
exact source. It permits only HTTPS R2 write-once upload, never sends the grant to
storage, masks credentials/signed URLs and withholds response bodies on failure.
It deliberately has no retry after uncertain writes. A failed attempt requires
operator inspection; the fixed content identity may resolve to the retained
artifact instead of creating another one, so do not blindly replay the probe.

This proves artifact rejection only. The operator must separately retain actual
hash-mismatch alert firing and its recovery after the 15-minute lookback expires.
Do not manually edit alert/audit rows or count this as private-repository, plugin,
publication, or full-alpha acceptance. The existing policy variables above apply.

The first operational run `34712339409` stopped with its sanitized failure before
completing. A read-only HTTP comparison showed that the edge refused Python's
default identity (403), while an explicit `provenance-alpha-hash-probe/1.0`
identity reached API health (200). The probe now identifies itself explicitly
and emits only closed progress codes, never arbitrary errors or response bodies.
That comparison identifies a client interoperability defect; it is not itself
artifact-rejection or alert acceptance. Retain the original failed run.

Local build:

```sh
mvn -B -ntp -s .github/provenance/maven-settings.xml verify
python3 .github/provenance/prepare.py
# After building the pinned shared config-schema package:
node .github/provenance/validate.mjs /path/to/provenance/packages/config-schema/dist/index.js
```
