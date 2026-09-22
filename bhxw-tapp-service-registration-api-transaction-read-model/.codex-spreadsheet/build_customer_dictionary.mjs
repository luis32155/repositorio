import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outputDir = "outputs/customer-data-dictionary";
const outputPath = outputDir + "/Diccionario_Datos_CORE_PostgreSQL.xlsx";
const previewDir = outputDir + "/previews";
const fontFamily = "Arial";

await fs.mkdir(outputDir, { recursive: true });
await fs.mkdir(previewDir, { recursive: true });

const wb = Workbook.create();

const colors = {
  navy: "#17365D",
  blue: "#1F4E78",
  teal: "#0F6B78",
  lightBlue: "#DCE6F1",
  paleBlue: "#EAF2F8",
  paleTeal: "#E2F0F2",
  amber: "#FFF2CC",
  red: "#FCE4D6",
  green: "#E2F0D9",
  gray: "#E7E6E6",
  lightGray: "#F3F6F8",
  text: "#1F2937",
  muted: "#5B6573",
  white: "#FFFFFF"
};

function colLetter(n) {
  let s = "";
  let x = n;
  while (x > 0) {
    x--;
    s = String.fromCharCode(65 + (x % 26)) + s;
    x = Math.floor(x / 26);
  }
  return s;
}

function baseSheet(sheet) {
  sheet.showGridLines = false;
}

function titleBlock(sheet, title, subtitle, lastColumn) {
  const last = colLetter(lastColumn);
  sheet.getRange("A2:" + last + "2").merge();
  sheet.getRange("A2").values = [[title]];
  sheet.getRange("A2").format.font = {
    name: fontFamily,
    size: 15,
    bold: true,
    color: colors.navy
  };
  sheet.getRange("A3:" + last + "3").merge();
  sheet.getRange("A3").values = [[subtitle]];
  sheet.getRange("A3").format.font = {
    name: fontFamily,
    size: 10,
    italic: true,
    color: colors.muted
  };
  sheet.getRange("A4:" + last + "4").format.fill = colors.teal;
  sheet.getRange("A4:" + last + "4").format.rowHeight = 3;
}

function writeTable(sheet, startRow, headers, rows, widths) {
  const endCol = colLetter(headers.length);
  const header = sheet.getRange("A" + startRow + ":" + endCol + startRow);
  header.values = [headers];
  header.format = {
    fill: colors.navy,
    font: { name: fontFamily, size: 10, bold: true, color: colors.white },
    horizontalAlignment: "center",
    verticalAlignment: "center",
    wrapText: true,
    borders: { preset: "inside", style: "thin", color: colors.white }
  };
  header.format.rowHeight = 30;

  if (rows.length > 0) {
    const body = sheet.getRange(
      "A" + (startRow + 1) + ":" + endCol + (startRow + rows.length)
    );
    body.values = rows;
    body.format.verticalAlignment = "top";
    body.format.wrapText = true;
    body.format.borders = {
      insideHorizontal: { style: "thin", color: "#D9E2F3" },
      bottom: { style: "thin", color: "#A6A6A6" }
    };
    for (let i = 0; i < rows.length; i++) {
      if (i % 2 === 1) {
        sheet.getRange(
          "A" + (startRow + 1 + i) + ":" + endCol + (startRow + 1 + i)
        ).format.fill = colors.lightGray;
      }
    }
  }

  widths.forEach((w, i) => {
    sheet.getRange(colLetter(i + 1) + ":" + colLetter(i + 1)).format.columnWidth = w;
  });
}

// --------------------------------------------------------------------------
// Datos fuente y mapeos
// --------------------------------------------------------------------------

const coreTables = [
  ["FSD001", "Personas", "Pepais, Petdoc, Pendoc, Petipo, Penom", "Base por país, tipo y número de documento", "users", "Parcial", "Pecuebt/customer_key y catálogo PN/PJ pendientes"],
  ["FSD002", "Persona física", "Pfpais, Pftdoc, Pfndoc, nombres, apellidos, Pffnac, Pfeciv, Pfcant", "Complementa la persona física", "users", "Directa", "Requiere normalizar fechas y catálogos"],
  ["FSD003", "Persona jurídica", "Pjpais, Pjtdoc, Pjndoc, Pjrazs, Pjfcon", "Razón social y constitución", "enterprises", "Parcial", "Confirmar que la persona jurídica representa enterprise_details"],
  ["LWVD50A", "Clave digital y contactos", "WV50APAIS, WV50ATDOC, WV50ANDOC, WV50ATDAT, WV50ADCON, WV50AEST", "Email y celular validados", "users", "Directa con regla", "Tipo 1=email; tipo 2=celular; estado 1=validado"],
  ["FSR008", "Documento a cuenta BT", "Pgcod, Ctnro, Pepais, Petdoc, Pendoc", "Vincula persona con empresa/cuenta BT", "Sin destino directo", "Pendiente", "No equivale por sí misma a un permiso de cuenta"],
  ["FSD008", "Maestro de cuenta BT", "PGCOD, CTNRO, CTNOM y atributos de cuenta", "Datos maestros de cuenta", "accounts", "Candidata", "Confirmar CTNRO como account_number y alcance de cuenta BT"],
  ["LCPD18", "Cuenta BT integradora", "CPD17CODRE, CPD18PGUNI, CPD18CTUNI, CPD18PGCOD, CPD18CTNRO", "Resuelve una cuenta BT única", "Sin destino directo", "Pendiente", "Definir dirección de relación y precedencia de la cuenta única"]
];

const corePgMap = [
  ["FSD001", "Petdoc", "Homologar tipo documental", "payload.user_details.document_type", "users", "document_type", "Parcial", "Confirmar catálogo y tipo INTEGER"],
  ["FSD001", "Pendoc", "Copiar conservando ceros", "payload.user_details.document_number", "users", "document_number", "Directa", "Identificador almacenado como texto"],
  ["FSD001", "Petipo", "Homologar PN/PJ o persona física/jurídica", "payload.user_details.person_type", "users", "person_type", "Pendiente", "Falta equivalencia de códigos CORE"],
  ["FSD001", "Penom", "Usar como nombre base cuando aplique", "payload.user_details.user_full_name", "users", "user_full_name", "Candidata", "Para persona física se propone concatenar FSD002"],
  ["FSD002", "Pfnom1 + Pfnom2 + Pfape1 + Pfape2", "Concatenar componentes informados", "payload.user_details.user_full_name", "users", "user_full_name", "Directa con transformación", "El Excel sugiere construir full_name en el servicio"],
  ["FSD002", "Pfnom2", "Segundo nombre", "payload.user_details.user_middle_name", "users", "user_middle_name", "Candidata", "Validar definición de middle_name"],
  ["FSD002", "Pffnac", "Convertir fecha CORE a ISO yyyy-MM-dd", "payload.user_details.birth_date", "users", "birth_date", "Directa con transformación", "Definir tratamiento de fechas inválidas"],
  ["FSD002", "Pfeciv", "Homologar catálogo", "payload.user_details.marital_status", "users", "marital_status", "Directa con catálogo", "Catálogo pendiente"],
  ["FSD002", "Pfcant", "Homologar catálogo de sexo/género", "payload.user_details.gender", "users", "gender", "Directa con catálogo", "Catálogo pendiente"],
  ["FSD003", "Pjrazs", "Copiar razón social", "payload.user_details.enterprise_details.enterprise_name", "enterprises", "enterprise_name", "Parcial", "Validar vínculo con enterprise_sco_id"],
  ["FSD003", "Pjfcon", "Convertir fecha CORE a ISO yyyy-MM-dd", "payload.user_details.enterprise_details.constitution_date", "enterprises", "constitution_date", "Directa con transformación", "Definir formato físico de ocho posiciones"],
  ["LWVD50A", "WV50ADCON cuando WV50ATDAT=1", "Seleccionar contacto validado WV50AEST=1", "payload.user_details.user_email", "users", "user_email", "Directa con regla", "Definir selección si hay más de un registro"],
  ["LWVD50A", "WV50ADCON cuando WV50ATDAT=2", "Seleccionar contacto validado WV50AEST=1", "payload.user_details.phone_number", "users", "phone_number", "Directa con regla", "Definir selección si hay más de un registro"],
  ["LWVD50A", "WV50APAIS", "Conservar código hasta homologación", "payload.user_details.user_country", "users", "user_country", "Candidata", "No asumir país de residencia"],
  ["LWVD50A", "WV50ATDOC + WV50ANDOC", "Contrastar identidad documental", "payload.user_details.document_type/document_number", "users", "document_type/document_number", "Alternativa", "FSD001 se propone como identidad base"],
  ["FSD008", "CTNRO", "Mapear únicamente tras confirmar equivalencia", "payload.user_details.entitlements.services[].accounts[].account_number", "accounts", "account_number", "Pendiente", "Cuenta BT no equivale automáticamente a cuenta operativa"],
  ["FSD008", "CTNOM", "Nombre maestro de cuenta", "payload.user_details.entitlements.services[].accounts[].account_name", "accounts", "account_name", "Candidata", "Depende de confirmar CTNRO"],
  ["FSR008", "Pgcod + Ctnro", "Resolver relación persona-cuenta en producer", "Sin campo directo", "user_service_accounts", "Relación indirecta", "Pendiente", "El modelo PostgreSQL representa permisos usuario-servicio-cuenta"],
  ["LCPD18", "CPD18CTUNI", "Resolver cuenta integrada según regla de negocio", "Sin campo confirmado", "accounts", "account_number", "Pendiente", "Definir precedencia frente a FSD008.CTNRO"],
  ["Sin fuente en Excel", "Servicios, roles y límites", "Obtener de fuentes de entitlement", "payload.user_details.entitlements", "services / user_services / user_service_accounts", "Varias", "Fuera del mapeo CORE", "Las siete tablas CORE analizadas no cubren permisos ACH"]
];

const pg = [];
function addPg(table, column, type, nullable, key, rule, jsonPath, description) {
  pg.push(["cdc_customer", table, column, type, nullable, key, rule, jsonPath, description]);
}

addPg("enterprises", "enterprise_key", "UUID", "No", "PK", "DEFAULT gen_random_uuid()", "Generado", "Clave técnica de empresa");
addPg("enterprises", "source_id", "TEXT", "No", "UK", "UK con enterprise_sco_id", "metadata.source_id", "Sistema fuente");
addPg("enterprises", "enterprise_sco_id", "TEXT", "No", "UK", "UK con source_id", "enterprise_details.enterprise_sco_id", "Identificador de empresa");
addPg("enterprises", "reference_code", "TEXT", "Sí", "", "", "enterprise_details.reference_code", "Código de referencia");
addPg("enterprises", "enterprise_name", "TEXT", "Sí", "", "", "enterprise_details.enterprise_name", "Nombre o razón social");
addPg("enterprises", "enterprise_country", "TEXT", "Sí", "", "", "enterprise_details.enterprise_country", "País de la empresa");
addPg("enterprises", "enterprise_status", "TEXT", "Sí", "", "", "enterprise_details.enterprise_status", "Estado de empresa");
addPg("enterprises", "constitution_date", "DATE", "Sí", "", "", "enterprise_details.constitution_date", "Fecha de constitución");
addPg("enterprises", "enterprise_attributes", "JSONB", "No", "", "Objeto JSON", "enterprise_details completo", "Campos dinámicos de empresa");
addPg("enterprises", "updated_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Última actualización");

addPg("users", "user_key", "UUID", "No", "PK", "DEFAULT gen_random_uuid()", "Generado", "Clave técnica de persona");
addPg("users", "user_full_name", "TEXT", "Sí", "", "", "user_details.user_full_name", "Nombre completo");
addPg("users", "user_middle_name", "TEXT", "Sí", "", "", "user_details.user_middle_name", "Segundo nombre o nombre intermedio");
addPg("users", "user_country", "TEXT", "Sí", "", "", "user_details.user_country", "País del usuario");
addPg("users", "user_preferred_language", "TEXT", "Sí", "", "", "user_details.user_preferred_language", "Idioma preferido");
addPg("users", "user_email", "TEXT", "Sí", "", "", "user_details.user_email", "Correo electrónico");
addPg("users", "user_email_domain", "TEXT", "Sí", "", "", "user_details.user_email_domain", "Dominio del correo");
addPg("users", "phone_number", "TEXT", "Sí", "", "", "user_details.phone_number", "Teléfono");
addPg("users", "document_type", "INTEGER", "Sí", "IDX", "idx_users_document", "user_details.document_type", "Tipo documental");
addPg("users", "document_number", "TEXT", "Sí", "IDX", "idx_users_document", "user_details.document_number", "Número documental");
addPg("users", "person_type", "TEXT", "Sí", "", "", "user_details.person_type", "Tipo de persona");
addPg("users", "birth_date", "DATE", "Sí", "", "", "user_details.birth_date", "Fecha de nacimiento");
addPg("users", "marital_status", "TEXT", "Sí", "", "", "user_details.marital_status", "Estado civil");
addPg("users", "gender", "TEXT", "Sí", "", "", "user_details.gender", "Género");
addPg("users", "user_attributes", "JSONB", "No", "", "Objeto JSON", "user_details completo", "Campos dinámicos del usuario");
addPg("users", "created_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Fecha de creación");
addPg("users", "updated_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Fecha de actualización");

addPg("enterprise_users", "enterprise_user_key", "UUID", "No", "PK", "DEFAULT gen_random_uuid()", "Generado", "Clave técnica usuario-empresa");
addPg("enterprise_users", "source_id", "TEXT", "No", "FK/UK", "FK a enterprises", "metadata.source_id", "Sistema fuente");
addPg("enterprise_users", "enterprise_sco_id", "TEXT", "No", "FK/UK", "FK a enterprises", "enterprise_details.enterprise_sco_id", "Empresa del usuario");
addPg("enterprise_users", "user_key", "UUID", "No", "FK/UK", "FK a users", "Resuelto por el consumer", "Perfil personal asociado");
addPg("enterprise_users", "external_user_id", "TEXT", "No", "UK", "UK por fuente y empresa", "user_details.user_id", "Identificador externo del usuario");
addPg("enterprise_users", "user_role_name", "TEXT", "Sí", "", "", "user_details.user_role_name", "Rol del usuario");
addPg("enterprise_users", "user_status", "TEXT", "Sí", "", "", "user_details.user_status", "Estado del usuario");
addPg("enterprise_users", "user_language", "TEXT", "Sí", "", "", "user_details.user_preferred_language", "Idioma del usuario");
addPg("enterprise_users", "last_signin_timestamp", "TIMESTAMPTZ", "Sí", "", "", "triggering_user_details.last_signin_timestamp", "Último acceso cuando está informado");
addPg("enterprise_users", "account_transfer_enabled", "BOOLEAN", "Sí", "", "", "entitlements.account_transfer.enabled", "Permiso de transferencia");
addPg("enterprise_users", "recipient_maintenance_enabled", "BOOLEAN", "Sí", "", "", "entitlements.recipient_maintenance_enabled", "Permiso de mantenimiento de beneficiarios");
addPg("enterprise_users", "triggered_by_enterprise_user_key", "UUID", "Sí", "FK", "Autorreferencia diferible", "triggering_user_details.user_id", "Actor que originó el evento");
addPg("enterprise_users", "event_name", "TEXT", "Sí", "", "", "metadata.event_id", "Nombre o identificador funcional del evento");
addPg("enterprise_users", "snapshot_at", "TIMESTAMPTZ", "Sí", "", "", "metadata.event_timestamp", "Fecha del snapshot");
addPg("enterprise_users", "snapshot_order", "NUMERIC(30,0)", "Sí", "", "", "payload.batch_sequence o criterio de orden", "Orden lógico del snapshot");
addPg("enterprise_users", "payload_snapshot", "JSONB", "Sí", "", "Objeto JSON", "payload completo", "Snapshot dinámico completo");
addPg("enterprise_users", "event_metadata", "JSONB", "Sí", "", "Objeto JSON", "metadata completo", "Metadatos completos");
addPg("enterprise_users", "created_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Fecha de creación");
addPg("enterprise_users", "updated_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Fecha de actualización");

addPg("services", "service_key", "UUID", "No", "PK", "DEFAULT gen_random_uuid()", "Generado", "Clave técnica de servicio");
addPg("services", "source_id", "TEXT", "No", "FK/UK", "FK a enterprises", "metadata.source_id", "Sistema fuente");
addPg("services", "enterprise_sco_id", "TEXT", "No", "FK/UK", "FK a enterprises", "enterprise_details.enterprise_sco_id", "Empresa del servicio");
addPg("services", "service_id", "TEXT", "No", "UK", "UK por empresa", "services[].service_id", "Identificador de servicio");
addPg("services", "service_name", "TEXT", "Sí", "", "", "services[].service_name", "Nombre de servicio");
addPg("services", "service_type", "TEXT", "Sí", "", "", "services[].service_type", "Tipo de servicio");
addPg("services", "service_attributes", "JSONB", "No", "", "Objeto JSON", "services[] completo", "Campos dinámicos del servicio");
addPg("services", "created_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Fecha de creación");
addPg("services", "updated_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Fecha de actualización");

addPg("accounts", "account_key", "UUID", "No", "PK", "DEFAULT gen_random_uuid()", "Generado", "Clave técnica de cuenta");
addPg("accounts", "source_id", "TEXT", "No", "FK/UK", "FK a enterprises", "metadata.source_id", "Sistema fuente");
addPg("accounts", "enterprise_sco_id", "TEXT", "No", "FK/UK", "FK a enterprises", "enterprise_details.enterprise_sco_id", "Empresa de la cuenta");
addPg("accounts", "account_number", "TEXT", "No", "UK", "UK por empresa", "accounts[].account_number", "Número de cuenta");
addPg("accounts", "account_type", "TEXT", "Sí", "", "", "accounts[].account_type", "Tipo de cuenta");
addPg("accounts", "account_name", "TEXT", "Sí", "", "", "accounts[].account_name", "Nombre de cuenta");
addPg("accounts", "account_status", "TEXT", "Sí", "", "", "accounts[].account_status", "Estado de cuenta");
addPg("accounts", "currency_code", "TEXT", "Sí", "", "", "accounts[].currency_code", "Moneda");
addPg("accounts", "account_attributes", "JSONB", "No", "", "Objeto JSON", "accounts[] completo", "Campos dinámicos de cuenta");
addPg("accounts", "created_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Fecha de creación");
addPg("accounts", "updated_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Fecha de actualización");

addPg("user_services", "user_service_key", "UUID", "No", "PK", "DEFAULT gen_random_uuid()", "Generado", "Clave técnica de asignación");
addPg("user_services", "source_id", "TEXT", "No", "FK/UK", "Parte de FK compuesta", "metadata.source_id", "Sistema fuente");
addPg("user_services", "enterprise_sco_id", "TEXT", "No", "FK/UK", "Parte de FK compuesta", "enterprise_details.enterprise_sco_id", "Empresa");
addPg("user_services", "enterprise_user_key", "UUID", "No", "FK/UK", "FK a enterprise_users", "Usuario resuelto", "Usuario empresarial");
addPg("user_services", "service_key", "UUID", "No", "FK/UK", "FK a services", "Servicio resuelto", "Servicio asignado");
addPg("user_services", "service_selected", "BOOLEAN", "Sí", "", "", "services[].service_selected", "Indica selección");
addPg("user_services", "user_role", "TEXT", "Sí", "", "", "services[].user_role", "Rol dentro del servicio");
addPg("user_services", "transaction_limit", "NUMERIC", "Sí", "", ">= 0", "services[].service_details.transaction_limit", "Límite por transacción");
addPg("user_services", "daily_limit", "NUMERIC", "Sí", "", ">= 0", "services[].service_details.daily_limit", "Límite diario");
addPg("user_services", "limit_currency", "TEXT", "Sí", "", "", "services[].service_details.limit_currency", "Moneda de límites");
addPg("user_services", "service_order", "INTEGER", "No", "", ">= 0", "Posición en services[]", "Orden estable del arreglo");
addPg("user_services", "entitlement_attributes", "JSONB", "No", "", "Objeto JSON", "services[] completo", "Campos dinámicos del permiso");
addPg("user_services", "created_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Fecha de creación");
addPg("user_services", "updated_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Fecha de actualización");

addPg("user_service_accounts", "user_service_account_key", "UUID", "No", "PK", "DEFAULT gen_random_uuid()", "Generado", "Clave técnica de relación");
addPg("user_service_accounts", "source_id", "TEXT", "No", "FK/UK", "Parte de FK compuesta", "metadata.source_id", "Sistema fuente");
addPg("user_service_accounts", "enterprise_sco_id", "TEXT", "No", "FK/UK", "Parte de FK compuesta", "enterprise_details.enterprise_sco_id", "Empresa");
addPg("user_service_accounts", "user_service_key", "UUID", "No", "FK/UK", "FK a user_services", "Asignación resuelta", "Usuario y servicio");
addPg("user_service_accounts", "account_key", "UUID", "No", "FK/UK", "FK a accounts", "Cuenta resuelta", "Cuenta habilitada");
addPg("user_service_accounts", "transaction_limit", "NUMERIC", "Sí", "", ">= 0", "accounts[].transaction_limit", "Límite por transacción");
addPg("user_service_accounts", "daily_limit", "NUMERIC", "Sí", "", ">= 0", "accounts[].daily_limit", "Límite diario");
addPg("user_service_accounts", "account_order", "INTEGER", "No", "", ">= 0", "Posición en accounts[]", "Orden estable del arreglo");
addPg("user_service_accounts", "entitlement_attributes", "JSONB", "No", "", "Objeto JSON", "accounts[] completo", "Campos dinámicos de relación");
addPg("user_service_accounts", "created_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Fecha de creación");
addPg("user_service_accounts", "updated_at", "TIMESTAMPTZ", "No", "", "", "Tiempo de procesamiento", "Fecha de actualización");

const relations = [
  ["PostgreSQL", "enterprises", "source_id + enterprise_sco_id", "enterprise_users", "source_id + enterprise_sco_id", "1:N", "fk_enterprise_users_enterprise", "Empresa y usuarios"],
  ["PostgreSQL", "users", "user_key", "enterprise_users", "user_key", "1:N", "fk_enterprise_users_user", "Perfil personal y pertenencia empresarial"],
  ["PostgreSQL", "enterprise_users", "enterprise_user_key", "enterprise_users", "triggered_by_enterprise_user_key", "1:N opcional", "fk_enterprise_users_actor", "Actor del evento"],
  ["PostgreSQL", "enterprises", "source_id + enterprise_sco_id", "services", "source_id + enterprise_sco_id", "1:N", "fk_services_enterprise", "Servicios observados por empresa"],
  ["PostgreSQL", "enterprises", "source_id + enterprise_sco_id", "accounts", "source_id + enterprise_sco_id", "1:N", "fk_accounts_enterprise", "Cuentas observadas por empresa"],
  ["PostgreSQL", "enterprise_users", "source_id + enterprise_sco_id + enterprise_user_key", "user_services", "mismas columnas", "1:N", "fk_user_services_enterprise_user", "Servicios habilitados para el usuario"],
  ["PostgreSQL", "services", "source_id + enterprise_sco_id + service_key", "user_services", "mismas columnas", "1:N", "fk_user_services_service", "Catálogo y asignación de servicio"],
  ["PostgreSQL", "user_services", "source_id + enterprise_sco_id + user_service_key", "user_service_accounts", "mismas columnas", "1:N", "fk_user_service_accounts_user_service", "Cuentas habilitadas por servicio"],
  ["PostgreSQL", "accounts", "source_id + enterprise_sco_id + account_key", "user_service_accounts", "mismas columnas", "1:N", "fk_user_service_accounts_account", "Catálogo y permiso de cuenta"],
  ["CORE candidata", "FSD001", "Pepais + Petdoc + Pendoc", "FSD002", "Pfpais + Pftdoc + Pfndoc", "1:0..1", "Por validar", "Persona base a persona física"],
  ["CORE candidata", "FSD001", "Pepais + Petdoc + Pendoc", "FSD003", "Pjpais + Pjtdoc + Pjndoc", "1:0..1", "Por validar", "Persona base a persona jurídica"],
  ["CORE candidata", "FSD001", "Pepais + Petdoc + Pendoc", "LWVD50A", "WV50APAIS + WV50ATDOC + WV50ANDOC", "1:N", "Por validar", "Persona y contactos"],
  ["CORE candidata", "FSD001", "Pepais + Petdoc + Pendoc", "FSR008", "Pepais + Petdoc + Pendoc", "1:N", "Por validar", "Persona y cuentas BT"],
  ["CORE candidata", "FSR008", "Pgcod + Ctnro", "FSD008", "PGCOD + CTNRO", "N:1", "Por validar", "Relación y maestro de cuenta BT"],
  ["CORE candidata", "FSR008", "Pgcod + Ctnro", "LCPD18", "CPD18PGCOD + CPD18CTNRO", "N:1 o N:N", "Por validar", "Cuenta BT y cuenta integrada"]
];

const pending = [
  [1, "Customer key", "¿Pecuebt ya existe y prevalece sobre FSR008/LCPD18?", "Alta", "CORE / B28", "Bloquea definición de identidad canónica"],
  [2, "Claves CORE", "Confirmar PK, UK, FK y cardinalidades reales de las siete tablas", "Alta", "DBA CORE", "Los JOIN actuales son candidatos"],
  [3, "Cuenta BT", "Confirmar si FSD008.CTNRO equivale a accounts[].account_number", "Alta", "Negocio / CORE", "Evita mezclar cuenta BT con cuenta operativa"],
  [4, "Empresa", "Confirmar si FSR008.Pgcod equivale a enterprise_sco_id", "Alta", "Negocio / Producer", "Necesario para vincular empresa y cuenta"],
  [5, "Catálogos", "Definir Petipo, documento, sexo, estado civil, país y códigos inesperados", "Media", "Negocio / Datos", "Necesario para homologación"],
  [6, "Contactos", "Definir selección cuando existan varios contactos validados", "Media", "Negocio", "No usar MAX(email) sin regla temporal"],
  [7, "Entitlements", "Identificar fuentes para servicios, roles, límites y permisos ACH", "Alta", "Arquitectura", "No aparecen en las siete tablas CORE"],
  [8, "Frescura", "Definir retraso permitido y frecuencia de refresh de la vista", "Media", "Arquitectura / Operaciones", "Determina estrategia de carga y refresco"]
];

// --------------------------------------------------------------------------
// Resumen
// --------------------------------------------------------------------------

const summary = wb.worksheets.add("Resumen");
baseSheet(summary);
summary.tabColor = colors.navy;
titleBlock(summary, "Diccionario de datos CORE → PostgreSQL", "Consumer CUSTOMER_INFORMATION_RETRIEVED · Esquema cdc_customer", 10);

summary.getRange("A6:B9").values = [
  ["Métrica", "Cantidad"],
  ["Tablas CORE analizadas", 7],
  ["Tablas PostgreSQL", 7],
  ["Columnas PostgreSQL documentadas", pg.length]
];
summary.getRange("A6:B6").format = {
  fill: colors.navy,
  font: { name: fontFamily, bold: true, color: colors.white },
  horizontalAlignment: "center"
};
summary.getRange("A7:A9").format.font = { name: fontFamily, bold: true, color: colors.navy };
summary.getRange("B7:B9").format.font = { name: fontFamily, size: 14, bold: true, color: colors.teal };
summary.getRange("A6:B9").format.borders = { preset: "outside", style: "thin", color: "#9EADBA" };

summary.getRange("D6:J6").merge();
summary.getRange("D6").values = [["Flujo de información"]];
summary.getRange("D6").format = {
  fill: colors.teal,
  font: { name: fontFamily, bold: true, color: colors.white },
  horizontalAlignment: "center"
};
summary.getRange("D7:J9").merge();
summary.getRange("D7").values = [["CORE Banking → Producer B28 → Kafka → Customer Event Consumer → 7 tablas cdc_customer → mv_customer_information_profiles"]];
summary.getRange("D7:J9").format = {
  fill: colors.paleTeal,
  font: { name: fontFamily, size: 11, bold: true, color: colors.navy },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
  borders: { preset: "outside", style: "thin", color: "#76A5AF" }
};

summary.getRange("A12:J12").merge();
summary.getRange("A12").values = [["Reglas de interpretación"]];
summary.getRange("A12").format = { fill: colors.blue, font: { name: fontFamily, bold: true, color: colors.white } };
summary.getRange("A13:J17").values = [
  ["1", "Las tablas CORE son fuentes upstream del producer; el consumer no las consulta directamente.", null, null, null, null, null, null, null, null],
  ["2", "La persistencia PostgreSQL normaliza el JSON por dominio y conserva campos dinámicos en JSONB.", null, null, null, null, null, null, null, null],
  ["3", "FSR008, FSD008 y LCPD18 no prueban por sí mismas permisos de cuentas del usuario.", null, null, null, null, null, null, null, null],
  ["4", "Los JOIN CORE son candidatos hasta confirmar DDL, claves, cardinalidades y catálogos reales.", null, null, null, null, null, null, null, null],
  ["5", "La vista materializada es un objeto derivado y no constituye una octava tabla de dominio.", null, null, null, null, null, null, null, null]
];
for (let r = 13; r <= 17; r++) {
  summary.getRange("B" + r + ":J" + r).merge();
}
summary.getRange("A13:A17").format = { fill: colors.lightBlue, font: { name: fontFamily, bold: true, color: colors.navy }, horizontalAlignment: "center" };
summary.getRange("B13:J17").format.wrapText = true;

summary.getRange("A20:J20").merge();
summary.getRange("A20").values = [["Fuentes"]];
summary.getRange("A20").format = { fill: colors.gray, font: { name: fontFamily, bold: true, color: colors.navy } };
summary.getRange("A21:J23").values = [
  ["Mapeo Campos B28 - Tablas CORE (1).xlsx", null, null, null, null, null, null, null, null, null],
  ["schema.sql del customer event consumer", null, null, null, null, null, null, null, null, null],
  ["Contrato JSON CUSTOMER_INFORMATION_RETRIEVED proporcionado para el consumer", null, null, null, null, null, null, null, null, null]
];
for (let r = 21; r <= 23; r++) summary.getRange("A" + r + ":J" + r).merge();
summary.getRange("A21:J23").format.font = { name: fontFamily, italic: true, color: colors.muted };
summary.getRange("A:A").format.columnWidth = 8;
summary.getRange("B:B").format.columnWidth = 18;
summary.getRange("C:C").format.columnWidth = 3;
for (let c = 4; c <= 10; c++) summary.getRange(colLetter(c) + ":" + colLetter(c)).format.columnWidth = 13;

// --------------------------------------------------------------------------
// Hojas de detalle
// --------------------------------------------------------------------------

const mapSheet = wb.worksheets.add("Mapeo CORE-PG");
baseSheet(mapSheet);
mapSheet.tabColor = colors.teal;
titleBlock(mapSheet, "Mapeo de campos CORE a PostgreSQL", "Las equivalencias pendientes requieren validación de negocio y del DDL CORE", 8);
writeTable(mapSheet, 6,
  ["Tabla CORE", "Campo CORE", "Transformación / regla", "Ruta JSON", "Tabla PostgreSQL", "Columna PostgreSQL", "Estado", "Observación"],
  corePgMap,
  [14, 29, 31, 42, 24, 25, 22, 40]
);
mapSheet.freezePanes.freezeRows(6);
mapSheet.freezePanes.freezeColumns(2);
for (let r = 7; r <= 6 + corePgMap.length; r++) {
  const status = String(corePgMap[r - 7][6]);
  const cell = mapSheet.getRange("G" + r);
  if (status.includes("Pendiente") || status.includes("Fuera")) cell.format.fill = colors.red;
  else if (status.includes("Directa")) cell.format.fill = colors.green;
  else cell.format.fill = colors.amber;
  cell.format.font = { name: fontFamily, bold: true, color: colors.text };
}

const dictSheet = wb.worksheets.add("Diccionario PostgreSQL");
baseSheet(dictSheet);
dictSheet.tabColor = colors.blue;
titleBlock(dictSheet, "Diccionario físico PostgreSQL", "DDL vigente del esquema cdc_customer · Tipos, claves, restricciones y origen JSON", 9);
writeTable(dictSheet, 6,
  ["Esquema", "Tabla", "Columna", "Tipo", "Nullable", "Clave", "Default / restricción", "Origen JSON", "Descripción"],
  pg,
  [18, 27, 31, 18, 11, 12, 28, 48, 43]
);
dictSheet.freezePanes.freezeRows(6);
dictSheet.freezePanes.freezeColumns(3);
for (let r = 7; r <= 6 + pg.length; r++) {
  if (pg[r - 7][5]) dictSheet.getRange("F" + r).format.fill = colors.paleBlue;
}

const coreSheet = wb.worksheets.add("Tablas CORE");
baseSheet(coreSheet);
titleBlock(coreSheet, "Diccionario funcional de tablas CORE", "Descripción consolidada del archivo de mapeo B28", 7);
writeTable(coreSheet, 6,
  ["Tabla CORE", "Función", "Campos relevantes", "Uso en el flujo", "Destino PostgreSQL", "Estado", "Observación"],
  coreTables,
  [16, 25, 48, 40, 26, 18, 48]
);
coreSheet.freezePanes.freezeRows(6);
for (let r = 7; r <= 6 + coreTables.length; r++) {
  const status = String(coreTables[r - 7][5]);
  coreSheet.getRange("F" + r).format.fill = status === "Directa" ? colors.green : (status === "Pendiente" ? colors.red : colors.amber);
}

const relSheet = wb.worksheets.add("Relaciones");
baseSheet(relSheet);
titleBlock(relSheet, "Relaciones y condiciones de unión", "Las relaciones CORE se consideran candidatas hasta confirmar claves y cardinalidades", 8);
writeTable(relSheet, 6,
  ["Sistema", "Tabla origen", "Columnas origen", "Tabla destino", "Columnas destino", "Cardinalidad", "Restricción / estado", "Propósito"],
  relations,
  [20, 24, 38, 27, 43, 17, 31, 43]
);
relSheet.freezePanes.freezeRows(6);
relSheet.freezePanes.freezeColumns(2);
for (let r = 7; r <= 6 + relations.length; r++) {
  if (relations[r - 7][0] === "CORE candidata") {
    relSheet.getRange("A" + r + ":H" + r).format.fill = colors.amber;
  }
}

const pendingSheet = wb.worksheets.add("Pendientes");
baseSheet(pendingSheet);
pendingSheet.tabColor = "#C55A11";
titleBlock(pendingSheet, "Decisiones pendientes", "Puntos que deben cerrarse antes de afirmar una equivalencia física CORE → PostgreSQL", 6);
writeTable(pendingSheet, 6,
  ["ID", "Tema", "Consulta", "Prioridad", "Responsable propuesto", "Impacto"],
  pending,
  [8, 25, 56, 14, 27, 48]
);
pendingSheet.freezePanes.freezeRows(6);
for (let r = 7; r <= 6 + pending.length; r++) {
  const priority = pending[r - 7][3];
  pendingSheet.getRange("D" + r).format.fill = priority === "Alta" ? colors.red : colors.amber;
  pendingSheet.getRange("D" + r).format.font = { name: fontFamily, bold: true, color: colors.text };
}

// Ajustes finales.
for (const name of ["Mapeo CORE-PG", "Diccionario PostgreSQL", "Tablas CORE", "Relaciones", "Pendientes"]) {
  const sh = wb.worksheets.getItem(name);
  const used = sh.getUsedRange();
  used.format.font = { name: fontFamily, size: 10, color: colors.text };
  sh.getRange("A2").format.font = { name: fontFamily, size: 15, bold: true, color: colors.navy };
  sh.getRange("A3").format.font = { name: fontFamily, size: 10, italic: true, color: colors.muted };
}

wb.recalculate();

const inspectSummary = await wb.inspect({
  kind: "table",
  sheetId: "Resumen",
  range: "A1:J23",
  include: "values,formulas",
  tableMaxRows: 25,
  tableMaxCols: 10,
  maxChars: 7000
});
console.log(inspectSummary.ndjson);

const errors = await wb.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!",
  options: { useRegex: true, maxResults: 100 },
  summary: "final formula error scan"
});
console.log(errors.ndjson);

for (const name of ["Resumen", "Mapeo CORE-PG", "Diccionario PostgreSQL", "Tablas CORE", "Relaciones", "Pendientes"]) {
  const preview = await wb.render({ sheetName: name, autoCrop: "all", scale: 0.8, format: "png" });
  const safeName = name.replace(/[^A-Za-z0-9]/g, "_");
  await fs.writeFile(previewDir + "/" + safeName + ".png", new Uint8Array(await preview.arrayBuffer()));
}

const out = await SpreadsheetFile.exportXlsx(wb);
await out.save(outputPath);
console.log("OUTPUT=" + outputPath);
console.log("PG_COLUMNS=" + pg.length);
