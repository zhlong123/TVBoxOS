import os
import re

root = os.path.join(os.path.dirname(os.path.dirname(__file__)), "app", "src", "main", "res", "layout")
count = 0
for fn in os.listdir(root):
    if not fn.endswith(".xml"):
        continue
    path = os.path.join(root, fn)
    with open(path, "r", encoding="utf-8") as f:
        s = f.read()
    orig = s
    s = s.replace('android:textColor="@android:color/white"', 'android:textColor="@color/ui_text_primary"')
    s = s.replace('android:textColor="@color/color_FFFFFF"', 'android:textColor="@color/ui_text_primary"')
    s = s.replace('android:textColor="@color/color_CCFFFFFF"', 'android:textColor="@color/ui_text_secondary"')
    s = s.replace('android:background="@color/color_FFFFFF"', 'android:background="@color/ui_stroke_subtle"')
    s = s.replace('android:indeterminateTint="@android:color/white"', 'android:indeterminateTint="@color/ui_accent"')
    s = s.replace('android:background="#FF333333"', 'android:background="@color/ui_stroke_subtle"')
    if s != orig:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(s)
        count += 1
        print(fn)
print("updated", count, "files")
