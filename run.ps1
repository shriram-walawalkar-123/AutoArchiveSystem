# Build (if needed) and start the app with JDK 21.
Set-Location $PSScriptRoot

$java = "C:\Program Files\Java\jdk-21\bin\java.exe"
$jar = "autoarchive-app\target\autoarchive-app-0.1.0-SNAPSHOT.jar"

if (-not (Test-Path $java)) {
    Write-Error "JDK 21 not found at: $java"
    exit 1
}

if (-not (Test-Path $jar)) {
    Write-Host "Building jar..."
    & "$PSScriptRoot\mvn.ps1" -pl autoarchive-app -am package -DskipTests -q
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

& $java -jar $jar @args
