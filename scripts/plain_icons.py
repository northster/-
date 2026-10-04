# fork: plain (non dot) icons, the counterparts of the dot matrix icons (ic_dot_*) for the "dot icons" setting turned
# off. From Phosphor Icons, light weight (github.com/phosphor-icons/core, MIT License, Copyright (c) 2023 Phosphor
# Icons): thin lines that go with the keyboard's look.
#
# usage: python3 scripts/plain_icons.py <phosphor assets folder> app/src/main/res/drawable
# (the assets folder is in the npm package: npm pack @phosphor-icons/core, package/assets)
# Writes ic_plain_<name>.xml for every ic_dot_<name>.xml; DotIcons.kt maps one to the other.
import os
import re
import sys

# our icon name -> (phosphor icon, weight)
MAP = {
    'backspace': ('backspace', 'light'),
    'check': ('check', 'light'),
    'clipboard': ('clipboard', 'light'),
    'close': ('x', 'light'),
    'copy': ('copy', 'light'),
    'cut': ('scissors', 'light'),
    'emoji_activities': ('soccer-ball', 'light'),
    'emoji_emoticons': ('smiley-wink', 'light'),
    'emoji_flags': ('flag', 'light'),
    'emoji_food': ('coffee', 'light'),
    'emoji_nature': ('leaf', 'light'),
    'emoji_objects': ('lightbulb', 'light'),
    'emoji_people': ('person', 'light'),
    'emoji_recents': ('clock', 'light'),
    'emoji_symbols': ('heart', 'light'),
    'emoji_travel': ('car', 'light'),
    'enter': ('arrow-elbow-down-left', 'light'),
    'gif': ('gif', 'light'),
    'globe': ('globe', 'light'),
    'keyboard': ('keyboard', 'light'),
    'left': ('caret-left', 'light'),
    'more': ('dots-three', 'light'),
    'paste': ('clipboard-text', 'light'),
    'pin': ('bookmark-simple', 'light'),
    'pin_filled': ('bookmark-simple', 'fill'),
    'pin_card': ('push-pin', 'light'),
    'pin_card_filled': ('push-pin', 'fill'),
    'question': ('question', 'light'),
    'redo': ('arrow-u-up-right', 'light'),
    'right': ('caret-right', 'light'),
    'search': ('magnifying-glass', 'light'),
    'select_all': ('selection-all', 'light'),
    'select_word': ('cursor-text', 'light'),
    'settings': ('gear-six', 'light'),
    'shift': ('arrow-fat-up', 'light'),
    'shift_filled': ('arrow-fat-up', 'fill'),
    'shift_locked': ('arrow-fat-line-up', 'fill'),
    'smile': ('smiley', 'light'),
    'sparkles': ('sparkle', 'light'),
    'star': ('star', 'light'),
    'star_filled': ('star', 'fill'),
    'translate': ('translate', 'light'),
    'trash': ('trash', 'light'),
    'undo': ('arrow-u-up-left', 'light'),
    'zap': ('lightning', 'light'),
}
# Phosphor has no space bar: a ⎵ with the light weight's line width (12 of 256), round ends
OWN = {
    'space': '<path android:strokeColor="#FFFFFFFF" android:strokeWidth="12" android:strokeLineCap="round" '
             'android:strokeLineJoin="round" android:pathData="M40,116V156H216V116"/>',
}

assets, out = sys.argv[1], sys.argv[2]
dots = sorted(f[7:-4] for f in os.listdir(out) if f.startswith('ic_dot_') and f.endswith('.xml'))
missing = [d for d in dots if d not in MAP and d not in OWN]
if missing:
    sys.exit('no plain icon for ' + ', '.join(missing))
for name in dots:
    if name in OWN:
        source, paths = 'own drawing', OWN[name]
    else:
        icon, weight = MAP[name]
        svg = open(os.path.join(assets, weight, '%s-%s.svg' % (icon, weight))).read()
        if re.search(r'<(circle|rect|line|poly|ellipse)', svg):
            sys.exit(icon + ': only paths are converted')
        source = 'Phosphor "%s" %s' % (icon, weight)
        paths = '\n    '.join('<path android:fillColor="#FFFFFFFF" android:pathData="%s"/>' % d
                               for d in re.findall(r'<path[^>]*\sd="([^"]+)"', svg))
    with open(os.path.join(out, 'ic_plain_%s.xml' % name), 'w') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<!-- fork: %s (MIT), scripts/plain_icons.py -->\n'
                '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    android:width="24dp" android:height="24dp"\n'
                '    android:viewportWidth="256" android:viewportHeight="256">\n'
                '    %s\n</vector>\n' % (source, paths))
    print(name, '<-', source)
