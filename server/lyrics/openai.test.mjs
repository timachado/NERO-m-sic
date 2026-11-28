import test from "node:test";
import assert from "node:assert/strict";
import {validLyricWav,preserveTranscript,transcribeLyrics} from "./openai.mjs";
function wav(){
 const a=new Uint8Array(32044);const v=new DataView(a.buffer);
 for(const [i,s] of [[0,"RIFF"],[8,"WAVE"],[12,"fmt "],[36,"data"]])a.set(new TextEncoder().encode(s),i);
 for(const [i,n] of [[4,a.length-8],[16,16],[24,16000],[28,32000],[40,a.length-44]])v.setUint32(i,n,true);
 for(const [i,n] of [[20,1],[22,1],[32,2],[34,16]])v.setUint16(i,n,true);
 return a;
}
test("bounded audio envelope",()=>{
 assert.equal(validLyricWav(wav()),true);
 assert.equal(validLyricWav(new Uint8Array(10)),false);
 const a=wav();a[24]=0;assert.equal(validLyricWav(a),false);
});
test("formatting cannot invent or replace words",()=>{
 assert.equal(preserveTranscript("Meu verso aqui","Meu verso,\naqui."),"Meu verso,\naqui.");
 assert.equal(preserveTranscript("Meu verso aqui","Meu verso inventado aqui"),"Meu verso aqui");
 assert.equal(preserveTranscript("Meu verso aqui","aqui Meu verso"),"Meu verso aqui");
});
test("transcribe and optionally format via mock only",async()=>{
 let calls=0;
 const fetcher=async(url,req)=>{
  calls++;
  assert.equal(req.redirect,"error");
  if(calls===1){
   assert.equal(url,"https://api.openai.com/v1/audio/transcriptions");
   assert.equal(req.body.get("model"),"gpt-transcribe");
   return new Response(JSON.stringify({text:"Meu verso aqui"}));
  }
  assert.equal(url,"https://api.openai.com/v1/responses");
  assert.equal(JSON.parse(req.body).store,false);
  return new Response(JSON.stringify({output:[{content:[{type:"output_text",text:"Meu verso,\naqui."}]}]}));
 };
 assert.deepEqual(await transcribeLyrics(wav(),"mock",{format:true,fetcher}),{status:"transcript",text:"Meu verso,\naqui.",formatted:true});
 assert.equal(calls,2);
});
test("invalid input does not contact provider",async()=>{
 assert.equal((await transcribeLyrics(new Uint8Array(4),"mock",{fetcher:()=>{throw Error("unexpected")}})).status,"unavailable");
});
