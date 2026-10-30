import json,pathlib,sys,numpy as np
root=pathlib.Path(sys.argv[1]); names=['detailed-64m','rotating-64m','translating-64m','detailed-32m']
runs={n:[json.loads(p.read_text()) for p in sorted((root/n).glob('frame-*.json'))] for n in names}
def collapse(row,fine=False):
 groups={}
 for c in row['cells']:
  k=c['cell'].get('parent-source-id',c['source-id']) if fine else c['source-id']
  if k not in groups:groups[k]=[0.,np.zeros(3)]
  groups[k][0]+=c['m'];groups[k][1]+=c['m']*np.array(c['p'])
 return {k:(v[0],v[1]/v[0]) for k,v in groups.items()}
def error(a,b,fine=False):
 a=collapse(a);b=collapse(b,fine)
 keys=sorted(a);m=np.array([a[k][0] for k in keys]);pa=np.array([a[k][1] for k in keys]);pb=np.array([b[k][1] for k in keys]);
 pa-=np.average(pa,axis=0,weights=m);pb-=np.average(pb,axis=0,weights=m)
 diff=np.linalg.norm(pa-pb,axis=1)
 return float(np.sqrt(np.average(diff*diff,weights=m))),float(np.max(diff))
summary={};curves={}
for n,rows in runs.items():
 start=rows[0]['account'];end=rows[-1]['account'];l=np.array(start['angular-momentum']);lf=np.array(end['angular-momentum'])
 errs=[error(a,b,n=='detailed-32m') for a,b in zip(runs['detailed-64m'],rows)]
 curves[n]={'time':[r['time-s']-rows[0]['time-s'] for r in rows],'rms':[x[0] for x in errs],'max':[x[1] for x in errs]}
 summary[n]={'cells':end['material-cells'],'active-bodies':end['physics-bodies'],'final-rms-position-error-m':errs[-1][0],'final-max-position-error-m':errs[-1][1],'angular-momentum-relative-change':float(np.linalg.norm(lf-l)/np.linalg.norm(l)),'energy-change-J':end['energy-J']-start['energy-J'],'max-water-residual-kg':max(abs(r['account']['water-kg']-start['water-kg']) for r in rows),'timing':json.loads((root/n/'timing.json').read_text())}
result={'method':'Mass-weighted COM-aligned positions; finer subcells first aggregated by source parent. Each branch has its own energy baseline.','interval-s':120,'branches':summary,'curves':curves,'study':json.loads((root/'study.json').read_text())}
(root/'comparison.json').write_text(json.dumps(result,indent=2));print(json.dumps(summary,indent=2))
