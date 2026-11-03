import re, sys
def words(t): return re.findall(r"[\w']+", t.lower())
def wer(ref, hyp):
    r, h = words(ref), words(hyp)
    d = list(range(len(h) + 1))
    for i in range(1, len(r) + 1):
        prev, d[0] = d[0], i
        for j in range(1, len(h) + 1):
            cur = d[j]
            d[j] = min(d[j] + 1, d[j - 1] + 1, prev + (r[i - 1] != h[j - 1]))
            prev = cur
    return d[len(h)] / max(1, len(r))
if __name__ == "__main__":
    for name, tag in (("dw", "dwauto"), ("f24", "f24"), ("aje", "aje")):
        ref = open(f"out/medium_{name}.txt").read()
        line = [name, f"{len(words(ref))} kata"]
        for m in ("tiny", "base", "small"):
            hyp = open(f"out/{m}_{tag}.txt").read()
            line.append(f"{m}={wer(ref, hyp)*100:.0f}%")
        print(*line)
