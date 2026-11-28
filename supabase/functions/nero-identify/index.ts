// DRAFT: not deployed. Keep provider and pilot allowlist configured server-side.
// Bundle this adapter beside index.ts before deploying the function.
import {validIdentifyWav, recognizeAudD} from "./audd.mjs";

const reply=(status:number,body:unknown)=>new Response(JSON.stringify(body),{status,headers:{"content-type":"application/json","cache-control":"no-store"}});
const MAX=256044;
Deno.serve(async (req:Request)=>{
  if(req.method!=="POST")return reply(405,{status:"unavailable"});
  const token=Deno.env.get("AUDD_API_TOKEN");
  const url=Deno.env.get("SUPABASE_URL");
  const service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const allow=new Set((Deno.env.get("NERO_IDENTIFY_PILOT_UIDS")||"").split(",").map(s=>s.trim()).filter(Boolean));
  if(!token||!url||!service||!allow.size)return reply(503,{status:"unavailable"});
  const auth=req.headers.get("authorization")||"";
  if(!/^Bearer [A-Za-z0-9_.-]{20,8192}$/.test(auth))return reply(401,{status:"unavailable"});
  if(req.headers.get("content-type")?.split(";")[0]!=="audio/wav")return reply(415,{status:"unavailable"});
  const length=req.headers.get("content-length");
  if(length!==null&&Number(length)!==MAX)return reply(413,{status:"unavailable"});
  let audio:Uint8Array|undefined;
  try{
    // Auth server validates the user's token; never trust client-supplied UID or premium flags.
    const userRes=await fetch(url+"/auth/v1/user",{headers:{apikey:service,authorization:auth},redirect:"error",signal:AbortSignal.timeout(5000)});
    if(!userRes.ok)return reply(401,{status:"unavailable"});
    const user=await userRes.json();
    if(typeof user.id!=="string"||user.is_anonymous===true||!allow.has(user.id))return reply(403,{status:"unavailable"});
    if(!req.body)return reply(400,{status:"unavailable"});
    audio=new Uint8Array(MAX);
    const reader=req.body.getReader();let size=0;
    try{
      while(true){
        const {done,value}=await reader.read();
        if(done)break;
        if(size+value.byteLength>MAX){await reader.cancel();return reply(413,{status:"unavailable"});}
        audio.set(value,size);size+=value.byteLength;
      }
    }finally{reader.releaseLock();}
    if(size!==MAX||!validIdentifyWav(audio))return reply(400,{status:"unavailable"});
    const reserve=await fetch(url+"/rest/v1/rpc/nero_identify_reserve_pilot",{
      method:"POST",headers:{apikey:service,authorization:"Bearer "+service,"content-type":"application/json"},
      body:JSON.stringify({p_user:user.id}),redirect:"error",signal:AbortSignal.timeout(5000)
    });
    if(!reserve.ok)return reply(503,{status:"unavailable"});
    if(await reserve.json()!==true)return reply(429,{status:"limit"});
    const result=await recognizeAudD(audio,token);
    return reply(result.status==="unavailable"?503:200,result);
  }catch{return reply(503,{status:"unavailable"});}
  finally{audio?.fill(0);}
});
