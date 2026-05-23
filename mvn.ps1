# Uses JDK 21 for this project even if system JAVA_HOME is still Java 17.
$jdk21 = "C:\Program Files\Java\jdk-21"
if (-not (Test-Path "$jdk21\bin\java.exe")) {
    Write-Error "JDK 21 not found at: $jdk21"
    exit 1
}
$env:JAVA_HOME = $jdk21
$env:Path = "$env:JAVA_HOME\bin;" + (($env:Path -split ';') | Where-Object {
        $_ -and $_ -notmatch 'jdk-17|jdk1\.8|Eclipse Adoptium\\jdk-17'
    }) -join ';'
$mvn = "C:\Program Files\apache-maven-3.9.15\bin\mvn.cmd"
if (-not (Test-Path $mvn)) {
    $mvn = "mvn"
}
& $mvn @args
exit $LASTEXITCODE
