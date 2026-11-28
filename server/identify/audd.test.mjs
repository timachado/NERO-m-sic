import test from "node:test";
import assert from "node:assert/strict";
import {validIdentifyWav,normalizeAudD,recognizeAudD} from "./audd.mjs";
function wav(){
 const a=new Uint8Array(256044);const v=new DataView(a.buffer);
 for(const [i,s] of [[0,"RIFF"],[8,"WAVE"],[12,"fmt "],[36,"data"]]) a.set(new TextEncoder().encode(s),i);
 for(const [i,n] of [[4,256036],[16,16],[24,16000],[28,32000],[40,256000]]) v.setUint32(i,n,true);
 for(const [i,n] of [[20,1],[22,1],[32,2],[34,16]]) v.setUint16(i,n,true);
 return a;
}
test("validates exact PCM WAV envelope",()=>{
 const a=wav(); assert.equal(validIdentifyWav(a),true); a[24]=0;
 assert.equal(validIdentifyWav(a),false);assert.equal(validIdentifyWav(new Uint8Array(10)),false);
});
test("normalizes real response contract and rejects missing fields",()=>{
 assert.deepEqual(normalizeAudD({status:"success",result:null}),{status:"no_match"});
 assert.deepEqual(normalizeAudD({status:"success",result:{title:"Song",artist:"Artist",album:"Album",other:"ignored"}}),{status:"match",title:"Song",artist:"Artist",album:"Album"});
 assert.equal(normalizeAudD({status:"error"}).status,"unavailable");
 assert.equal(normalizeAudD({status:"success",result:{title:"Song"}}).status,"unavailable");
});
test("sends multipart only to approved provider using mocked transport",async()=>{
 let calls=0;
 const fetcher=async(url,request)=>{
   calls++; assert.equal(url,"https://api.audd.io/");assert.equal(request.method,"POST");assert.equal(request.redirect,"error");
   assert.equal(request.body.get("api_token"),"mock-only");
   assert.equal(request.body.get("file").size,256044);
   return new Response(JSON.stringify({status:"success",result:null}));
 };
 assert.equal((await recognizeAudD(wav(),"mock-only",fetcher)).status,"no_match");
 assert.equal((await recognizeAudD(wav(),"",fetcher)).status,"unavailable");
 assert.equal(calls,1);
});
