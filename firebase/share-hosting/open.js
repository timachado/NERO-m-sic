"use strict";
const id=location.pathname.match(/^\/s\/([a-f0-9]{32})$/);
if(id){
 const a=document.getElementById("open");
 a.href="intent://"+location.host+"/s/"+id[1]+"#Intent;scheme=https;package=br.com.timachado.neromusic;end";
 a.hidden=false;
}else document.getElementById("status").textContent="Link inválido.";
