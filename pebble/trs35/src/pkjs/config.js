// Clay settings page, opened from the Pebble phone app (watchface > settings).
// Select values travel as strings ("0".."5"); the watch accepts both.

function swatch(colors) {
  return colors.map(function(c) {
    return '<span style="display:inline-block;width:18px;height:18px;margin-right:2px;' +
      'border:1px solid #888;background:' + c + '"></span>';
  }).join('');
}

// Same order and colors as src/c/theme.c (Pebble palette values):
// field, main ink, block, panel, spot.
var THEMES = [
  ['LIME', 'Acid lime, black and white blocks, blue spot', ['#AAFF00', '#000000', '#000000', '#FFFFFF', '#0055FF']],
  ['MINT', 'Pale mint field', ['#AAFFFF', '#000000', '#000000', '#FFFFFF', '#0055FF']],
  ['ACID', 'Black field, lime block', ['#000000', '#FFFFFF', '#AAFF00', '#FFFFFF', '#55FFFF']],
  ['VIOLET', 'Black field, violet block', ['#000000', '#FFFFFF', '#AA55FF', '#FFFFFF', '#AAFF00']],
  ['SIGNAL', 'White paper, lime panel', ['#FFFFFF', '#000000', '#000000', '#AAFF00', '#0055FF']],
  ['NULL', 'Hot pink field', ['#FF0055', '#000000', '#000000', '#FFFFFF', '#000000']],
  ['FLARE', 'Ultramarine field, lime panel', ['#5500FF', '#FFFFFF', '#000000', '#AAFF00', '#FF0055']]
];

var themeTable = THEMES.map(function(t) {
  return '<div style="margin:6px 0">' + swatch(t[2]) +
    ' <b>' + t[0] + '</b> <small>' + t[1] + '</small></div>';
}).join('');

module.exports = [
  {
    type: 'heading',
    defaultValue: 'TR-S 35'
  },
  {
    type: 'text',
    defaultValue: 'Geometric collage watchface. 설정은 저장하면 워치에 바로 반영됩니다.'
  },
  {
    type: 'section',
    items: [
      { type: 'heading', defaultValue: '모양 / Look' },
      {
        type: 'select',
        messageKey: 'THEME',
        label: '색 테마 / Color theme',
        defaultValue: '0',
        options: THEMES.map(function(t, i) {
          return { label: t[0] + ' — ' + t[1], value: String(i) };
        })
      },
      { type: 'text', defaultValue: themeTable },
      {
        type: 'radiogroup',
        messageKey: 'LAYOUT',
        label: '배치 프리셋 / Layout',
        defaultValue: '0',
        options: [
          { label: 'STACK — 시간 위, 날씨 왼쪽·걸음 오른쪽', value: '0' },
          { label: 'SWAP — 날씨·걸음 좌우 바꿈', value: '1' },
          { label: 'INVERT — 날씨·걸음 위, 시간 아래', value: '2' }
        ]
      }
    ]
  },
  {
    type: 'section',
    items: [
      { type: 'heading', defaultValue: '단위 / Units' },
      {
        type: 'radiogroup',
        messageKey: 'CLOCK_MODE',
        label: '시간 표시 / Clock',
        defaultValue: '0',
        options: [
          { label: '워치 설정 따름 / Watch setting', value: '0' },
          { label: '12시간 / 12h', value: '1' },
          { label: '24시간 / 24h', value: '2' }
        ]
      },
      {
        type: 'radiogroup',
        messageKey: 'TEMP_UNIT',
        label: '온도 / Temperature',
        defaultValue: '0',
        options: [
          { label: '섭씨 °C', value: '0' },
          { label: '화씨 °F', value: '1' }
        ]
      }
    ]
  },
  {
    type: 'section',
    items: [
      { type: 'heading', defaultValue: '동작 / Behaviour' },
      {
        type: 'toggle',
        messageKey: 'SHAKE_ANIM',
        label: '흔들면 애니메이션 / Shake animation',
        description: '손목을 흔들면 약 1초 동안 블록이 재배치됩니다 (16프레임).',
        defaultValue: true
      },
      {
        type: 'toggle',
        messageKey: 'BT_VIBE',
        label: '연결 끊김 진동 / Vibrate on disconnect',
        defaultValue: true
      }
    ]
  },
  {
    type: 'submit',
    defaultValue: '저장 / Save'
  }
];
