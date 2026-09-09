"""Check the distributable and resolve the release-managed artifact version."""
import hashlib
import os
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ns = {"m": "http://maven.apache.org/POM/4.0.0"}
version = ET.parse("sigil-plugin/pom.xml").getroot().findtext("m:version", namespaces=ns)
if not version or any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_" for c in version):
    raise SystemExit("Invalid project version")
artifact = Path(f"sigil-plugin/target/Sigil-{version}.jar")
with zipfile.ZipFile(artifact) as jar:
    names = jar.namelist()
    for prefix in ("dev/bwmp/sigil/libs/keystone/", "dev/bwmp/sigil/libs/kyori/"):
        if not any(n.startswith(prefix) for n in names):
            raise SystemExit(f"Missing shaded package: {prefix}")
    if any(n.startswith(("net/kyori/", "dev/bwmp/keystone/")) for n in names):
        raise SystemExit("Unrelocated dependency found")
    if "dev/bwmp/sigil/SigilPlugin.class" not in names:
        raise SystemExit("Missing plugin entrypoint")

# The checked-in configuration is the source-qualified execution authority.
# Fail the build if a release changes Maven without updating its configuration.
template = Path("provenance.yml").read_text()
if f"  version: {version} # x-release-please-version" not in template or f"  path: {artifact} # x-release-please-version" not in template:
    raise SystemExit("Committed provenance.yml artifact version/path is stale")
print("Shaded entrypoint and Keystone/Adventure relocation checks passed")
digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
print(f"Artifact: {artifact}; SHA-256: {digest}")
if os.environ.get("GITHUB_OUTPUT"):
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"artifact={artifact}\nversion={version}\nsha256={digest}\n")
