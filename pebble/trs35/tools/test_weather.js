// node tools/test_weather.js  -- checks the Open-Meteo parsing without a phone
var assert = require('assert');
var w = require('../src/pkjs/weather');

var sample = JSON.stringify({
  latitude: 37.56, longitude: 126.98, timezone: 'Asia/Seoul',
  current_units: { temperature_2m: '°C', weather_code: 'wmo code', is_day: '' },
  current: { time: '2026-10-07T16:00', interval: 900, temperature_2m: 18.46, weather_code: 3, is_day: 1 }
});
assert.deepStrictEqual(w.parse(sample), { tempC10: 185, code: 3, isDay: true });

var night = JSON.stringify({ current: { temperature_2m: -4.04, weather_code: 71, is_day: 0 } });
assert.deepStrictEqual(w.parse(night), { tempC10: -40, code: 71, isDay: false });

assert.strictEqual(w.parse('not json'), null);
assert.strictEqual(w.parse(JSON.stringify({ error: true, reason: 'x' })), null);

var u = w.url(37.5671, 126.978);
assert.ok(u.indexOf('latitude=37.567&longitude=126.978') > 0, u);
assert.ok(u.indexOf('current=temperature_2m,weather_code,is_day') > 0, u);
console.log('weather.js ok');
