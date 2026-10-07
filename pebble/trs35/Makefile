# One-line shortcuts around the pebble tool.
#
#   make emu                      build + run in the basalt emulator
#   make phone PHONE=192.168.0.12 build + install on the watch through the phone
#   make demo                     emulator with frozen 10:08 and sample data
#   make shot                     emulator screenshot -> docs/screenshots/latest.png
#   make font FONT=KHInterference-Bold.otf [NARROW=...]
#                                 use another display font (not committed)
#   make font-reset               back to the bundled TRS Grid
#   make logs / make test
#
# PHONE can also come from the PEBBLE_PHONE environment variable, which the
# pebble tool reads by itself (then plain `pebble install` goes to the watch).

PHONE ?= $(PEBBLE_PHONE)
PEBBLE ?= pebble
PEBBLE_PY := $(dir $(shell readlink -f $(shell command -v $(PEBBLE) 2>/dev/null)))python

.PHONY: build emu demo phone logs shot test health config font font-reset fonts clean

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

# Display fonts: resources/fonts/{time,date,temp,steps}.ttf. They are
# git-ignored so a licensed font never lands in the repository; positions
# are re-measured on the next build. NARROW (optional) is used for steps.
FONT_FILES := time date temp
font:
	@test -n "$(FONT)" || { echo "usage: make font FONT=path/to/font.otf [NARROW=path]"; exit 1; }
	for f in $(FONT_FILES); do cp "$(FONT)" resources/fonts/$$f.ttf; done
	cp "$(or $(NARROW),$(FONT))" resources/fonts/steps.ttf
	$(PEBBLE) clean

font-reset:
	for f in Time Date Temp Steps; do \
	  cp resources/fonts/TRSGrid-$$f.ttf resources/fonts/$$(echo $$f | tr A-Z a-z).ttf; done
	$(PEBBLE) clean

# Rebuild the bundled TRS Grid pixel cuts (needs: pip install fonttools shapely)
fonts:
	python3 tools/make_font.py

clean:
	$(PEBBLE) clean
