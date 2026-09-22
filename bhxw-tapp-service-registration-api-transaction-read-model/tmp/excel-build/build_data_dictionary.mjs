import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const ROOT = path.resolve(import.meta.dirname, "../..");
const JSON_DIR = path.join(ROOT, "tmp", "spreadsheet-json");
const OUTPUT_DIR = path.join(ROOT, "outputs", "niubiz-juspay-contracts");
const PREVIEW_DIR = path.join(ROOT, "tmp", "excel-build", "previews");
const OUTPUT_FILE = path.join(OUTPUT_DIR, "diccionario_datos_contratos_niubiz_juspay_ordenado.xlsx");
const REFERENCE_FILE = "C:/Users/Windows/Downloads/Mapeo Niubiz_V1.xlsx";

if (process.argv.includes("--inspect-reference")) {
  const referenceInput = await FileBlob.load(REFERENCE_FILE);
  const referenceWorkbook = await SpreadsheetFile.importXlsx(referenceInput);
  const overview = await referenceWorkbook.inspect({
    kind: "workbook,sheet,table",
    maxChars: 12000,
    tableMaxRows: 12,
    tableMaxCols: 16,
    tableMaxCellChars: 120,
  });
  console.log(overview.ndjson);
  const sheets = await referenceWorkbook.inspect({ kind: "sheet", include: "id,name", maxChars: 8000 });
  console.log(sheets.ndjson);
  const names = [];
  for (const line of sheets.ndjson.split(/\r?\n/).filter(Boolean)) {
    try {
      const row = JSON.parse(line);
      if (row.name) names.push(row.name);
      else if (row.sheet?.name) names.push(row.sheet.name);
    } catch {}
  }
  const previewDir = path.join(ROOT, "tmp", "excel-build", "reference-previews");
  await fs.mkdir(previewDir, { recursive: true });
  for (const sheetName of [...new Set(names)]) {
    const preview = await referenceWorkbook.render({ sheetName, autoCrop: "all", scale: 1, format: "png" });
    const safeName = sheetName.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "sheet";
    await fs.writeFile(path.join(previewDir, `${safeName}.png`), new Uint8Array(await preview.arrayBuffer()));
  }
  console.log(JSON.stringify({ reference: REFERENCE_FILE, sheets: [...new Set(names)], previewDir }, null, 2));
  process.exit(0);
}

if (process.argv.includes("--verify-output")) {
  const outputInput = await FileBlob.load(OUTPUT_FILE);
  const outputWorkbook = await SpreadsheetFile.importXlsx(outputInput);
  const summary = await outputWorkbook.inspect({
    kind: "table",
    range: "00 Inicio!A1:J28",
    include: "values,formulas",
    tableMaxRows: 30,
    tableMaxCols: 12,
    maxChars: 18000,
  });
  const canonicalEndpoint = await outputWorkbook.inspect({
    kind: "table",
    range: "01 CBS Listar Cuentas!A1:O45",
    include: "values,formulas",
    tableMaxRows: 46,
    tableMaxCols: 15,
    maxChars: 22000,
  });
  const questions = await outputWorkbook.inspect({
    kind: "table",
    range: "95 Consultas Niubiz!A1:F14",
    include: "values,formulas",
    tableMaxRows: 16,
    tableMaxCols: 8,
    maxChars: 12000,
  });
  const sheets = await outputWorkbook.inspect({ kind: "sheet", include: "id,name", maxChars: 12000 });
  const formulaErrors = await outputWorkbook.inspect({
    kind: "match",
    searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
    options: { useRegex: true, maxResults: 300 },
    summary: "reopened workbook formula error scan",
  });
  console.log(summary.ndjson);
  console.log(canonicalEndpoint.ndjson);
  console.log(questions.ndjson);
  console.log(sheets.ndjson);
  console.log(formulaErrors.ndjson);
  process.exit(0);
}

const openApiFiles = [
  "juspay-peru-cbs-api.json",
  "accounts-api.json",
  "customer-profile-api.json",
  "debit-card-api.json",
  "debit-payments-api.json",
  "credit-payments-api.json",
];
const asyncApiFiles = ["asyncapi-cdc.json", "asyncapi.json"];
const avroFiles = [
  path.join(ROOT, "docs", "avro", "account-domain-event.avsc"),
  path.join(ROOT, "docs", "avro", "customer-domain-event.avsc"),
];

const METHOD_NAMES = new Set(["get", "post", "put", "patch", "delete", "head", "options", "trace"]);
const COLORS = {
  red: "#548235",
  dark: "#375623",
  slate: "#51606E",
  light: "#F2F2F2",
  paleRed: "#FFF200",
  paleBlue: "#E8F1FA",
  paleAmber: "#FFF3D6",
  paleGreen: "#E2F0D9",
  white: "#FFFFFF",
  border: "#D4D9DE",
};

const readJson = async (file) => JSON.parse(await fs.readFile(file, "utf8"));
const clean = (value) => {
  if (value === undefined || value === null) return "";
  if (Array.isArray(value)) return value.map(clean).filter(Boolean).join(" | ");
  if (typeof value === "object") return JSON.stringify(value);
  return String(value).replace(/\s+/g, " ").trim();
};
const refName = (ref = "") => decodeURIComponent(ref.split("/").pop() || "");
const resolveRef = (doc, ref) => {
  if (!ref?.startsWith("#/")) return null;
  return ref.slice(2).split("/").reduce((node, key) => node?.[decodeURIComponent(key.replace(/~1/g, "/").replace(/~0/g, "~"))], doc);
};
const resolveObject = (doc, value) => value?.$ref ? (resolveRef(doc, value.$ref) || value) : value;
const firstExample = (schema = {}) => {
  if (schema.example !== undefined) return clean(schema.example);
  if (Array.isArray(schema.examples) && schema.examples.length) return clean(schema.examples[0]);
  if (schema.default !== undefined) return clean(schema.default);
  return "";
};
const nullableOf = (schema = {}) => {
  if (schema.nullable === true) return "Sí";
  if (Array.isArray(schema.type) && schema.type.includes("null")) return "Sí";
  if (Array.isArray(schema.oneOf) && schema.oneOf.some((x) => x?.type === "null")) return "Sí";
  if (Array.isArray(schema.anyOf) && schema.anyOf.some((x) => x?.type === "null")) return "Sí";
  return "No";
};
const constraintsOf = (schema = {}) => {
  const parts = [];
  const labels = {
    minLength: "minLength",
    maxLength: "maxLength",
    minimum: "minimum",
    maximum: "maximum",
    exclusiveMinimum: "exclusiveMinimum",
    exclusiveMaximum: "exclusiveMaximum",
    minItems: "minItems",
    maxItems: "maxItems",
    minProperties: "minProperties",
    maxProperties: "maxProperties",
  };
  for (const [key, label] of Object.entries(labels)) {
    if (schema[key] !== undefined) parts.push(`${label}=${clean(schema[key])}`);
  }
  if (schema.pattern) parts.push(`pattern=${schema.pattern}`);
  if (schema.uniqueItems) parts.push("uniqueItems=true");
  if (schema.additionalProperties === false) parts.push("additionalProperties=false");
  return parts.join("; ");
};
const typeOf = (doc, raw = {}) => {
  const schema = resolveObject(doc, raw) || raw;
  const suffix = raw?.$ref ? ` (${refName(raw.$ref)})` : "";
  if (schema.type === "array") return `array<${typeOf(doc, schema.items || {})}>${suffix}`;
  if (Array.isArray(schema.type)) return `${schema.type.join(" | ")}${suffix}`;
  if (schema.type) return `${schema.type}${suffix}`;
  if (schema.oneOf) return `oneOf<${schema.oneOf.map((x) => typeOf(doc, x)).join(" | ")}>${suffix}`;
  if (schema.anyOf) return `anyOf<${schema.anyOf.map((x) => typeOf(doc, x)).join(" | ")}>${suffix}`;
  if (schema.allOf) return `allOf<object>${suffix}`;
  if (schema.properties) return `object${suffix}`;
  return suffix ? suffix.trim() : "unspecified";
};

function schemaView(doc, raw = {}, seen = new Set()) {
  let ref = raw?.$ref || "";
  let schema = resolveObject(doc, raw) || {};
  const currentName = refName(ref);
  if (currentName && seen.has(currentName)) {
    return { schema, properties: schema.properties || {}, required: new Set(schema.required || []), conditional: new Map(), refName: currentName };
  }
  const nextSeen = new Set(seen);
  if (currentName) nextSeen.add(currentName);
  const properties = { ...(schema.properties || {}) };
  const required = new Set(schema.required || []);
  const conditional = new Map();
  for (const itemRaw of schema.allOf || []) {
    const item = resolveObject(doc, itemRaw) || itemRaw;
    if (item?.if && item?.then) {
      const conditionParts = [];
      for (const [name, condition] of Object.entries(item.if.properties || {})) {
        if (condition.const !== undefined) conditionParts.push(`${name}=${clean(condition.const)}`);
        else if (condition.enum) conditionParts.push(`${name} in (${clean(condition.enum)})`);
      }
      const rule = conditionParts.length ? `Cuando ${conditionParts.join(" y ")}` : "Según condición JSON Schema";
      for (const field of item.then.required || []) conditional.set(field, rule);
      continue;
    }
    const sub = schemaView(doc, itemRaw, nextSeen);
    Object.assign(properties, sub.properties);
    for (const field of sub.required) required.add(field);
    for (const [field, rule] of sub.conditional) conditional.set(field, rule);
  }
  return { schema, properties, required, conditional, refName: currentName };
}

function flattenHttpSchema(doc, rawSchema, context, parentPath = "", depth = 0, lineage = new Set()) {
  if (!rawSchema || depth > 10) return [];
  const view = schemaView(doc, rawSchema, lineage);
  const rows = [];
  const nextLineage = new Set(lineage);
  if (view.refName) {
    if (nextLineage.has(view.refName)) return rows;
    nextLineage.add(view.refName);
  }
  for (const [field, propertyRaw] of Object.entries(view.properties)) {
    const property = resolveObject(doc, propertyRaw) || propertyRaw;
    const fieldPath = parentPath ? `${parentPath}.${field}` : field;
    const conditionalRule = view.conditional.get(field) || "";
    const requiredStatus = view.required.has(field) ? "Sí" : conditionalRule ? "Condicional" : "No";
    const enumValues = property.enum || (property.const !== undefined ? [property.const] : []);
    rows.push([
      context.scope,
      context.contract,
      context.version,
      context.operationId,
      context.direction,
      context.httpStatus,
      context.rootSchema,
      fieldPath,
      field,
      requiredStatus,
      conditionalRule,
      typeOf(doc, propertyRaw),
      clean(property.format),
      clean(enumValues),
      constraintsOf(property),
      nullableOf(property),
      clean(property.description),
      firstExample(property),
      clean(property["x-peru-action"]),
      context.source,
    ]);

    const childView = schemaView(doc, propertyRaw, nextLineage);
    if (Object.keys(childView.properties).length) {
      rows.push(...flattenHttpSchema(doc, propertyRaw, context, fieldPath, depth + 1, nextLineage));
    } else if (property.type === "array" && property.items) {
      const itemView = schemaView(doc, property.items, nextLineage);
      if (Object.keys(itemView.properties).length) {
        rows.push(...flattenHttpSchema(doc, property.items, context, `${fieldPath}[]`, depth + 1, nextLineage));
      }
    }
  }
  return rows;
}

function securityText(doc, operation) {
  const groups = operation.security ?? doc.security ?? [];
  return groups.map((group) => Object.entries(group).map(([name, scopes]) => `${name}${scopes?.length ? `(${scopes.join(",")})` : ""}`).join(" AND ")).join(" OR ");
}

function extractOpenApi(doc, source) {
  const scope = doc["x-contract-scope"] || "UNSPECIFIED";
  const contract = doc.info?.title || source;
  const version = doc.info?.version || "";
  const operationRows = [];
  const dictionaryRows = [];
  for (const [apiPath, pathItem] of Object.entries(doc.paths || {})) {
    for (const [method, operation] of Object.entries(pathItem || {})) {
      if (!METHOD_NAMES.has(method)) continue;
      const requestSchemas = [];
      for (const [mediaType, media] of Object.entries(operation.requestBody?.content || {})) {
        if (!media?.schema) continue;
        const rootSchema = media.schema.$ref ? refName(media.schema.$ref) : `${operation.operationId || method}_request_inline`;
        requestSchemas.push(`${mediaType}:${rootSchema}`);
        dictionaryRows.push(...flattenHttpSchema(doc, media.schema, {
          scope, contract, version, operationId: operation.operationId || "", direction: "REQUEST",
          httpStatus: "", rootSchema, source,
        }));
      }
      const responseSchemas = [];
      for (const [status, responseRaw] of Object.entries(operation.responses || {})) {
        const response = resolveObject(doc, responseRaw) || responseRaw;
        for (const [mediaType, media] of Object.entries(response?.content || {})) {
          if (!media?.schema) continue;
          const rootSchema = media.schema.$ref ? refName(media.schema.$ref) : `${operation.operationId || method}_response_${status}_inline`;
          responseSchemas.push(`${status}/${mediaType}:${rootSchema}`);
          dictionaryRows.push(...flattenHttpSchema(doc, media.schema, {
            scope, contract, version, operationId: operation.operationId || "", direction: "RESPONSE",
            httpStatus: status, rootSchema, source,
          }));
        }
      }
      const mapping = operation["x-architecture-mapping"] || operation["x-core-transactions"] || [];
      operationRows.push([
        scope,
        contract,
        version,
        operation.operationId || "",
        method.toUpperCase(),
        apiPath,
        clean(operation.summary),
        clean(operation.description),
        securityText(doc, operation),
        clean(mapping),
        requestSchemas.join(" | "),
        responseSchemas.join(" | "),
        Object.keys(operation.responses || {}).join(" | "),
        source,
      ]);
    }
  }
  const securityRows = [];
  for (const [name, scheme] of Object.entries(doc.components?.securitySchemes || {})) {
    const scopes = [];
    for (const flow of Object.values(scheme.flows || {})) {
      for (const [scopeName, description] of Object.entries(flow.scopes || {})) scopes.push(`${scopeName}: ${description}`);
    }
    securityRows.push([
      scope, contract, "Security Scheme", name, clean(scheme.type), clean(scheme.in), clean(scheme.name), "",
      clean(scheme.description), scopes.join(" | "), source,
    ]);
  }
  for (const [name, paramRaw] of Object.entries(doc.components?.parameters || {})) {
    const param = resolveObject(doc, paramRaw) || paramRaw;
    if (param.in !== "header") continue;
    securityRows.push([
      scope, contract, "Header", name, typeOf(doc, param.schema || {}), "header", clean(param.name), param.required ? "Sí" : "No",
      clean(param.description), clean(param.schema?.enum || param.schema?.const), source,
    ]);
  }
  return { operationRows, dictionaryRows, securityRows };
}

function asyncMessageNames(doc, operation) {
  const names = [];
  for (const msgRaw of operation.messages || []) {
    const msg = resolveObject(doc, msgRaw) || msgRaw;
    names.push(msg?.name || refName(msgRaw?.$ref));
  }
  return names.filter(Boolean);
}

function extractAsyncApi(doc, source) {
  const contract = doc.info?.title || source;
  const version = doc.info?.version || "";
  const rows = [];
  const coveredChannels = new Set();
  for (const [operationId, operation] of Object.entries(doc.operations || {})) {
    const channelRef = operation.channel?.$ref || "";
    const channelKey = refName(channelRef);
    const channel = resolveObject(doc, operation.channel) || operation.channel || {};
    coveredChannels.add(channelKey);
    const messages = asyncMessageNames(doc, operation);
    const group = operation.bindings?.kafka?.groupId;
    rows.push([
      contract, version, operationId, clean(operation.action), channelKey, clean(channel.address), messages.join(" | "),
      clean(doc.defaultContentType), clean(group?.const || group?.enum || group), clean(operation.summary || operation.title), source,
    ]);
  }
  for (const [channelKey, channel] of Object.entries(doc.channels || {})) {
    if (coveredChannels.has(channelKey)) continue;
    const messageNames = [];
    for (const [key, msgRaw] of Object.entries(channel.messages || {})) {
      const msg = resolveObject(doc, msgRaw) || msgRaw;
      messageNames.push(msg?.name || key);
    }
    rows.push([
      contract, version, "", "CHANNEL", channelKey, clean(channel.address), messageNames.join(" | "),
      clean(doc.defaultContentType), "", clean(channel.description), source,
    ]);
  }
  const dictionaryRows = [];
  for (const [schemaName, schema] of Object.entries(doc.components?.schemas || {})) {
    const flattened = flattenHttpSchema(doc, schema, {
      scope: "ASYNC_INTERNAL", contract, version, operationId: "", direction: "EVENT", httpStatus: "",
      rootSchema: schemaName, source,
    });
    for (const row of flattened) {
      dictionaryRows.push([
        row[1], row[2], row[6], row[7], row[8], row[9], row[10], row[11], row[12], row[13], row[14], row[15], row[16], row[17], row[19],
      ]);
    }
  }
  return { rows, dictionaryRows };
}

function avroTypeText(type) {
  if (typeof type === "string") return type;
  if (Array.isArray(type)) return type.map(avroTypeText).join(" | ");
  if (!type || typeof type !== "object") return clean(type);
  if (type.type === "record") return `record:${type.name}`;
  if (type.type === "enum") return `enum:${type.name}`;
  if (type.type === "array") return `array<${avroTypeText(type.items)}>`;
  if (type.type === "map") return `map<${avroTypeText(type.values)}>`;
  return avroTypeText(type.type);
}
function avroNullable(type) {
  return Array.isArray(type) && type.some((item) => item === "null" || item?.type === "null") ? "Sí" : "No";
}
function embeddedAvro(type, kind) {
  const items = Array.isArray(type) ? type : [type];
  return items.find((item) => item && typeof item === "object" && item.type === kind);
}
function flattenAvro(schema, source) {
  const rows = [];
  const rootName = schema.name || source;
  const namespace = schema.namespace || "";
  const walkRecord = (record, prefix = "", lineage = new Set()) => {
    if (!record || record.type !== "record" || lineage.has(record.name)) return;
    const next = new Set(lineage);
    next.add(record.name);
    for (const field of record.fields || []) {
      const fieldPath = prefix ? `${prefix}.${field.name}` : field.name;
      const enumType = embeddedAvro(field.type, "enum");
      rows.push([
        source, namespace, rootName, record.name, fieldPath, field.name, avroTypeText(field.type),
        avroNullable(field.type), field.default === undefined ? "" : clean(field.default), clean(enumType?.symbols), clean(field.doc),
      ]);
      const nestedRecord = embeddedAvro(field.type, "record");
      if (nestedRecord) walkRecord(nestedRecord, fieldPath, next);
    }
  };
  walkRecord(schema);
  return rows;
}

function colLetter(number) {
  let n = number;
  let out = "";
  while (n > 0) {
    const rem = (n - 1) % 26;
    out = String.fromCharCode(65 + rem) + out;
    n = Math.floor((n - 1) / 26);
  }
  return out;
}

function styleTitle(sheet, lastCol, title, subtitle) {
  sheet.showGridLines = false;
  sheet.getRange(`A1:${lastCol}1`).merge();
  sheet.getRange("A1").values = [[title]];
  sheet.getRange(`A2:${lastCol}2`).merge();
  sheet.getRange("A2").values = [[subtitle]];
  sheet.getRange(`A1:${lastCol}1`).format = {
    fill: COLORS.red,
    font: { bold: true, color: COLORS.white, size: 16 },
    verticalAlignment: "center",
  };
  sheet.getRange(`A2:${lastCol}2`).format = {
    fill: COLORS.light,
    font: { italic: true, color: COLORS.slate, size: 10 },
    wrapText: true,
    verticalAlignment: "center",
  };
  sheet.getRange(`A1:${lastCol}1`).format.rowHeight = 30;
  sheet.getRange(`A2:${lastCol}2`).format.rowHeight = 34;
}

function writeDataSheet(workbook, config) {
  const { name, title, subtitle, headers, rows, widths, tableName } = config;
  const sheet = workbook.worksheets.add(name);
  const lastCol = colLetter(headers.length);
  const headerRow = 4;
  const dataStart = 5;
  const dataEnd = Math.max(dataStart, dataStart + rows.length - 1);
  styleTitle(sheet, lastCol, title, subtitle);
  sheet.getRange(`A${headerRow}:${lastCol}${dataEnd}`).values = [headers, ...rows.length ? rows : [headers.map(() => "")]];
  sheet.getRange(`A${headerRow}:${lastCol}${headerRow}`).format = {
    fill: COLORS.dark,
    font: { bold: true, color: COLORS.white, size: 10 },
    wrapText: true,
    verticalAlignment: "center",
    borders: { preset: "outside", style: "thin", color: COLORS.dark },
  };
  sheet.getRange(`A${headerRow}:${lastCol}${headerRow}`).format.rowHeight = 34;
  if (rows.length) {
    const body = sheet.getRange(`A${dataStart}:${lastCol}${dataEnd}`);
    body.format = {
      font: { color: COLORS.dark, size: 9 },
      verticalAlignment: "top",
      wrapText: true,
      borders: { preset: "inside", style: "thin", color: COLORS.border },
    };
    const table = sheet.tables.add(`A${headerRow}:${lastCol}${dataEnd}`, true, tableName);
    table.style = "TableStyleMedium7";
    table.showFilterButton = true;
  }
  sheet.freezePanes.freezeRows(headerRow);
  for (let i = 0; i < headers.length; i++) {
    const col = colLetter(i + 1);
    sheet.getRange(`${col}${headerRow}:${col}${dataEnd}`).format.columnWidth = widths[i] || 16;
  }
  return { sheet, dataEnd };
}

const openApiDocs = [];
for (const file of openApiFiles) {
  openApiDocs.push({ file, doc: await readJson(path.join(JSON_DIR, file)) });
}
const asyncApiDocs = [];
for (const file of asyncApiFiles) {
  asyncApiDocs.push({ file, doc: await readJson(path.join(JSON_DIR, file)) });
}

const operationRows = [];
const httpDictionaryRows = [];
const securityRows = [];
for (const { file, doc } of openApiDocs) {
  const extracted = extractOpenApi(doc, file.replace(/\.json$/, ".yaml"));
  operationRows.push(...extracted.operationRows);
  httpDictionaryRows.push(...extracted.dictionaryRows);
  securityRows.push(...extracted.securityRows);
}
const asyncOperationRows = [];
const eventDictionaryRows = [];
for (const { file, doc } of asyncApiDocs) {
  const extracted = extractAsyncApi(doc, file.replace(/\.json$/, ".yaml"));
  asyncOperationRows.push(...extracted.rows);
  eventDictionaryRows.push(...extracted.dictionaryRows);
}
const avroRows = [];
for (const file of avroFiles) avroRows.push(...flattenAvro(await readJson(file), path.basename(file)));

const catalogMap = new Map();
for (const row of httpDictionaryRows) {
  if (!row[13]) continue;
  const key = [row[0], row[1], row[6], row[7], row[13]].join("|");
  catalogMap.set(key, [row[0], row[1], row[6], row[7], "ENUM/CONST", row[13], row[19]]);
}
for (const row of eventDictionaryRows) {
  if (!row[9]) continue;
  const key = ["ASYNC_INTERNAL", row[0], row[2], row[3], row[9]].join("|");
  catalogMap.set(key, ["ASYNC_INTERNAL", row[0], row[2], row[3], "ENUM/CONST", row[9], row[14]]);
}
for (const row of avroRows) {
  if (!row[9]) continue;
  const key = ["AVRO", row[0], row[2], row[4], row[9]].join("|");
  catalogMap.set(key, ["AVRO", row[0], row[2], row[4], "ENUM", row[9], row[0]]);
}
const catalogRows = [...catalogMap.values()].sort((a, b) => `${a[0]}${a[1]}${a[3]}`.localeCompare(`${b[0]}${b[1]}${b[3]}`));

const canonicalPeruRows = httpDictionaryRows.filter((row) => row[0] === "NIUBIZ_JUSPAY_CBS" && row[18]);
const pendingMap = new Map();
for (const row of canonicalPeruRows) {
  const key = `${row[7]}|${row[18]}`;
  pendingMap.set(key, ["Alta", "Campo CBS", row[7], row[18], row[16], "Confirmar con Niubiz/Juspay y Core", row[19]]);
}
const manualPending = [
  ["Alta", "Localización", "Moneda", "CONFIRM_PEN_AND_CURRENCY_HANDLING", "El PDF presupone INR y no define un campo currency.", "Acordar PEN y transporte de moneda.", "Juspay-Peru CBS API Specs.pdf"],
  ["Alta", "Catálogos", "status / cbsStatus", "DEFINE_STATUS_CATALOGS", "El documento no presenta catálogos generales de estado.", "Definir valores, transiciones y códigos reintentables.", "Juspay-Peru CBS API Specs.pdf"],
  ["Alta", "Catálogos", "responseCode / cbsResponseCode", "DEFINE_RESPONSE_CODES", "No se entrega la matriz de códigos CBS/UPI.", "Solicitar catálogo y mapeo para Perú.", "Juspay-Peru CBS API Specs.pdf"],
  ["Media", "Transporte", "URLs y métodos", "CONFIRM_HTTP_TRANSPORT", "El PDF define cuerpos, no paths ni verbos HTTP.", "Aprobar paths POST propuestos y hostname Apigee.", "Juspay-Peru CBS API Specs.pdf"],
  ["Alta", "Seguridad", "API key / mTLS", "CONFIRM_SECURITY_PROFILE", "La seguridad proviene de diagramas, no del PDF.", "Aprobar certificados, header API key y allowlist.", "Diagramas de arquitectura"],
  ["Media", "Tiempo", "transactionTimestamp / updatedAt", "CONFIRM_TIME_FORMAT", "No hay formato técnico ni zona horaria final para Perú.", "Usar ISO 8601 y acordar UTC o America/Lima.", "Juspay-Peru CBS API Specs.pdf"],
];
for (const row of manualPending) pendingMap.set(`${row[2]}|${row[3]}`, row);
const pendingRows = [...pendingMap.values()];

const referenceSheets = {
  listAccounts: "Fetch Customer Profile",
  validateCardDetails: "Verify Card at Onboarding",
  getAccountBalance: "Read Balance",
  creditMoney: "Debit, Credit and Reversal",
  debitMoney: "Debit, Credit and Reversal",
  checkTransactionStatus: "Status_Check",
};
const referenceFieldOverrides = new Map([
  ["listAccounts|REQUEST|mobileNumber", ["registeredMobileNumber / mobileNumber", "WJ24/JN24", "Sí", "RENOMBRADO", "La V1 usa registeredMobileNumber y la V2 muestra mobileNumber."]],
  ["listAccounts|RESPONSE|payload.lk", ["ik / lk", "", "No", "CONFLICTO_NOMBRE", "La tabla de referencia muestra ik; el PDF define lk."]],
  ["listAccounts|RESPONSE|payload.cbsStatus", ["cbcStatus / cbsStatus", "", "", "CONFLICTO_NOMBRE", "La tabla muestra cbcStatus y el JSON de ejemplo usa cbsStatus."]],
  ["listAccounts|RESPONSE|payload.cbsResponseCode", ["cbcResponseCode / cbsResponseCode", "", "", "CONFLICTO_NOMBRE", "La tabla contiene cbcResponseCode; el JSON usa cbsResponseCode."]],
  ["listAccounts|RESPONSE|payload.cbsResponseMessage", ["cbcResponseMessage / cbsResponseMessage", "", "No", "CONFLICTO_NOMBRE", "Inconsistencia tipográfica dentro de la referencia."]],
  ["validateCardDetails|REQUEST|accountIdentifier", ["accountNumber", "TP08/TP07", "Sí", "RENOMBRADO", "La referencia utiliza Account number."]],
  ["validateCardDetails|REQUEST|cardNumber", ["cardDigitsEntered", "TP08", "Sí", "RENOMBRADO", "Ambos representan los últimos seis dígitos."]],
  ["validateCardDetails|REQUEST|expiry", ["cardExpiryEntered", "TP08", "Sí", "RENOMBRADO", "Formato MMYY en ambos documentos."]],
  ["creditMoney|REQUEST|orgGatewayReferenceId", ["orgGatewayReferenceId", "TP04", "No", "DIFERENCIA_REQUERIDO", "La referencia lo marca NO; el contrato lo usa para reversa/reembolso."]],
  ["creditMoney|REQUEST|orgGatewayTransactionId", ["orgGatewayTransactionId", "TP04", "No", "DIFERENCIA_REQUERIDO", "El contrato lo exige condicionalmente para REFUND."]],
  ["creditMoney|REQUEST|type", ["type", "TP03/TP04", "Sí", "COINCIDE", "TRANSACTION, DEBIT_REVERSAL o REFUND."]],
  ["debitMoney|REQUEST|umn", ["umn", "", "No", "DIFERENCIA_REQUERIDO", "El contrato lo exige condicionalmente cuando type=MANDATE."]],
  ["debitMoney|REQUEST|transactionTimestamp", ["transactionTimestamp", "TP02", "No", "DIFERENCIA_REQUERIDO", "El contrato lo exige condicionalmente para MANDATE."]],
]);

const knownReferencePaths = {
  listAccounts: new Set([
    "status", "responseCode", "responseMessage", "payload", "payload.ac", "payload.sa", "payload.cbsAccounts",
    "payload.cbsAccounts[].accountType", "payload.cbsAccounts[].accountNumber", "payload.cbsAccounts[].aadhaarEnabled",
    "payload.cbsAccounts[].aadhaarNumber", "payload.cbsAccounts[].name", "payload.cbsAccounts[].ifsc", "payload.cbsAccounts[].status",
  ]),
  getAccountBalance: new Set(["accountIdentifier", "ifsc", "status", "responseCode", "responseMessage", "payload", "payload.availableBalance", "payload.cbsStatus", "payload.cbsResponseCode", "payload.cbsResponseMessage"]),
  creditMoney: new Set([
    "amount", "gatewayReferenceId", "gatewayTransactionId", "isDirectCredit", "payeeAccountNumber", "payerAccountNumber", "payeeVpa",
    "payerVpa", "payerAcType", "payeeAcType", "payerIfsc", "payeeIfsc", "payerMcc", "payeeMcc", "payerName", "payeeName",
    "remarks", "transactionTimestamp", "udfParameters", "status", "responseCode", "responseMessage", "payload", "payload.cbsStatus",
    "payload.cbsResponseCode", "payload.cbsResponseMessage", "payload.amount", "payload.gatewayTransactionId", "payload.gatewayReferenceId",
    "payload.payeeAccountNumber", "payload.cbsReferenceId", "payload.updatedAt", "payload.remarks",
  ]),
  debitMoney: new Set([
    "amount", "gatewayReferenceId", "gatewayTransactionId", "orgMandateId", "orgGatewayTransactionId", "payeeAccountNumber",
    "payerAccountNumber", "payeeAcType", "payeeIfsc", "payeeMcc", "payeeName", "payeeVpa", "payerAcType", "payerIfsc",
    "payerMcc", "payerName", "payerVpa", "remarks", "type", "udfParameters", "status", "responseCode", "responseMessage", "payload",
    "payload.cbsStatus", "payload.cbsResponseCode", "payload.cbsResponseMessage", "payload.amount", "payload.gatewayTransactionId",
    "payload.gatewayReferenceId", "payload.accountIdentifier", "payload.cbsReferenceId", "payload.updatedAt", "payload.remarks",
  ]),
  checkTransactionStatus: new Set(["gatewayTransactionId", "type", "gatewayReferenceId", "status", "responseCode", "responseMessage", "payload", "payload.cbsStatus", "payload.cbsReferenceId", "payload.cbsResponseCode", "payload.cbsResponseMessage", "payload.updatedAt"]),
};
const defaultCoreByOperation = {
  listAccounts: "WJ24/JN24",
  validateCardDetails: "TP08/TP07",
  getAccountBalance: "WJ12",
  creditMoney: "",
  debitMoney: "",
  checkTransactionStatus: "",
};
const creditTp03Fields = new Set(["amount", "gatewayTransactionId", "payeeAccountNumber", "payerAccountNumber", "payeeVpa", "payerVpa", "payeeIfsc", "payeeName", "remarks", "transactionTimestamp", "payload.cbsResponseCode", "payload.cbsResponseMessage", "payload.payeeAccountNumber", "payload.cbsReferenceId"]);
const debitTp02Fields = new Set(["amount", "gatewayTransactionId", "payerAccountNumber", "payeeAcType", "payeeIfsc", "payeeName", "payeeVpa", "remarks", "transactionTimestamp", "payload.cbsResponseCode", "payload.accountIdentifier", "payload.cbsReferenceId"]);

const mappingRows = [];
for (const row of httpDictionaryRows.filter((item) => item[0] === "NIUBIZ_JUSPAY_CBS")) {
  const operationId = row[3];
  const direction = row[4];
  const fieldPath = row[7];
  const key = `${operationId}|${direction}|${fieldPath}`;
  const override = referenceFieldOverrides.get(key);
  let referenceField = override?.[0] || "";
  let coreMapping = override?.[1] || "";
  let referenceRequired = override?.[2] || "";
  let comparison = override?.[3] || "";
  let observation = override?.[4] || "";
  const isKnown = knownReferencePaths[operationId]?.has(fieldPath);
  if (!referenceField && isKnown) referenceField = row[8];
  if (!coreMapping) {
    if (operationId === "creditMoney" && creditTp03Fields.has(fieldPath)) coreMapping = "TP03";
    else if (operationId === "debitMoney" && debitTp02Fields.has(fieldPath)) coreMapping = "TP02";
    else if (isKnown) coreMapping = defaultCoreByOperation[operationId] || "";
  }
  if (!referenceRequired && isKnown) referenceRequired = row[9];
  if (!comparison) comparison = isKnown ? "COINCIDE" : operationId === "validateCardDetails" && direction === "RESPONSE" ? "CONFLICTO_ESTRUCTURA" : "SIN_MAPEO_EXPLÍCITO";
  if (!observation && comparison === "CONFLICTO_ESTRUCTURA") observation = "La referencia responde verificationOutcome/failureReason/attemptsRemaining; el PDF usa status/responseCode/payload CBS.";
  mappingRows.push([
    operationId, direction, fieldPath, row[8], referenceField, coreMapping, row[9], referenceRequired,
    comparison, observation, referenceSheets[operationId] || "", "Mapeo Niubiz_V1.xlsx",
  ]);
}
const extraReferenceRows = [
  ["listAccounts", "REQUEST", "[extra] customerType", "", "customerType", "", "No", "No", "EXTRA_REFERENCIA", "Campo mostrado en V2, no incluido en el PDF CBS.", "Fetch Customer Profile", "Mapeo Niubiz_V1.xlsx"],
  ["listAccounts", "REQUEST", "[extra] documentType", "", "documentType", "", "No", "No", "EXTRA_REFERENCIA", "Usado para P2M en el archivo de mapeo.", "Fetch Customer Profile", "Mapeo Niubiz_V1.xlsx"],
  ["listAccounts", "REQUEST", "[extra] customerReferenceNumber", "", "customerReferenceNumber", "", "No", "No", "EXTRA_REFERENCIA", "Usado para entidades P2M en el archivo de mapeo.", "Fetch Customer Profile", "Mapeo Niubiz_V1.xlsx"],
  ["validateCardDetails", "REQUEST", "[extra] cardEncryptedPin", "", "cardEncryptedPin", "TP07", "No", "Sí", "EXTRA_REFERENCIA", "TP07/PIN no aparece en Juspay-Peru CBS API Specs.", "Verify Card at Onboarding", "Mapeo Niubiz_V1.xlsx"],
  ["validateCardDetails", "REQUEST", "[extra] TipoCliente", "", "TipoCliente", "TP07/TP08", "No", "Pendiente", "EXTRA_REFERENCIA", "La referencia solicita pedir este dato a Niubiz.", "Verify Card at Onboarding", "Mapeo Niubiz_V1.xlsx"],
  ["validateCardDetails", "REQUEST", "[extra] Celular", "", "Celular", "TP07/TP08", "No", "Pendiente", "EXTRA_REFERENCIA", "La referencia solicita pedir este dato a Niubiz.", "Verify Card at Onboarding", "Mapeo Niubiz_V1.xlsx"],
  ["validateCardDetails", "HEADER", "[extra] Transaction ID / DPI / IP", "", "Transaction ID / DPI / IP", "TP08/TP07", "No", "Pendiente", "EXTRA_REFERENCIA", "Metadatos de trazabilidad solicitados en la referencia.", "Verify Card at Onboarding", "Mapeo Niubiz_V1.xlsx"],
  ["validateCardDetails", "RESPONSE", "[extra] verificationOutcome", "", "verificationOutcome", "TP08/TP07", "No", "Sí", "EXTRA_REFERENCIA", "Respuesta propuesta MATCHED; no existe en el PDF CBS.", "Verify Card at Onboarding", "Mapeo Niubiz_V1.xlsx"],
  ["validateCardDetails", "RESPONSE", "[extra] failureReason", "", "failureReason", "TP08/TP07", "No", "No", "EXTRA_REFERENCIA", "No existe en el PDF CBS.", "Verify Card at Onboarding", "Mapeo Niubiz_V1.xlsx"],
  ["validateCardDetails", "RESPONSE", "[extra] attemptsRemaining", "", "attemptsRemaining", "TP08/TP07", "No", "No", "EXTRA_REFERENCIA", "No existe en el PDF CBS.", "Verify Card at Onboarding", "Mapeo Niubiz_V1.xlsx"],
  ["creditMoney", "REQUEST", "[faltante] currency", "", "tipo moneda", "", "No", "Pendiente", "FALTANTE_REFERENCIA", "La referencia indica que falta denominación de moneda.", "Debit, Credit and Reversal", "Mapeo Niubiz_V1.xlsx"],
  ["debitMoney", "REQUEST", "[faltante] currency", "", "tipo moneda", "", "No", "Pendiente", "FALTANTE_REFERENCIA", "La referencia indica que falta denominación de moneda.", "Debit, Credit and Reversal", "Mapeo Niubiz_V1.xlsx"],
  ["debitMoney", "REQUEST", "[faltante] payerMobileNumber", "", "número teléfono remitente", "", "No", "Pendiente", "FALTANTE_REFERENCIA", "La referencia solicita el teléfono del remitente.", "Debit, Credit and Reversal", "Mapeo Niubiz_V1.xlsx"],
  ["checkTransactionStatus", "RESPONSE", "payload.cbsStatus[extra]", "", "DEEMED", "", "No", "Pendiente", "CONFLICTO_CATÁLOGO", "El PDF permite SUCCESS/FAILURE/NOTFOUND; la referencia pregunta por DEEMED.", "Status_Check", "Mapeo Niubiz_V1.xlsx"],
];
mappingRows.push(...extraReferenceRows);

const consultationRows = [
  ["Wilder, Pablo, Richard", "Fetch Customer Profile", "¿Nos pueden enviar el tipo del cliente? - P2P", "", "Pendiente", "Mapeo Niubiz_V1.xlsx"],
  ["Wilder, Pablo, Richard", "Fetch Customer Profile", "¿Nos pueden enviar el tipo de documento? - P2M", "", "Pendiente", "Mapeo Niubiz_V1.xlsx"],
  ["Wilder, Pablo, Richard", "Fetch Customer Profile", "¿Nos pueden enviar el tipo de número del cliente? - P2M", "", "Pendiente", "Mapeo Niubiz_V1.xlsx"],
  ["Wilder, Pablo, Richard", "Fetch Customer Profile", "¿Nos pueden enviar DPI, TXN ID e IP para gestionar trazabilidad? - Metadata", "", "Pendiente", "Mapeo Niubiz_V1.xlsx"],
  ["", "Validación tarjeta", "¿Nos pueden enviar el tipo de cliente?", "", "Pendiente", "Mapeo Niubiz_V1.xlsx"],
  ["", "Validación tarjeta", "¿Nos pueden enviar el celular?", "", "Pendiente", "Mapeo Niubiz_V1.xlsx"],
  ["Fabio", "Débitos", "En el contrato hace falta el tipo de moneda (denominación) y el número de teléfono del remitente.", "", "Pendiente", "Mapeo Niubiz_V1.xlsx"],
  ["María", "Créditos", "En el contrato hace falta el tipo de moneda (denominación).", "", "Pendiente", "Mapeo Niubiz_V1.xlsx"],
  ["Mafer / Manuel", "Status Check", "¿Existe catálogo oficial de códigos CBS/AS400 y se debe retornar DEEMED?", "", "Pendiente", "Mapeo Niubiz_V1.xlsx"],
];

const sourceRows = [];
for (const { file, doc } of openApiDocs) {
  sourceRows.push([
    "OpenAPI", doc["x-contract-scope"] || "UNSPECIFIED", doc.info?.title || "", doc.info?.version || "",
    `docs/openapi/${file.replace(/\.json$/, ".yaml")}`,
    doc["x-contract-scope"] === "NIUBIZ_JUSPAY_CBS" ? "Canónico - draft" : "Borrador interno",
    doc["x-contract-scope"] === "NIUBIZ_JUSPAY_CBS" ? "Contrato externo basado en el PDF Juspay." : "Inferido de diagramas; no usar como contrato Niubiz/Juspay.",
  ]);
}
for (const { file, doc } of asyncApiDocs) {
  sourceRows.push(["AsyncAPI", "ASYNC_INTERNAL", doc.info?.title || "", doc.info?.version || "", `docs/${file.replace(/\.json$/, ".yaml")}`, "Draft interno", "Eventos Kafka y modelos de lectura internos."]);
}
for (const file of avroFiles) {
  const doc = await readJson(file);
  sourceRows.push(["Avro", "ASYNC_INTERNAL", doc.name || path.basename(file), "1.0 draft", `docs/avro/${path.basename(file)}`, "Draft interno", "Esquema serializable del evento CDC."]);
}
sourceRows.push(["PDF", "SOURCE", "Juspay-Peru CBS API Specs", "20 páginas", "C:/Users/Windows/Downloads/Juspay-Peru CBS API Specs.pdf", "Fuente", "Especificación funcional de seis operaciones CBS."]);
sourceRows.push(["Excel", "REFERENCE_MAPPING", "Mapeo Niubiz_V1", "V1", "C:/Users/Windows/Downloads/Mapeo Niubiz_V1.xlsx", "Referencia", "Mapeo de campos, tramas, obligatoriedad, observaciones y consultas del equipo."]);

const workbook = Workbook.create();

// Vista reorganizada: una pestaña autocontenida por endpoint.
// El bloque termina el proceso después de exportar, dejando el generador histórico
// debajo como referencia de trazabilidad sin ejecutarlo.
{
  const endpointDefinitions = [
    ["NIUBIZ_JUSPAY_CBS", "listAccounts", "01 CBS Listar Cuentas"],
    ["NIUBIZ_JUSPAY_CBS", "validateCardDetails", "02 CBS Validar Tarjeta"],
    ["NIUBIZ_JUSPAY_CBS", "getAccountBalance", "03 CBS Consultar Saldo"],
    ["NIUBIZ_JUSPAY_CBS", "creditMoney", "04 CBS Credito-Reversa"],
    ["NIUBIZ_JUSPAY_CBS", "debitMoney", "05 CBS Debito"],
    ["NIUBIZ_JUSPAY_CBS", "checkTransactionStatus", "06 CBS Estado Trans"],
    ["INTERNAL_DRAFT_NOT_NIUBIZ", "listCustomerAccounts", "07 INT Listar Cuentas"],
    ["INTERNAL_DRAFT_NOT_NIUBIZ", "getAccountDetail", "08 INT Detalle Cuenta"],
    ["INTERNAL_DRAFT_NOT_NIUBIZ", "getAccountBalance", "09 INT Saldo Cuenta"],
    ["INTERNAL_DRAFT_NOT_NIUBIZ", "getCustomerProfile", "10 INT Perfil Cliente"],
    ["INTERNAL_DRAFT_NOT_NIUBIZ", "validateDebitCard", "11 INT Validar Tarjeta"],
    ["INTERNAL_DRAFT_NOT_NIUBIZ", "createCreditTransaction", "12 INT Crear Credito"],
    ["INTERNAL_DRAFT_NOT_NIUBIZ", "createDebitTransaction", "13 INT Crear Debito"],
    ["INTERNAL_DRAFT_NOT_NIUBIZ", "reverseDebitTransaction", "14 INT Reversar Debito"],
  ];
  const endpointNameByKey = new Map(endpointDefinitions.map(([scope, operationId, name]) => [`${scope}|${operationId}`, name]));
  const endpointOrderByKey = new Map(endpointDefinitions.map(([scope, operationId], index) => [`${scope}|${operationId}`, index]));
  const canonicalDomains = {
    listAccounts: new Set(["Fetch Customer Profile"]),
    validateCardDetails: new Set(["Validación tarjeta"]),
    creditMoney: new Set(["Créditos"]),
    debitMoney: new Set(["Débitos"]),
    checkTransactionStatus: new Set(["Status Check"]),
  };
  const dictionaryHeaders = [
    "Dirección", "HTTP", "Schema raíz", "Ruta del campo", "Campo", "Requerido", "Regla condicional",
    "Tipo", "Formato", "Enum / Const", "Restricciones", "Nullable", "Descripción", "Ejemplo / Default", "Acción Perú",
  ];
  const dictionaryWidths = [12, 11, 25, 36, 22, 13, 27, 23, 15, 28, 35, 11, 55, 25, 30];
  const mappingHeaders = [
    "Operation ID", "Dirección", "Ruta contrato", "Campo contrato", "Campo referencia", "Trama / API",
    "Obligatorio contrato", "Obligatorio referencia", "Comparación", "Observación", "Hoja referencia", "Fuente",
  ];
  const securityHeaders = [
    "Tipo", "Nombre lógico", "Tipo dato / seguridad", "Ubicación", "Nombre HTTP", "Requerido",
    "Descripción", "Scopes / Valores", "Fuente",
  ];
  const consultationHeaders = ["Responsable(s)", "Dominio", "Consulta", "Respuesta Niubiz", "Estado", "Fuente"];

  const introSheet = workbook.worksheets.add("00 Inicio");
  const endpointSummaries = [];

  function styleSectionTitle(sheet, row, title) {
    sheet.getRange(`A${row}:O${row}`).merge();
    sheet.getRange(`A${row}`).values = [[title]];
    sheet.getRange(`A${row}:O${row}`).format = {
      fill: COLORS.dark,
      font: { bold: true, color: COLORS.white, size: 11 },
      verticalAlignment: "center",
    };
    sheet.getRange(`A${row}:O${row}`).format.rowHeight = 24;
  }

  function writeEndpointSection(sheet, row, title, headers, rows, tableName) {
    styleSectionTitle(sheet, row, title);
    const headerRow = row + 1;
    const lastCol = colLetter(headers.length);
    sheet.getRange(`A${headerRow}:${lastCol}${headerRow}`).values = [headers];
    sheet.getRange(`A${headerRow}:${lastCol}${headerRow}`).format = {
      fill: COLORS.red,
      font: { bold: true, color: COLORS.white, size: 9 },
      wrapText: true,
      verticalAlignment: "center",
      borders: { preset: "outside", style: "thin", color: COLORS.dark },
    };
    sheet.getRange(`A${headerRow}:${lastCol}${headerRow}`).format.rowHeight = 32;
    if (!rows.length) {
      const noteRow = headerRow + 1;
      sheet.getRange(`A${noteRow}:${lastCol}${noteRow}`).merge();
      sheet.getRange(`A${noteRow}`).values = [["Sin datos específicos para esta sección."]];
      sheet.getRange(`A${noteRow}:${lastCol}${noteRow}`).format = {
        fill: COLORS.light,
        font: { italic: true, color: COLORS.slate, size: 9 },
        verticalAlignment: "center",
      };
      return { headerRow, dataStart: 0, dataEnd: 0, nextRow: noteRow + 2 };
    }
    const dataStart = headerRow + 1;
    const dataEnd = dataStart + rows.length - 1;
    sheet.getRange(`A${dataStart}:${lastCol}${dataEnd}`).values = rows;
    sheet.getRange(`A${dataStart}:${lastCol}${dataEnd}`).format = {
      font: { color: COLORS.dark, size: 9 },
      verticalAlignment: "top",
      wrapText: true,
      borders: {
        insideHorizontal: { style: "thin", color: COLORS.border },
        bottom: { style: "thin", color: COLORS.border },
      },
    };
    const table = sheet.tables.add(`A${headerRow}:${lastCol}${dataEnd}`, true, tableName);
    table.style = "TableStyleMedium7";
    table.showFilterButton = true;
    return { headerRow, dataStart, dataEnd, nextRow: dataEnd + 2 };
  }

  function countFormula(rangeInfo, column) {
    return rangeInfo.dataStart ? `COUNTA(${column}${rangeInfo.dataStart}:${column}${rangeInfo.dataEnd})` : "0";
  }

  const orderedOperations = [...operationRows].sort((a, b) => {
    const aKey = `${a[0]}|${a[3]}`;
    const bKey = `${b[0]}|${b[3]}`;
    return (endpointOrderByKey.get(aKey) ?? 999) - (endpointOrderByKey.get(bKey) ?? 999);
  });

  for (const [index, operation] of orderedOperations.entries()) {
    const [scope, contract, version, operationId, method, apiPath, operationSummary, description, security, coreMapping, requestSchemas, responseSchemas, responseCodes, source] = operation;
    const key = `${scope}|${operationId}`;
    const sheetName = endpointNameByKey.get(key) || `${String(index + 1).padStart(2, "0")} ${operationId}`.slice(0, 31);
    const endpointSheet = workbook.worksheets.add(sheetName);
    styleTitle(endpointSheet, "O", `${String(index + 1).padStart(2, "0")} · ${operationSummary || operationId}`, `${method} ${apiPath} · ${scope === "NIUBIZ_JUSPAY_CBS" ? "Contrato CBS canónico Niubiz/Juspay" : "API interna — borrador inferido de arquitectura"}`);

    endpointSheet.getRange("A4:O11").format = { font: { color: COLORS.dark, size: 10 }, verticalAlignment: "top", wrapText: true };
    const labelRanges = ["A4", "A5", "A6", "A7", "A8", "A9", "A10", "A11", "F4", "F5", "F10", "I4", "I9", "I10", "L5", "L10", "M11"];
    for (const address of labelRanges) endpointSheet.getRange(address).format = { fill: COLORS.dark, font: { bold: true, color: COLORS.white, size: 9 }, verticalAlignment: "center", wrapText: true };

    endpointSheet.getRange("A4").values = [["Operation ID"]];
    endpointSheet.getRange("B4:E4").merge();
    endpointSheet.getRange("B4").values = [[operationId]];
    endpointSheet.getRange("F4").values = [["Método"]];
    endpointSheet.getRange("G4:H4").merge();
    endpointSheet.getRange("G4").values = [[method]];
    endpointSheet.getRange("I4").values = [["Path"]];
    endpointSheet.getRange("J4:O4").merge();
    endpointSheet.getRange("J4").values = [[apiPath]];

    endpointSheet.getRange("A5").values = [["Alcance"]];
    endpointSheet.getRange("B5:E5").merge();
    endpointSheet.getRange("B5").values = [[scope]];
    endpointSheet.getRange("F5").values = [["Contrato"]];
    endpointSheet.getRange("G5:K5").merge();
    endpointSheet.getRange("G5").values = [[contract]];
    endpointSheet.getRange("L5").values = [["Versión"]];
    endpointSheet.getRange("M5:O5").merge();
    endpointSheet.getRange("M5").values = [[version]];

    endpointSheet.getRange("A6").values = [["Resumen"]];
    endpointSheet.getRange("B6:O6").merge();
    endpointSheet.getRange("B6").values = [[operationSummary]];
    endpointSheet.getRange("A7").values = [["Descripción"]];
    endpointSheet.getRange("B7:O7").merge();
    endpointSheet.getRange("B7").values = [[description || "Sin descripción adicional en el contrato."]];
    endpointSheet.getRange("A8").values = [["Seguridad"]];
    endpointSheet.getRange("B8:O8").merge();
    endpointSheet.getRange("B8").values = [[security || "No declarada en el documento."]];
    endpointSheet.getRange("A9").values = [["Schemas"]];
    endpointSheet.getRange("B9:H9").merge();
    endpointSheet.getRange("B9").values = [[requestSchemas || "Sin request body"]];
    endpointSheet.getRange("I9").values = [["Responses"]];
    endpointSheet.getRange("J9:O9").merge();
    endpointSheet.getRange("J9").values = [[responseSchemas || "Sin response body"]];
    endpointSheet.getRange("A10").values = [["Mapeo Core"]];
    endpointSheet.getRange("B10:E10").merge();
    endpointSheet.getRange("B10").values = [[coreMapping || "No declarado"]];
    endpointSheet.getRange("F10").values = [["Códigos HTTP"]];
    endpointSheet.getRange("G10:H10").merge();
    endpointSheet.getRange("G10").values = [[responseCodes]];
    endpointSheet.getRange("I10").values = [["Campos"]];
    endpointSheet.getRange("J10:K10").merge();
    endpointSheet.getRange("L10").values = [["Mapeos"]];
    endpointSheet.getRange("M10:O10").merge();
    endpointSheet.getRange("A11").values = [["Fuente"]];
    endpointSheet.getRange("B11:L11").merge();
    endpointSheet.getRange("B11").values = [[source]];
    endpointSheet.getRange("M11").values = [["Consultas"]];
    endpointSheet.getRange("N11:O11").merge();

    endpointSheet.getRange("B4:O11").format = {
      fill: COLORS.paleGreen,
      font: { color: "#1F5D32", size: 10 },
      verticalAlignment: "top",
      wrapText: true,
      borders: { preset: "inside", style: "thin", color: "#B7D6A8" },
    };
    for (const address of labelRanges) endpointSheet.getRange(address).format = { fill: COLORS.dark, font: { bold: true, color: COLORS.white, size: 9 }, verticalAlignment: "center", wrapText: true };
    endpointSheet.getRange("A4:O11").format.rowHeight = 26;
    endpointSheet.getRange("A7:O9").format.rowHeight = 38;

    const endpointDictionary = httpDictionaryRows.filter((row) => row[0] === scope && row[1] === contract && row[3] === operationId);
    const toDictionaryView = (row) => [row[4], row[5], row[6], row[7], row[8], row[9], row[10], row[11], row[12], row[13], row[14], row[15], row[16], row[17], row[18]];
    const requestRows = endpointDictionary.filter((row) => row[4] === "REQUEST").map(toDictionaryView);
    const responseRows = endpointDictionary.filter((row) => row[4] === "RESPONSE").map(toDictionaryView);
    const contractSecurityRows = securityRows
      .filter((row) => row[0] === scope && row[1] === contract)
      .map((row) => [row[2], row[3], row[4], row[5], row[6], row[7], row[8], row[9], row[10]]);
    const endpointMappingRows = scope === "NIUBIZ_JUSPAY_CBS" ? mappingRows.filter((row) => row[0] === operationId) : [];
    const acceptedDomains = canonicalDomains[operationId] || new Set();
    const endpointConsultations = scope === "NIUBIZ_JUSPAY_CBS" ? consultationRows.filter((row) => acceptedDomains.has(row[1])) : [];

    let cursor = 13;
    const securityInfo = writeEndpointSection(endpointSheet, cursor, "1. Seguridad y headers comunes", securityHeaders, contractSecurityRows, `E${String(index + 1).padStart(2, "0")}Security`);
    cursor = securityInfo.nextRow;
    const requestInfo = writeEndpointSection(endpointSheet, cursor, "2. Request — diccionario de campos", dictionaryHeaders, requestRows, `E${String(index + 1).padStart(2, "0")}Request`);
    cursor = requestInfo.nextRow;
    const responseInfo = writeEndpointSection(endpointSheet, cursor, "3. Response y errores — diccionario de campos", dictionaryHeaders, responseRows, `E${String(index + 1).padStart(2, "0")}Response`);
    cursor = responseInfo.nextRow;
    let mappingInfo = { dataStart: 0, dataEnd: 0, nextRow: cursor };
    let consultationInfo = { dataStart: 0, dataEnd: 0, nextRow: cursor };
    if (scope === "NIUBIZ_JUSPAY_CBS") {
      mappingInfo = writeEndpointSection(endpointSheet, cursor, "4. Cruce con Mapeo Niubiz_V1", mappingHeaders, endpointMappingRows, `E${String(index + 1).padStart(2, "0")}Mapping`);
      cursor = mappingInfo.nextRow;
      consultationInfo = writeEndpointSection(endpointSheet, cursor, "5. Consultas abiertas de este endpoint", consultationHeaders, endpointConsultations, `E${String(index + 1).padStart(2, "0")}Questions`);
      cursor = consultationInfo.nextRow;
      if (mappingInfo.dataStart) {
        endpointSheet.getRange(`I${mappingInfo.dataStart}:I${mappingInfo.dataEnd}`).conditionalFormats.add("containsText", { text: "COINCIDE", format: { fill: COLORS.paleGreen, font: { bold: true, color: "#1F5D32" } } });
        endpointSheet.getRange(`I${mappingInfo.dataStart}:I${mappingInfo.dataEnd}`).conditionalFormats.add("containsText", { text: "CONFLICTO", format: { fill: COLORS.paleRed, font: { bold: true, color: "#000000" } } });
        endpointSheet.getRange(`I${mappingInfo.dataStart}:I${mappingInfo.dataEnd}`).conditionalFormats.add("containsText", { text: "EXTRA", format: { fill: COLORS.paleAmber, font: { bold: true, color: "#8A5A00" } } });
      }
      if (consultationInfo.dataStart) endpointSheet.getRange(`E${consultationInfo.dataStart}:E${consultationInfo.dataEnd}`).conditionalFormats.add("containsText", { text: "Pendiente", format: { fill: COLORS.paleRed, font: { bold: true, color: "#000000" } } });
    }

    for (const info of [requestInfo, responseInfo]) {
      if (!info.dataStart) continue;
      endpointSheet.getRange(`F${info.dataStart}:F${info.dataEnd}`).conditionalFormats.add("containsText", { text: "Sí", format: { fill: COLORS.paleGreen, font: { bold: true, color: "#1F5D32" } } });
      endpointSheet.getRange(`F${info.dataStart}:F${info.dataEnd}`).conditionalFormats.add("containsText", { text: "Condicional", format: { fill: COLORS.paleAmber, font: { bold: true, color: "#8A5A00" } } });
      endpointSheet.getRange(`O${info.dataStart}:O${info.dataEnd}`).conditionalFormats.add("notContainsBlanks", { format: { fill: COLORS.paleAmber, font: { bold: true, color: "#8A5A00" } } });
    }

    endpointSheet.getRange("J10").formulas = [[`=${countFormula(requestInfo, "E")}+${countFormula(responseInfo, "E")}`]];
    endpointSheet.getRange("M10").formulas = [[`=${countFormula(mappingInfo, "I")}`]];
    endpointSheet.getRange("N11").formulas = [[`=${countFormula(consultationInfo, "C")}`]];
    for (const address of ["J10:K10", "M10:O10", "N11:O11"]) endpointSheet.getRange(address).format = { fill: COLORS.paleBlue, font: { bold: true, color: COLORS.dark, size: 11 }, horizontalAlignment: "center", verticalAlignment: "center", numberFormat: "#,##0" };

    for (let col = 0; col < dictionaryWidths.length; col++) {
      endpointSheet.getRange(`${colLetter(col + 1)}1:${colLetter(col + 1)}${Math.max(cursor, 20)}`).format.columnWidth = dictionaryWidths[col];
    }
    endpointSheet.getRange(`D1:D${Math.max(cursor, 20)}`).format.columnWidth = 36;
    endpointSheet.getRange(`M1:M${Math.max(cursor, 20)}`).format.columnWidth = 55;
    endpointSheet.freezePanes.freezeRows(11);
    endpointSummaries.push({ sheetName, operation, fieldCell: "J10", mappingCell: "M10", consultationCell: "N11" });
  }

  writeDataSheet(workbook, {
    name: "90 Catalogos",
    title: "Anexo · Catálogos, enumeraciones y constantes",
    subtitle: "Valores permitidos encontrados en OpenAPI, AsyncAPI y Avro.",
    headers: ["Alcance", "Contrato", "Schema", "Ruta del campo", "Tipo", "Valores", "Fuente"],
    rows: catalogRows,
    widths: [22, 30, 30, 42, 15, 58, 30],
    tableName: "AnnexCatalogsTable",
  });
  writeDataSheet(workbook, {
    name: "91 AsyncAPI",
    title: "Anexo · Canales y operaciones AsyncAPI",
    subtitle: "Tópicos Kafka, mensajes, dirección y grupos consumidores de los contratos internos.",
    headers: ["Contrato", "Versión", "Operation ID", "Acción", "Canal lógico", "Tópico", "Mensaje", "Content Type", "Consumer Group", "Resumen", "Fuente"],
    rows: asyncOperationRows,
    widths: [32, 14, 34, 12, 28, 38, 36, 22, 34, 55, 24],
    tableName: "AnnexAsyncOperationsTable",
  });
  const annexEvents = writeDataSheet(workbook, {
    name: "92 Eventos Async",
    title: "Anexo · Diccionario de eventos",
    subtitle: "Campos de los esquemas AsyncAPI para Customer, Account y Transaction Projection.",
    headers: ["Contrato", "Versión", "Schema raíz", "Ruta del campo", "Campo", "Requerido", "Regla condicional", "Tipo", "Formato", "Enum / Const", "Restricciones", "Nullable", "Descripción", "Ejemplo / Default", "Fuente"],
    rows: eventDictionaryRows,
    widths: [32, 14, 32, 42, 25, 13, 26, 26, 16, 34, 38, 11, 58, 26, 28],
    tableName: "AnnexEventDictionaryTable",
  });
  annexEvents.sheet.freezePanes.freezeColumns(3);
  writeDataSheet(workbook, {
    name: "93 Avro",
    title: "Anexo · Diccionario de esquemas Avro",
    subtitle: "Representación serializable de los eventos CDC Account y Customer.",
    headers: ["Archivo", "Namespace", "Root record", "Record", "Ruta del campo", "Campo", "Tipo Avro", "Nullable", "Default", "Símbolos enum", "Descripción"],
    rows: avroRows,
    widths: [30, 42, 28, 28, 44, 24, 32, 11, 18, 52, 52],
    tableName: "AnnexAvroDictionaryTable",
  });
  const annexPending = writeDataSheet(workbook, {
    name: "94 Pendientes Peru",
    title: "Anexo · Decisiones pendientes para Perú",
    subtitle: "Acciones necesarias antes de aprobar el contrato Niubiz/Juspay CBS como versión 1.0.0.",
    headers: ["Prioridad", "Categoría", "Campo / Tema", "Acción", "Motivo", "Decisión requerida", "Fuente"],
    rows: pendingRows,
    widths: [12, 18, 38, 34, 60, 48, 30],
    tableName: "AnnexPeruPendingTable",
  });
  annexPending.sheet.getRange(`A5:A${annexPending.dataEnd}`).conditionalFormats.add("containsText", { text: "Alta", format: { fill: COLORS.paleRed, font: { bold: true, color: "#000000" } } });
  annexPending.sheet.getRange(`A5:A${annexPending.dataEnd}`).conditionalFormats.add("containsText", { text: "Media", format: { fill: COLORS.paleAmber, font: { bold: true, color: "#8A5A00" } } });
  const annexConsultations = writeDataSheet(workbook, {
    name: "95 Consultas Niubiz",
    title: "Anexo · Consultas abiertas del mapeo Niubiz",
    subtitle: "Vista consolidada; cada consulta también aparece en la pestaña del endpoint correspondiente.",
    headers: consultationHeaders,
    rows: consultationRows,
    widths: [28, 28, 78, 55, 16, 28],
    tableName: "AnnexNiubizQuestionsTable",
  });
  annexConsultations.sheet.getRange(`E5:E${annexConsultations.dataEnd}`).conditionalFormats.add("containsText", { text: "Pendiente", format: { fill: COLORS.paleRed, font: { bold: true, color: "#000000" } } });
  writeDataSheet(workbook, {
    name: "96 Fuentes",
    title: "Anexo · Inventario de fuentes y contratos",
    subtitle: "Trazabilidad hacia los archivos fuente, la especificación PDF y el mapeo de referencia.",
    headers: ["Tipo", "Alcance", "Título", "Versión", "Archivo", "Estado", "Notas"],
    rows: sourceRows,
    widths: [14, 22, 38, 16, 62, 20, 65],
    tableName: "AnnexSourcesTable",
  });

  styleTitle(introSheet, "J", "Diccionario de contratos Niubiz/Juspay y TAPP", "Libro reorganizado: una pestaña por endpoint y anexos técnicos al final.");
  introSheet.getRange("A4:J4").merge();
  introSheet.getRange("A4").values = [["Cómo usar este libro"]];
  introSheet.getRange("A4:J4").format = { fill: COLORS.dark, font: { bold: true, color: COLORS.white, size: 12 }, verticalAlignment: "center" };
  introSheet.getRange("A5:J7").merge();
  introSheet.getRange("A5").values = [["Abra la pestaña del endpoint que desea revisar. Cada una contiene identificación, seguridad y headers, request, response/errores y, para las seis operaciones CBS, el cruce con Mapeo Niubiz_V1 y sus consultas abiertas. Las pestañas 90–96 son anexos consolidados."]];
  introSheet.getRange("A5:J7").format = { fill: COLORS.paleGreen, font: { color: "#1F5D32", size: 11 }, wrapText: true, verticalAlignment: "top", borders: { preset: "outside", style: "thin", color: "#A7CDB1" } };

  const cardHeaders = [["Endpoints", null, "CBS canónicos", null, "Campos HTTP", null, "Mapeos Niubiz", null, "Consultas", null]];
  introSheet.getRange("A9:J9").values = cardHeaders;
  for (const range of ["A9:B9", "C9:D9", "E9:F9", "G9:H9", "I9:J9"]) {
    introSheet.getRange(range).merge();
    introSheet.getRange(range).format = { fill: COLORS.dark, font: { bold: true, color: COLORS.white, size: 10 }, horizontalAlignment: "center", verticalAlignment: "center" };
  }
  for (const range of ["A10:B11", "C10:D11", "E10:F11", "G10:H11", "I10:J11"]) {
    introSheet.getRange(range).merge();
    introSheet.getRange(range).format = { fill: COLORS.paleBlue, font: { bold: true, color: COLORS.dark, size: 16 }, horizontalAlignment: "center", verticalAlignment: "center", numberFormat: "#,##0", borders: { preset: "outside", style: "thin", color: COLORS.border } };
  }

  const indexHeaderRow = 14;
  const indexDataStart = 15;
  const indexDataEnd = indexDataStart + endpointSummaries.length - 1;
  introSheet.getRange(`A${indexHeaderRow}:J${indexDataEnd}`).values = [
    ["#", "Pestaña", "Alcance", "Operation ID", "Método", "Path", "Resumen", "Campos", "Mapeos", "Consultas"],
    ...endpointSummaries.map(({ sheetName, operation }, index) => [index + 1, sheetName, operation[0], operation[3], operation[4], operation[5], operation[6], null, null, null]),
  ];
  introSheet.getRange(`A${indexHeaderRow}:J${indexHeaderRow}`).format = { fill: COLORS.dark, font: { bold: true, color: COLORS.white, size: 10 }, wrapText: true, verticalAlignment: "center" };
  introSheet.getRange(`A${indexDataStart}:J${indexDataEnd}`).format = { font: { color: COLORS.dark, size: 9 }, wrapText: true, verticalAlignment: "top", borders: { insideHorizontal: { style: "thin", color: COLORS.border } } };
  const indexTable = introSheet.tables.add(`A${indexHeaderRow}:J${indexDataEnd}`, true, "EndpointIndexTable");
  indexTable.style = "TableStyleMedium7";
  indexTable.showFilterButton = true;
  for (let index = 0; index < endpointSummaries.length; index++) {
    const row = indexDataStart + index;
    const summary = endpointSummaries[index];
    introSheet.getRange(`H${row}:J${row}`).formulas = [[
      `='${summary.sheetName}'!${summary.fieldCell}`,
      `='${summary.sheetName}'!${summary.mappingCell}`,
      `='${summary.sheetName}'!${summary.consultationCell}`,
    ]];
  }
  introSheet.getRange("A10").formulas = [[`=COUNTA(B${indexDataStart}:B${indexDataEnd})`]];
  introSheet.getRange("C10").formulas = [[`=COUNTIF(C${indexDataStart}:C${indexDataEnd},"NIUBIZ_JUSPAY_CBS")`]];
  introSheet.getRange("E10").formulas = [[`=SUM(H${indexDataStart}:H${indexDataEnd})`]];
  introSheet.getRange("G10").formulas = [[`=SUM(I${indexDataStart}:I${indexDataEnd})`]];
  introSheet.getRange("I10").formulas = [[`=SUM(J${indexDataStart}:J${indexDataEnd})`]];
  introSheet.getRange(`C${indexDataStart}:C${indexDataEnd}`).conditionalFormats.add("containsText", { text: "NIUBIZ_JUSPAY_CBS", format: { fill: COLORS.paleGreen, font: { bold: true, color: "#1F5D32" } } });
  const introWidths = [6, 28, 28, 28, 11, 38, 36, 12, 12, 12];
  for (let index = 0; index < introWidths.length; index++) introSheet.getRange(`${colLetter(index + 1)}1:${colLetter(index + 1)}${indexDataEnd}`).format.columnWidth = introWidths[index];
  introSheet.freezePanes.freezeRows(indexHeaderRow);

  await fs.mkdir(OUTPUT_DIR, { recursive: true });
  await fs.mkdir(PREVIEW_DIR, { recursive: true });
  const previewSheets = ["00 Inicio", ...endpointSummaries.map((item) => item.sheetName), "90 Catalogos", "91 AsyncAPI", "92 Eventos Async", "93 Avro", "94 Pendientes Peru", "95 Consultas Niubiz", "96 Fuentes"];
  for (const sheetName of previewSheets) {
    const preview = await workbook.render({ sheetName, range: sheetName === "00 Inicio" ? "A1:J30" : sheetName.match(/^\d{2} (CBS|INT)/) ? "A1:O45" : "A1:O20", scale: 1, format: "png" });
    const safeName = sheetName.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
    await fs.writeFile(path.join(PREVIEW_DIR, `${safeName}.png`), new Uint8Array(await preview.arrayBuffer()));
  }

  const reorganizedInspect = await workbook.inspect({ kind: "table", range: `00 Inicio!A1:J${indexDataEnd}`, include: "values,formulas", tableMaxRows: 32, tableMaxCols: 12, maxChars: 20000 });
  console.log(reorganizedInspect.ndjson);
  const reorganizedErrors = await workbook.inspect({ kind: "match", searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A", options: { useRegex: true, maxResults: 300 }, summary: "reorganized workbook formula error scan" });
  console.log(reorganizedErrors.ndjson);

  const reorganizedOutput = await SpreadsheetFile.exportXlsx(workbook);
  await reorganizedOutput.save(OUTPUT_FILE);
  console.log(JSON.stringify({ output: OUTPUT_FILE, endpointSheets: endpointSummaries.length, totalSheets: previewSheets.length }, null, 2));
  process.exit(0);
}

const operationSheet = writeDataSheet(workbook, {
  name: "Operaciones HTTP",
  title: "Inventario de operaciones HTTP",
  subtitle: "Incluye el contrato canónico Niubiz/Juspay y los borradores internos inferidos de los diagramas.",
  headers: ["Alcance", "Contrato", "Versión", "Operation ID", "Método", "Path", "Resumen", "Descripción", "Seguridad", "Mapeo Core", "Request schemas", "Response schemas", "Códigos HTTP", "Fuente"],
  rows: operationRows,
  widths: [22, 27, 14, 26, 10, 34, 30, 55, 35, 18, 38, 55, 20, 28],
  tableName: "HttpOperationsTable",
});
operationSheet.sheet.getRange(`A5:A${operationSheet.dataEnd}`).conditionalFormats.add("containsText", { text: "NIUBIZ_JUSPAY_CBS", format: { fill: COLORS.paleGreen, font: { bold: true, color: "#1F6B37" } } });

const httpDictSheet = writeDataSheet(workbook, {
  name: "Diccionario HTTP",
  title: "Diccionario de datos HTTP",
  subtitle: "Una fila por campo y contexto de operación. Requerido condicional refleja reglas JSON Schema.",
  headers: ["Alcance", "Contrato", "Versión", "Operation ID", "Dirección", "HTTP Status", "Schema raíz", "Ruta del campo", "Campo", "Requerido", "Regla condicional", "Tipo", "Formato", "Enum / Const", "Restricciones", "Nullable", "Descripción", "Ejemplo / Default", "Acción Perú", "Fuente"],
  rows: httpDictionaryRows,
  widths: [22, 26, 14, 24, 12, 12, 28, 38, 24, 13, 28, 24, 16, 30, 38, 11, 58, 26, 30, 28],
  tableName: "HttpDictionaryTable",
});
httpDictSheet.sheet.getRange(`S5:S${httpDictSheet.dataEnd}`).conditionalFormats.add("notContainsBlanks", { format: { fill: COLORS.paleAmber, font: { bold: true, color: "#8A5A00" } } });
httpDictSheet.sheet.freezePanes.freezeColumns(4);

writeDataSheet(workbook, {
  name: "Seguridad Headers",
  title: "Seguridad y headers HTTP",
  subtitle: "Mecanismos declarados por contrato. En el CBS canónico, API key y mTLS provienen de los diagramas, no del PDF.",
  headers: ["Alcance", "Contrato", "Tipo", "Nombre lógico", "Tipo de dato / seguridad", "Ubicación", "Nombre HTTP", "Requerido", "Descripción", "Scopes / Valores", "Fuente"],
  rows: securityRows,
  widths: [22, 28, 18, 25, 24, 14, 24, 12, 58, 45, 28],
  tableName: "SecurityHeadersTable",
});

const mappingSheet = writeDataSheet(workbook, {
  name: "Cruce Mapeo Niubiz",
  title: "Cruce del contrato canónico con Mapeo Niubiz_V1",
  subtitle: "Conserva Trama/API, obligatoriedad y observaciones de la referencia; las diferencias se muestran explícitamente.",
  headers: ["Operation ID", "Dirección", "Ruta contrato", "Campo contrato", "Campo referencia", "Trama / API", "Obligatorio contrato", "Obligatorio referencia", "Comparación", "Observación", "Hoja referencia", "Fuente"],
  rows: mappingRows,
  widths: [27, 13, 42, 26, 34, 18, 18, 20, 25, 62, 32, 28],
  tableName: "NiubizMappingCrosswalkTable",
});
mappingSheet.sheet.getRange(`I5:I${mappingSheet.dataEnd}`).conditionalFormats.add("containsText", { text: "COINCIDE", format: { fill: COLORS.paleGreen, font: { bold: true, color: "#1F5D32" } } });
mappingSheet.sheet.getRange(`I5:I${mappingSheet.dataEnd}`).conditionalFormats.add("containsText", { text: "CONFLICTO", format: { fill: COLORS.paleRed, font: { bold: true, color: "#000000" } } });
mappingSheet.sheet.getRange(`I5:I${mappingSheet.dataEnd}`).conditionalFormats.add("containsText", { text: "PENDIENTE", format: { fill: COLORS.paleAmber, font: { bold: true, color: "#8A5A00" } } });
mappingSheet.sheet.getRange(`I5:I${mappingSheet.dataEnd}`).conditionalFormats.add("containsText", { text: "EXTRA", format: { fill: COLORS.paleAmber, font: { bold: true, color: "#8A5A00" } } });
mappingSheet.sheet.freezePanes.freezeColumns(3);

const consultationsSheet = writeDataSheet(workbook, {
  name: "Consultas Niubiz",
  title: "Consultas abiertas del mapeo Niubiz",
  subtitle: "Preguntas preservadas y normalizadas desde la hoja Consultas y las secciones operativas de Mapeo Niubiz_V1.",
  headers: ["Responsable(s)", "Dominio", "Consulta", "Respuesta Niubiz", "Estado", "Fuente"],
  rows: consultationRows,
  widths: [28, 28, 78, 55, 16, 28],
  tableName: "NiubizQuestionsTable",
});
consultationsSheet.sheet.getRange(`E5:E${consultationsSheet.dataEnd}`).conditionalFormats.add("containsText", { text: "Pendiente", format: { fill: COLORS.paleRed, font: { bold: true, color: "#000000" } } });

writeDataSheet(workbook, {
  name: "Catálogos",
  title: "Catálogos, enumeraciones y constantes",
  subtitle: "Valores permitidos encontrados en OpenAPI, AsyncAPI y Avro.",
  headers: ["Alcance", "Contrato", "Schema", "Ruta del campo", "Tipo", "Valores", "Fuente"],
  rows: catalogRows,
  widths: [22, 30, 30, 42, 15, 58, 30],
  tableName: "CatalogsTable",
});

writeDataSheet(workbook, {
  name: "Operaciones Async",
  title: "Canales y operaciones AsyncAPI",
  subtitle: "Tópicos Kafka, mensajes, dirección y grupos consumidores de los contratos internos.",
  headers: ["Contrato", "Versión", "Operation ID", "Acción", "Canal lógico", "Tópico", "Mensaje", "Content Type", "Consumer Group", "Resumen", "Fuente"],
  rows: asyncOperationRows,
  widths: [32, 14, 34, 12, 28, 38, 36, 22, 34, 55, 24],
  tableName: "AsyncOperationsTable",
});

const eventSheet = writeDataSheet(workbook, {
  name: "Diccionario Eventos",
  title: "Diccionario de datos de eventos",
  subtitle: "Campos de los esquemas AsyncAPI para Customer, Account y Transaction Projection.",
  headers: ["Contrato", "Versión", "Schema raíz", "Ruta del campo", "Campo", "Requerido", "Regla condicional", "Tipo", "Formato", "Enum / Const", "Restricciones", "Nullable", "Descripción", "Ejemplo / Default", "Fuente"],
  rows: eventDictionaryRows,
  widths: [32, 14, 32, 42, 25, 13, 26, 26, 16, 34, 38, 11, 58, 26, 28],
  tableName: "EventDictionaryTable",
});
eventSheet.sheet.freezePanes.freezeColumns(3);

writeDataSheet(workbook, {
  name: "Diccionario Avro",
  title: "Diccionario de esquemas Avro",
  subtitle: "Representación serializable de los eventos CDC Account y Customer.",
  headers: ["Archivo", "Namespace", "Root record", "Record", "Ruta del campo", "Campo", "Tipo Avro", "Nullable", "Default", "Símbolos enum", "Descripción"],
  rows: avroRows,
  widths: [30, 42, 28, 28, 44, 24, 32, 11, 18, 52, 52],
  tableName: "AvroDictionaryTable",
});

const pendingSheet = writeDataSheet(workbook, {
  name: "Pendientes Perú",
  title: "Decisiones pendientes para Perú",
  subtitle: "Acciones necesarias antes de aprobar el contrato Niubiz/Juspay CBS como versión 1.0.0.",
  headers: ["Prioridad", "Categoría", "Campo / Tema", "Acción", "Motivo", "Decisión requerida", "Fuente"],
  rows: pendingRows,
  widths: [12, 18, 38, 34, 60, 48, 30],
  tableName: "PeruPendingTable",
});
pendingSheet.sheet.getRange(`A5:A${pendingSheet.dataEnd}`).conditionalFormats.add("containsText", { text: "Alta", format: { fill: COLORS.paleRed, font: { bold: true, color: "#000000" } } });
pendingSheet.sheet.getRange(`A5:A${pendingSheet.dataEnd}`).conditionalFormats.add("containsText", { text: "Media", format: { fill: COLORS.paleAmber, font: { bold: true, color: "#8A5A00" } } });

writeDataSheet(workbook, {
  name: "Fuentes",
  title: "Inventario de fuentes y contratos",
  subtitle: "Trazabilidad del workbook hacia los archivos fuente del repositorio y la especificación PDF.",
  headers: ["Tipo", "Alcance", "Título", "Versión", "Archivo", "Estado", "Notas"],
  rows: sourceRows,
  widths: [14, 22, 38, 16, 62, 20, 65],
  tableName: "SourcesTable",
});

const summary = workbook.worksheets.add("Resumen");
summary.showGridLines = false;
summary.getRange("A1:J1").merge();
summary.getRange("A1").values = [["Diccionario de datos - Contratos Niubiz/Juspay y TAPP"]];
summary.getRange("A2:J2").merge();
summary.getRange("A2").values = [["Inventario consolidado de OpenAPI, AsyncAPI y Avro. Contrato CBS canónico basado en Juspay-Peru CBS API Specs."]];
summary.getRange("A1:J1").format = { fill: COLORS.red, font: { bold: true, color: COLORS.white, size: 18 }, verticalAlignment: "center" };
summary.getRange("A2:J2").format = { fill: COLORS.light, font: { italic: true, color: COLORS.slate, size: 10 }, wrapText: true, verticalAlignment: "center" };
summary.getRange("A1:J1").format.rowHeight = 34;
summary.getRange("A2:J2").format.rowHeight = 34;
summary.getRange("A4:B4").values = [["Indicador", "Valor"]];
summary.getRange("A5:A12").values = [
  ["Contratos OpenAPI"], ["Operaciones HTTP"], ["Campos HTTP"], ["Contratos AsyncAPI"],
  ["Canales/operaciones async"], ["Campos de eventos"], ["Esquemas Avro"], ["Pendientes Perú"],
];
summary.getRange("B5:B12").formulas = [
  [`=COUNTIF('Fuentes'!A5:A${4 + sourceRows.length},"OpenAPI")`],
  [`=COUNTA('Operaciones HTTP'!D5:D${4 + operationRows.length})`],
  [`=COUNTA('Diccionario HTTP'!I5:I${4 + httpDictionaryRows.length})`],
  [`=COUNTIF('Fuentes'!A5:A${4 + sourceRows.length},"AsyncAPI")`],
  [`=COUNTA('Operaciones Async'!F5:F${4 + asyncOperationRows.length})`],
  [`=COUNTA('Diccionario Eventos'!E5:E${4 + eventDictionaryRows.length})`],
  [`=COUNTIF('Fuentes'!A5:A${4 + sourceRows.length},"Avro")`],
  [`=COUNTA('Pendientes Perú'!D5:D${4 + pendingRows.length})`],
];
summary.getRange("A4:B4").format = { fill: COLORS.dark, font: { bold: true, color: COLORS.white }, borders: { preset: "outside", style: "thin", color: COLORS.dark } };
summary.getRange("A5:B12").format = { fill: COLORS.white, borders: { preset: "all", style: "thin", color: COLORS.border }, font: { color: COLORS.dark } };
summary.getRange("B5:B12").format = { fill: COLORS.paleBlue, font: { bold: true, color: COLORS.dark, size: 12 }, horizontalAlignment: "right", numberFormat: "#,##0" };

summary.getRange("D4:J4").merge();
summary.getRange("D4").values = [["Contrato canónico Niubiz/Juspay CBS"]];
summary.getRange("D5:J10").merge();
summary.getRange("D5").values = [[
  "6 operaciones: List Accounts, Validate Card Details, Balance Enquiry, Credit Money, Debit Money y Status Check. " +
  "La reversa se procesa por Credit Money con type=DEBIT_REVERSAL. Los campos Aadhaar, IFSC e INR requieren localización para Perú."
]];
summary.getRange("D4:J4").format = { fill: COLORS.dark, font: { bold: true, color: COLORS.white, size: 12 }, verticalAlignment: "center" };
summary.getRange("D5:J10").format = { fill: COLORS.paleGreen, font: { color: "#1F5D32", size: 11 }, wrapText: true, verticalAlignment: "top", borders: { preset: "outside", style: "thin", color: "#A7CDB1" } };

summary.getRange("A15:J15").merge();
summary.getRange("A15").values = [["Operaciones del contrato canónico"]];
summary.getRange("A15:J15").format = { fill: COLORS.red, font: { bold: true, color: COLORS.white, size: 12 } };
const canonicalOps = operationRows.filter((row) => row[0] === "NIUBIZ_JUSPAY_CBS");
summary.getRange(`A16:E${16 + canonicalOps.length}`).values = [
  ["Operation ID", "Método", "Path", "Resumen", "Mapeo Core"],
  ...canonicalOps.map((row) => [row[3], row[4], row[5], row[6], row[9]]),
];
summary.getRange("A16:E16").format = { fill: COLORS.dark, font: { bold: true, color: COLORS.white }, wrapText: true };
summary.getRange(`A17:E${16 + canonicalOps.length}`).format = { borders: { preset: "inside", style: "thin", color: COLORS.border }, wrapText: true, verticalAlignment: "top" };
summary.getRange(`A16:E${16 + canonicalOps.length}`).format.columnWidth = 24;
summary.getRange(`C16:C${16 + canonicalOps.length}`).format.columnWidth = 36;
summary.getRange(`D16:D${16 + canonicalOps.length}`).format.columnWidth = 32;
summary.getRange("A4:A12").format.columnWidth = 28;
summary.getRange("B4:B12").format.columnWidth = 14;
summary.getRange("D4:J10").format.columnWidth = 14;
summary.freezePanes.freezeRows(2);

await fs.mkdir(OUTPUT_DIR, { recursive: true });
await fs.mkdir(PREVIEW_DIR, { recursive: true });

const inspectSummary = await workbook.inspect({ kind: "table", range: "Resumen!A1:J22", include: "values,formulas", tableMaxRows: 24, tableMaxCols: 12 });
console.log(inspectSummary.ndjson);
const formulaErrors = await workbook.inspect({ kind: "match", searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A", options: { useRegex: true, maxResults: 300 }, summary: "final formula error scan" });
console.log(formulaErrors.ndjson);

const previewSheets = [
  ["Resumen", "A1:J22"],
  ["Operaciones HTTP", "A1:N18"],
  ["Diccionario HTTP", "A1:T18"],
  ["Seguridad Headers", "A1:K18"],
  ["Cruce Mapeo Niubiz", "A1:L18"],
  ["Consultas Niubiz", "A1:F18"],
  ["Catálogos", "A1:G18"],
  ["Operaciones Async", "A1:K18"],
  ["Diccionario Eventos", "A1:O18"],
  ["Diccionario Avro", "A1:K18"],
  ["Pendientes Perú", "A1:G18"],
  ["Fuentes", "A1:G18"],
];
for (const [sheetName, range] of previewSheets) {
  const preview = await workbook.render({ sheetName, range, scale: 1, format: "png" });
  const safeName = sheetName.toLowerCase().replace(/\s+/g, "-");
  await fs.writeFile(path.join(PREVIEW_DIR, `${safeName}.png`), new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(OUTPUT_FILE);

console.log(JSON.stringify({
  output: OUTPUT_FILE,
  counts: {
    openApiDocuments: openApiDocs.length,
    httpOperations: operationRows.length,
    httpFields: httpDictionaryRows.length,
    asyncApiDocuments: asyncApiDocs.length,
    asyncOperationsAndChannels: asyncOperationRows.length,
    eventFields: eventDictionaryRows.length,
    avroFields: avroRows.length,
    peruPending: pendingRows.length,
    niubizCrosswalkRows: mappingRows.length,
    niubizQuestions: consultationRows.length,
  },
}, null, 2));
