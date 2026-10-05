# fork: plain (non dot) icons, the counterparts of the dot matrix icons (ic_dot_*) for the "dot icons" setting turned
# off. iOS (SF Symbols) style: Framework7 Icons (github.com/framework7io/framework7-icons, MIT License, Copyright (c)
# 2016 Vladimir Kharlampidi); the few it has no icon for from Phosphor Icons, regular weight (similar line width;
# github.com/phosphor-icons/core, MIT License, Copyright (c) 2023 Phosphor Icons).
#
# usage: python3 scripts/plain_icons.py <framework7 svg folder> <phosphor assets folder> app/src/main/res/drawable
# (npm pack framework7-icons: package/svg; npm pack @phosphor-icons/core: package/assets)
# Writes ic_plain_<name>.xml for every ic_dot_<name>.xml; DotIcons.kt maps one to the other.
import os
import re
import sys

# our icon name -> Framework7 icon, or ('ph', Phosphor icon)
MAP = {
    'backspace': 'delete_left',
    'check': 'checkmark',
    'clipboard': ('ph', 'clipboard'),
    'close': 'xmark',
    'copy': 'doc_on_doc',
    'cut': 'scissors',
    'emoji_activities': 'sportscourt',
    'emoji_emoticons': 'text_bubble',
    'emoji_flags': 'flag',
    'emoji_food': ('ph', 'fork-knife'),
    'emoji_nature': 'hare',
    'emoji_objects': 'lightbulb',
    'emoji_people': 'person',
    'emoji_recents': 'clock',
    'emoji_symbols': 'heart',
    'emoji_travel': 'airplane',
    'enter': 'return',
    'gif': ('ph', 'gif'),
    'globe': 'globe',
    'keyboard': 'keyboard',
    'left': 'chevron_left',
    'more': 'ellipsis',
    'paste': 'arrow_down_doc',
    'pin': 'bookmark',
    'pin_filled': 'bookmark_fill',
    'pin_card': 'pin',
    'pin_card_filled': 'pin_fill',
    'question': 'question_circle',
    'redo': 'arrow_uturn_right',
    'right': 'chevron_right',
    'search': 'search',
    'select_all': 'selection_pin_in_out',
    'select_word': 'text_cursor',
    'settings': 'gear',
    'shift': 'shift',
    'shift_filled': 'shift_fill',
    'shift_locked': 'capslock_fill',
    'smile': 'smiley',
    'sparkles': 'sparkles',
    'star': 'star',
    'star_filled': 'star_fill',
    'translate': ('ph', 'translate'),
    'trash': 'trash',
    'undo': 'arrow_uturn_left',
    'zap': 'bolt',
}
# neither has a space bar: a ⎵ with Phosphor regular's line width (16 of 256), round ends
OWN = {
    'space': ('256', '<path android:strokeColor="#FFFFFFFF" android:strokeWidth="16" android:strokeLineCap="round" '
                     'android:strokeLineJoin="round" android:pathData="M40,112V156H216V112"/>'),
}

f7, ph, out = sys.argv[1], sys.argv[2], sys.argv[3]
dots = sorted(f[7:-4] for f in os.listdir(out) if f.startswith('ic_dot_') and f.endswith('.xml'))
missing = [d for d in dots if d not in MAP and d not in OWN]
if missing:
    sys.exit('no plain icon for ' + ', '.join(missing))
for name in dots:
    if name in OWN:
        source = 'own drawing'
        size, paths = OWN[name]
    else:
        icon = MAP[name]
        if isinstance(icon, tuple):
            path, source = os.path.join(ph, 'regular', icon[1] + '.svg'), 'Phosphor Icons "%s" regular' % icon[1]
        else:
            path, source = os.path.join(f7, icon + '.svg'), 'Framework7 Icons "%s"' % icon
        svg = open(path).read()
        if re.search(r'<(circle|rect|line|poly|ellipse|g)\b|transform=', svg):
            sys.exit(path + ': only plain paths are converted')
        size = re.search(r'viewBox="0 0 (\d+) \1"', svg).group(1)
        paths = '\n    '.join(
            '<path android:fillColor="#FFFFFFFF"%s android:pathData="%s"/>' % (
                ' android:fillType="evenOdd"' if 'evenodd' in attrs else '', re.sub(r'\s+', ' ', d).strip())
            for attrs, d in re.findall(r'<path([^>]*?)\sd="([^"]+)"', svg))
    with open(os.path.join(out, 'ic_plain_%s.xml' % name), 'w') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<!-- fork: %s (MIT), scripts/plain_icons.py -->\n'
                '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    android:width="24dp" android:height="24dp"\n'
                '    android:viewportWidth="%s" android:viewportHeight="%s">\n'
                '    %s\n</vector>\n' % (source, size, size, paths))
    print(name, '<-', source)
