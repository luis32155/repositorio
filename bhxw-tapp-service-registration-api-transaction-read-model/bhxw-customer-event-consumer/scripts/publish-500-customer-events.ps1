[CmdletBinding()]
param(
    [ValidateRange(1, 100000)]
    [int] $Count = 500,
    [ValidateRange(10, 3600)]
    [int] $TimeoutSeconds = 300,
    [switch] $SkipBuild,
    [switch] $SkipComposeUp
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectDirectory = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $ProjectDirectory "docker-compose.yml"
$GradleWrapper = Join-Path $ProjectDirectory "gradlew.bat"
$KafkaContainer = "customer-kafka"
$PostgresContainer = "customer-postgres"
$KafkaServer = "localhost:29092"
$Topic = "bhxw.tapp.customer.cdc.v1"
$Database = "customer_event_consumer"
$DatabaseUser = "customer_consumer"
$BatchId = [Guid]::NewGuid().ToString()
$StartedAt = [DateTimeOffset]::UtcNow

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory)]
        [scriptblock] $Command,
        [Parameter(Mandatory)]
        [string] $FailureMessage
    )
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw $FailureMessage
    }
}

function Invoke-PostgresScalar {
    param([Parameter(Mandatory)][string] $Sql)
    $Result = docker exec $PostgresContainer psql --username $DatabaseUser --dbname $Database --tuples-only --no-align --command $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL query failed."
    }
    return ($Result | Select-Object -Last 1).Trim()
}

function Wait-Until {
    param(
        [Parameter(Mandatory)][scriptblock] $Condition,
        [Parameter(Mandatory)][string] $Description
    )
    $Deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if (& $Condition) {
            return
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $Deadline)
    throw "Timeout waiting for $Description."
}

if (-not $SkipBuild) {
    Push-Location $ProjectDirectory
    try {
        Invoke-NativeCommand -Command { & $GradleWrapper bootJar } -FailureMessage "Gradle bootJar failed."
    }
    finally {
        Pop-Location
    }
}

if (-not $SkipComposeUp) {
    Invoke-NativeCommand -Command { docker compose --file $ComposeFile up --detach --build } -FailureMessage "Docker Compose could not start the local stack."
}

Wait-Until -Description "PostgreSQL table cdc_customer.customer_identity" -Condition {
    try {
        $Table = Invoke-PostgresScalar "SELECT to_regclass('cdc_customer.customer_identity');"
        return $Table -eq "cdc_customer.customer_identity"
    }
    catch {
        return $false
    }
}

Wait-Until -Description "Kafka topic $Topic" -Condition {
    $Topics = docker exec $KafkaContainer /opt/kafka/bin/kafka-topics.sh --bootstrap-server $KafkaServer --list 2>$null
    return $LASTEXITCODE -eq 0 -and $Topics -contains $Topic
}

$Records = [System.Text.StringBuilder]::new()
$KeySeparator = [char]9

for ($Index = 1; $Index -le $Count; $Index++) {
    $DocumentNumber = (10000000 + $Index).ToString()
    $PrimaryAccount = (100000000 + ($Index * 3)).ToString()
    $SavingsAccount = (100000001 + ($Index * 3)).ToString()
    $IntegratedAccount = (100000002 + ($Index * 3)).ToString()
    $EventTime = $StartedAt.AddMilliseconds($Index).ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    $EventId = [Guid]::NewGuid().ToString()

    $Identity = [ordered]@{
        country_code = "604"
        document_type = "01"
        document_number = $DocumentNumber
        customer_type = "I"
        person_name = "CUSTOMER $Index"
    }
    $Individual = [ordered]@{
        country_code = "604"
        document_type = "01"
        document_number = $DocumentNumber
        first_last_name = "LASTNAME $Index"
        second_last_name = "SECOND"
        first_given_name = "CUSTOMER"
        second_given_name = "$Index"
        birth_date = "1990-01-15"
        marital_status = "1"
        gender = "1"
    }
    $Accounts = @(
        [ordered]@{
            company_code = "001"
            account_number = $PrimaryAccount
            account_officer_code = "101"
            account_name = "OPERATING $Index"
            opened_date = "2020-01-15"
            sector_code = "100"
        },
        [ordered]@{
            company_code = "001"
            account_number = $SavingsAccount
            account_name = "SAVINGS $Index"
            opened_date = "2021-02-20"
        },
        [ordered]@{
            company_code = "001"
            account_number = $IntegratedAccount
            account_name = "INTEGRATED $Index"
            opened_date = "2019-01-01"
        }
    )
    $Links = @(
        [ordered]@{
            company_code = "001"
            account_number = $PrimaryAccount
            country_code = "604"
            document_type = "01"
            document_number = $DocumentNumber
        },
        [ordered]@{
            company_code = "001"
            account_number = $SavingsAccount
            country_code = "604"
            document_type = "01"
            document_number = $DocumentNumber
        }
    )

    $Event = [ordered]@{
        payload = [ordered]@{
            snapshot_mode = "FULL"
            batch_id = $BatchId
            customer_identity = $Identity
            individual_profile = $Individual
            legal_entity_profile = $null
            digital_contacts = @(
                [ordered]@{
                    country_code = "604"
                    document_type = "01"
                    document_number = $DocumentNumber
                    contact_type = "1"
                    contact_value = "customer.$Index@example.com"
                    validation_status = "1"
                }
            )
            account_profiles = $Accounts
            customer_account_links = $Links
            account_consolidations = @(
                [ordered]@{
                    relationship_code = "001"
                    integrated_company_code = "001"
                    integrated_account_number = $IntegratedAccount
                    source_company_code = "001"
                    source_account_number = $PrimaryAccount
                }
            )
        }
        metadata = [ordered]@{
            source_id = "BHXW"
            channel = "TAPP"
            event_id = $EventId
            event_timestamp = $EventTime
            source_sequence = $Index.ToString()
            trace_id = $EventId
            correlation_id = $EventId
        }
    }

    $Json = $Event | ConvertTo-Json -Depth 20 -Compress
    $KafkaKey = "604|01|$DocumentNumber"
    [void] $Records.Append($KafkaKey)
    [void] $Records.Append($KeySeparator)
    [void] $Records.AppendLine($Json)
}

Write-Host "Publishing $Count records to $Topic. Batch: $BatchId"

# Prevent an extra empty line from being parsed as a Kafka record without a key.
$InputRecords = $Records.ToString().TrimEnd([char]13, [char]10)
$InputRecords | docker exec -i $KafkaContainer /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server $KafkaServer --topic $Topic --reader-property parse.key=true --reader-property "key.separator=$KeySeparator"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to publish the Kafka batch."
}

$EscapedBatchId = $BatchId.Replace("'", "''")
$ProcessedSql = @"
SELECT count(*)
FROM cdc_customer.customer_identity
WHERE raw_payload->>'batch_id' = '$EscapedBatchId';
"@

Wait-Until -Description "$Count customer snapshots in PostgreSQL" -Condition {
    $Processed = [int] (Invoke-PostgresScalar $ProcessedSql)
    Write-Progress -Activity "Consuming customer snapshots" -Status "$Processed of $Count" -PercentComplete ([Math]::Min(100, ($Processed * 100 / $Count)))
    return $Processed -eq $Count
}

Write-Progress -Activity "Consuming customer snapshots" -Completed

$ValidationSql = @"
SELECT json_build_object(
  'batch_id', '$EscapedBatchId',
  'customer_identity', (
    SELECT count(*) FROM cdc_customer.customer_identity
    WHERE raw_payload->>'batch_id' = '$EscapedBatchId'
  ),
  'customer_individual', (
    SELECT count(*) FROM cdc_customer.customer_individual i
    JOIN cdc_customer.customer_identity c USING
      (source_id, country_code, document_type, document_number)
    WHERE c.raw_payload->>'batch_id' = '$EscapedBatchId'
  ),
  'digital_contacts', (
    SELECT count(*) FROM cdc_customer.customer_digital_contact d
    JOIN cdc_customer.customer_identity c USING
      (source_id, country_code, document_type, document_number)
    WHERE c.raw_payload->>'batch_id' = '$EscapedBatchId'
  ),
  'account_links', (
    SELECT count(*) FROM cdc_customer.customer_account_link l
    JOIN cdc_customer.customer_identity c USING
      (source_id, country_code, document_type, document_number)
    WHERE c.raw_payload->>'batch_id' = '$EscapedBatchId'
  )
);
"@

$Summary = Invoke-PostgresScalar $ValidationSql
$Statistics = $Summary | ConvertFrom-Json
if (
    [int] $Statistics.customer_identity -ne $Count -or
    [int] $Statistics.customer_individual -ne $Count -or
    [int] $Statistics.digital_contacts -ne $Count -or
    [int] $Statistics.account_links -ne ($Count * 2)
) {
    throw "Unexpected PostgreSQL totals for batch ${BatchId}: $Summary"
}

Write-Host "End-to-end test completed successfully."
Write-Host $Summary
