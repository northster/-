# fork: thin line icons, the third icon style (Theme > display > icons): every line the same thin width, no thick and
# thin parts, like a sans-serif font. From Tabler Icons (github.com/tabler/tabler-icons, MIT License, Copyright (c)
# 2020-2026 Paweł Kuna), outline icons drawn with a thinner line (WIDTH instead of 2), filled ones for active states.
#
# usage: python3 scripts/line_icons.py <tabler icons folder> app/src/main/res/drawable
# (the icons folder is in the npm package: npm pack @tabler/icons, package/icons)
# Writes ic_line_<name>.xml for every ic_dot_<name>.xml; DotIcons.kt maps one to the other.
import os
import re
import sys

WIDTH = '1.5'

# our icon name -> Tabler icon, or ('filled', Tabler icon)
MAP = {
    'backspace': 'backspace',
    'check': 'check',
    'clipboard': 'clipboard',
    'close': 'x',
    'copy': 'copy',
    'cut': 'scissors',
    'emoji_activities': 'ball-football',
    'emoji_emoticons': 'mood-wink',
    'emoji_flags': 'flag',
    'emoji_food': 'tools-kitchen-2',
    'emoji_nature': 'leaf',
    'emoji_objects': 'bulb',
    'emoji_people': 'user',
    'emoji_recents': 'clock',
    'emoji_symbols': 'heart',
    'emoji_travel': 'car',
    'enter': 'corner-down-left',
    'gif': 'gif',
    'globe': 'world',
    'keyboard': 'keyboard',
    'left': 'chevron-left',
    'more': 'dots',
    'paste': 'clipboard-text',
    'pin': 'bookmark',
    'pin_filled': ('filled', 'bookmark'),
    'pin_card': 'pin',
    'pin_card_filled': ('filled', 'pin'),
    'question': 'help-circle',
    'redo': 'arrow-forward-up',
    'right': 'chevron-right',
    'search': 'search',
    'select_all': 'select-all',
    'select_word': 'cursor-text',
    'settings': 'settings',
    'shift': 'arrow-big-up',
    'shift_filled': ('filled', 'arrow-big-up'),
    'shift_locked': ('filled', 'arrow-big-up-lines'),
    'smile': 'mood-smile',
    'space': 'space',
    'sparkles': 'sparkles',
    'star': 'star',
    'star_filled': ('filled', 'star'),
    'translate': 'language',
    'trash': 'trash',
    'undo': 'arrow-back-up',
    'zap': 'bolt',
}

icons, out = sys.argv[1], sys.argv[2]
dots = sorted(f[7:-4] for f in os.listdir(out) if f.startswith('ic_dot_') and f.endswith('.xml'))
missing = [d for d in dots if d not in MAP]
if missing:
    sys.exit('no line icon for ' + ', '.join(missing))
for name in dots:
    icon = MAP[name]
    filled = isinstance(icon, tuple)
    if filled:
        icon = icon[1]
    svg = open(os.path.join(icons, 'filled' if filled else 'outline', icon + '.svg')).read()
    if re.search(r'<(circle|rect|line|poly|ellipse|g)\b|transform=', svg):
        sys.exit(icon + ': only paths are converted')
    paths = []
    for attrs, d in re.findall(r'<path([^>]*?)\sd="([^"]+)"', svg):
        if 'stroke="none"' in attrs:  # the invisible 24x24 frame
            continue
        if filled:
            paths.append('<path android:fillColor="#FFFFFFFF" android:pathData="%s"/>' % d)
        else:
            paths.append('<path android:strokeColor="#FFFFFFFF" android:strokeWidth="%s" android:strokeLineCap="round" '
                         'android:strokeLineJoin="round" android:pathData="%s"/>' % (WIDTH, d))
    with open(os.path.join(out, 'ic_line_%s.xml' % name), 'w') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<!-- fork: Tabler Icons "%s"%s (MIT), scripts/line_icons.py -->\n'
                '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    android:width="24dp" android:height="24dp"\n'
                '    android:viewportWidth="24" android:viewportHeight="24">\n'
                '    %s\n</vector>\n' % (icon, ' filled' if filled else '', '\n    '.join(paths)))
    print(name, '<-', icon, 'filled' if filled else '')
