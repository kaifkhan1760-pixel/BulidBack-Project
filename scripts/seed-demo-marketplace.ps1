<#
  Fills BuildBack with demo SELLER accounts and listings for presentations,
  through the real API (validation, CSRF and ownership rules all apply).

  - Data comes from scripts\demo-data\marketplace.json (12 sellers, ~50 listings,
    free-licence Unsplash photos linked by URL).
  - Demo sellers use the reserved "demo.buildback.test" email domain.
  - One random password is generated on the first run and kept in
    scripts\demo-sellers.local (git-ignored, never printed). Later runs reuse it.
  - Safe to run again: existing sellers are logged into, and listings whose
    title already exists for that seller are skipped. Nothing is deleted.

  Usage (backend running):
      powershell -ExecutionPolicy Bypass -File scripts\seed-demo-marketplace.ps1
  Optional: -ApiBase http://localhost:8081/api
#>
param([string] $ApiBase = "http://localhost:8081/api")

$ErrorActionPreference = "Stop"
$dataFile = Join-Path $PSScriptRoot "demo-data\marketplace.json"
$secretFile = Join-Path $PSScriptRoot "demo-sellers.local"
$data = Get-Content $dataFile -Raw -Encoding UTF8 | ConvertFrom-Json

if (Test-Path $secretFile) {
    $password = (Get-Content $secretFile -Raw).Trim()
} else {
    $chars = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    # Same rule as the register form: upper case, lower case and a number
    do {
        $bytes = New-Object byte[] 20
        $random.GetBytes($bytes)
        $password = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
    } until ($password -cmatch '[A-Z]' -and $password -cmatch '[a-z]' -and $password -match '\d')
    Set-Content -Path $secretFile -Value $password -NoNewline -Encoding ASCII
    Write-Host "Generated a demo-seller password and saved it to scripts\demo-sellers.local"
}

function Get-Csrf($session) {
    $csrf = Invoke-RestMethod -Uri "$ApiBase/auth/csrf" -WebSession $session
    return @{ $csrf.headerName = $csrf.token }
}

function Get-StatusCode($errorRecord) {
    if ($errorRecord.Exception.Response) { return [int]$errorRecord.Exception.Response.StatusCode }
    return 0
}

$created = 0; $skipped = 0
foreach ($seller in $data.sellers) {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

    try {
        # 9000000000 is a placeholder mobile number for demo accounts, not a real contact
        $body = @{ name = $seller.name; email = $seller.email; phone = "9000000000"; password = $password; role = "SELLER" } | ConvertTo-Json
        Invoke-RestMethod -Uri "$ApiBase/auth/register" -Method Post -WebSession $session -Headers (Get-Csrf $session) `
            -ContentType "application/json" -Body $body | Out-Null
        Write-Host "Registered seller: $($seller.name)"
    } catch {
        if ((Get-StatusCode $_) -ne 409) { throw }
        Write-Host "Seller already exists: $($seller.name)"
    }

    Invoke-RestMethod -Uri "$ApiBase/auth/login" -Method Post -WebSession $session -Headers (Get-Csrf $session) `
        -Body @{ email = $seller.email; password = $password } | Out-Null

    $existing = (Invoke-RestMethod -Uri "$ApiBase/seller/materials?size=50" -WebSession $session).content |
        ForEach-Object { $_.title }

    foreach ($item in $seller.listings) {
        if ($existing -contains $item.title) { $skipped++; continue }
        $listing = [ordered]@{
            title = $item.title; description = $item.description; category = $item.category
            unitPrice = $item.unitPrice; availableQuantity = $item.availableQuantity; unit = $item.unit
            city = $seller.city
            imageUrls = @("https://images.unsplash.com/photo-$($item.image)?auto=format&fit=crop&w=1200&q=80")
        }
        if ($item.tileLengthFeet) {
            $listing.tileLengthFeet = $item.tileLengthFeet
            $listing.tileWidthFeet = $item.tileWidthFeet
        }
        $json = $listing | ConvertTo-Json -Depth 3
        Invoke-RestMethod -Uri "$ApiBase/seller/materials" -Method Post -WebSession $session -Headers (Get-Csrf $session) `
            -ContentType "application/json; charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($json)) | Out-Null
        $created++
    }

    Invoke-RestMethod -Uri "$ApiBase/auth/logout" -Method Post -WebSession $session -Headers (Get-Csrf $session) | Out-Null
}

$password = $null
Write-Host "Done. Created $created listings, skipped $skipped that already existed."
