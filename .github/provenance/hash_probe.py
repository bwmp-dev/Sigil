"""Explicit manual alpha artifact-rejection fixture; never submits a candidate."""
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone

ORIGIN = "https://api.provenance.bwmp.dev"
PAYLOAD = b"provenance-alpha-hash-probe-B\n"
DECLARED = hashlib.sha256(b"provenance-alpha-hash-probe-A\n").hexdigest()
UUID = re.compile(r"[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")


class RefuseRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def https(value, suffix=None):
    parsed = urllib.parse.urlsplit(value)
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username
            or parsed.password or parsed.fragment or parsed.port not in (None, 443)
            or (suffix and not parsed.hostname.endswith(suffix))):
        raise ValueError("destination refused")
    return parsed


def request(method, url, headers, data=None):
    https(url)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), RefuseRedirect())
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        response = opener.open(req, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read(1048577)
        if len(raw) > 1048576:
            raise ValueError("response bound exceeded")
        if 300 <= response.status < 400:
            raise ValueError("redirect refused")
        return response.status, raw


def mask(value):
    escaped = value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print("::add-mask::" + escaped, flush=True)


def run(env, transport=request, hide=mask):
    if (env.get("GITHUB_EVENT_NAME") != "workflow_dispatch"
            or env.get("GITHUB_REF") != "refs/heads/main"
            or env.get("GITHUB_REPOSITORY") != "bwmp-dev/Sigil"
            or env.get("PROVENANCE_HASH_PROBE") != "true"):
        raise ValueError("explicit trusted manual context required")
    project = env.get("PROVENANCE_PROJECT_ID", "")
    commit = env.get("GITHUB_SHA", "")
    audience = env.get("PROVENANCE_AUDIENCE", "")
    if not UUID.fullmatch(project) or not re.fullmatch("[a-f0-9]{40}", commit) or not 1 <= len(audience) <= 2048:
        raise ValueError("explicit project/source/audience required")
    for key in ("GITHUB_REPOSITORY_ID", "GITHUB_REPOSITORY_OWNER_ID"):
        if not re.fullmatch("[1-9][0-9]{0,19}", env.get(key, "")):
            raise ValueError("numeric repository identities required")
    oidc_url = https(env["ACTIONS_ID_TOKEN_REQUEST_URL"], ".actions.githubusercontent.com")
    query = urllib.parse.parse_qs(oidc_url.query, strict_parsing=True)
    query["audience"] = [audience]
    oidc_url = urllib.parse.urlunsplit(oidc_url._replace(query=urllib.parse.urlencode(query, doseq=True)))
    bearer = env["ACTIONS_ID_TOKEN_REQUEST_TOKEN"]
    if not bearer or "\n" in bearer or "\r" in bearer: raise ValueError("OIDC credential required")
    hide(bearer)
    status, raw = transport("GET", oidc_url, {"Authorization": "Bearer " + bearer})
    if status != 200: raise ValueError("OIDC refused")
    assertion = json.loads(raw)["value"]
    if not isinstance(assertion, str) or len(assertion) > 16384 or not re.fullmatch(r"[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", assertion):
        raise ValueError("invalid assertion")
    hide(assertion)
    status, raw = transport("POST", ORIGIN + "/v1/auth/github-actions/grants",
        {"Content-Type": "application/json", "Idempotency-Key": str(uuid.uuid4())},
        json.dumps({"assertion": assertion}).encode())
    if status != 201: raise ValueError("grant refused; issuance is never retried")
    grant = json.loads(raw)
    token = grant["accessToken"]
    if not isinstance(token,str) or not re.fullmatch(r"pva_[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]",token):
        raise ValueError("invalid grant")
    hide(token)
    expected = dict(projectId=project, repositoryId=env["GITHUB_REPOSITORY_ID"],
        repositoryOwnerId=env["GITHUB_REPOSITORY_OWNER_ID"], sourceCommit=commit,
        sourceRef="refs/heads/main", workflowRef="bwmp-dev/Sigil/.github/workflows/provenance.yml@refs/heads/main")
    if grant.get("principalType") != "github-actions" or grant.get("tokenType") != "Bearer" or any(grant["scope"].get(k) != v for k,v in expected.items()):
        raise ValueError("grant scope mismatch")
    expiry = datetime.fromisoformat(grant["expiresAt"].replace("Z", "+00:00"))
    if not 120 < (expiry-datetime.now(timezone.utc)).total_seconds() <= 3600:
        raise ValueError("bounded unexpired grant required")

    def call(method, path, payload=None):
        if datetime.now(timezone.utc) >= expiry: raise ValueError("grant expired")
        headers={"Authorization":"Bearer "+token,"Accept":"application/json"}
        data=None
        if payload is not None:
            headers.update({"Content-Type":"application/json","Idempotency-Key":str(uuid.uuid4())})
            data=json.dumps(payload).encode()
        return transport(method, ORIGIN+path, headers, data)

    status, raw = call("POST", "/v1/projects/"+project+"/artifacts/uploads",
        {"fileName":"provenance-alpha-hash-probe.jar","sizeBytes":len(PAYLOAD),"sha256":DECLARED})
    if status != 201: raise ValueError("upload admission refused")
    upload=json.loads(raw);artifact=upload["artifactId"]
    if not isinstance(artifact,str) or not UUID.fullmatch(artifact):raise ValueError("invalid artifact identity")
    https(upload["uploadUrl"], ".r2.cloudflarestorage.com")
    hide(upload["uploadUrl"])
    if datetime.fromisoformat(upload["expiresAt"].replace("Z", "+00:00")) <= datetime.now(timezone.utc):
        raise ValueError("upload expired")
    headers=upload.get("requiredHeaders",{})
    if not isinstance(headers,dict):raise ValueError("invalid upload headers")
    seen=set()
    for key,value in headers.items():
        name=key.lower()
        if (name in seen or name not in {"content-type","if-none-match"}
                or not isinstance(value,str) or len(value)>256 or "\r" in value or "\n" in value
                or (name=="if-none-match" and value!="*")):
            raise ValueError("unexpected storage header")
        seen.add(name)
    if "if-none-match" not in seen:raise ValueError("write-once upload required")
    status,_=transport("PUT",upload["uploadUrl"],headers,PAYLOAD)
    if status not in (200,201,204):raise ValueError("synthetic upload failed")
    status,_=call("POST","/v1/artifacts/"+artifact+"/complete",{"sizeBytes":len(PAYLOAD),"sha256":DECLARED})
    if status != 422:raise ValueError("expected hash rejection was not returned")
    status,raw=call("GET","/v1/artifacts/"+artifact)
    record=json.loads(raw)
    if status!=200 or any(record.get(k)!=v for k,v in dict(id=artifact,projectId=project,fileName="provenance-alpha-hash-probe.jar",state="rejected",sha256=DECLARED,sizeBytes=len(PAYLOAD)).items()):
        raise ValueError("durable rejected artifact not confirmed")
    return dict(artifactId=artifact,state="rejected",bytes=len(PAYLOAD),declaredSha256=DECLARED,
        uploadedSha256=hashlib.sha256(PAYLOAD).hexdigest(),candidateCreated=False)


if __name__ == "__main__":
    try:
        print(json.dumps({"hashMismatchProbe":run(os.environ)}))
    except Exception:
        raise SystemExit("Hash rejection probe incomplete; credentials, URLs and response bodies withheld.") from None
