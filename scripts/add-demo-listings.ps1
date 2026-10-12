<#
  Adds clearly labelled DEMO listings for an EXISTING seller account, through the
  real BuildBack API (so validation, CSRF and ownership rules all apply).
  Nothing is hard-coded: you register the seller in the app first, and this
  script asks for that seller's password (it is never stored or printed).

  Usage (backend running):
      powershell -ExecutionPolicy Bypass -File scripts\add-demo-listings.ps1 -Email seller@example.com
  Optional: -ApiBase http://localhost:8081/api
#>
param(
    [Parameter(Mandatory = $true)] [string] $Email,
    [string] $ApiBase = "http://localhost:8081/api"
)

$ErrorActionPreference = "Stop"
$secure = Read-Host "Password for $Email" -AsSecureString
$password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

# One web session = one cookie jar (JSESSIONID), exactly like a browser
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Get-Csrf {
    $csrf = Invoke-RestMethod -Uri "$ApiBase/auth/csrf" -WebSession $session
    return @{ $csrf.headerName = $csrf.token }
}

# Log in (form fields, like the React login page)
Invoke-RestMethod -Uri "$ApiBase/auth/login" -Method Post -WebSession $session -Headers (Get-Csrf) `
    -Body @{ email = $Email; password = $password } | Out-Null
$password = $null
Write-Host "Logged in as $Email"

$listings = @(
    @{ title = "[Demo] Ivory vitrified floor tiles 2x2 ft"; description = "35 unused glossy vitrified tiles left after a flat renovation. Proposal demo listing.";
       category = "TILES"; unitPrice = "45.00"; availableQuantity = 35; unit = "PIECE"; city = "Pune"; tileLengthFeet = "2.00"; tileWidthFeet = "2.00"; imageUrls = @("https://images.unsplash.com/photo-1600328604921-300918f36018?auto=format&fit=crop&w=1200&q=80") },
    @{ title = "[Demo] Matte ceramic wall tiles 1x1.5 ft"; description = "Matte white bathroom wall tiles, 6 boxes, no chips.";
       category = "TILES"; unitPrice = "28.50"; availableQuantity = 90; unit = "PIECE"; city = "Mumbai"; tileLengthFeet = "1.50"; tileWidthFeet = "1.00"; imageUrls = @("https://images.unsplash.com/photo-1580398562556-d33329a0f29b?auto=format&fit=crop&w=1200&q=80") },
    @{ title = "[Demo] Interior emulsion paint 20 L"; description = "Sealed 20 litre cans of white interior emulsion. Price is per can.";
       category = "PAINT"; unitPrice = "3200.00"; availableQuantity = 3; unit = "CAN"; city = "Pune"; imageUrls = @("https://images.unsplash.com/photo-1599619585752-c3edb42a414c?auto=format&fit=crop&w=1200&q=80") },
    @{ title = "[Demo] Marine plywood 18 mm (8x4 ft)"; description = "BWP marine grade plywood sheets, 8x4 ft, 18 mm thick.";
       category = "PLYWOOD"; unitPrice = "3150.00"; availableQuantity = 12; unit = "SHEET"; city = "Pune"; imageUrls = @("https://images.unsplash.com/photo-1690768162439-7ca7a1813038?auto=format&fit=crop&w=1200&q=80") },
    @{ title = "[Demo] Brass pillar taps"; description = "Brand new boxed brass taps with chrome finish.";
       category = "FITTINGS"; unitPrice = "780.00"; availableQuantity = 6; unit = "PIECE"; city = "Nashik"; imageUrls = @("https://images.unsplash.com/photo-1619365566184-272a34acfeb9?auto=format&fit=crop&w=1200&q=80") }
)

foreach ($listing in $listings) {
    $created = Invoke-RestMethod -Uri "$ApiBase/seller/materials" -Method Post -WebSession $session `
        -Headers (Get-Csrf) -ContentType "application/json" -Body ($listing | ConvertTo-Json -Depth 3)
    Write-Host ("Created #{0}: {1}" -f $created.id, $created.title)
}

Invoke-RestMethod -Uri "$ApiBase/auth/logout" -Method Post -WebSession $session -Headers (Get-Csrf) | Out-Null
Write-Host "Done. Listings are marked [Demo]; archive them from 'My materials' after the presentation."
