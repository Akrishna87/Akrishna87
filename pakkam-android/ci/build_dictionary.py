"""Builds the offline dictionary for Reading mode from Open English WordNet.

Reads the WN-LMF XML release (gzipped) and writes a small SQLite database:

  synset(id INTEGER PRIMARY KEY, pos TEXT, def TEXT, ex TEXT, words TEXT)
      ex and words are joined with "\\n"; words are the synonyms in the synset.
  entry(word TEXT PRIMARY KEY, shown TEXT, pron TEXT, synsets TEXT)
      word is lower case; synsets is a comma-separated list of synset ids, most common sense first.
  form(form TEXT PRIMARY KEY, word TEXT)
      irregular forms ("ran", "geese") pointing at their headword.

Open English WordNet is © the Open English WordNet team, CC BY 4.0
(https://github.com/globalwordnet/english-wordnet).

Usage: build_dictionary.py <english-wordnet.xml.gz> <out.db>
"""
import gzip
import os
import sqlite3
import sys
import xml.etree.ElementTree as ET

MAX_EXAMPLES = 2


def main(src, out):
    entries = {}  # lowercase word -> [shown, pron, [synset ids]]
    forms = {}
    member_words = {}  # LexicalEntry id -> written form
    synsets = []  # (xml id, pos, def, examples, member entry ids)

    with gzip.open(src, "rb") as f:
        for _, el in ET.iterparse(f, events=("end",)):
            tag = el.tag
            if tag == "LexicalEntry":
                lemma = el.find("Lemma")
                if lemma is None:
                    el.clear()
                    continue
                word = lemma.get("writtenForm", "")
                member_words[el.get("id")] = word
                key = word.lower()
                e = entries.setdefault(key, [word, "", []])
                if not e[1]:
                    prons = lemma.findall("Pronunciation")
                    # Prefer the unmarked or British pronunciation; any is better than none.
                    pick = next((p for p in prons if p.get("variety") in (None, "GB")), prons[0] if prons else None)
                    if pick is not None and pick.text:
                        e[1] = pick.text.strip()
                for s in el.findall("Sense"):
                    e[2].append(s.get("synset"))
                for fm in el.findall("Form"):
                    w = (fm.get("writtenForm") or "").lower()
                    if w and w != key:
                        forms.setdefault(w, key)
                el.clear()
            elif tag == "Synset":
                d = el.find("Definition")
                exs = [x.text.strip() for x in el.findall("Example") if x.text][:MAX_EXAMPLES]
                synsets.append((el.get("id"), el.get("partOfSpeech"), (d.text or "").strip() if d is not None else "", exs, (el.get("members") or "").split()))
                el.clear()

    ids = {sid: i + 1 for i, (sid, *_rest) in enumerate(synsets)}
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.executescript(
        """
        CREATE TABLE synset(id INTEGER PRIMARY KEY, pos TEXT, def TEXT, ex TEXT, words TEXT);
        CREATE TABLE entry(word TEXT PRIMARY KEY, shown TEXT, pron TEXT, synsets TEXT) WITHOUT ROWID;
        CREATE TABLE form(form TEXT PRIMARY KEY, word TEXT) WITHOUT ROWID;
        """
    )
    db.executemany(
        "INSERT INTO synset VALUES (?,?,?,?,?)",
        (
            (ids[sid], pos, d, "\n".join(exs), "\n".join(dict.fromkeys(member_words.get(m, "") for m in members if m in member_words)))
            for sid, pos, d, exs, members in synsets
        ),
    )
    db.executemany(
        "INSERT INTO entry VALUES (?,?,?,?)",
        ((k, v[0], v[1], ",".join(str(ids[s]) for s in dict.fromkeys(v[2]) if s in ids)) for k, v in entries.items()),
    )
    db.executemany("INSERT INTO form VALUES (?,?)", ((k, v) for k, v in forms.items() if k not in entries))
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"{len(entries)} words, {len(synsets)} meanings, {len(forms)} irregular forms -> {out} ({os.path.getsize(out) // 1024} KB)")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
