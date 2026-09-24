"""Build EscoF from a Git checkout or an extracted source ZIP."""
import argparse, hashlib, json, os, shlex, shutil, subprocess, sys, urllib.parse
from pathlib import Path

root=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--java-home',type=Path,default=os.environ.get('JAVA_HOME'))
p.add_argument('--gradle',type=Path,help='Optional installed Gradle 9.4.1 executable; default uses wrapper')
p.add_argument('--max-workers',type=int,default=4,help='Build workers, independent of runtime --workers')
a=p.parse_args()
env=os.environ.copy()
if a.java_home:
    env['JAVA_HOME']=str(a.java_home.resolve())
    env['PATH']=str(a.java_home.resolve()/'bin')+os.pathsep+env['PATH']
java=shutil.which('java',path=env['PATH'])
if not java:raise SystemExit('Install JDK 25 and set JAVA_HOME or pass --java-home.')
subprocess.run([java,'-version'],env=env,check=True)
env.update(GIT_AUTHOR_NAME='EscoF Build',GIT_AUTHOR_EMAIL='build@escof.invalid',
           GIT_COMMITTER_NAME='EscoF Build',GIT_COMMITTER_EMAIL='build@escof.invalid')
if not (root/'.git').exists():
    subprocess.run(['git','init','-b','main'],cwd=root,env=env,check=True)
    subprocess.run(['git','add','.'],cwd=root,env=env,check=True)
    subprocess.run(['git','commit','--quiet','-m','Import EscoF source distribution'],cwd=root,env=env,check=True)
tmp=root/'.esco-build-tmp';tmp.mkdir(exist_ok=True)
env['TMPDIR']=str(tmp)
env['JAVA_TOOL_OPTIONS']='"-Djava.io.tmpdir='+str(tmp)+'"'
options=['-Xmx2G',f'-XX:ActiveProcessorCount={max(1,a.max_workers)}','-Djava.io.tmpdir='+str(tmp)]
for scheme in ('http','https'):
    proxy=urllib.parse.urlparse(env.get(scheme.upper()+'_PROXY',''))
    if proxy.username or proxy.password:raise SystemExit('Authenticated proxy requires explicit Gradle proxy configuration.')
    if proxy.hostname:options += [f'-D{scheme}.proxyHost={proxy.hostname}',f'-D{scheme}.proxyPort={proxy.port or 80}']
if Path('/etc/ssl/certs/java/cacerts').exists():options += ['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts']
env['GRADLE_OPTS']=subprocess.list2cmdline(options) if os.name=='nt' else shlex.join(options)
gradle=([str(a.gradle.resolve())] if os.name=='nt' else ['bash',str(a.gradle.resolve())]) if a.gradle else \
       ([str(root/'gradlew.bat')] if os.name=='nt' else ['bash',str(root/'gradlew')])
# Bootstrap build-script patches separately; prepare applied trees sequentially.
# The upstream patch tasks are not safe to reapply blindly to prepared source trees.
# Fingerprint canonical inputs and retain a marker only after a successful preparation.
inputs=sorted([*root.glob('leaf-server/minecraft-patches/**/*.patch'),
    *root.glob('leaf-server/paper-patches/**/*.patch'),*root.glob('leaf-api/paper-patches/**/*.patch'),
    root/'leaf-server/build.gradle.kts.patch',root/'leaf-api/build.gradle.kts.patch',
    root/'gradle.properties',root/'build.gradle.kts',root/'settings.gradle.kts'])
digest=hashlib.sha256()
for path in inputs:
    digest.update(str(path.relative_to(root)).encode());digest.update(path.read_bytes())
fingerprint=digest.hexdigest();marker=root/'.esco-build-state.json'
prepared=json.loads(marker.read_text()) if marker.is_file() else {}
ready=(root/'leaf-server/src/minecraft/java/net/minecraft/server/MinecraftServer.java').is_file() and (root/'paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java').is_file()
if prepared and prepared.get('patch_inputs_sha256') != fingerprint:
    raise SystemExit('Canonical patch inputs changed. Preserve any applied-source edits, then build in a fresh source folder.')
tasks=['createPaperclipJar'] if ready and prepared.get('patch_inputs_sha256')==fingerprint else ['applyPaperSingleFilePatches','applyAllPatches','createPaperclipJar']
for task in tasks:
    # Patch preparation contains temporary Git repositories; do not restore these from Gradle build cache.
    preparing=task!='createPaperclipJar'
    cache=['--no-build-cache','--no-parallel'] if preparing else []
    subprocess.run([*gradle,task,'--no-daemon','--console=plain','--stacktrace',
        *cache,f'--max-workers={1 if preparing else a.max_workers}','-Dorg.gradle.jvmargs='+' '.join(options)],cwd=root,env=env,check=True)
    if task=='applyAllPatches':marker.write_text(json.dumps({'patch_inputs_sha256':fingerprint},indent=2)+'\n')
jar=root/'leaf-server/build/libs/leaf-paperclip-26.2.local-SNAPSHOT.jar'
target=root/'dist/escof-26.2-0.7.0.jar';target.parent.mkdir(exist_ok=True)
shutil.copy2(jar,target)
print('Built '+str(target))
