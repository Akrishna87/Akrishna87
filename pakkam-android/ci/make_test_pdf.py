"""Writes a small 5-page PDF for the smoke test: plain text in Helvetica, a table of contents,
the word "elephant" on pages 2 and 4 so search has something to find, and a last page twice as
tall as the others (like a long receipt) so fitting a page to the screen has to zoom out.
Usage: make_test_pdf.py <out.pdf>
"""
import sys

PAGES = [
    [(72, 700, 24, "Pakkam smoke test"), (72, 400, 20, "Highlight this sentence please."), (72, 300, 14, "Page one of five.")],
    [(72, 700, 18, "Chapter 1"), (72, 600, 14, "An elephant never forgets."), (72, 560, 14, "Page two of five.")],
    [(72, 700, 18, "Chapter 2"), (72, 600, 14, "Nothing to find here."), (72, 560, 14, "Page three of five.")],
    [(72, 700, 18, "Chapter 3"), (72, 600, 14, "The elephant came back."), (72, 560, 14, "Page four of five.")],
    [(72, 700, 18, "The end"), (72, 600, 14, "Page five of five.")],
]


def esc(s):
    return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def main(path):
    objs = {}  # number -> bytes
    n_pages = len(PAGES)
    # 1 catalog, 2 pages, 3 font, 4 outlines, then per page: page, contents; then outline items.
    first_page = 5
    page_ids = [first_page + 2 * i for i in range(n_pages)]
    outline_ids = [first_page + 2 * n_pages + i for i in range(3)]
    objs[1] = b"<< /Type /Catalog /Pages 2 0 R /Outlines 4 0 R /PageMode /UseOutlines >>"
    objs[2] = ("<< /Type /Pages /Count %d /Kids [%s] >>" % (n_pages, " ".join("%d 0 R" % p for p in page_ids))).encode()
    objs[3] = b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
    objs[4] = ("<< /Type /Outlines /First %d 0 R /Last %d 0 R /Count 3 >>" % (outline_ids[0], outline_ids[-1])).encode()
    for i, lines in enumerate(PAGES):
        pid = page_ids[i]
        cid = pid + 1
        body = "".join("BT /F1 %d Tf %d %d Td (%s) Tj ET\n" % (size, x, y, esc(t)) for x, y, size, t in lines).encode()
        height = 1584 if i == n_pages - 1 else 792
        objs[pid] = ("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 %d] /Resources << /Font << /F1 3 0 R >> >> /Contents %d 0 R >>" % (height, cid)).encode()
        objs[cid] = b"<< /Length %d >>\nstream\n" % len(body) + body + b"endstream"
    for k, oid in enumerate(outline_ids):
        target = page_ids[k + 1]
        parts = ["/Title (Chapter %d)" % (k + 1), "/Parent 4 0 R", "/Dest [%d 0 R /Fit]" % target]
        if k > 0:
            parts.append("/Prev %d 0 R" % outline_ids[k - 1])
        if k < len(outline_ids) - 1:
            parts.append("/Next %d 0 R" % outline_ids[k + 1])
        objs[oid] = ("<< %s >>" % " ".join(parts)).encode()

    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = {}
    for num in sorted(objs):
        offsets[num] = len(out)
        out += b"%d 0 obj\n" % num + objs[num] + b"\nendobj\n"
    xref = len(out)
    size = max(objs) + 1
    out += b"xref\n0 %d\n0000000000 65535 f \n" % size
    for num in range(1, size):
        out += b"%010d 00000 n \n" % offsets[num]
    out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (size, xref)
    with open(path, "wb") as f:
        f.write(out)


if __name__ == "__main__":
    main(sys.argv[1])
