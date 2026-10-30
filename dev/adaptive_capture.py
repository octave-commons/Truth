"""Capture the existing native renderer on a local TCP Xvfb display."""
import argparse, os, pathlib, socket, subprocess, time
ap=argparse.ArgumentParser();ap.add_argument('source');ap.add_argument('output');args=ap.parse_args()
out=pathlib.Path(args.output);out.mkdir(parents=True,exist_ok=True)
display=88
with open(out/'display.log','w') as log:
    x=subprocess.Popen(['Xvfb',':'+str(display),'-screen','0','1280x720x24','-nolisten','unix','-nolisten','local','-listen','tcp','-ac'],stdout=log,stderr=log)
    try:
        for _ in range(100):
            try:
                with socket.create_connection(('127.0.0.1',6000+display),timeout=.2): break
            except OSError: time.sleep(.1)
        else: raise RuntimeError('Local X display did not start')
        env=dict(os.environ,DISPLAY='127.0.0.1:'+str(display),LIBGL_ALWAYS_SOFTWARE='1',LP_NUM_THREADS='3')
        cmd=['java','-Xmx2g','-XX:ActiveProcessorCount=4','-cp',env['TRUTH_ADAPTIVE_CP'],'clojure.main','-m','adaptive-replay',args.source,args.output]
        subprocess.run(cmd,env=env,check=True)
    finally:
        x.terminate();x.wait(timeout=10)
