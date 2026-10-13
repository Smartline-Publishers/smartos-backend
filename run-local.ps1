<#
.SYNOPSIS
  Loads .env into process env, validates it, and runs the SmartOS backend.
.DESCRIPTION
  Spring Boot reads process environment variables, not .env files, so this
  script bridges the gap: it parses KEY=VALUE lines (skipping blanks and
  #-comments, stripping optional surrounding quotes), requires the keys the
  API needs, sanity-checks the JWT key strength the same way JwtService does,
  then launches .\mvnw.cmd spring-boot:run. Secret values are never printed.
.EXAMPLE
  .\run-local.ps1
.EXAMPLE
  .\run-local.ps1 -NoRun            # load + validate only
.EXAMPLE
  .\run-local.ps1 -- -Dspring-boot.run.profiles=dev   # extra args to Maven
#>
[CmdletBinding()]
param(
  [string]$EnvFile = ".env",
  [switch]$NoRun,
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$MvnArgs = @()
)

$ErrorActionPreference = "Stop"

function Resolve-EnvPath([string]$p) {
  if ([System.IO.Path]::IsPathRooted($p)) { return $p }
  return (Join-Path $PSScriptRoot $p)
}

function Import-DotEnv([string]$path) {
  $loaded = 0
  foreach ($line in [System.IO.File]::ReadAllLines($path)) {
    $t = $line.Trim()
    if ($t.Length -eq 0 -or $t.StartsWith("#")) { continue }
    $i = $t.IndexOf("=")
    if ($i -le 0) { continue }
    $k = $t.Substring(0, $i).Trim()
    $v = $t.Substring($i + 1).Trim()
    if ($v.Length -ge 2 -and (($v.StartsWith('"') -and $v.EndsWith('"')) -or ($v.StartsWith("'") -and $v.EndsWith("'")))) {
      $v = $v.Substring(1, $v.Length - 2)
    }
    [System.Environment]::SetEnvironmentVariable($k, $v, "Process")
    $loaded++
  }
  return $loaded
}

function Get-JwtKeyBytes([string]$s) {
  try {
    return [Convert]::FromBase64String($s)
  } catch {
  }
  try {
    $p = $s.Replace("-", "+").Replace("_", "/")
    while (($p.Length % 4) -ne 0) { $p += "=" }
    return [Convert]::FromBase64String($p)
  } catch {
  }
  return [System.Text.Encoding]::UTF8.GetBytes($s)
}

$envPath = Resolve-EnvPath $EnvFile
if (-not (Test-Path -LiteralPath $envPath)) {
  Write-Error "Env file not found: $envPath. Create a local .env (never committed) - see README Prerequisites for the required variables."
  exit 1
}

$count = Import-DotEnv $envPath
Write-Host "Loaded $count variables from $envPath."

$missing = @("DB_USER", "DB_PASSWORD", "JWT_SECRET") | Where-Object {
  [string]::IsNullOrWhiteSpace([System.Environment]::GetEnvironmentVariable($_))
}
if ($missing.Count -gt 0) {
  Write-Error ("Missing required values in {0}: {1}" -f $envPath, ($missing -join ", "))
  exit 1
}

$jwt = [System.Environment]::GetEnvironmentVariable("JWT_SECRET")
if ($jwt -like "*REPLACE_WITH*" -or $jwt -like "*change-me*") {
  Write-Warning "JWT_SECRET still looks like a placeholder. Generate one: openssl rand -base64 48"
}
$bytes = (Get-JwtKeyBytes $jwt).Length
if ($bytes -lt 32) {
  Write-Error ("JWT_SECRET decodes to {0} bytes; at least 32 are required. The backend would refuse to start." -f $bytes)
  exit 1
}
Write-Host ("JWT key strength OK ({0} bytes)." -f $bytes)

if ($NoRun) {
  Write-Host "Check only (-NoRun): environment is valid, Maven not started."
  exit 0
}

& (Join-Path $PSScriptRoot "mvnw.cmd") spring-boot:run @MvnArgs
