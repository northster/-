// Open-Meteo request + response parsing, kept free of Pebble APIs so it can
// be checked with plain node (see tools/test_weather.js).

var BASE = 'https://api.open-meteo.com/v1/forecast';

function url(lat, lon) {
  return BASE + '?latitude=' + lat.toFixed(3) + '&longitude=' + lon.toFixed(3) +
    '&current=temperature_2m,weather_code,is_day&temperature_unit=celsius&timezone=auto';
}

// Returns { tempC10, code, isDay } or null.
function parse(text) {
  var r;
  try {
    r = JSON.parse(text);
  } catch (e) {
    return null;
  }
  var c = r && r.current;
  if (!c || typeof c.temperature_2m !== 'number' || typeof c.weather_code !== 'number') {
    return null;
  }
  return {
    tempC10: Math.round(c.temperature_2m * 10),
    code: c.weather_code,
    isDay: c.is_day !== 0
  };
}

module.exports = { url: url, parse: parse };
