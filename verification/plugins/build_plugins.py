import argparse, os, subprocess, zipfile
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--java-home', default=os.environ.get('JAVA_HOME'), required=False)
p.add_argument('--runtime', type=Path, required=True, help='A Paperclip working directory containing libraries/ and versions/')
p.add_argument('--community-dir', type=Path)
a=p.parse_args()
root=Path(__file__).resolve().parent
out=root/'build'
out.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join(map(str, [*a.runtime.glob('libraries/**/*.jar'), *a.runtime.glob('versions/**/*.jar')]))
if a.community_dir: cp += os.pathsep + os.pathsep.join(map(str,a.community_dir.glob('*.jar')))
sources=list((root/'src').rglob('*.java'))
if not a.community_dir: sources=[s for s in sources if s.name!='CommunityProbe.java']
subprocess.run([str(Path(a.java_home)/'bin/javac'), '-cp', cp, '-d', str(out/'classes'), *map(str,sources)],check=True)
for name, main, descriptor, entry in [('benchmark','BenchPlugin','bench-plugin.yml','plugin.yml'),
                                     ('paper-probe','PaperProbe','paper-plugin.yml','paper-plugin.yml'),
                                     ('bukkit-probe','BukkitProbe','bukkit-plugin.yml','plugin.yml'),
                                     ('community-probe','CommunityProbe','community-plugin.yml','plugin.yml')]:
    if name=='community-probe' and not a.community_dir: continue
    if not (root/descriptor).exists(): continue
    with zipfile.ZipFile(out/(name+'.jar'),'w',zipfile.ZIP_DEFLATED) as jar:
        jar.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\npaperweight-mappings-namespace: mojang\n\n')
        jar.write(root/descriptor,entry)
        for file in (out/'classes/dev/escos/verify').glob(main+'*.class'):
            jar.write(file,str(file.relative_to(out/'classes')))
    print(out/(name+'.jar'))
