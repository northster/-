# One-line shortcuts around the pebble tool.
#
#   make emu                      build + run in the basalt emulator
#   make phone PHONE=192.168.0.12 build + install on the watch through the phone
#   make demo                     emulator with frozen 10:08 and sample data
#   make shot                     emulator screenshot -> docs/screenshots/latest.png
#   make logs / make test
#
# PHONE can also come from the PEBBLE_PHONE environment variable, which the
# pebble tool reads by itself (then plain `pebble install` goes to the watch).

PHONE ?= $(PEBBLE_PHONE)
PEBBLE ?= pebble
PEBBLE_PY := $(dir $(shell readlink -f $(shell command -v $(PEBBLE) 2>/dev/null)))python

.PHONY: build emu demo phone logs shot test health config clean

build:
	$(PEBBLE) build

emu: build
	$(PEBBLE) install --emulator basalt

demo:
	TRS_DEMO=1 $(PEBBLE) build
	$(PEBBLE) install --emulator basalt

phone: build
	@test -n "$(PHONE)" || { echo "set PHONE=<ip shown in the phone app's developer connection>"; exit 1; }
	$(PEBBLE) install --phone $(PHONE) --logs

logs:
	$(PEBBLE) logs $(if $(PHONE),--phone $(PHONE),--emulator basalt)

shot:
	$(PEBBLE) screenshot --no-open --emulator basalt docs/screenshots/latest.png

# Pebble Health is off in an emulator built from firmware source.
health:
	$(PEBBLE_PY) tools/emu_health_on.py basalt

# Open the Clay settings page for the emulator (in your desktop browser).
config:
	$(PEBBLE) emu-app-config --emulator basalt

test:
	node tools/test_weather.js

clean:
	$(PEBBLE) clean
