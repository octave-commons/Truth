"""Read-only scalar-field surfaces of recorded Truth cells; not a physics solver."""
import argparse, json, pathlib, numpy as np
from scipy.ndimage import map_coordinates

CORNERS=np.array([[0,0,0],[1,0,0],[1,1,0],[0,1,0],[0,0,1],[1,0,1],[1,1,1],[0,1,1]])
TETS=[[0,5,1,6],[0,1,2,6],[0,2,3,6],[0,3,7,6],[0,7,4,6],[0,4,5,6]]
EDGES=np.array([[0,1],[1,2],[2,0],[0,3],[1,3],[2,3]])
CASES={1:[[0,3,2]],2:[[0,1,4]],3:[[1,4,2],[2,4,3]],4:[[1,2,5]],5:[[0,3,5],[0,5,1]],6:[[0,2,5],[0,5,4]],7:[[5,4,3]]}
CASES.update({15-k:[list(reversed(t)) for t in v] for k,v in list(CASES.items())})

def surface(data, spacing=12., iso=.28, view='composition'):
    cells=data['cells']; pos=np.array([c['p'] for c in cells]); radius=np.array([c['r'] for c in cells])
    support=radius*1.8
    lo=np.floor(np.min(pos-support[:,None]-spacing,axis=0)/spacing)*spacing
    hi=np.ceil(np.max(pos+support[:,None]+spacing,axis=0)/spacing)*spacing
    shape=tuple((np.rint((hi-lo)/spacing).astype(int)+1).tolist())
    if np.prod(shape)>10_000_000: raise ValueError(f'Explicit surface grid budget exceeded: {shape}')
    field=np.zeros(shape,dtype=np.float32); rgb=np.zeros(shape+(3,),dtype=np.float32)
    for c,p,r in zip(cells,pos,support):
        a=np.maximum(0,np.floor((p-r-lo)/spacing).astype(int)); b=np.minimum(shape,np.ceil((p+r-lo)/spacing).astype(int)+1)
        xx=np.ogrid[tuple(slice(a[i],b[i]) for i in range(3))]
        d2=sum(((xx[i]*spacing+lo[i]-p[i])/r)**2 for i in range(3))
        weight=np.maximum(0.,1.-d2)**3
        stocks=c['cell']; total=c['m']; water=stocks['water-kg']/total; iron=stocks['iron-kg']/total
        color=np.array([.30,.70,.95] if water>.2 else [.88,.47,.20] if iron>.5 else [.56,.52,.44])
        if view=='damage':
            # This contact-dissipation proxy saturates early; it is not fracture mechanics.
            damage=c.get('damage',0.); color=(1.-damage)*color+damage*np.array([1.,.14,.05])
        if view=='resolution': color=np.array([.25,.85,.65] if c.get('cached?',False) else [.48,.58,1.])
        sl=tuple(slice(a[i],b[i]) for i in range(3)); field[sl]+=weight; rgb[sl]+=weight[...,None]*color
    rgb/=np.maximum(field[...,None],1e-9)
    corners=[field[tuple(slice(int(k),int(k)+shape[i]-1) for i,k in enumerate(off))] for off in CORNERS]
    active=np.argwhere((np.minimum.reduce(corners)<iso)&(np.maximum.reduce(corners)>=iso))
    cube_idx=active[:,None,:]+CORNERS[None,:,:]
    cube_val=field[tuple(cube_idx.transpose(2,0,1))]
    verts=[]
    for tet in TETS:
        idx=cube_idx[:,tet,:]; val=cube_val[:,tet]; masks=np.sum((val>=iso)*(1<<np.arange(4)),axis=1)
        for mask,tris in CASES.items():
            chosen=(masks==mask)
            if not chosen.any(): continue
            ci=idx[chosen]; cv=val[chosen]
            for tri in tris:
                edge=EDGES[tri]; va=cv[:,edge[:,0]]; vb=cv[:,edge[:,1]]
                t=(iso-va)/(vb-va)
                out=ci[:,edge[:,0],:]+t[...,None]*(ci[:,edge[:,1],:]-ci[:,edge[:,0],:])
                verts.append(out.reshape(-1,3))
    if not verts: raise ValueError('No scalar surface')
    v=np.concatenate(verts); coords=v.T
    gradients=np.gradient(field,spacing)
    normals=-np.stack([map_coordinates(g,coords,order=1,mode='nearest') for g in gradients],axis=1)
    normals/=np.maximum(np.linalg.norm(normals,axis=1)[:,None],1e-12)
    colors=np.stack([map_coordinates(rgb[...,i],coords,order=1,mode='nearest') for i in range(3)],axis=1)
    positions=(v*spacing+lo)/1000.
    packed=np.concatenate([positions,normals,colors],axis=1).astype('<f4')
    if not np.isfinite(packed).all(): raise ValueError('Nonfinite surface')
    return packed,{'triangles':len(v)//3,'display-grid-m':spacing,'iso':iso,'field-grid-shape':shape,'physics-cell-edges-m':sorted(set(c['cell']['edge-m'] for c in cells))}

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('source');ap.add_argument('--stride',type=int,default=2);ap.add_argument('--spacing',type=float,default=12.);args=ap.parse_args()
    files=sorted(pathlib.Path(args.source).glob('frame-*.json')); chosen=[]; previous=None
    for i,path in enumerate(files):
        d=json.loads(path.read_text()); state=(d['phase'], d['account']['coarse-bodies'])
        if i%args.stride==0 or state!=previous or state[1]>0: chosen.append(path)
        previous=state
    if files and files[-1] not in chosen: chosen.append(files[-1])
    summary=[]
    for i,path in enumerate(chosen):
        data=json.loads(path.read_text())
        path.with_suffix('.render.edn').write_text('{:display-grid-m '+str(args.spacing)+'}')
        for view in ['composition','resolution']:
            mesh,info=surface(data,view=view,spacing=args.spacing); mesh.tofile(path.with_suffix('.'+view+'.mesh'))
        summary.append(dict(file=path.name,**info))
        if i%10==0: print(i,path.name,info['triangles'],flush=True)
    pathlib.Path(args.source,'surface-index.json').write_text(json.dumps(summary,indent=2))
