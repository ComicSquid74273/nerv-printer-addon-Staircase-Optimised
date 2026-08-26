$ErrorActionPreference = 'Stop'

$agentRoot = $PSScriptRoot
$sourceRoot = Join-Path $agentRoot 'src'
$classesRoot = Join-Path $agentRoot 'build\classes'
$outputJar = Join-Path $agentRoot 'build\nerv-live-reload-agent.jar'
$manifest = Join-Path $agentRoot 'MANIFEST.MF'
$source = Join-Path $sourceRoot 'com\julflips\nerv_printer\dev\NervLiveReloadAgent.java'

New-Item -ItemType Directory -Force -Path $classesRoot | Out-Null
& javac --release 21 -d $classesRoot $source
if ($LASTEXITCODE -ne 0) { throw 'javac failed while building the live-reload agent' }
& jar --create --file $outputJar --manifest $manifest -C $classesRoot .
if ($LASTEXITCODE -ne 0) { throw 'jar failed while packaging the live-reload agent' }
Write-Output $outputJar
