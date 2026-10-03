# fork: plain (non dot) icons, the counterparts of the dot matrix icons (ic_dot_*) for the "dot icons" setting turned
# off. From Material Symbols (github.com/google/material-design-icons, Apache License 2.0, Google), rounded style.
#
# usage: python3 scripts/plain_icons.py app/src/main/res/drawable
# Writes ic_plain_<name>.xml for every ic_dot_<name>.xml; DotIcons.kt maps one to the other.
import os
import re
import sys
import urllib.request

BASE = 'https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/{0}/materialsymbolsrounded/{0}{1}_24px.xml'

# our icon name -> (material symbol, filled)
MAP = {
    'backspace': ('backspace', False),
    'check': ('check', False),
    'clipboard': ('content_paste', False),
    'close': ('close', False),
    'copy': ('content_copy', False),
    'cut': ('content_cut', False),
    'emoji_activities': ('sports_soccer', False),
    'emoji_emoticons': ('sentiment_very_satisfied', False),
    'emoji_flags': ('flag', False),
    'emoji_food': ('coffee', False),
    'emoji_nature': ('eco', False),
    'emoji_objects': ('lightbulb', False),
    'emoji_people': ('person', False),
    'emoji_recents': ('schedule', False),
    'emoji_symbols': ('favorite', False),
    'emoji_travel': ('directions_car', False),
    'enter': ('keyboard_return', False),
    'gif': ('gif_box', False),
    'globe': ('language', False),
    'keyboard': ('keyboard', False),
    'left': ('chevron_left', False),
    'more': ('more_horiz', False),
    'paste': ('content_paste_go', False),
    'pin': ('bookmark', False),
    'pin_filled': ('bookmark', True),
    'pin_card': ('push_pin', False),
    'pin_card_filled': ('push_pin', True),
    'question': ('help', False),
    'redo': ('redo', False),
    'right': ('chevron_right', False),
    'search': ('search', False),
    'select_all': ('select_all', False),
    'select_word': ('text_select_end', False),
    'settings': ('settings', False),
    'shift': ('shift', False),
    'shift_filled': ('shift', True),
    'shift_locked': ('shift_lock', True),
    'smile': ('mood', False),
    'space': ('space_bar', False),
    'sparkles': ('auto_awesome', False),
    'star': ('star', False),
    'star_filled': ('star', True),
    'translate': ('translate', False),
    'trash': ('delete', False),
    'undo': ('undo', False),
    'zap': ('bolt', False),
}

out = sys.argv[1]
dots = sorted(f[7:-4] for f in os.listdir(out) if f.startswith('ic_dot_') and f.endswith('.xml'))
missing = [d for d in dots if d not in MAP]
if missing:
    sys.exit('no plain icon for ' + ', '.join(missing))
for name in dots:
    symbol, filled = MAP[name]
    xml = urllib.request.urlopen(BASE.format(symbol, '_fill1' if filled else '')).read().decode()
    # white like the dot icons, colored by the keyboard (no theme tint)
    xml = re.sub(r'\s*android:tint="[^"]*"', '', xml)
    xml = xml.replace('@android:color/white', '#FFFFFFFF')
    xml = xml.replace('<vector', '<!-- fork: Material Symbols "%s" (Apache License 2.0), scripts/plain_icons.py -->\n<vector' % symbol, 1)
    with open(os.path.join(out, 'ic_plain_%s.xml' % name), 'w') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n' + xml)
    print(name, '<-', symbol, 'filled' if filled else '')
