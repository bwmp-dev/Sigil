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

# The Action validates the complete YAML against the Provenance schema.
template = Path("provenance.yml").read_text()
template = template.replace("version: 1.0.0\n", f"version: {version}\n", 1)
template = template.replace("sigil-plugin/target/Sigil-1.0.0.jar", str(artifact), 1)
Path("sigil-plugin/target/provenance.yml").write_text(template)
print(f"Artifact: {artifact}; SHA-256: {hashlib.sha256(artifact.read_bytes()).hexdigest()}")
if os.environ.get("GITHUB_OUTPUT"):
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"artifact={artifact}\nversion={version}\n")
