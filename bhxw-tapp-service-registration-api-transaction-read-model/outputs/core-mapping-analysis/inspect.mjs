import fs from 'node:fs/promises';
import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';

const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load('C:/Users/Windows/Downloads/Mapeo Campos B28 - Tablas CORE (1).xlsx'));
const summary = await workbook.inspect({kind: 'workbook,sheet,table', maxChars: 16000, tableMaxRows: 4, tableMaxCols: 10, tableMaxCellChars: 140});
console.log(summary.ndjson);
await fs.writeFile(new URL('./summary.ndjson', import.meta.url), summary.ndjson);
const letters = value => {
  let result = '';
  for (let n = value + 1; n > 0; n = Math.floor((n - 1) / 26)) result = String.fromCharCode(65 + (n - 1) % 26) + result;
  return result;
};
const extracted = [];
for (let index = 0; index < 4; index++) {
  const sheet = workbook.worksheets.getItemAt(index);
  const values = sheet.getRange('A1:W80').values;
  const rows = values.map((row, r) => ({row:r + 1, cells: row.flatMap((value, c) => value === null || value === undefined || value === '' ? [] : [{cell: `${letters(c)}${r + 1}`, value}])})).filter(row => row.cells.length);
  extracted.push({name:sheet.name, rows});
  await fs.writeFile(new URL(`./sheet-${index + 1}.json`, import.meta.url), JSON.stringify({name:sheet.name, rows}, null, 2));
  console.log(`EXTRACTED ${sheet.name}: ${rows.length} populated rows`);
}
await fs.writeFile(new URL('./workbook-extracted.json', import.meta.url), JSON.stringify(extracted, null, 2));
const preview = await workbook.render({sheetName:'Materialized View', range:'A12:W32', scale:1, format:'png'});
await fs.writeFile(new URL('./materialized-view-source.png', import.meta.url), new Uint8Array(await preview.arrayBuffer()));
