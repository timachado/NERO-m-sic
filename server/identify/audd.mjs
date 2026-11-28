// Server-only AudD adapter. Never expose to the mobile client or call before
// account authorization and atomic quota reservation. No live credentials here.
const MAX_WAV = 256044;
export function validIdentifyWav(audio) {
  if (!(audio instanceof Uint8Array) || audio.byteLength !== MAX_WAV) return false;
  const view = new DataView(audio.buffer, audio.byteOffset, audio.byteLength);
  const tag = (offset, text) => [...text].every((ch, i) => audio[offset+i] === ch.charCodeAt(0));
  return tag(0,"RIFF") && view.getUint32(4,true) === MAX_WAV-8 &&
    tag(8,"WAVE") && tag(12,"fmt ") && view.getUint32(16,true) === 16 &&
    view.getUint16(20,true) === 1 && view.getUint16(22,true) === 1 &&
    view.getUint32(24,true) === 16000 && view.getUint32(28,true) === 32000 &&
    view.getUint16(32,true) === 2 && view.getUint16(34,true) === 16 &&
    tag(36,"data") && view.getUint32(40,true) === MAX_WAV-44;
}
export function normalizeAudD(body) {
  if (body?.status !== "success") return {status:"unavailable"};
  if (body.result === null) return {status:"no_match"};
  const result=body.result;
  if (!result || typeof result.title !== "string" || typeof result.artist !== "string")
    return {status:"unavailable"};
  const title=result.title.trim(), artist=result.artist.trim();
  if (!title || !artist || title.length>300 || artist.length>300) return {status:"unavailable"};
  return {status:"match",title,artist,album:typeof result.album==="string"?result.album.slice(0,300):""};
}
export async function recognizeAudD(audio, token, fetcher=fetch) {
  if (!validIdentifyWav(audio) || typeof token !== "string" || !token.trim())
    return {status:"unavailable"};
  const form=new FormData();
  form.set("api_token",token);
  form.set("file",new Blob([audio],{type:"audio/wav"}),"identify.wav");
  try {
    const response=await fetcher("https://api.audd.io/",{
      method:"POST",body:form,redirect:"error",signal:AbortSignal.timeout(18000)
    });
    if (!response.ok || !response.body) return {status:"unavailable"};
    const reader=response.body.getReader();
    let size=0; const decoder=new TextDecoder(); let text="";
    try {
      while (true) {
        const {done,value}=await reader.read();
        if (done) break;
        size+=value.byteLength;
        if (size>16384) { await reader.cancel(); return {status:"unavailable"}; }
        text+=decoder.decode(value,{stream:true});
      }
      text+=decoder.decode();
      return normalizeAudD(JSON.parse(text));
    } finally {reader.releaseLock();}
  } catch {return {status:"unavailable"};}
}
