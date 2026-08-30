# The mark

`vinto-mark.png` — 144px of flat #FF6000 on transparency, the orange V.

It is the only piece of original artwork this project has that is not card art, and every icon
the game ships is generated from it: the Android launcher icons (`make-launcher-icons.py`), and
the browser favicon, the web app manifest icons, the Apple touch icon and the share card
(`make-web-icons.py`).

It lived in `legacy-web/apps/vinto/public/favicon.png` until that directory was deleted. It is
here now because two generators read it — which is the kind of dependency a `grep` for a
directory name finds and a build does not, since nothing runs these scripts automatically and
the PNGs they write are committed.

Re-run both generators if it ever changes:

```sh
python3 tools/make-launcher-icons.py
python3 tools/make-web-icons.py
```
