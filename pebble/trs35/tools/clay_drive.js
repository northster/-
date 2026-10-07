// Headless stand-in for the phone's settings webview, for `pebble emu-app-config`:
//   BROWSER="node tools/clay_drive.js %s &" pebble emu-app-config --emulator basalt
// Opens the Clay page, screenshots it, applies CLAY_SET (JSON of
// {label text: value}), screenshots again and presses Save, which hands the
// result back to the emulator exactly like the phone app would.
const path = require('path');
let chromium;
try { ({ chromium } = require('playwright')); } catch (e) {
  ({ chromium } = require(path.join(process.env.NODE_PATH || '/opt/node-tools/node_modules', 'playwright')));
}

const url = process.argv[2];
const out = process.env.CLAY_SHOTS || '.';
const want = JSON.parse(process.env.CLAY_SET || '{}');

(async () => {
  const browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2 });
  // In the emulator Clay hands its page to clay.pebble.com, which writes the
  // HTML from the URL hash into the document with $$RETURN_TO$$ filled in
  // from ?return_to. Do the same locally (that host is not reachable here).
  await page.route(/clay\.pebble\.com/, (route) => route.fulfill({
    contentType: 'text/html',
    body: '<script>var r=new URLSearchParams(location.search).get("return_to")||"pebblejs://close#";' +
      'var h=decodeURIComponent(location.hash.slice(1)).split("$$RETURN_TO$$").join(r);' +
      'document.open();document.write(h);document.close();</script>',
  }));
  page.on('framenavigated', (f) => { if (f === page.mainFrame()) console.log('nav ' + f.url().slice(0, 120)); });
  await page.goto(url);
  await page.waitForSelector('button, input[type=submit]');
  await page.screenshot({ path: path.join(out, 'clay-before.png'), fullPage: true });

  // CLAY_SET keys are matched against each component's label text (e.g.
  // {"Color theme": "4", "Layout": "2"}); selects and radio groups take the
  // option value, toggles take true/false.
  for (const [label, value] of Object.entries(want)) {
    const ok = await page.evaluate(([l, v]) => {
      const comp = [...document.querySelectorAll('.component')]
        .find((c) => c.querySelector('.label') && c.querySelector('.label').textContent.includes(l));
      if (!comp) return false;
      const sel = comp.querySelector('select');
      if (sel) {
        sel.value = v;
        sel.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
      }
      const radio = comp.querySelector(`input[type=radio][value="${v}"]`);
      if (radio) { radio.click(); return true; }
      const box = comp.querySelector('input[type=checkbox]');
      if (box) { if (box.checked !== (v === true || v === 'true')) box.click(); return true; }
      return false;
    }, [label, value]);
    if (!ok) console.error('could not set ' + label);
  }
  await page.screenshot({ path: path.join(out, 'clay-after.png'), fullPage: true });
  await Promise.all([
    page.waitForURL(/close/, { timeout: 10000 }).catch(() => {}),
    page.locator('button.button, input[type=submit], .component-submit button').first().click(),
  ]);
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
