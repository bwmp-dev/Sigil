import importlib.util
import json
from pathlib import Path
import unittest
from datetime import datetime, timezone, timedelta

spec=importlib.util.spec_from_file_location('probe',Path(__file__).with_name('hash_probe.py'))
probe=importlib.util.module_from_spec(spec);spec.loader.exec_module(probe)
PROJECT='00000000-0000-4000-8000-000000000001'
ARTIFACT='00000000-0000-4000-8000-000000000002'
ENV=dict(GITHUB_EVENT_NAME='workflow_dispatch',GITHUB_REF='refs/heads/main',GITHUB_REPOSITORY='bwmp-dev/Sigil',
    PROVENANCE_HASH_PROBE='true',PROVENANCE_PROJECT_ID=PROJECT,GITHUB_SHA='a'*40,PROVENANCE_AUDIENCE='fixture',
    GITHUB_REPOSITORY_ID='123',GITHUB_REPOSITORY_OWNER_ID='456',ACTIONS_ID_TOKEN_REQUEST_URL='https://fixture.actions.githubusercontent.com/token?api-version=2',ACTIONS_ID_TOKEN_REQUEST_TOKEN='synthetic-oidc-request')

class Fixture:
    def __init__(self):
        self.calls=[];self.masks=[];self.mode='success'
    def request(self, method,url,headers,data=None):
        self.calls.append((method,url,headers,data))
        if len(self.calls)==1:return 200,json.dumps({'value':'synthetic.assertion.token'}).encode()
        if url.endswith('/grants'):
            scope=dict(projectId=PROJECT,repositoryId='123',repositoryOwnerId='456',sourceCommit='a'*40,sourceRef='refs/heads/main',workflowRef='bwmp-dev/Sigil/.github/workflows/provenance.yml@refs/heads/main')
            if self.mode=='foreign':scope['projectId']=ARTIFACT
            expiry=datetime.now(timezone.utc)+timedelta(minutes=10 if self.mode!='expired' else -1)
            return 201,json.dumps(dict(accessToken='pva_'+'a'*42+'A',principalType='github-actions',tokenType='Bearer',scope=scope,expiresAt=expiry.isoformat())).encode()
        if url.endswith('/uploads'):
            upload=dict(artifactId=ARTIFACT,uploadUrl='https://fixture.r2.cloudflarestorage.com/test?signature=synthetic',expiresAt=(datetime.now(timezone.utc)+timedelta(minutes=5)).isoformat(),requiredHeaders={'If-None-Match':'*','Content-Type':'application/java-archive'})
            if self.mode=='foreign_storage':upload['uploadUrl']='https://attacker.example/test'
            if self.mode=='credential_header':upload['requiredHeaders']['Authorization']='private'
            if self.mode=='overwrite':upload['requiredHeaders']['If-None-Match']='anything'
            return 201,json.dumps(upload).encode()
        if method=='PUT':return 200,b''
        if url.endswith('/complete'):return (202 if self.mode=='accepted' else 422),b'{}'
        return 200,json.dumps(dict(id=ARTIFACT,projectId=PROJECT,fileName='provenance-alpha-hash-probe.jar',state='ready' if self.mode=='not_rejected' else 'rejected',sha256=probe.DECLARED,sizeBytes=len(probe.PAYLOAD))).encode()
    def run(self):return probe.run(ENV,self.request,self.masks.append)

class Tests(unittest.TestCase):
    def test_actual_flow_only_uploads_and_rejects(self):
        f=Fixture();result=f.run()
        self.assertFalse(result['candidateCreated']);self.assertNotEqual(result['declaredSha256'],result['uploadedSha256'])
        self.assertEqual(len(f.calls),6)
        put=next(c for c in f.calls if c[0]=='PUT')
        self.assertNotIn('Authorization',put[2]);self.assertEqual(put[3],probe.PAYLOAD)
        self.assertFalse(any('release-candidates' in c[1] for c in f.calls))
        self.assertEqual(len(f.masks),4)
    def test_context_refused_without_network(self):
        for key,value in [('GITHUB_EVENT_NAME','pull_request'),('GITHUB_REF','refs/heads/other'),('PROVENANCE_HASH_PROBE','false'),('GITHUB_REPOSITORY','foreign/repo')]:
            with self.subTest(key=key):
                f=Fixture()
                with self.assertRaises(ValueError):probe.run(dict(ENV,**{key:value}),f.request,f.masks.append)
                self.assertEqual(f.calls,[])
    def test_foreign_or_expired_grant_never_admits_upload(self):
        for mode in ('foreign','expired'):
            f=Fixture();f.mode=mode
            with self.assertRaises(ValueError):f.run()
            self.assertEqual(len(f.calls),2)
    def test_unsafe_storage_never_uploads(self):
        for mode in ('foreign_storage','credential_header','overwrite'):
            f=Fixture();f.mode=mode
            with self.assertRaises(ValueError):f.run()
            self.assertFalse(any(c[0]=='PUT' for c in f.calls))
    def test_non_rejection_fails(self):
        for mode in ('accepted','not_rejected'):
            f=Fixture();f.mode=mode
            with self.assertRaises(ValueError):f.run()
    def test_destination_validation(self):
        for url in ('http://fixture.r2.cloudflarestorage.com/x','https://user:pass@fixture.r2.cloudflarestorage.com/x','https://fixture.r2.cloudflarestorage.com:444/x','https://fixture.r2.cloudflarestorage.com/x#secret','https://fixture.r2.cloudflarestorage.com.attacker.example/x'):
            with self.assertRaises(ValueError):probe.https(url,'.r2.cloudflarestorage.com')
    def test_redirects_refused(self):
        self.assertIsNone(probe.RefuseRedirect().redirect_request(None,None,None,None,None,None))
    def test_headers_and_identity_are_bound(self):
        f=Fixture();f.run()
        init=json.loads(f.calls[2][3]);complete=json.loads(f.calls[4][3])
        self.assertEqual(init['sha256'],complete['sha256']);self.assertEqual(init['sizeBytes'],len(probe.PAYLOAD))
        self.assertNotEqual(f.calls[2][2]['Idempotency-Key'],f.calls[4][2]['Idempotency-Key'])

if __name__=='__main__':unittest.main()
