// Shared wallpaper scaffolding. Everything is drawn in "D" = the phone's on-screen layout
// (1440 x 3128, from the user's screenshots), then mapped back into the 1440x3120 image
// to undo the launcher's ~5% zoom.
const W=1440,H=3120,ORANGE='#F2711C';
const colX=[211,549,888,1227], rowY=[1162,1529,1897,2264];
const FOLDERS=[[0,0],[1,0],[2,0],[0,1],[0,2],[0,3]];
const GRID={x0:42,x1:1396,y0:1000,y1:2489}, rowB=[1000,1387,1754,2122,2489], colB=[42,380,718,1057,1396];
const WIDGET={x:50,y:252,w:1338,h:720};
const DOCK={x:42,y:2712,w:1354,h:232,cy:2828};
const LOCK={date:330,clockTop:430,clockBot:1024,base:2200,fp:[719,2384],sc:[[194,2931],[1246,2931]]};
const c=document.getElementById('c'),g=c.getContext('2d');
function rr(x,y,w,h,r){g.beginPath();g.moveTo(x+r,y);g.arcTo(x+w,y,x+w,y+h,r);g.arcTo(x+w,y+h,x,y+h,r);g.arcTo(x,y+h,x,y,r);g.arcTo(x,y,x+w,y,r);g.closePath();}
function txt(t,x,y,{size=22,color='#5a5a5a',align='left',font='JetBrains Mono',weight=500,spacing=2}={}){
  g.save();g.font=`${weight} ${size}px "${font}"`;g.fillStyle=color;g.textAlign=align;g.textBaseline='middle';
  if('letterSpacing' in g)g.letterSpacing=spacing+'px';g.fillText(t,x,y);g.restore();}
function lPath(r){ // L-shaped folder zone: top row cols 0-2 + left column
  const x0=colB[0],x1=colB[3],x2=colB[1],y0=rowB[0],y1=rowB[1],y2=rowB[4];
  g.beginPath();g.moveTo(x0+r,y0);g.lineTo(x1-r,y0);g.arcTo(x1,y0,x1,y0+r,r);g.lineTo(x1,y1-r);g.arcTo(x1,y1,x1-r,y1,r);
  g.lineTo(x2+r,y1);g.arcTo(x2,y1,x2,y1+r,r);g.lineTo(x2,y2-r);g.arcTo(x2,y2,x2-r,y2,r);g.lineTo(x0+r,y2);g.arcTo(x0,y2,x0,y2-r,r);
  g.lineTo(x0,y0+r);g.arcTo(x0,y0,x0+r,y0,r);g.closePath();}
function inL(x,y){return (y>=rowB[0]&&y<=rowB[1]&&x>=colB[0]&&x<=colB[3])||(x>=colB[0]&&x<=colB[1]&&y>=rowB[0]&&y<=rowB[4]);}
function grain(a=3){const img=g.getImageData(0,0,W,H),d=img.data;for(let i=0;i<d.length;i+=4){const n=(Math.random()-0.5)*a;d[i]+=n;d[i+1]+=n;d[i+2]+=n;}g.putImageData(img,0,0);}
async function paint(bgFn,drawFn){
  await document.fonts.load('40px Doto');await document.fonts.load('500 20px "JetBrains Mono"');
  g.setTransform(1,0,0,1,0,0);bgFn();                       // fills the whole image, incl. the cropped margin
  g.setTransform(0.94994,0,0,0.94994,36.46,59.45);drawFn(); // on-screen layout coords
  g.setTransform(1,0,0,1,0,0);grain();document.title='done';}

function stackMark(x,y,col='#4a4a4a',size=15){ // right-aligned "▲ STACK" without relying on the font's triangle
  g.save();g.font=`500 ${size}px "JetBrains Mono"`;if('letterSpacing' in g)g.letterSpacing='3px';const w=g.measureText('STACK').width;g.restore();
  txt('STACK',x,y,{size,color:col,align:'right',spacing:3});
  const tx=x-w-16;g.fillStyle=col;g.beginPath();g.moveTo(tx,y-6);g.lineTo(tx+7,y+6);g.lineTo(tx-7,y+6);g.closePath();g.fill();}
