// C1 HAIRLINE — restraint: matte black, hairline grid, crop marks, one orange edge
function bg1(){let b=g.createLinearGradient(0,0,0,H);b.addColorStop(0,'#0D0D0D');b.addColorStop(1,'#070707');g.fillStyle=b;g.fillRect(0,0,W,H);
  let r=g.createRadialGradient(720,560,0,720,560,1100);r.addColorStop(0,'rgba(255,255,255,0.035)');r.addColorStop(1,'rgba(255,255,255,0)');g.fillStyle=r;g.fillRect(0,0,W,H);}
const HL='rgba(255,255,255,0.07)';
function crop(x,y,w,h,arm=38,col=ORANGE,lw=3,skip=[]){g.strokeStyle=col;g.lineWidth=lw;
  [[x,y,1,1],[x+w,y,-1,1],[x+w,y+h,-1,-1],[x,y+h,1,-1]].forEach(([cx,cy,sx,sy],i)=>{if(skip.includes(i))return;g.beginPath();g.moveTo(cx,cy+sy*arm);g.lineTo(cx,cy);g.lineTo(cx+sx*arm,cy);g.stroke();});}
function plus(x,y,col,s=10){g.strokeStyle=col;g.lineWidth=2;g.beginPath();g.moveTo(x-s,y);g.lineTo(x+s,y);g.moveTo(x,y-s);g.lineTo(x,y+s);g.stroke();}
function home1(){
  // display well for the transparent widget
  rr(WIDGET.x,WIDGET.y,WIDGET.w,WIDGET.h,52);g.fillStyle='#040404';g.fill();g.strokeStyle=HL;g.lineWidth=1.5;g.stroke();
  crop(WIDGET.x-20,WIDGET.y-20,WIDGET.w+40,WIDGET.h+40,40,ORANGE,3,[3]);
  g.fillStyle=ORANGE;g.fillRect(WIDGET.x+4,WIDGET.y-58,12,12);
  txt('KOYOMI',WIDGET.x+28,WIDGET.y-54,{size:26,color:'#8c8c8c',spacing:6});
  txt('DOT DISPLAY',WIDGET.x+WIDGET.w-70,WIDGET.y-52,{size:22,color:'#5e5e5e',align:'right',spacing:4});
  txt('01',WIDGET.x+WIDGET.w-4,WIDGET.y-54,{size:26,color:ORANGE,align:'right',spacing:2});
  // folder zone: a faint lifted L with the orange top + left edge
  lPath(40);g.fillStyle='rgba(255,255,255,0.048)';g.fill();g.strokeStyle='rgba(255,255,255,0.11)';g.lineWidth=1.5;g.stroke();
  g.fillStyle=ORANGE;g.fillRect(colB[0]+40,rowB[0]-1.5,colB[3]-colB[0]-80,3);g.fillRect(colB[0]-1.5,rowB[0]+40,3,rowB[4]-rowB[0]-80);
  txt('FOLDERS',colB[0]+2,rowB[0]-26,{size:21,color:ORANGE,spacing:4});
  FOLDERS.forEach(([cx,ry],i)=>txt('0'+(i+1),colX[cx]-150,rowB[ry]+30,{size:19,color:'rgba(242,113,28,0.85)'}));
  // hairline grid over the app cells only
  g.strokeStyle='rgba(255,255,255,0.045)';g.lineWidth=1.5;
  for(const x of [colB[2],colB[3]]){g.beginPath();g.moveTo(x,rowB[1]+30);g.lineTo(x,rowB[4]-30);g.stroke();}
  for(const y of [rowB[2],rowB[3]]){g.beginPath();g.moveTo(colB[1]+30,y);g.lineTo(colB[4]-30,y);g.stroke();}
  for(const x of [colB[2],colB[3]])for(const y of [rowB[2],rowB[3]])plus(x,y,(x===colB[3]&&y===rowB[2])?ORANGE:'rgba(255,255,255,0.28)');
  // scale along the grid's right edge
  for(let y=rowB[1];y<=rowB[4];y+=46){const major=((y-rowB[1])/46)%4===0;g.fillStyle=major?'rgba(242,113,28,0.8)':'rgba(255,255,255,0.12)';g.fillRect(colB[4]-(major?18:10),y,major?18:10,2);}
  // dock: a hairline shelf with an orange index
  g.fillStyle='rgba(255,255,255,0.08)';g.fillRect(DOCK.x,DOCK.y,DOCK.w,1.5);
  g.fillStyle=ORANGE;g.fillRect(720-50,DOCK.y-1,100,4);
  txt('DOCK',DOCK.x,DOCK.y-24,{size:19,color:'#5e5e5e',spacing:4});
  txt('K-01 / 2026',DOCK.x+DOCK.w,DOCK.y-24,{size:18,color:'#4e4e4e',align:'right',spacing:3});
}
function lock1(){
  const fx=300,fy=262,fw=840,fh=840;
  crop(fx,fy,fw,fh,44);
  g.fillStyle=ORANGE;g.fillRect(fx,fy-52,12,12);
  txt('KOYOMI',fx+24,fy-46,{size:26,color:'#8c8c8c',spacing:6});
  txt('LOCK',fx+fw-60,fy-46,{size:22,color:'#5e5e5e',align:'right',spacing:4});txt('00',fx+fw,fy-46,{size:26,color:ORANGE,align:'right'});
  // ruler under the clock
  for(let i=0;i<=28;i++){const x=fx+i*fw/28,major=i%7===0;g.fillStyle=major?ORANGE:'rgba(255,255,255,0.14)';g.fillRect(x-1,fy+fh+28,2,major?22:12);}
  txt('STANDBY',fx,fy+fh+74,{size:19,color:'#5e5e5e',spacing:4});
  // notifications stack up from a baseline: rail + base, fading upward
  const bx=48,bw=1342,base=LOCK.base,top=1260;
  let rail=g.createLinearGradient(0,top,0,base);rail.addColorStop(0,'rgba(242,113,28,0)');rail.addColorStop(1,ORANGE);g.fillStyle=rail;g.fillRect(bx-2,top,3,base-top);
  g.fillStyle='rgba(255,255,255,0.09)';g.fillRect(bx,base,bw,1.5);g.fillStyle=ORANGE;g.fillRect(bx,base-1,220,4);
  for(let n=1;n<=3;n++){const y=base-n*270;g.fillStyle='rgba(242,113,28,'+(0.8-n*0.2)+')';g.fillRect(bx,y,16,2);}
  txt('NOTICE',bx+30,base+30,{size:19,color:ORANGE,spacing:4});stackMark(bx+bw,base+30,'#5e5e5e',18);
  // fingerprint dial
  const [px,py]=LOCK.fp;g.strokeStyle='rgba(255,255,255,0.09)';g.lineWidth=1.5;g.beginPath();g.arc(px,py,132,0,7);g.stroke();
  for(let i=0;i<60;i++){const a=i/60*Math.PI*2-Math.PI/2,m=i%15===0,r1=146,r2=m?166:154;g.strokeStyle=i===0?ORANGE:`rgba(255,255,255,${m?0.25:0.08})`;g.lineWidth=i===0?3:1.5;g.beginPath();g.moveTo(px+Math.cos(a)*r1,py+Math.sin(a)*r1);g.lineTo(px+Math.cos(a)*r2,py+Math.sin(a)*r2);g.stroke();}
  for(const [sx,sy] of LOCK.sc){g.strokeStyle='rgba(255,255,255,0.10)';g.lineWidth=1.5;g.beginPath();g.arc(sx,sy,88,0,7);g.stroke();g.strokeStyle=ORANGE;g.lineWidth=3;g.beginPath();g.arc(sx,sy,100,-0.6*Math.PI,-0.4*Math.PI);g.stroke();}
}
