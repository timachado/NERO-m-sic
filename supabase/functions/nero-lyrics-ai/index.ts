// DRAFT ONLY. No deployment or paid calls until owner authorizes pilot budget.
import {validLyricWav,transcribeLyrics} from "./openai.mjs";
const reply=(status:number,body:unknown)=>new Response(JSON.stringify(body),{status,headers:{"content-type":"application/json","cache-control":"no-store"}});
const MAX=9600044;
Deno.serve(async(req:Request)=>{
  if(req.method!=="POST")return reply(405,{status:"unavailable"});
  const key=Deno.env.get("OPENAI_API_KEY");
  const url=Deno.env.get("SUPABASE_URL");
  const service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  // Reuse the existing private pilot account allowlist, not its quota.
  const allow=new Set((Deno.env.get("NERO_IDENTIFY_PILOT_UIDS")||"").split(",").map(s=>s.trim()).filter(Boolean));
  if(!key||!url||!service||!allow.size)return reply(503,{status:"unavailable"});
  const auth=req.headers.get("authorization")||"";
  if(!/^Bearer [A-Za-z0-9_.-]{20,8192}$/.test(auth))return reply(401,{status:"unavailable"});
  if(req.headers.get("x-nero-lyrics-consent")!=="v1")return reply(400,{status:"consent_required"});
  if(req.headers.get("content-type")?.split(";")[0]!=="audio/wav")return reply(415,{status:"unavailable"});
  const length=req.headers.get("content-length");
  if(length!==null&&(!/^\d+$/.test(length)||Number(length)<46||Number(length)>MAX))return reply(413,{status:"unavailable"});
  let audio:Uint8Array|undefined;
  let buffer:Uint8Array|undefined;
  try{
    const userRes=await fetch(url+"/auth/v1/user",{headers:{apikey:service,authorization:auth},redirect:"error",signal:AbortSignal.timeout(5000)});
    if(!userRes.ok)return reply(401,{status:"unavailable"});
    const user=await userRes.json();
    if(typeof user.id!=="string"||user.is_anonymous===true||!allow.has(user.id))return reply(403,{status:"unavailable"});
    if(!req.body)return reply(400,{status:"unavailable"});
    buffer=new Uint8Array(MAX);
    const reader=req.body.getReader();
    const timeout=AbortSignal.timeout(15000);
    const cancel=()=>{void reader.cancel().catch(()=>{});};
    timeout.addEventListener("abort",cancel,{once:true});
    let size=0;
    try{
      while(true){
        const {done,value}=await reader.read();
        if(timeout.aborted||req.signal.aborted)return reply(408,{status:"unavailable"});
        if(done)break;
        if(size+value.byteLength>MAX){await reader.cancel();return reply(413,{status:"unavailable"});}
        buffer.set(value,size);size+=value.byteLength;
      }
    }finally{timeout.removeEventListener("abort",cancel);reader.releaseLock();}
    audio=buffer.subarray(0,size);
    if(!validLyricWav(audio))return reply(400,{status:"invalid_audio"});
    const seconds=Math.ceil((audio.length-44)/32000);
    // Each reservation covers one transcription and at most one formatting call.
    const reserve=await fetch(url+"/rest/v1/rpc/nero_lyrics_ai_reserve_pilot",{
      method:"POST",headers:{apikey:service,authorization:"Bearer "+service,"content-type":"application/json"},
      body:JSON.stringify({p_seconds:seconds}),redirect:"error",signal:AbortSignal.timeout(5000)
    });
    if(!reserve.ok)return reply(503,{status:"unavailable"});
    if(await reserve.json()!==true)return reply(429,{status:"limit"});
    const result=await transcribeLyrics(audio,key,{format:true});
    return reply(result.status==="unavailable"?503:200,result);
  }catch{return reply(503,{status:"unavailable"});}
  finally{buffer?.fill(0);}
});
