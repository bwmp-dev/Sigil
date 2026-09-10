"""Derive only the test candidate version from the checked-in configuration."""
import os
from pathlib import Path
import re


def render(template, version, run_id, attempt):
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,64}", version):
        raise ValueError("Invalid build version")
    if not all(re.fullmatch(r"[1-9][0-9]{0,19}", value) for value in (run_id, attempt)):
        raise ValueError("Invalid workflow run identity")
    marker = f"  version: {version} # x-release-please-version"
    if template.splitlines().count(marker) != 1 or "  mode: test-only" not in template.splitlines():
        raise ValueError("Expected one release-managed version and test-only configuration")
    candidate = f"{version}-provenance.{run_id}.{attempt}"
    lines = template.splitlines(keepends=True)
    output = "".join(f"  version: {candidate}\n" if line.rstrip("\r\n") == marker else line for line in lines)
    return candidate, output


if __name__ == "__main__":
    version, configuration = render(Path("provenance.yml").read_text(), os.environ["BUILD_VERSION"], os.environ["GITHUB_RUN_ID"], os.environ["GITHUB_RUN_ATTEMPT"])
    with Path("provenance-pilot.yml").open("x") as file:
        file.write(configuration)
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"version={version}\n")
