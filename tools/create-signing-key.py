#!/usr/bin/env python3
"""Create a local preview signing identity once; preserve it for future updates."""
from pathlib import Path
import os
import secrets
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
folder = root / '.signing'
folder.mkdir(mode=0o700, exist_ok=True)
properties = folder / 'keystore.properties'
key = folder / 'preview.jks'
if properties.exists() and key.exists():
    print('Existing signing identity retained.')
    raise SystemExit(0)
if properties.exists() or key.exists():
    raise SystemExit('Incomplete signing identity; restore the matching key and properties before continuing.')
keytool = Path(os.environ.get('JAVA_HOME', '')) / 'bin' / 'keytool'
command = str(keytool) if keytool.is_file() else shutil.which('keytool')
if not command:
    raise SystemExit('JDK keytool is required.')
password = secrets.token_urlsafe(32)
env = dict(os.environ, QINGYING_KEYSTORE_PASSWORD=password)
subprocess.run([command, '-genkeypair', '-keystore', str(key), '-storetype', 'PKCS12',
    '-storepass:env', 'QINGYING_KEYSTORE_PASSWORD', '-keypass:env', 'QINGYING_KEYSTORE_PASSWORD',
    '-alias', 'qingying-preview', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '3650',
    '-dname', 'CN=QingYing Preview, O=Personal Development', '-noprompt'], env=env, check=True)
key.chmod(0o600)
fd = os.open(properties, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as output:
    output.write(f'storePassword={password}\nkeyPassword={password}\nkeyAlias=qingying-preview\n')
print('Local signing identity created in .signing; do not publish this directory.')
