"""Called only by the protected official publishing workflow; never trusts client input."""
import json, os, re, pathlib
import google.auth.transport.requests
from google.oauth2 import service_account
import requests

def main():
    row=json.loads(pathlib.Path('updates/stable.json').read_text())
    assert row['applicationId']=='br.com.timachado.neromusic' and row['channel']=='stable' and row['published'] is True
    code=row['versionCode']
    assert type(code) is int and code>0
    assert re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+',row['versionName'])
    project=os.environ['NERO_FIREBASE_PROJECT_ID']
    assert re.fullmatch(r'[a-z][a-z0-9-]{4,62}',project)
    info=json.loads(os.environ['NERO_FIREBASE_SERVICE_ACCOUNT_JSON'])
    assert info['project_id']==project
    creds=service_account.Credentials.from_service_account_info(info,scopes=['https://www.googleapis.com/auth/firebase.messaging'])
    creds.refresh(google.auth.transport.requests.Request())
    payload={'message':{'topic':'nero_music_stable_updates_v1','data':{'kind':'nero_update_v1','applicationId':row['applicationId'],'versionCode':str(code)},'android':{'priority':'high','collapse_key':'nero_update'}}}
    response=requests.post('https://fcm.googleapis.com/v1/projects/'+project+'/messages:send',headers={'Authorization':'Bearer '+creds.token},json=payload,timeout=20)
    if response.status_code!=200: raise RuntimeError('FCM request failed: HTTP '+str(response.status_code))
    name=response.json()['name']
    assert name.startswith('projects/'+project+'/messages/')
    pathlib.Path(os.environ['NERO_PUSH_RECEIPT']).write_text(json.dumps({'versionCode':code,'status':'accepted','messageName':name},indent=2)+'\n')

if __name__=='__main__': main()
