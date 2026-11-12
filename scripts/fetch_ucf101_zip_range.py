"""从 UCF101 整包 zip（hf-mirror 镜像）按 HTTP Range 抽取指定动作类的 avi。

免整包下载：zip 的中央目录在文件尾部，先取尾部解析出每个条目的字节区间，
再按区间抽取并解压选中条目。zip 超过 4GB，走 Zip64 解析。

产出：.tmp_fetch/ucf101_subset/UCF_zip/<Class>/v_*.avi
随后重跑 fetch_real_media.py --only video 即可将其纳入选材。
"""

from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path

import httpx

URL = "https://hf-mirror.com/datasets/quchenyuan/UCF101-ZIP/resolve/main/UCF-101.zip"
FILE_SIZE = 6_957_373_664
OUT = Path(".tmp_fetch") / "ucf101_subset" / "UCF_zip"

# 期望覆盖的动作类（日常、画面多样）；按顺序取前 20 个命中的
WANT_CLASSES = [
    "Basketball", "Biking", "CliffDiving", "Diving", "Fencing", "GolfSwing",
    "HorseRace", "HulaHoop", "IceDancing", "Juggling", "JumpingJack",
    "PlayingGuitar", "PlayingPiano", "PlayingViolin", "PizzaTossing",
    "Rafting", "SkateBoarding", "Skiing", "Surfing", "Swimming",
    "TaiChi", "TrampolineJumping", "YoYo", "Drumming",
]


def fetch_range(client: httpx.Client, start: int, end: int) -> bytes:
    for attempt in range(1, 6):
        try:
            r = client.get(URL, headers={"Range": f"bytes={start}-{end}"},
                           timeout=120, follow_redirects=True)
            r.raise_for_status()
            data = r.content
            if len(data) == end - start + 1:
                return data
            print(f"  [warn] 区间长度不符 {start}-{end}: got {len(data)}")
        except Exception as e:  # noqa: BLE001
            print(f"  [retry {attempt}/5] {start}-{end}: {e}")
    raise SystemExit(f"区间获取失败: {start}-{end}")


def parse_central_dir(client: httpx.Client) -> list[dict]:
    tail_len = 1 << 16
    tail = fetch_range(client, FILE_SIZE - tail_len, FILE_SIZE - 1)

    eocd_pos = tail.rfind(b"PK\x05\x06")
    if eocd_pos < 0:
        raise SystemExit("找不到 EOCD")
    eocd = tail[eocd_pos:eocd_pos + 22]
    cd_size, cd_offset = struct.unpack("<II", eocd[12:20])
    if cd_size == 0xFFFFFFFF or cd_offset == 0xFFFFFFFF:  # Zip64
        loc_pos = tail.rfind(b"PK\x06\x07", 0, eocd_pos)
        if loc_pos < 0:
            raise SystemExit("找不到 EOCD64 locator")
        eocd64_off = struct.unpack("<Q", tail[loc_pos + 8:loc_pos + 16])[0]
        head = fetch_range(client, eocd64_off, eocd64_off + 55)
        if head[:4] != b"PK\x06\x06":
            raise SystemExit("EOCD64 签名不符")
        cd_size, cd_offset = struct.unpack("<QQ", head[40:56])
    print(f"[cd] size={cd_size} offset={cd_offset}")
    cd = fetch_range(client, cd_offset, cd_offset + cd_size - 1)

    entries: list[dict] = []
    pos = 0
    while pos + 46 <= len(cd) and cd[pos:pos + 4] == b"PK\x01\x02":
        method, = struct.unpack("<H", cd[pos + 10:pos + 12])
        csize, usize, name_len, extra_len, comment_len = struct.unpack(
            "<IIHHH", cd[pos + 20:pos + 34])
        lho, = struct.unpack("<I", cd[pos + 42:pos + 46])
        name = cd[pos + 46:pos + 46 + name_len].decode("utf-8", "replace")
        extra = cd[pos + 46 + name_len:pos + 46 + name_len + extra_len]
        # Zip64 extra：0xFFFFFFFF 字段按序落在 8 字节槽里
        if csize == 0xFFFFFFFF or usize == 0xFFFFFFFF or lho == 0xFFFFFFFF:
            epos = 0
            while epos + 4 <= len(extra):
                eid, esz = struct.unpack("<HH", extra[epos:epos + 4])
                if eid == 0x0001:
                    body = extra[epos + 4:epos + 4 + esz]
                    slots = []
                    if usize == 0xFFFFFFFF:
                        usize = struct.unpack("<Q", body[len(slots) * 8:len(slots) * 8 + 8])[0]
                        slots.append(1)
                    if csize == 0xFFFFFFFF:
                        base = len(slots) * 8
                        csize = struct.unpack("<Q", body[base:base + 8])[0]
                        slots.append(1)
                    if lho == 0xFFFFFFFF:
                        base = len(slots) * 8
                        lho = struct.unpack("<Q", body[base:base + 8])[0]
                    break
                epos += 4 + esz
        entries.append({"name": name, "method": method, "csize": csize,
                        "usize": usize, "lho": lho})
        pos += 46 + name_len + extra_len + comment_len
    print(f"[cd] 解析出 {len(entries)} 个条目")
    return entries


def extract(client: httpx.Client, e: dict) -> bytes:
    head = fetch_range(client, e["lho"], e["lho"] + 29)
    if head[:4] != b"PK\x03\x04":
        raise SystemExit(f"本地头签名不符: {e['name']}")
    name_len, extra_len = struct.unpack("<HH", head[26:30])
    data_start = e["lho"] + 30 + name_len + extra_len
    data = fetch_range(client, data_start, data_start + e["csize"] - 1)
    if e["method"] == 0:
        return data
    if e["method"] == 8:
        return zlib.decompressobj(-15).decompress(data)
    raise SystemExit(f"不支持的压缩方式 {e['method']}: {e['name']}")


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    entries = None
    with httpx.Client(headers={"User-Agent": "cmr-testset/1.0 (dataset build)"}) as client:
        entries = parse_central_dir(client)

        by_class: dict[str, list[dict]] = {}
        for e in entries:
            parts = e["name"].split("/")
            if len(parts) == 3 and parts[2].lower().endswith(".avi"):
                by_class.setdefault(parts[1], []).append(e)

        chosen: list[dict] = []
        for cls in WANT_CLASSES:
            if len(chosen) >= 20:
                break
            cands = sorted(by_class.get(cls, []), key=lambda e: e["csize"])
            if not cands:
                print(f"[pick] 类不存在，跳过: {cls}")
                continue
            chosen.append(cands[0])  # 同类取最小的一条
        if len(chosen) < 20:
            raise SystemExit(f"只凑到 {len(chosen)} 类")

        for i, e in enumerate(chosen, 1):
            cls = e["name"].split("/")[1]
            dst = OUT / cls / e["name"].split("/")[-1]
            if dst.exists() and dst.stat().st_size == e["usize"]:
                print(f"[get] {i}/20 已缓存 {dst}")
                continue
            raw = extract(client, e)
            if not raw.startswith(b"RIFF"):
                raise SystemExit(f"非 AVI: {e['name']} ({raw[:8]!r})")
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_bytes(raw)
            print(f"[get] {i}/20 {dst} {len(raw) // 1024}KB ← {e['name']}")
    print("完成")


if __name__ == "__main__":
    main()
