# Creates credentials only for this local Docker Compose project.
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$target = Join-Path $repo '.env'

function New-LocalPassword {
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes)
    } finally {
        $generator.Dispose()
    }
}

if (Test-Path -LiteralPath $target) {
    Write-Host 'Local .env already exists. Keeping its credentials.'
    return
}

$lines = @(
    "VERIFI_DB_PASSWORD=$(New-LocalPassword)"
    'VERIFI_DEMO_ENABLED=true'
    'VERIFI_DEMO_EMAIL=operator@example.test'
    "VERIFI_DEMO_PASSWORD=$(New-LocalPassword)"
    'VERIFI_WEB_PORT=5180'
    'VERIFI_DB_PORT=55432'
)

# CreateNew prevents a second simultaneous invocation from replacing credentials.
$stream = [IO.File]::Open($target, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
try {
    $bytes = [Text.Encoding]::UTF8.GetBytes(($lines -join [char]10) + [char]10)
    $stream.Write($bytes, 0, $bytes.Length)
} finally {
    $stream.Dispose()
}
Write-Host 'Created .env for local development. Use VERIFI_DEMO_EMAIL and VERIFI_DEMO_PASSWORD to sign in.'
