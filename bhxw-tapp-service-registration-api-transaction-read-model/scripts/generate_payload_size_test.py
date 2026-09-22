#!/usr/bin/env python3
"""Generate reproducible dummy XML payloads and measure their UTF-8 sizes.

The XMLDSig values generated here are deliberately dummy data.  They are useful
only for payload-size tests and must never be treated as cryptographically valid.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import re
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from pathlib import Path


DEFAULT_SOURCE = Path(
    r"C:\Users\Windows\.codex\attachments\9d62291f-8311-4ee9-8d6c-d0bd87767df2\pasted-text.txt"
)

VOLUME_PROJECTION = {
    "conservador": {
        2027: (0.09, 1.1),
        2028: (0.41, 4.9),
        2029: (0.73, 8.8),
        2030: (1.05, 12.6),
        2031: (1.37, 16.5),
        2032: (1.69, 20.3),
    },
    "optimista": {
        2027: (0.15, 2.0),
        2028: (0.53, 6.0),
        2029: (0.90, 11.0),
        2030: (1.28, 15.0),
        2031: (1.66, 20.0),
        2032: (2.04, 25.0),
    },
}


REALISTIC_REQPAY = """<upi:ReqPay xmlns:upi="http://npci.org/upi/schema/">
  <Head ver="2.0" ts="2027-01-15T10:30:45-05:00" orgId="000805" msgId="MSG20270115000000000000000000000001" prodType="UPI"/>
  <Meta>
    <Tag name="PAYREQSTART" value="2027-01-15T10:30:45-05:00"/>
    <Tag name="PAYREQEND" value="2027-01-15T10:30:46-05:00"/>
  </Meta>
  <Txn id="TXN20270115000000000000000000000001" note="Transferencia CCE" custRef="000012345678" refId="REF202701150001" refUrl="https://api.example.test/transfers/REF202701150001" ts="2027-01-15T10:30:45-05:00" refCategory="00" type="PAY" purpose="00" initiationMode="00" subType="PAY"/>
  <Payer addr="ana.perez@banco.test" name="ANA PEREZ" seqNum="1" type="PERSON" code="0000" cmId="CM001">
    <Info>
      <Identity id="DOC00012345" type="DNI" verifiedName="ANA PEREZ"/>
      <Rating VerifiedAddress="TRUE"/>
    </Info>
    <Device>
      <Tag name="MOBILE" value="51987654321"/>
      <Tag name="GEOCODE" value="-12.0464,-77.0428"/>
      <Tag name="LOCATION" value="LIMA"/>
      <Tag name="IP" value="192.0.2.10"/>
      <Tag name="TYPE" value="ANDROID"/>
      <Tag name="ID" value="DEVICE-DEMO-0001"/>
      <Tag name="OS" value="ANDROID14"/>
      <Tag name="APP" value="TAPP"/>
    </Device>
    <Ac addrType="ACCOUNT">
      <Detail name="IFSC" value="000805"/>
      <Detail name="ACTYPE" value="SAVINGS"/>
      <Detail name="ACNUM" value="00112233445566778899"/>
    </Ac>
    <Creds>
      <Cred type="PIN" subType="MPIN">
        <Data code="NPCI" ki="01">cTVmL211Uk5GMm9zT2w3ODVUTkZBVk1hVXcyVEhPQVp5ZzJzczlmeTVNb08rM3o4NldEL3dkUzFpUW1oZmlQQm4y</Data>
      </Cred>
    </Creds>
    <Amount value="1250.50" curr="PEN"/>
  </Payer>
  <Payees>
    <Payee addr="comercio.demo@banco.test" name="COMERCIO DEMO" seqNum="1" type="ENTITY" code="0001" cmId="CM100">
      <Info>
        <Identity id="RUC20123456789" type="RUC" verifiedName="COMERCIO DEMO SAC"/>
        <Rating VerifiedAddress="TRUE"/>
      </Info>
      <Ac addrType="ACCOUNT">
        <Detail name="IFSC" value="000123"/>
        <Detail name="ACTYPE" value="CURRENT"/>
        <Detail name="ACNUM" value="00998877665544332211"/>
      </Ac>
      <Amount value="1250.50" curr="PEN"/>
    </Payee>
  </Payees>
</upi:ReqPay>"""


CONSERVATIVE_REQPAY = """<upi:ReqPay xmlns:upi="http://npci.org/upi/schema/">
  <Head ver="2.0" ts="2027-01-15T10:30:45.521-05:00" orgId="000805" msgId="MSG20270115000000000000000000000001" prodType="UPI"/>
  <Meta>
    <Tag name="PAYREQSTART" value="2027-01-15T10:30:45.521-05:00"/>
    <Tag name="PAYREQEND" value="2027-01-15T10:30:46.041-05:00"/>
  </Meta>
  <Txn id="TXN20270115000000000000000000000001" note="Transferencia interbancaria CCE de prueba" custRef="CUSTREF000012345678" refId="REF20270115000000000001" refUrl="https://api.example.test/v1/transfers/REF20270115000000000001" ts="2027-01-15T10:30:45.521-05:00" refCategory="00" type="PAY" orgTxnId="ORGTXN2027011500000000000000000001" purpose="00" orgRrn="000123456789" orgTxnDate="2027-01-15T10:30:45.521-05:00" initiationMode="00" subType="PAY" orgRespCode="00" clVersion="2.0">
    <RiskScores>
      <Score provider="TAPP" type="TXNRISK" value="00000000000000000000000000000032"/>
      <Score provider="BANK" type="FRAUDRISK" value="00000000000000000000000000000040"/>
      <Score provider="CCE" type="NETWORKRISK" value="00000000000000000000000000000010"/>
    </RiskScores>
    <Rules>
      <Rule name="EXPIREAFTER" value="64800"/>
      <Rule name="MINAMOUNT" value="0.01"/>
    </Rules>
    <QR qVer="000001" ts="2027-01-15T10:30:45.521-05:00" qrMedium="DYNAMIC" expireTs="2027-01-16T10:30:45.521-05:00" query="TRANSFERENCIA CCE COMERCIO DEMO" verToken="TOKEN-DEMO-20270115-0000000001" stan="123456"/>
  </Txn>
  <Payer addr="ana.maria.perez.garcia@banco-origen.test" name="ANA MARIA PEREZ GARCIA" seqNum="000001" type="PERSON" code="0000" cmId="CM-ORIGEN-0000001">
    <Merchant>
      <Identifier subCode="7407" mid="MID-ORIGEN-000001" sid="SID-ORIGEN-000001" tid="TID-ORIGEN-000001" merchantType="SMALL" merchantGenre="ONLINE" onBoardingType="BANK" pinCode="15001" regIdNo="REG-ORIGEN-000001" tier="TIER1"/>
      <Name brand="TAPP DEMO" legal="TAPP DEMO SOCIEDAD ANONIMA" franchise="TAPP"/>
      <Ownership type="PROPRIETARY"/>
      <Invoice name="FACTURA ELECTRONICA" num="F001-00001234" date="2027-01-15T10:30:45.521-05:00"/>
    </Merchant>
    <Institution type="BANK" route="CCE">
      <Name value="BANCO DE ORIGEN DEMO" acNum="00112233445566778899"/>
      <Purpose code="P01" note="TRANSFERENCIA INTERBANCARIA"/>
      <Originator name="ANA MARIA PEREZ GARCIA" type="INDIVIDUAL" refNo="ORI20270115000001">
        <Address location="AVENIDA DEMO 123" city="LIMA" country="PE" geocode="-12.0464,-77.0428"/>
      </Originator>
      <Beneficiary name="COMERCIO DEMO SOCIEDAD ANONIMA CERRADA"/>
    </Institution>
    <Info>
      <Identity id="DOC00012345" type="DNI" verifiedName="ANA MARIA PEREZ GARCIA"/>
      <Rating VerifiedAddress="TRUE"/>
    </Info>
    <Device>
      <Tag name="MOBILE" value="51987654321"/>
      <Tag name="GEOCODE" value="-12.0464,-77.0428"/>
      <Tag name="LOCATION" value="LIMA"/>
      <Tag name="IP" value="192.0.2.10"/>
      <Tag name="TYPE" value="ANDROID"/>
      <Tag name="ID" value="DEVICE-DEMO-2027-00000001"/>
      <Tag name="OS" value="ANDROID14"/>
      <Tag name="APP" value="TAPP-MOBILE-2.0.0"/>
      <Tag name="CAPABILITY" value="UPI_FULL"/>
    </Device>
    <Ac addrType="ACCOUNT">
      <Detail name="IFSC" value="000805"/>
      <Detail name="ACTYPE" value="SAVINGS"/>
      <Detail name="ACNUM" value="00112233445566778899"/>
    </Ac>
    <Creds>
      <Cred type="PIN" subType="MPIN">
        <Data code="NPCI" ki="01">q5fmuRNF2osOl785TNFAVdMaUw2THOAZyg2ss9fy5MoO3z86WDwdS1iQmhfiPBn2sVooqp35q4QFdUej2QSvUTximKmXvyx1UhZ0G7aPt5GGKStaEaV8EMHVGrC0u9atFqFmxXJzwVQYWCCdqCVplRDhpDnyeg0hJbFu8dMNeR7ut3cvDwvdvMY4F1n2RMXu7gTp0dduUk7vKndblZ3SKMkrWWLAfUP9jWDIVPZfy0ZgHTTX3xooX2NrRojvjP9e36I6j2OkCENP8B0vRgozkX36XdjxkoSBoQcltzZojKKY4VfMitCHwgpxIn86SqaSEEI8eHZqlDw</Data>
      </Cred>
      <Cred type="PREAPPROVED" subType="NA">
        <Data>UFJFQVBQUk9WRUQtREVNTy1UT0tFTi0yMDI3MDExNS0wMDAwMDAwMDAwMDAwMDAx</Data>
      </Cred>
    </Creds>
    <Amount value="999999999.99" curr="PEN">
      <Split name="CONPCT" value="PURCHASE"/>
    </Amount>
  </Payer>
  <Payees>
    <Payee addr="comercio.demo@banco-destino.test" name="COMERCIO DEMO SOCIEDAD ANONIMA CERRADA" seqNum="001" type="ENTITY" code="0001" cmId="CM-DESTINO-0000001">
      <Merchant>
        <Identifier subCode="7407" mid="MID-DESTINO-000001" sid="SID-DESTINO-000001" tid="TID-DESTINO-000001" merchantType="SMALL" merchantGenre="ONLINE" onBoardingType="BANK" pinCode="15001" regIdNo="RUC20123456789" tier="TIER1" merchantLoc="LIMA" merchantInstId="INST-DESTINO-000001"/>
        <Name brand="COMERCIO DEMO" legal="COMERCIO DEMO SOCIEDAD ANONIMA CERRADA" franchise="COMERCIO DEMO"/>
        <Ownership type="PARTNERSHIP"/>
        <Invoice name="FACTURA ELECTRONICA" num="F001-00001234" date="2027-01-15T10:30:45.521-05:00"/>
      </Merchant>
      <Info>
        <Identity id="RUC20123456789" type="RUC" verifiedName="COMERCIO DEMO SOCIEDAD ANONIMA CERRADA"/>
        <Rating VerifiedAddress="TRUE"/>
      </Info>
      <Device>
        <Tag name="MOBILE" value="51912345678"/>
        <Tag name="APP" value="CCE-RECEIVER-1.0"/>
      </Device>
      <Ac addrType="ACCOUNT">
        <Detail name="IFSC" value="000123"/>
        <Detail name="ACTYPE" value="CURRENT"/>
        <Detail name="ACNUM" value="00998877665544332211"/>
      </Ac>
      <Amount value="999999999.99" curr="PEN">
        <Split name="CONPCT" value="PURCHASE"/>
      </Amount>
    </Payee>
  </Payees>
</upi:ReqPay>"""


SIGNATURE_BLOCK = """  <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
    <ds:SignedInfo>
      <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
      <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
      <ds:Reference URI="">
        <ds:Transforms>
          <ds:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
          <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
        </ds:Transforms>
        <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
        <ds:DigestValue>RERERERERERERERERERERERERERERERERERERERERERE</ds:DigestValue>
      </ds:Reference>
    </ds:SignedInfo>
    <ds:SignatureValue>{signature_value}</ds:SignatureValue>
    <ds:KeyInfo>
      <ds:X509Data>
        <ds:X509Certificate>{certificate}</ds:X509Certificate>
      </ds:X509Data>
    </ds:KeyInfo>
  </ds:Signature>""".format(signature_value="S" * 344, certificate="C" * 1024)


@dataclass(frozen=True)
class Result:
    file: str
    message: str
    profile: str
    account_count: int | None
    signed: bool
    utf8_bytes: int
    kib: float
    minified_utf8_bytes: int
    minified_kib: float
    gzip_bytes: int
    xml_well_formed: bool


def extract_block(source: str, start_tag: str, end_tag: str) -> str:
    match = re.search(re.escape(start_tag) + r".*?" + re.escape(end_tag), source, re.DOTALL)
    if not match:
        raise ValueError(f"Could not find XML block {start_tag} ... {end_tag}")
    return match.group(0)


def account_xml(index: int) -> str:
    suffix = f"{index:06d}"
    return f"""    <Account accType="SAVINGS" accRefNumber="117795514{suffix}" maskedAccnumber="XXXXXXXXX{suffix}"
      ifsc="382" mmid="3{index:06d}" name="CLIENTE DEMO {index:03d}" aeba="N" mbeba="Y">
      <CredsAllowed dLength="6" dType="Numeric" subType="SMS" type="OTP"/>
      <CredsAllowed dLength="6" dType="Numeric" subType="MPIN" type="PIN"/>
      <CredsAllowed dLength="6" dType="Numeric" subType="ATMPIN" type="PIN"/>
    </Account>"""


def list_account_xml(count: int) -> str:
    accounts = "\n".join(account_xml(i) for i in range(1, count + 1))
    return f"""<RespListAccount>
  <Head ver="2.0" ts="2027-01-15T10:30:46.521-05:00" orgId="000805" msgId="MSG20270115000000000000000000000001" prodType="UPI"/>
  <Resp reqMsgId="MSG20270115000000000000000000000001" result="SUCCESS"/>
  <Txn id="TXN20270115000000000000000000000001" note="Consulta de cuentas CCE" refId="REF20270115000000000001" refUrl="https://api.example.test/v1/accounts" ts="2027-01-15T10:30:46.521-05:00" type="ListAccount"/>
  <AccountList>
{accounts}
  </AccountList>
</RespListAccount>"""


def add_signature(xml: str) -> str:
    closing_match = re.search(r"(?P<closing></(?:[A-Za-z_][\w.-]*:)?[A-Za-z_][\w.-]*>)\s*$", xml)
    if not closing_match:
        raise ValueError("Could not locate root closing tag")
    newline = "\r\n" if "\r\n" in xml else "\n"
    signature = SIGNATURE_BLOCK.replace("\n", newline)
    start = closing_match.start("closing")
    return xml[:start] + signature + newline + xml[start:]


def minify(xml: str) -> str:
    return re.sub(r">\s+<", "><", xml.strip())


def measure(path: Path, message: str, profile: str, count: int | None, signed: bool) -> Result:
    raw = path.read_bytes()
    text = raw.decode("utf-8")
    compact = minify(text).encode("utf-8")
    try:
        ET.fromstring(text)
        valid = True
    except ET.ParseError:
        valid = False
    return Result(
        file=path.name,
        message=message,
        profile=profile,
        account_count=count,
        signed=signed,
        utf8_bytes=len(raw),
        kib=round(len(raw) / 1024, 3),
        minified_utf8_bytes=len(compact),
        minified_kib=round(len(compact) / 1024, 3),
        gzip_bytes=len(gzip.compress(raw, compresslevel=6, mtime=0)),
        xml_well_formed=valid,
    )


def write_payload(path: Path, xml: str) -> None:
    path.write_bytes(xml.encode("utf-8"))


def build_payloads(output: Path, source_path: Path) -> tuple[list[Result], dict[str, int]]:
    source = source_path.read_bytes().decode("utf-8-sig")
    source_response = extract_block(source, "<RespListAccount>", "</RespListAccount>")
    source_max_request = extract_block(source, '<upi:ReqPay xmlns:upi="http://npci.org/upi/schema/">', "</upi:ReqPay>")

    payloads: list[tuple[str, str, str, str, int | None]] = [
        ("reqpay-realistic-unsigned.xml", REALISTIC_REQPAY, "ReqPay", "realistic", None),
        ("reqpay-conservative-unsigned.xml", CONSERVATIVE_REQPAY, "ReqPay", "conservative", None),
        ("reqpay-maximum-source-unsigned.xml", source_max_request, "ReqPay", "maximum-source", None),
        ("resp-list-account-source-5-unsigned.xml", source_response, "RespListAccount", "source", 5),
    ]
    for count in (1, 5, 7, 10, 20, 50):
        payloads.append(
            (
                f"resp-list-account-{count:02d}-unsigned.xml",
                list_account_xml(count),
                "RespListAccount",
                "generated",
                count,
            )
        )

    results: list[Result] = []
    for filename, xml, message, profile, count in payloads:
        unsigned_path = output / filename
        write_payload(unsigned_path, xml)
        results.append(measure(unsigned_path, message, profile, count, False))

        signed_filename = filename.replace("-unsigned.xml", "-signed.xml")
        signed_path = output / signed_filename
        write_payload(signed_path, add_signature(xml))
        results.append(measure(signed_path, message, profile, count, True))

    thresholds: dict[str, int] = {}
    for signed in (False, True):
        for count in range(1, 101):
            xml = list_account_xml(count)
            if signed:
                xml = add_signature(xml)
            if len(xml.encode("utf-8")) > 3 * 1024:
                thresholds["signed" if signed else "unsigned"] = count
                break
    return results, thresholds


def write_results(output: Path, results: list[Result], thresholds: dict[str, int], source: Path) -> None:
    columns = list(asdict(results[0]).keys())
    with (output / "payload-size-results.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        for result in results:
            writer.writerow(asdict(result))

    data = {
        "methodology": {
            "encoding": "UTF-8 without BOM",
            "uncompressed_unit": "bytes and KiB (1 KiB = 1024 bytes)",
            "minification": "Only whitespace between XML tags is removed",
            "gzip": "gzip level 6, reported only as additional evidence",
            "signature_warning": "Dummy XMLDSig values are not cryptographically valid",
            "source_sample": str(source),
        },
        "first_account_count_over_3_kib": thresholds,
        "results": [asdict(result) for result in results],
    }
    (output / "payload-size-results.json").write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


def phase_for(year: int) -> int:
    if year <= 2028:
        return 1
    if year <= 2030:
        return 2
    return 3


def write_sizing_projection(output: Path) -> None:
    seconds_per_year = 365 * 24 * 60 * 60
    rows: list[dict[str, float | int | str]] = []
    for scenario, years in VOLUME_PROJECTION.items():
        for year, (monthly_mm, annual_mm) in years.items():
            average_tps = annual_mm * 1_000_000 / seconds_per_year
            peak_tps = average_tps * 10
            compact_data_gb = annual_mm * 3.0
            rows.append(
                {
                    "scenario": scenario,
                    "phase": phase_for(year),
                    "year": year,
                    "monthly_transfers_mm": monthly_mm,
                    "annual_transactions_mm": annual_mm,
                    "average_transactions_s": round(average_tps, 4),
                    "peak_transactions_s_10x": round(peak_tps, 4),
                    "average_mongo_writes_s_4_events": round(average_tps * 4, 4),
                    "peak_mongo_writes_s_4_events_10x": round(peak_tps * 4, 4),
                    "compact_3kb_data_gb": round(compact_data_gb, 2),
                    "compact_3kb_plus_20pct_indexes_plus_30pct_headroom_gb_per_node": round(
                        compact_data_gb * 1.20 * 1.30, 2
                    ),
                    "three_data_nodes_aggregate_gb": round(compact_data_gb * 1.20 * 1.30 * 3, 2),
                }
            )

    with (output / "sizing-projection-2027-2032.csv").open(
        "w", encoding="utf-8", newline=""
    ) as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    (output / "sizing-projection-2027-2032.json").write_text(
        json.dumps(
            {
                "assumptions": {
                    "retention_days": 365,
                    "document_size_bytes": 3000,
                    "events_per_transaction": 4,
                    "provisional_peak_factor": 10,
                    "index_allowance_percent": 20,
                    "operational_headroom_percent": 30,
                    "replica_set_data_nodes": 3,
                    "storage_unit": "decimal GB",
                    "warning": "Compression, oplog and backups are not deducted or added; validate with platform metrics.",
                },
                "rows": rows,
            },
            indent=2,
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    results, thresholds = build_payloads(args.output, args.source)
    write_results(args.output, results, thresholds, args.source)
    write_sizing_projection(args.output)

    print(f"Generated {len(results)} XML measurements in {args.output}")
    print(f"First unsigned ListAccount over 3 KiB: {thresholds['unsigned']} accounts")
    print(f"First signed ListAccount over 3 KiB: {thresholds['signed']} accounts")
    for result in results:
        if result.message == "ReqPay" or result.account_count in (1, 5, 10, 20, 50):
            print(
                f"{result.file}: {result.utf8_bytes} bytes ({result.kib:.3f} KiB), "
                f"well-formed={result.xml_well_formed}"
            )


if __name__ == "__main__":
    main()
