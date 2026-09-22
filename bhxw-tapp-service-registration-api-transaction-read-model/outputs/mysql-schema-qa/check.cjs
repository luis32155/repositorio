const fs = require('node:fs');
const { Parser } = require('node-sql-parser');

const file = 'bhxw-customer-event-consumer/scripts/mysql/drafts/core-b28-mysql-schema.sql';
const sql = fs.readFileSync(file, 'utf8');
const ddl = sql.split('DELIMITER $$')[0];
const parser = new Parser();
const ast = parser.astify(ddl, { database: 'MySQL' });
const tables = [...sql.matchAll(/CREATE TABLE IF NOT EXISTS\s+([a-z0-9_]+)/gi)].map(match => match[1]);
const sourceTables = tables.filter(name => name !== 'customer_core_b28_projection');

if (sourceTables.length !== 7) throw new Error(`Expected 7 source tables, found ${sourceTables.length}`);
if (new Set(tables).size !== 8) throw new Error(`Expected 8 distinct physical tables, found ${new Set(tables).size}`);
if (!sql.includes('CREATE PROCEDURE refresh_customer_core_b28_projection()')) throw new Error('Refresh procedure missing');
if (!sql.includes("wv50atdat = '1' AND wv50aest = '1'")) throw new Error('Validated email rule missing');
if (!sql.includes("wv50atdat = '2' AND wv50aest = '1'")) throw new Error('Validated phone rule missing');

console.log(`Parsed ${Array.isArray(ast) ? ast.length : 1} MySQL DDL statements.`);
console.log(`Verified ${sourceTables.length} CORE tables and 1 physical B28 projection table.`);
