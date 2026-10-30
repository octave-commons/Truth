"""Exact triangle extrusion and mesh overlays for Truth's existing native renderer."""
from pathlib import Path
import json,sys,shutil,math
import numpy as np
root=Path(sys.argv[1]);out=root/'render';out.mkdir(exist_ok=True)
times=[0,20,40,50,60,70,80,90,100,110,120,140,180,240,360,500,700,900,1100,1300,1500,1800]
mapping=[]
def geometry(data,view):
 f=data['field'];obs=data['observation'];points=np.array(f['x']);center=np.array(obs['center']);thickness=64.;verts=[]
 def p(x,y,depth):return np.array([(x-center[0])/400.,depth/400.,(y-center[1])/400.])
 def tri(ps,color,normal=None):
  ps=np.array(ps);n=np.cross(ps[1]-ps[0],ps[2]-ps[0]) if normal is None else np.array(normal);n=n/max(1e-20,np.linalg.norm(n))
  for q in ps:verts.extend([*q,*n,*color])
 def color(i):
  if view=='pressure':
   v=max(-1.,min(1.,data['pressure'][i]/20000.))
   return [.25+.7*max(v,0),.55-.3*max(v,0),.78-.62*max(v,0)] if v>=0 else [.25,.55+.25*(-v),.78+.15*(-v)]
  if view=='damage':
   d=f['damage'][i]/.65;return (np.array([.42,.57,.65])*(1-d)+np.array([1.,.15,.06])*d).tolist()
  return [.38,.53,.59]
 edges={}
 for i,ids in enumerate(f['tri']):
  q=points[ids];co=color(i)
  for depth in [-thickness/2,thickness/2]:tri([p(x,y,depth) for x,y in q],co,normal=[0,-1 if depth<0 else 1,0])
  for a,b in zip(ids,[ids[1],ids[2],ids[0]]):edges.setdefault(tuple(sorted((a,b))),[]).append(i)
 for (a,b),owners in edges.items():
  qa,qb=points[[a,b]]
  if len(owners)==1:
   aa=p(*qa,-thickness/2);ab=p(*qa,thickness/2);ba=p(*qb,-thickness/2);bb=p(*qb,thickness/2);co=color(owners[0]);tri([aa,ba,bb],co);tri([aa,bb,ab],co)
  # Front-face ribbons are geometry, so the normal opaque pass retains them.
  direction=qb-qa;side=np.array([-direction[1],direction[0]]);side=.18*side/max(np.linalg.norm(side),1e-20)
  aa=p(*(qa-side),-32.15);ab=p(*(qa+side),-32.15);ba=p(*(qb-side),-32.15);bb=p(*(qb+side),-32.15)
  tri([aa,ba,bb],[.045,.08,.095],[0,-1,0]);tri([aa,bb,ab],[.045,.08,.095],[0,-1,0])
 projectile=f['projectile'];cx,cy=projectile['p'];radius=projectile['radius'];co=[.90,.47,.16]
 for i in range(48):
  a=2*math.pi*i/48;b=2*math.pi*(i+1)/48;qa=[cx+radius*math.cos(a),cy+radius*math.sin(a)];qb=[cx+radius*math.cos(b),cy+radius*math.sin(b)]
  for depth in [-32,32]:tri([p(cx,cy,depth),p(*qa,depth),p(*qb,depth)],co,[0,-1 if depth<0 else 1,0])
  aa=p(*qa,-32);ab=p(*qa,32);ba=p(*qb,-32);bb=p(*qb,32);tri([aa,ba,bb],co);tri([aa,bb,ab],co)
 array=np.array(verts,dtype='<f4').reshape(-1,9)
 assert np.isfinite(array).all();return array
for dt in ['0.5','0.25']:
 for mode in ['coarse','fine','adaptive']:
  case=mode+'-dt-'+dt;files=sorted((root/'run-extended'/case).glob('frame-*.json'));parsed=[(p,json.loads(p.read_text())) for p in files]
  for t in times:
   path,data=min(parsed,key=lambda a:abs(a[1]['observation']['time-s']-t))
   assert abs(data['observation']['time-s']-t)<1e-7,(case,t)
   index=len(mapping);stem=f'frame-{index:04d}'
   for view in ['pressure','damage','mesh']:geometry(data,view).tofile(out/(stem+'.'+view+'.mesh'))
   shutil.copy2(path,out/(stem+'.json'));shutil.copy2(path.with_suffix('.edn'),out/(stem+'.edn'))
   mapping.append({'case':case,'dt':dt,'mode':mode,'time':t,'index':index,'source':path.name})
(root/'render-map.json').write_text(json.dumps(mapping,indent=2));print('Native frames prepared',len(mapping))
