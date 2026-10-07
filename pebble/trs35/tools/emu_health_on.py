#!/usr/bin/env python3
"""Turn Pebble Health on in a running emulator.

Firmware built from source starts with Health disabled, so any app that
reads step counts gets the "This app requires Pebble Health" popup. The
phone app normally flips this through the Prefs BlobDB; this does the same.

Run it with the pebble tool's own Python (it has libpebble2):

  $(dirname $(readlink -f $(which pebble)))/python tools/emu_health_on.py [basalt]
"""
import json
import os
import struct
import sys
import tempfile
from types import SimpleNamespace

from libpebble2.communication import PebbleConnection
from libpebble2.communication.transports.websocket import WebsocketTransport
from libpebble2.services.blobdb import BlobDBClient, BlobStatus, SyncWrapper

PREFS_DB = 0x07  # BlobDBIdPrefs in the firmware
KEY = b'activityPreferences\0'  # the firmware matches keys with strncmp


def pypkjs_port(platform):
    with open(os.path.join(tempfile.gettempdir(), 'pb-emulator.json')) as f:
        info = json.load(f)[platform]
    return next(iter(info.values()))['pypkjs']['port']


def main():
    platform = sys.argv[1] if len(sys.argv) > 1 else 'basalt'
    conn = PebbleConnection(WebsocketTransport('ws://localhost:%d/' % pypkjs_port(platform)))
    conn.connect()
    conn.run_async()
    # ActivitySettings (packed): height_mm, weight_dag, tracking_enabled,
    # activity_insights, sleep_insights, age_years, gender
    value = struct.pack('<hh???bb', 1700, 7000, True, False, False, 30, 2)
    # BlobDBClient expects a UUID-like key (it reads key.bytes)
    result = SyncWrapper(BlobDBClient(conn).insert, PREFS_DB, SimpleNamespace(bytes=KEY), value).wait()
    print('health on:', 'ok' if result == BlobStatus.Success else result)
    return 0 if result == BlobStatus.Success else 1


if __name__ == '__main__':
    sys.exit(main())
