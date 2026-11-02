"""Create a private release identity once; never include these files in payloads."""
import os, pathlib, secrets, subprocess, shutil
root=pathlib.Path(__file__).resolve().parent
key=root/'local-ai-release.jks'
props=root/'signing.properties'
if key.exists() or props.exists():
    raise SystemExit('Signing identity already exists; keeping it unchanged.')
password=secrets.token_urlsafe(32)
env=os.environ.copy();env['LOCAL_AI_KEY_PASSWORD']=password
keytool = shutil.which('keytool')
if not keytool:
    raise SystemExit('Install JDK 21 and add its bin directory to PATH.')
subprocess.run([keytool,
    '-genkeypair','-keystore',str(key),'-alias','local-ai','-keyalg','RSA','-keysize','4096',
    '-validity','10000','-dname','CN=Local AI, OU=Offline Android, O=Personal',
    '-storepass:env','LOCAL_AI_KEY_PASSWORD','-keypass:env','LOCAL_AI_KEY_PASSWORD'],env=env,check=True)
props.write_text(f'storeFile=local-ai-release.jks\nstorePassword={password}\nkeyAlias=local-ai\nkeyPassword={password}\n')
print('Created private Local AI release signing identity.')
