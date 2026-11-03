import json, os, re, subprocess, statistics as st
import bench, wer
def split(name, win, step):
    paths = []
    for i, start in enumerate(range(0, 60 - win + 1, step)):
        out = f"chunks/{name}_{win}_{i:02d}.wav"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-ss", str(start), "-t", str(win), "-i", f"audio/{name}.wav", "-c", "copy", out], check=True)
        paths.append(out)
    return paths
def run_chunks(model, name, lang, win, step, threads, extra=()):
    times, texts = [], []
    for p in split(name, win, step):
        cmd = [f"{bench.CLI}", "-m", f"{bench.W}/models/ggml-{model}.bin", "-f", p, "-l", lang, "-t", str(threads), "-nt", "-otxt", "-of", p[:-4] + f"_{model}", *extra]
        r = subprocess.run(cmd, capture_output=True, text=True)
        load = float(re.search(r"load time =\s+([\d.]+) ms", r.stderr).group(1)); tot = float(re.search(r"total time =\s+([\d.]+) ms", r.stderr).group(1))
        times.append((tot - load) / 1000); texts.append(open(p[:-4] + f"_{model}.txt").read().strip())
    return times, texts
if __name__ == "__main__":
    ref = {n: open(f"out/medium_{n}.txt").read() for n in ("dw", "f24", "aje")}
    rows = []
    cases = [("base", "aje", "en", 5, 5, 8, ()), ("small", "aje", "en", 5, 5, 8, ()), ("small", "aje", "en", 5, 5, 4, ()),
             ("small", "aje", "en", 5, 5, 8, ("-ac", "400")), ("small", "aje", "en", 10, 5, 8, ()),
             ("small", "f24", "fr", 5, 5, 8, ()), ("medium", "f24", "fr", 5, 5, 8, ()), ("small", "dw", "auto", 5, 5, 8, ())]
    for model, name, lang, win, step, th, extra in cases:
        times, texts = run_chunks(model, name, lang, win, step, th, extra)
        merged = " ".join(texts)
        r = dict(model=model, sample=name, window=win, step=step, threads=th, extra=" ".join(extra), chunks=len(times),
                 proc_mean=round(st.mean(times), 2), proc_max=round(max(times), 2), rtf_vs_step=round(st.mean(times) / step, 2),
                 words=len(wer.words(merged)), ref_words=len(wer.words(ref[name])), wer_vs_medium=round(wer.wer(ref[name], merged), 2), sample_text=merged[:140])
        rows.append(r); print(json.dumps(r, ensure_ascii=False), flush=True)
    json.dump(rows, open("bench_chunks.json", "w"), indent=1, ensure_ascii=False)
