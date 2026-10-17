# Pakkam (பக்கம்) — a PDF reader and editor for Android

Pakkam does what Adobe Acrobat Reader does with a paid subscription, without the AI features,
without an account and without the internet. The app has no `INTERNET` permission, so
documents never leave the phone.

**Download:** the latest build is the `Pakkam.apk` in the
[`pakkam-latest` release](https://github.com/Akrishna87/Akrishna87/releases/tag/pakkam-latest), or from the
[download page](https://akrishna87.github.io/Akrishna87/apps/).

## What it does

**Reading**
- Fast, sharp pages drawn by PDFium (the PDF engine in Chrome), including annotations and form fields made in other apps
- Continuous scroll or page by page; pinch or double-tap to zoom, in to 500% or out until whole pages fit on the
  screen; *Fit page to screen* and *Fit to width* in the menu, and a zoom badge that taps back to normal; two fingers
  move the page while a tool is active
- Search with every match highlighted, and next/previous
- Table of contents, page thumbnails, your own bookmarks, and a list of all comments
- Select and copy text (long-press a word)
- Day, Night and Sepia modes; keep the screen on; go to page; remembers where you stopped
- **Reading mode**, like a Kindle: the text is lifted out of the page layout and set as a book you turn page by page,
  in your choice of font, size, line spacing, margins and page colour (white, sepia, green, black), with a chapter list,
  a page slider and your place remembered. Pinch to change the text size; tap the top of the page for the menus. Back closes the book, and a PDF last
  read this way opens this way again. **Long-press any word** to see its meaning, offline, from the built-in
  English dictionary (also **Define** on a selected word in page view), with buttons for your translate apps and the web.
  **Highlight and add notes** as you read: hold a word and slide to choose more, then pick a colour or write a note;
  tap a highlight to change or remove it, and see them all under *Notes and highlights*. They are saved as ordinary
  PDF highlights, so they also show in page view, in the Comments list and in other PDF apps.
  Built from the text's sizes and positions, not AI. Pictures and tables stay
  in page view, and scanned PDFs (pictures of text) can't be reflowed.
- Read aloud with the phone's own voice
- Opens password-protected PDFs; "Open with Pakkam" from Files, Gmail, WhatsApp…
- Share, print, save a copy, document properties

**Comment** (standard PDF annotations, so Acrobat and other readers show them)
- Highlight, underline, strikethrough
- Pen and highlighter marker, rectangle, oval, line, arrow, in your choice of colour and thickness
- Sticky notes and text boxes
- Erase any mark, including ones made in other apps; undo and redo

**Fill & Sign**
- Type anywhere on the page, add the date, ticks, crosses and dots
- Draw your signature and initials once; they're kept on the phone for next time
- Or sign on paper and take (or choose) a photo of it: drag a box around it and the paper, its lines and shadows are
  taken away so only the ink is left, as photographed or in blue or black
- Fill in real PDF forms (text fields, checkboxes, choices)

**Edit**
- Add text and pictures, white-out parts of a page

**Tools**
- Organize pages: reorder, turn, delete, duplicate, insert blank pages or another PDF, extract pages to a new file
- Combine several PDFs into one (keeps bookmarks and forms)
- Split by page ranges or every few pages
- Compress (three levels; pictures are resampled, text stays sharp)
- Protect with a password (AES-256) and limit printing, copying or changes; remove a password
- Add a watermark or page numbers
- Export to Word (.docx, text in paragraphs), plain text, or JPEG/PNG pictures of each page
- Create a PDF from photos, or scan paper with the camera (greyscale and black-and-white filters)
- Flatten comments and form entries into the page; edit title, author and keywords

Every change is made on a private copy first. The file you opened is only touched when you tap
**Save** (or **Save a copy** when the app that shared it doesn't allow changes).

## Not included

- The AI features (assistant, summaries, generative search) — on purpose.
- Changing the existing text of a PDF in place. White-out plus Add text covers most needs. White-out hides
  text from view but the original text is still in the file, so it isn't a secure redaction.
- Exporting to Excel or PowerPoint, and keeping the layout or pictures in Word exports: Acrobat does these on
  Adobe's servers.
- OCR (making scanned text searchable).

## How it's built

| Part | Library |
|---|---|
| Drawing pages, text positions, search, links, outline | [PdfiumAndroidKt](https://github.com/johngray1965/PdfiumAndroidKt) (`io.legere:pdfiumandroid`, Apache 2.0) |
| Writing annotations, forms, page edits, merge, split, passwords, compression | [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache 2.0) |
| UI | Jetpack Compose, Material 3 |

| Folder | What's there |
|---|---|
| `app/src/main/java/…/pdf` | The PDF engine: `PdfDoc` (PDFium), `PdfEditor` (annotations, forms, flatten), `PdfTools` (whole-document tools), `Docx` (Word export), `Reflow` and `ReadingMarks` (text and highlights for Reading mode), `InkLifter` (signatures from photos) |
| `app/src/main/java/…/ui` | Screens: Home, the viewer (`ViewerScreen`, `PageList`, `ViewerState`), Reading mode (`ReadingScreen`), tools, combine and photos-to-PDF |
| `app/src/main/java/…/data` | Recent files, settings, saved signatures, moving files in and out |
| `ci/` | The emulator smoke test and the sample PDF it uses; `build_dictionary.py` turns Open English WordNet into the app's dictionary |

## Building

The dictionary is built from [Open English WordNet](https://github.com/globalwordnet/english-wordnet) (© the Open English
WordNet team, CC BY 4.0) and isn't kept in git. Make it once before building, or the app builds without a dictionary:

```
curl -L -o /tmp/oewn.xml.gz https://github.com/globalwordnet/english-wordnet/releases/download/2025-edition/english-wordnet-2025.xml.gz
python3 ci/build_dictionary.py /tmp/oewn.xml.gz app/src/main/assets/dictionary.db
```

```
cd pakkam-android
./gradlew assembleRelease
```

The release build is signed with `signing/sideload.keystore` so that every build installs as an update
over the last one. The [workflow](../.github/workflows/pakkam-apk.yml) runs the unit tests, builds the APK,
runs a smoke test on an Android 14 emulator (opening, search, contents, night mode, Reading mode with its dictionary,
highlights and notes, highlighting, drawing, signing with a drawn tick and a photographed signature, saving, organizing) and publishes the APK with the test's screenshots.
