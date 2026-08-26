$ErrorActionPreference = 'Stop'

$repository = $PSScriptRoot
Push-Location $repository
try {
    & .\gradlew.bat test remapJar
    if ($LASTEXITCODE -ne 0) {
        throw 'Tests or remapped-jar build failed; the running game was not injected.'
    }
    $jar = Join-Path $repository 'build\libs\nerv-printer-1.21.11.jar'
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash
    Write-Output "Hot-reload build ready: $jar"
    Write-Output "SHA-256: $hash"
    Write-Output 'The running agent will inject the stable jar automatically.'
} finally {
    Pop-Location
}
