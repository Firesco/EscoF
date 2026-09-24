"""Summarize diagnostic JFR samples. Sampling counts are not benchmark timing claims."""
import argparse
from collections import Counter
import json
from pathlib import Path
import subprocess

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--recording',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
raw=subprocess.check_output([str(a.java_home/'bin/jfr'),'print','--json','--events',
                             'jdk.ExecutionSample,jdk.NativeMethodSample,jdk.ThreadPark,jdk.JavaMonitorEnter',
                             str(a.recording)],text=True)
events=json.loads(raw)['recording']['events']
tops=Counter();esco_frames=Counter();parks=[];sampled=Counter()
for event in events:
    value=event['values']
    thread=value.get('sampledThread') or value.get('eventThread') or {}
    name=thread.get('javaName','unknown')
    frames=(value.get('stackTrace') or {}).get('frames',[])
    methods=[]
    for frame in frames:
        method=frame.get('method',{})
        methods.append(method.get('type',{}).get('name','unknown')+'.'+method.get('name','unknown'))
    if event['type'] in ('jdk.ExecutionSample','jdk.NativeMethodSample'):
        sampled[name]+=1
        if name=='Server thread' and methods: tops[methods[0]]+=1
        for method in methods:
            if method.startswith('dev/escos/'):
                esco_frames[method]+=1
    elif name=='Server thread':
        parks.append(dict(type=event['type'],start=value.get('startTime'),duration=value.get('duration'),stack=methods[:12]))
result=dict(recording=a.recording.name,total_events=len(events),samples_by_thread=dict(sampled),
            top_server_methods=tops.most_common(30),esco_frame_counts=esco_frames.most_common(30),
            server_park_or_monitor_events=parks)
a.output.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='server_park_or_monitor_events'},indent=2))
