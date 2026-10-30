"""Numerical readout of matched continuum impact cases and refinement transfers."""
from pathlib import Path
import json,sys,numpy as np
root=Path(sys.argv[1]);runs={p.name:[json.loads(f.read_text()) for f in sorted(p.glob('frame-*.json'))] for p in (root/'run-extended').iterdir() if p.is_dir()}
summary={};curves={}
for name,data in runs.items():
 obs=[r['observation'] for r in data];initial=obs[0];e0=initial['energy-J'];p0=np.array(initial['momentum']);l0=initial['angular-momentum'];last=obs[-1]
 drift=[(o['energy-J']-e0)/e0 for o in obs]
 summary[name]={'initial-energy-J':e0,'maximum-absolute-energy-error-fraction':max(map(abs,drift)),'final-energy-error-fraction':drift[-1],'maximum-linear-momentum-residual-kg-m-s':max(float(np.linalg.norm(np.array(o['momentum'])-p0)) for o in obs),'maximum-angular-momentum-residual-kg-m2-s':max(abs(o['angular-momentum']-l0) for o in obs),'final-heat-J':last['heat-J'],'maximum-damage':max(o['maximum-damage'] for o in obs),'maximum-overlap-m':max(o['maximum-overlap-m'] for o in obs),'minimum-jacobian':min(o['min-J'] for o in obs),'final-elements':last['elements'],'coarsen-rejections':last['coarsen-rejections'],'transfers':last['transfers']}
 curves[name]={'time':[o['time-s'] for o in obs],'energy-error':drift,'heat-J':[o['heat-J'] for o in obs],'elements':[o['elements'] for o in obs],'maximum-damage':[o['maximum-damage'] for o in obs],'peak-pressure-pa':[o['peak-pressure-pa'] for o in obs]}
# Integrate displacement error using the fine consistent mass matrix and the
# same piecewise-affine field at every time, including after coarsening.
for dt in ['0.5','0.25']:
 fine={r['observation']['time-s']:r for r in runs['fine-dt-'+dt]}
 ref=np.array(next(iter(fine.values()))['field']['ref']);tris=next(iter(fine.values()))['field']['tri'];M=np.zeros((len(ref),len(ref)))
 for ids in tris:
  q=ref[ids];V=.5*np.linalg.det(np.column_stack([q[1]-q[0],q[2]-q[0]]))*64.;block=np.ones((3,3))+np.eye(3)
  M[np.ix_(ids,ids)]+=3000.*V/12.*block
 base_tri=runs['coarse-dt-'+dt][0]['field']['tri'];edges=sorted(set(tuple(sorted(e)) for t in base_tri for e in [(t[0],t[1]),(t[1],t[2]),(t[2],t[0])]))
 for mode in ['coarse','adaptive']:
  errs=[]
  for r in runs[mode+'-dt-'+dt]:
   t=r['observation']['time-s']
   if t not in fine:continue
   a=np.array(r['field']['x']);b=np.array(fine[t]['field']['x'])
   if len(a)<len(b):a=np.vstack([a,np.array([(a[i]+a[j])/2. for i,j in edges])])
   delta=a-b;error=float(np.sqrt(sum(delta[:,k]@M@delta[:,k] for k in range(2))/M.sum()))
   errs.append((t,error))
  summary[mode+'-dt-'+dt]['maximum-mass-weighted-position-rms-to-fine-m']=max(x[1] for x in errs)
  curves[mode+'-dt-'+dt]['position-difference-time']=[x[0] for x in errs];curves[mode+'-dt-'+dt]['position-difference-m']=[x[1] for x in errs]
result={'cases':summary,'curves':curves,'method':'Energy includes kinetic, elastic, contact and released heat; no gravity or external work. Position RMS integrates the same piecewise-affine field using the fine consistent mass matrix. Coarse geometry is prolonged before comparison. Extrema are over retained sampled states.'}
(root/'summary.json').write_text(json.dumps(result,indent=2));print(json.dumps(summary,indent=2))
