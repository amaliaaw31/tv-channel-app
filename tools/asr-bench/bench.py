import json, re, subprocess, sys, os
W = os.environ.get("WHISPER_DIR", "/home/ubuntu/asr-lab/whisper.cpp")  # a whisper.cpp checkout built with cmake
CLI = f"{W}/build/bin/whisper-cli"
os.environ["LD_LIBRARY_PATH"] = f"{W}/build/bin"
def run(model, wav, lang, threads, out):
    cmd = ["/usr/bin/time", "-v", CLI, "-m", f"{W}/models/ggml-{model}.bin", "-f", wav, "-l", lang, "-t", str(threads), "-nt", "-otxt", "-of", out]
    p = subprocess.run(cmd, capture_output=True, text=True)
    err = p.stderr
    g = lambda pat: float(re.search(pat, err).group(1)) if re.search(pat, err) else None
    wall = re.search(r"Elapsed \(wall clock\) time.*: (?:(\d+):)?(\d+):([\d.]+)", err)
    wall_s = (int(wall.group(1) or 0) * 3600 + int(wall.group(2)) * 60 + float(wall.group(3)))
    return {
        "wall": wall_s, "user": g(r"User time \(seconds\): ([\d.]+)"), "sys": g(r"System time \(seconds\): ([\d.]+)"),
        "rss_mb": g(r"Maximum resident set size \(kbytes\): (\d+)") / 1024,
        "load_ms": g(r"load time =\s+([\d.]+) ms"), "total_ms": g(r"total time =\s+([\d.]+) ms"),
        "encode_ms": g(r"encode time =\s+([\d.]+) ms"),
    }
if __name__ == "__main__":
    rows = []
    for model in ("tiny", "base", "small"):
        for name, lang in (("dw", "de"), ("f24", "fr"), ("aje", "en")):
            r = run(model, f"audio/{name}.wav", lang, 8, f"out/{model}_{name}")
            r.update(model=model, sample=name, threads=8, audio_s=60.0)
            r["rtf_wall"] = r["wall"] / 60; r["rtf_proc"] = (r["total_ms"] - r["load_ms"]) / 1000 / 60
            r["cpu_cores"] = (r["user"] + r["sys"]) / r["wall"]
            rows.append(r); print(json.dumps({k: (round(v, 2) if isinstance(v, float) else v) for k, v in r.items()}), flush=True)
    json.dump(rows, open("bench8.json", "w"), indent=1)
