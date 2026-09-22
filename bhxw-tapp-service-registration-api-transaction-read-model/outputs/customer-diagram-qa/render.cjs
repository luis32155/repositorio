const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('C:/Users/Windows/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');

(async () => {
  const root = path.resolve(__dirname, '../..');
  const file = path.join(root, 'bhxw-customer-event-consumer/docs', process.argv[2] || 'diagrama-base-datos-customer-consumer.md');
  const markdown = fs.readFileSync(file, 'utf8');
  const diagrams = [...markdown.matchAll(/```mermaid\r?\n([\s\S]*?)```/g)].map(match => match[1]);
  if (diagrams.length === 0) throw new Error('No Mermaid diagrams found');
  const browser = await chromium.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 2000, height: 1400 }, deviceScaleFactor: 1 });
    await page.setContent('<html><head><style>body { margin: 24px; background: #fff; } #diagram { width: max-content; padding: 16px; }</style></head><body><div id="diagram"></div></body></html>');
    await page.addScriptTag({ path: path.join(__dirname, 'node_modules/mermaid/dist/mermaid.min.js') });
    await page.evaluate(() => mermaid.initialize({ startOnLoad: false, securityLevel: 'strict' }));
    for (let i = 0; i < diagrams.length; i++) {
      const result = await page.evaluate(async ({ code, index }) => {
        await mermaid.parse(code);
        const { svg } = await mermaid.render(`customerDiagram${index}`, code);
        const host = document.getElementById('diagram');
        host.innerHTML = svg;
        const element = host.querySelector('svg');
        const box = element.viewBox.baseVal;
        element.style.maxWidth = 'none';
        element.style.width = `${Math.ceil(box.width)}px`;
        element.style.height = `${Math.ceil(box.height)}px`;
        return { svg, width: Math.ceil(box.width), height: Math.ceil(box.height) };
      }, { code: diagrams[i], index: i + 1 });
      fs.writeFileSync(path.join(__dirname, `diagram-${i + 1}.svg`), result.svg);
      await page.setViewportSize({ width: result.width + 100, height: Math.min(result.height + 100, 1800) });
      await page.locator('#diagram').screenshot({ path: path.join(__dirname, `diagram-${i + 1}.png`) });
      console.log(`Diagram ${i + 1}: parsed and rendered (${result.width} x ${result.height})`);
    }
    for (const match of markdown.matchAll(/\]\(([^)]+)\)/g)) {
      if (!/^https?:/.test(match[1]) && !fs.existsSync(path.resolve(path.dirname(file), match[1]))) throw new Error(`Broken reference: ${match[1]}`);
    }
    console.log('All local document references resolve.');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
