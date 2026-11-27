const {chromium}=require('playwright');const path=require('path');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:800,height:800}});p.on('pageerror',e=>console.log('ERR',e.message));
await p.goto('file://'+path.resolve('prev.html'));
const jobs=[];for(const k of ['month','today','hitome','himekuri','mini'])for(const l of ['ja','en'])for(const t of ['light','dark'])jobs.push([k,l,t]);
for(const l of ['ja','en'])jobs.push(['dot',l,'dark']);
for(const [k,l,t] of jobs){await p.evaluate(a=>render(...a),[k,l,t]);await p.waitForTimeout(150);await (await p.$('#w')).screenshot({path:`out/${k}_${l}_${t}.png`,omitBackground:true});}
await b.close();})();
