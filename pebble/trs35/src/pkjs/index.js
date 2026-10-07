// PebbleKit JS: weather from Open-Meteo (no API key) at the phone's location.
//
// The watch asks with WEATHER_REQ every 30 minutes; on start-up we send the
// cached reading right away and only hit the network when it is old.
// On failure nothing is sent: the watch keeps the last reading.
// Temperature always travels in tenths of a degree Celsius; the watch
// converts to Fahrenheit itself, so switching units needs no refetch.

var Clay = require('@rebble/clay');
var clayConfig = require('./config');
var weather = require('./weather');

// Clay's own handlers would send the settings outside our queue and could
// collide with a weather message, so open and close are handled here.
var clay = new Clay(clayConfig, null, { autoHandleEvents: false });

var CACHE_KEY = 'trs35.weather';
var COORDS_KEY = 'trs35.coords';
var FRESH_MS = 20 * 60 * 1000;

/* ---- outgoing messages (one in flight at a time) ------------------------ */

var queue = [];
var sending = false;

function pump() {
  if (sending || queue.length === 0) return;
  sending = true;
  var entry = queue.shift();
  Pebble.sendAppMessage(entry.msg, function() {
    sending = false;
    pump();
  }, function() {
    sending = false;
    if (!entry.retried) {
      entry.retried = true;
      queue.unshift(entry);
      setTimeout(pump, 500);
    } else {
      pump();
    }
  });
}

function send(msg) {
  queue.push({ msg: msg, retried: false });
  pump();
}

/* ---- weather ------------------------------------------------------------ */

function readJSON(key) {
  try {
    return JSON.parse(localStorage.getItem(key));
  } catch (e) {
    return null;
  }
}

function sendWeather(w) {
  var msg = { TEMP_C10: w.tempC10, WCODE: w.code, IS_DAY: w.isDay ? 1 : 0 };
  // the face prints where the reading is from, e.g. 37.57N 126.98E
  if (typeof w.lat === 'number') {
    msg.LAT100 = Math.round(w.lat * 100);
    msg.LON100 = Math.round(w.lon * 100);
  }
  send(msg);
}

function fetchFor(lat, lon) {
  var xhr = new XMLHttpRequest();
  xhr.open('GET', weather.url(lat, lon), true);
  xhr.timeout = 15000;
  xhr.onload = function() {
    var w = xhr.status === 200 ? weather.parse(xhr.responseText) : null;
    if (!w) {
      console.log('open-meteo: bad response ' + xhr.status);
      return;
    }
    w.at = Date.now();
    w.lat = lat;
    w.lon = lon;
    localStorage.setItem(CACHE_KEY, JSON.stringify(w));
    console.log('open-meteo: ' + (w.tempC10 / 10) + 'C code ' + w.code);
    sendWeather(w);
  };
  xhr.onerror = xhr.ontimeout = function() {
    console.log('open-meteo: network error');
  };
  xhr.send();
}

function refresh() {
  navigator.geolocation.getCurrentPosition(function(pos) {
    var c = { lat: pos.coords.latitude, lon: pos.coords.longitude };
    localStorage.setItem(COORDS_KEY, JSON.stringify(c));
    fetchFor(c.lat, c.lon);
  }, function(err) {
    // no fix right now: fall back to the last known place
    var c = readJSON(COORDS_KEY);
    console.log('geolocation failed (' + (err && err.message) + ')' + (c ? ', using last fix' : ''));
    if (c) fetchFor(c.lat, c.lon);
  }, { timeout: 15000, maximumAge: 10 * 60 * 1000, enableHighAccuracy: false });
}

Pebble.addEventListener('ready', function() {
  var cached = readJSON(CACHE_KEY);
  if (cached) sendWeather(cached);
  if (!cached || Date.now() - cached.at > FRESH_MS) refresh();
});

Pebble.addEventListener('appmessage', function(e) {
  if (e.payload && 'WEATHER_REQ' in e.payload) refresh();
});

Pebble.addEventListener('showConfiguration', function() {
  Pebble.openURL(clay.generateUrl());
});

Pebble.addEventListener('webviewclosed', function(e) {
  if (!e || !e.response) return;  // cancelled
  send(clay.getSettings(e.response));
});
