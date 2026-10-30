from pathlib import Path
import json,sys,shutil,numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from PIL import Image,ImageDraw,ImageFont
root=Path(sys.argv[1]);target=Path(sys.argv[2]);target.mkdir(parents=True,exist_ok=True);assets=target/'assets';assets.mkdir(exist_ok=True)
series={};metrics={};allruns={}
def mechanics(d):
 c=d['cells'];m=np.array([x['m'] for x in c]);p=np.array([x['p'] for x in c]);v=np.array([x['v'] for x in c]);r=np.array([x['r'] for x in c]);a,b=np.triu_indices(len(c),1);dist=np.linalg.norm(p[a]-p[b],axis=1)
 k=float(.5*np.sum(m[:,None]*v*v));u=float(np.sum(-6.6743e-11*m[a]*m[b]/np.sqrt(dist**2+64)));over=np.maximum(0,r[a]+r[b]-dist);s=float(np.sum(.5*m[a]*m[b]/(m[a]+m[b])*.08**2*over**2))
 return k,u,s
def clusters(d):
 c=d['cells'];p=np.array([x['p'] for x in c]);r=np.array([x['r'] for x in c]);parents=list(range(len(c)))
 def find(a):
  while parents[a]!=a:parents[a]=parents[parents[a]];a=parents[a]
  return a
 a,b=np.triu_indices(len(c),1);ds=np.linalg.norm(p[a]-p[b],axis=1)
 for x,y in zip(a[ds<1.18*(r[a]+r[b])],b[ds<1.18*(r[a]+r[b])]):parents[find(int(x))]=find(int(y))
 groups={}
 for i in range(len(c)):groups.setdefault(find(i),[]).append(i)
 biggest=max(groups.values(),key=len);mass=sum(c[i]['m'] for i in biggest)
 return {'clusters':len(groups),'largest-cluster-cells':len(biggest),'largest-cluster-mass-kg':mass,'largest-mass-fraction':mass/d['account']['mass-kg']}
for branch,label in [('control-cloud','64 m / 81 cells'),('fine-cloud','32 m / 648 cells')]:
 data=[json.loads(p.read_text()) for p in sorted((root/branch).glob('frame-*.json'))];allruns[branch]=data;series[branch]=[]
 start=data[0]['account'];k,u,s=mechanics(data[0]);scale=k+abs(u)+s
 energy=[(d['account']['energy-J']-start['energy-J'])/scale for d in data]
 com=[];radius=[]
 for d in data:
  m=np.array([c['m'] for c in d['cells']]);p=np.array([c['p'] for c in d['cells']]);center=np.average(p,axis=0,weights=m)
  radius.append(float(np.sqrt(np.average(np.sum((p-center)**2,axis=1),weights=m))))
 metrics[branch]={'label':label,'final-time-s':data[-1]['time-s'],'initial':start,'final':data[-1]['account'],'clusters':clusters(data[-1]),'mechanical-scale-J':scale,'max-abs-energy-drift-fraction':max(map(abs,energy)),'rms-radius-m':radius,'energy-drift':energy,'time':[d['time-s'] for d in data]}
# Native sequence has 11 exact snapshots per arm, matching the mapped indices.
maprows=json.loads((root/'cloud-render-map.json').read_text())
for row in maprows:
 b=row['branch'];i=row['render-index'];d=json.loads((root/b/row['source']).read_text());paths={}
 for view in ['composition','raw','resolution']:
  src=root/'cloud-native'/f'{view}-{i:04d}.png';dst=assets/f'{b}-{view}-{len(series[b]):03d}.png';shutil.copy2(src,dst);paths[view]='assets/'+dst.name
 series[b].append({'time':d['time-s'],'images':paths,'cells':d['account']['material-cells']})
(root/'cloud-comparison.json').write_text(json.dumps(metrics,indent=2));shutil.copy2(root/'cloud-comparison.json',target/'measurements.json')
plt.style.use('dark_background');fig,axs=plt.subplots(2,1,figsize=(10,6),sharex=True,layout='constrained')
for b,color in [('control-cloud','#a2b7ff'),('fine-cloud','#78e6bf')]:
 d=metrics[b];axs[0].plot(d['time'],d['rms-radius-m'],color=color,label=d['label']);axs[1].plot(d['time'],np.array(d['energy-drift'])*100,color=color)
axs[0].set_ylabel('Mass-weighted RMS radius / m');axs[0].legend();axs[0].set_title('Same macro cloud, different material discretization')
axs[1].set_ylabel('Energy drift / % of initial mechanical scale');axs[1].set_xlabel('Simulated seconds')
for ax in axs:ax.grid(alpha=.15)
fig.savefig(assets/'measurements.png',dpi=150);plt.close(fig)
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',20);frames=[]
for j in range(11):
 im=Image.new('RGB',(1280,400),(8,16,25));draw=ImageDraw.Draw(im)
 for k,b in enumerate(['control-cloud','fine-cloud']):
  src=target/series[b][j]['images']['composition'];p=Image.open(src).convert('RGB').crop((180,205,1080,660)).resize((640,360),Image.Resampling.LANCZOS);im.paste(p,(k*640,40));draw.text((k*640+15,10),metrics[b]['label']+f' / {j*240} s',font=font,fill='#dcefe8')
 frames.append(im)
frames[0].save(root/'fine-cloud-comparison.gif',save_all=True,append_images=frames[1:],duration=200,loop=0)
frames[-1].save(root/'fine-cloud-comparison.png')
rows=''.join(f"<tr><td>{d['label']}</td><td>{d['clusters']['largest-cluster-cells']}</td><td>{d['clusters']['largest-mass-fraction']*100:.1f}%</td><td>{d['max-abs-energy-drift-fraction']*100:.2f}%</td></tr>" for d in metrics.values())
html='''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Truth · Finer material assembly</title><style>:root{color-scheme:dark;font:17px/1.6 system-ui;background:#081019;color:#dfedf1}body{max-width:1120px;margin:auto;padding:28px 20px 64px}a{color:#87e1c4}nav{display:flex;gap:22px;flex-wrap:wrap;font-size:14px;margin-bottom:42px}.eyebrow{letter-spacing:.14em;font-size:12px;color:#87d7bd}h1{font-size:clamp(36px,6vw,62px);line-height:1.08;letter-spacing:-.03em}h2{margin-top:42px}p{max-width:900px;color:#b5cad6}.controls{display:flex;gap:10px;flex-wrap:wrap;margin:24px 0}button,select{background:#182d38;color:#e1f6ed;border:1px solid #446777;border-radius:7px;padding:10px 13px}#screen{display:block;width:100%;border:1px solid #345261;border-radius:12px}input{width:100%;accent-color:#8bdfc2}#status{color:#8ce2bf}table{border-collapse:collapse;width:100%;font-size:14px}th,td{padding:12px;border-bottom:1px solid #345261;text-align:left}.note{background:#142731;border-left:3px solid #83a6ff;padding:20px}.plot{width:100%}footer{font-size:13px;color:#89a4b2;margin-top:42px}</style></head><body><nav><a href="../">← Planetary Lab</a><a href="../rotation/">Spin and refinement test</a><a href="../adaptive/">Adaptive cycle</a></nav><div class="eyebrow">EXPERIMENT 05 · 24 OCTOBER 2026</div><h1>Eight times as many cells.<br>Start from the cloud.</h1><p>A fresh assembly experiment repeats the same 81-carrier macro cloud with either 64 m material cells or 648 cells at 32 m. Both runs cover 2,400 simulated seconds with the same 0.5 s timestep and Barnes–Hut tolerance 0.5. The finer cells begin before neighboring macro parcels collide.</p><div class="controls"><select id="branch" aria-label="Material resolution"><option value="fine-cloud">32 m / 648 cells</option><option value="control-cloud">64 m / 81 cells</option></select><select id="view" aria-label="Render view"><option value="composition">Smooth material</option><option value="raw">Raw material cells</option></select><button id="play">Play</button><button id="end">Final state</button></div><p id="status" aria-live="polite"></p><img id="screen" alt="Actual native Truth material assembly capture"><input id="seek" type="range" min="0" max="10" value="0" aria-label="Simulation frame"><p>Switch physical resolution at the same simulated time. Native snapshots are sampled every 240 s. Both smooth views use a 6 m surface extraction grid.</p><h2>Measured outcomes</h2><table><tr><th>Discretization</th><th>Largest cluster cells</th><th>Mass in largest cluster</th><th>Maximum energy drift</th></tr>TABLE_ROWS</table><img class="plot" src="assets/measurements.png" alt="Mass-weighted radius and energy error in the two discretizations"><h2>A resolution experiment, not a convergence claim.</h2><div class="note"><p>Each coarse carrier is replaced by eight equal-volume cells before the assembly run. Total material stocks and momentum match to floating-point precision. This reveals within-carrier gravity and changes granular contact geometry, so each branch has its own mechanical-energy baseline.</p><p>The same initial macro distribution does not make these two discrete granular models mathematically equivalent. The result tests sensitivity to resolution. It does not establish a continuum material law, planetary realism or quantitative impact accuracy.</p></div><p>The 32 m run is a real finer physical discretization. The 6 m surface grid is a rendering choice. Water remains an explicit finite stock; no new reaction chemistry, fluid solver or Primordia feedback is active in this experiment.</p><footer><a href="measurements.json">Inspect recorded measurements</a> · Existing single ECS and renderer · See the spin experiment for integration and conservation checks.</footer><script>const data=SERIES_DATA;let index=0,playing=false,timer;const $=id=>document.getElementById(id);function show(){const f=data[$('branch').value][index];$('screen').src=f.images[$('view').value];$('seek').value=index;$('status').textContent=`${f.time.toFixed(0)} simulated seconds · ${f.cells} active material cells`}function loop(){if(!playing)return;index=(index+1)%11;show();timer=setTimeout(loop,450)}$('branch').onchange=show;$('view').onchange=show;$('seek').oninput=e=>{index=+e.target.value;show()};$('end').onclick=()=>{index=10;show()};$('play').onclick=()=>{playing=!playing;$('play').textContent=playing?'Pause':'Play';clearTimeout(timer);if(playing)loop()};show();</script></body></html>'''
html=html.replace('TABLE_ROWS',rows).replace('SERIES_DATA',json.dumps(series,separators=(',',':')));(target/'index.html').write_text(html)
print(json.dumps({b:{k:v for k,v in d.items() if k in ['clusters','max-abs-energy-drift-fraction']} for b,d in metrics.items()},indent=2))
