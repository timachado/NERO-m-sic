// Original synthetic test signals only. No external music, accounts or credentials.
const RATE=22050;
const DURATION=25;
function wav(frequency){
  const count=RATE*DURATION, data=new Uint8Array(44+count*2), v=new DataView(data.buffer);
  function ascii(offset,s){for(let i=0;i<s.length;i++)data[offset+i]=s.charCodeAt(i);}
  ascii(0,"RIFF");v.setUint32(4,data.length-8,true);ascii(8,"WAVE");ascii(12,"fmt ");
  v.setUint32(16,16,true);v.setUint16(20,1,true);v.setUint16(22,1,true);
  v.setUint32(24,RATE,true);v.setUint32(28,RATE*2,true);v.setUint16(32,2,true);v.setUint16(34,16,true);
  ascii(36,"data");v.setUint32(40,count*2,true);
  for(let i=0;i<count;i++){
    const t=i/RATE, phase=t%1, envelope=Math.min(1,phase*30,(1-phase)*30);
    const value=phase<0.35?Math.round(1800*envelope*Math.sin(2*Math.PI*frequency*t)):0;
    v.setInt16(44+i*2,value,true);
  }
  return data;
}
const tracks={"/track-a.wav":wav(440),"/track-b.wav":wav(660)};
export default {fetch(request){
  const url=new URL(request.url);
  if(!["GET","HEAD"].includes(request.method))return new Response("Method not allowed",{status:405,headers:{Allow:"GET, HEAD"}});
  if(url.pathname==="/")return new Response("NERO Connect native audio diagnostic — original synthetic test signals only.",{headers:{"content-type":"text/plain; charset=utf-8","x-robots-tag":"noindex"}});
  const bytes=Object.prototype.hasOwnProperty.call(tracks,url.pathname)?tracks[url.pathname]:null;
  if(!bytes)return new Response("Not found",{status:404});
  const headers={"content-type":"audio/wav","accept-ranges":"bytes","cache-control":"public, max-age=86400","x-content-type-options":"nosniff","x-robots-tag":"noindex"};
  let start=0,end=bytes.length-1,status=200;
  const range=request.headers.get("Range");
  if(range){
    const m=/^bytes=(\d*)-(\d*)$/.exec(range);
    if(!m||(!m[1]&&!m[2]))return new Response(null,{status:416,headers:{"content-range":"bytes */"+bytes.length}});
    if(!m[1]){const n=Number(m[2]);if(!Number.isSafeInteger(n)||n<=0)return new Response(null,{status:416,headers:{"content-range":"bytes */"+bytes.length}});start=Math.max(0,bytes.length-n);}
    else{start=Number(m[1]);if(m[2])end=Math.min(end,Number(m[2]));}
    if(!Number.isSafeInteger(start)||!Number.isSafeInteger(end)||start<0||start>end||start>=bytes.length)return new Response(null,{status:416,headers:{"content-range":"bytes */"+bytes.length}});
    status=206;headers["content-range"]="bytes "+start+"-"+end+"/"+bytes.length;
  }
  headers["content-length"]=String(end-start+1);
  return new Response(request.method==="HEAD"?null:bytes.slice(start,end+1),{status,headers});
}};
