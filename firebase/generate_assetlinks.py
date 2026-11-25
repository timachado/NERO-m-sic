import json, pathlib, re, sys
if len(sys.argv) != 2:
    raise SystemExit("Usage: python generate_assetlinks.py CERT_SHA256")
fingerprint=sys.argv[1].replace(":","").strip().upper()
if not re.fullmatch(r"[0-9A-F]{64}",fingerprint):
    raise SystemExit("Invalid SHA256 certificate fingerprint")
path=pathlib.Path(__file__).parent/"share-hosting"/".well-known"/"assetlinks.json"
path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(json.dumps([{"relation":["delegate_permission/common.handle_all_urls"],"target":{"namespace":"android_app","package_name":"br.com.timachado.neromusic","sha256_cert_fingerprints":[":".join(fingerprint[i:i+2] for i in range(0,64,2))]}}],indent=2))
print(path)
