import { readFileSync } from "node:fs";
import { randomBytes } from "node:crypto";
import { test } from "node:test";
import assert from "node:assert/strict";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, collection, getDoc, getDocs, setDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp, Timestamp } from "firebase/firestore";

test("NERO protected sharing: isolated Firestore policy", async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "Refuse to run outside emulator");
  const env = await initializeTestEnvironment({projectId:"demo-nero-share",firestore:{rules:readFileSync(new URL("./firestore.rules",import.meta.url),"utf8")}});
  const google = uid => env.authenticatedContext(uid,{firebase:{sign_in_provider:"google.com",identities:{}}}).firestore();
  const sender=google("sender"), receiver=google("receiver");
  const visitor=env.unauthenticatedContext().firestore();
  const password=env.authenticatedContext("password-user",{firebase:{sign_in_provider:"password",identities:{}}}).firestore();
  const id=randomBytes(16).toString("hex");
  const protectedDoc=(db,key=id)=>doc(db,"nero_shared",key);
  const previewDoc=(db,key=id)=>doc(db,"nero_shared_previews",key);
  const payload=JSON.stringify({format:"nero-playlist-v1",name:"Teste",songs:[{title:"Faixa",artist:"Artista",videoId:"abcdefghijk"}]});
  const content=()=>({owner:"sender",createdAt:serverTimestamp(),payload});
  const preview=()=>({createdAt:serverTimestamp(),title:"Teste",subtitle:"Artista",count:1,single:true});
  try {
    await env.clearFirestore();
    const batch=writeBatch(sender);
    batch.set(protectedDoc(sender),content());
    batch.set(previewDoc(sender),preview());
    await assertSucceeds(batch.commit());
    const visible=await assertSucceeds(getDoc(previewDoc(visitor)));
    assert.deepEqual(Object.keys(visible.data()).sort(),["count","createdAt","single","subtitle","title"]);
    await assertFails(getDoc(protectedDoc(visitor)));
    await assertFails(getDoc(protectedDoc(password)));
    const opened=await assertSucceeds(getDoc(protectedDoc(receiver)));
    assert.equal(opened.data().payload,payload);
    await assertFails(getDocs(collection(visitor,"nero_shared_previews")));
    await assertFails(getDocs(collection(receiver,"nero_shared")));
    await assertFails(updateDoc(protectedDoc(receiver),{payload:"changed"}));
    await assertFails(deleteDoc(protectedDoc(receiver)));
    await assertFails(setDoc(previewDoc(sender,randomBytes(16).toString("hex")),preview()));
    const forged=randomBytes(16).toString("hex");
    const invalid=writeBatch(sender);
    invalid.set(protectedDoc(sender,forged),content());
    invalid.set(previewDoc(sender,forged),{...preview(),videoId:"abcdefghijk"});
    await assertFails(invalid.commit());
    await assertFails(setDoc(protectedDoc(password,randomBytes(16).toString("hex")),content()));
    const expired=randomBytes(16).toString("hex");
    await env.withSecurityRulesDisabled(async ctx=>{
      const db=ctx.firestore();
      const createdAt=Timestamp.fromMillis(Date.now()-8*86400000);
      await setDoc(protectedDoc(db,expired),{...content(),createdAt});
      await setDoc(previewDoc(db,expired),{...preview(),createdAt});
    });
    await assertFails(getDoc(previewDoc(visitor,expired)));
    await assertFails(getDoc(protectedDoc(receiver,expired)));
    await assertSucceeds(deleteDoc(protectedDoc(sender)));
    await assertFails(getDoc(previewDoc(visitor)));
    await assertFails(getDoc(protectedDoc(receiver)));
  } finally {await env.cleanup();}
});
