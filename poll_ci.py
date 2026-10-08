import json, subprocess, time, urllib.request, sys
tok_out = subprocess.run(
    ["C:/Users/Administrator/.workbuddy/binaries/PortableGit/versions/1.2.0/mingw64/bin/git-credential-manager.exe", "get"],
    input="protocol=https\nhost=github.com\n\n", capture_output=True, text=True, timeout=20
).stdout
token = [l for l in tok_out.splitlines() if l.startswith("password=")][0].split("=",1)[1]
PROXY = urllib.request.ProxyHandler({"https": "http://127.0.0.1:51354", "http": "http://127.0.0.1:51354"})
opener = urllib.request.build_opener(PROXY)
opener.addheaders = [("Authorization", f"Bearer {token}"), ("User-Agent", "dokacam-ci")]
def get(path):
    return json.loads(opener.open(f"https://api.github.com{path}", timeout=30).read())
for i in range(16):
    try:
        runs = get("/repos/Wen-jh/DokaCam/actions/runs?per_page=1")["workflow_runs"]
    except Exception as e:
        print("api err", e); time.sleep(30); continue
    r = runs[0]
    print(f"[{(i+1)*30}s] run#{r['run_number']} {r['status']}/{r.get('conclusion')}", flush=True)
    if r["status"] == "completed":
        if r.get("conclusion") == "success":
            rel = get("/repos/Wen-jh/DokaCam/releases/latest")
            print("=== BUILD SUCCESS ===")
            print("Release:", rel.get("name"), "| tag:", rel.get("tag_name"))
            print("页面:", rel.get("html_url"))
            for a in rel.get("assets", []):
                print(f"  APK {a['name']}  {a['size']/1048576:.1f}MB")
                print(f"  下载 {a['browser_download_url']}")
        else:
            jobs = get(f"/repos/Wen-jh/DokaCam/actions/runs/{r['id']}/jobs")
            job_id = jobs["jobs"][0]["id"]
            class NoAuthRedirect(urllib.request.HTTPRedirectHandler):
                def redirect_request(self, req, fp, code, msg, headers, newurl):
                    return urllib.request.Request(newurl, method="GET")
            op2 = urllib.request.build_opener(PROXY, NoAuthRedirect())
            op2.addheaders = [("User-Agent", "dokacam-ci")]
            req = urllib.request.Request(
                f"https://api.github.com/repos/Wen-jh/DokaCam/actions/jobs/{job_id}/logs",
                headers={"Authorization": f"Bearer {token}", "User-Agent": "dokacam-ci"})
            log = op2.open(req, timeout=60).read().decode("utf-8","replace")
            open("ci_debug.log","w",encoding="utf-8").write(log)
            for s in jobs["jobs"][0]["steps"]:
                if s.get("conclusion") == "failure": print(f"  FAILED: {s['name']}")
            shown=0
            for l in log.splitlines():
                if ("e: file" in l or "error:" in l.lower() or "FAILURE" in l or "Caused by" in l) and shown<12:
                    print("   ", l.strip()[:220]); shown+=1
        sys.exit(0)
    time.sleep(30)
print("TIMEOUT_STILL_RUNNING")
