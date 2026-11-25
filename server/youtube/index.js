// Firebase Functions v2. Deploy only after setting up a dedicated NERO Firebase project.
// Required secrets: YOUTUBE_DATA_API_KEY; API restricted to YouTube Data API v3.
const {onRequest}=require("firebase-functions/v2/https");
const {defineSecret}=require("firebase-functions/params");
const {initializeApp}=require("firebase-admin/app");
const {getAuth}=require("firebase-admin/auth");
const {getFirestore,FieldValue}=require("firebase-admin/firestore");
initializeApp();
const apiKey=defineSecret("YOUTUBE_DATA_API_KEY");
exports.neroYouTubeSearch=onRequest({secrets:[apiKey],maxInstances:2,timeoutSeconds:20},async(req,res)=>{
  res.set("Cache-Control","no-store");
  if(req.method!=="GET"){res.status(405).end();return;}
  const q=typeof req.query.q==="string"?req.query.q.trim():"";
  if(q.length<3||q.length>100){res.status(400).end();return;}
  let uid;
  try{const token=(req.headers.authorization||"").match(/^Bearer (\S+)$/);if(!token)throw Error();
      uid=(await getAuth().verifyIdToken(token[1],true)).uid;}
  catch{res.status(401).end();return;}
  try{
    // Conservative project-wide daily cap plus per-account cap. UTC buckets;
    // upstream quota remains authoritative. No query or tokens are persisted.
    const day=new Date().toISOString().slice(0,10);
    const db=getFirestore(), global=db.doc("nero_youtube_quota/"+day);
    const personal=global.collection("users").doc(uid);
    const admitted=await db.runTransaction(async tx=>{
      const a=await tx.get(global),b=await tx.get(personal);
      if((a.get("count")||0)>=75||(b.get("count")||0)>=10)return false;
      tx.set(global,{count:FieldValue.increment(1)},{merge:true});
      tx.set(personal,{count:FieldValue.increment(1)},{merge:true});return true;
    });
    if(!admitted){res.status(429).end();return;}
    const url=new URL("https://www.googleapis.com/youtube/v3/search");
    for(const [k,v] of Object.entries({part:"snippet",type:"video",videoCategoryId:"10",videoEmbeddable:"true",videoSyndicated:"true",q,maxResults:"15",regionCode:"BR",relevanceLanguage:"pt",key:apiKey.value()}))url.searchParams.set(k,v);
    const upstream=await fetch(url,{signal:AbortSignal.timeout(10000)});
    if(!upstream.ok){res.status(upstream.status===403||upstream.status===429?429:502).end();return;}
    const data=await upstream.json();
    const items=(data.items||[]).filter(x=>/^[A-Za-z0-9_-]{11}$/.test(x.id?.videoId||"")).map(x=>({id:x.id.videoId,title:x.snippet?.title||"",channel:x.snippet?.channelTitle||""}));
    res.json({items});
  }catch{res.status(502).end();}
});
