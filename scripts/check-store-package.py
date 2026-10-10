"""Validate APK manifest, 16 KB ELF/ZIP alignment and production certificate. No secrets."""
import argparse, hashlib, json, re, struct, subprocess, zipfile
from pathlib import Path

def run(tool,*args):
    result=subprocess.run([str(tool),*map(str,args)],capture_output=True,text=True,encoding="utf-8",errors="replace")
    if result.returncode: raise ValueError(f"{tool.name} failed: {result.stdout} {result.stderr}")
    return result.stdout

def elf_alignments(data):
    if data[:4]!=b"\x7fELF" or data[5]!=1: raise ValueError("Unsupported ELF")
    is64=data[4]==2
    offset=struct.unpack_from("<Q" if is64 else "<I",data,32 if is64 else 28)[0]
    size,count=struct.unpack_from("<HH",data,54 if is64 else 42)
    result=[]
    for i in range(count):
        base=offset+i*size
        if struct.unpack_from("<I",data,base)[0]==1:
            alignment=struct.unpack_from("<Q" if is64 else "<I",data,base+(48 if is64 else 28))[0]
            result.append(alignment)
    if not result or any(x<16384 for x in result): raise ValueError(f"ELF alignment {result}")
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk",type=Path);parser.add_argument("--build-tools",type=Path,required=True)
    parser.add_argument("--allow-unsigned",action="store_true",help="Structural local check only; never store-ready")
    parser.add_argument("--expected-cert",help="SHA256 of APP SIGNING certificate (not Play upload certificate)")
    parser.add_argument("--report",type=Path)
    args=parser.parse_args();tools=args.build_tools
    exe=lambda n: tools/(n+(".bat" if n=="apksigner" else ".exe") if __import__('os').name=="nt" else n)
    badging=run(exe("aapt"),"dump","badging",args.apk)
    if "package: name='com.xekep.space'" not in badging: raise ValueError("Unexpected application ID")
    if "targetSdkVersion:'36'" not in badging: raise ValueError("Expected API 36")
    if "application-debuggable" in badging: raise ValueError("Debuggable package")
    libraries={}
    with zipfile.ZipFile(args.apk) as archive, args.apk.open("rb") as source:
        for item in archive.infolist():
            if item.filename.startswith("lib/") and item.filename.endswith(".so"):
                libraries[item.filename]=elf_alignments(archive.read(item))
                if item.compress_type==zipfile.ZIP_STORED:
                    source.seek(item.header_offset+26);name,extra=struct.unpack("<HH",source.read(4))
                    offset=item.header_offset+30+name+extra
                    if offset%16384: raise ValueError(f"ZIP alignment: {item.filename} at {offset}")
    run(exe("zipalign"),"-c","-P","16","-v","4",args.apk)
    certificate=None
    if not args.allow_unsigned:
        output=run(exe("apksigner"),"verify","--verbose","--print-certs",args.apk)
        if "CN=Android Debug" in output: raise ValueError("Android debug signing is not a production identity")
        matches=re.findall(r"certificate SHA-256 digest: ([0-9a-f]+)",output,re.I)
        if len(matches)!=1: raise ValueError("Expected one signer; review certificate lineage manually")
        certificate=matches[0].lower()
        if args.expected_cert and certificate!=re.sub(r"[^0-9a-f]","",args.expected_cert.lower()): raise ValueError("App signing certificate mismatch")
    report=dict(apk=args.apk.name,sha256=hashlib.sha256(args.apk.read_bytes()).hexdigest(),targetSdk=36,
                libraries=libraries,certificateSha256=certificate,productionSignatureChecked=not args.allow_unsigned)
    if args.report: args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(report,indent=2))
if __name__=="__main__": main()
