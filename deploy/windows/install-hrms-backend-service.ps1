<#
.SYNOPSIS
  Installs the JCI HRMS backend (hrms-backend) as a genuine, auto-restarting Windows service using
  NSSM (Non-Sucking Service Manager), so it survives reboots and terminal/SSH-session closures without
  a developer manually running `mvn spring-boot:run` every morning.

.DESCRIPTION
  This addresses the recurring ERR_CONNECTION_REFUSED failure: the backend was previously started
  manually (per CLAUDE.md, `mvn spring-boot:run`) in a terminal/IDE session that does not survive a
  reboot or the session closing. A plain `java -jar` process has no OS-level supervisor of its own, so
  if it crashes (OOM, uncaught exception, DB briefly unavailable at boot) nothing brings it back - this
  script wraps it in a real Windows service with NSSM's own crash-restart behavior, which is the
  Windows-host equivalent of what the AWS `dev` environment already gets for free from ECS Fargate
  (desired_count=1 + ALB health check - see infra/main.tf).

.NOTES
  Discover-before-run, not fill-in-the-blanks: this script infers the JAR name and version from THIS
  repo's pom.xml at run time rather than hardcoding it, so it stays correct as the version bumps. It
  still requires you to supply the two things genuinely specific to the target machine (NSSM's install
  path, and which Java to run) - there is no way to discover those from the repository alone.

  Run this AS ADMINISTRATOR, from the machine that will actually run the backend, after building the
  jar (`mvn -B clean package -DskipTests` from backend/, or the full `mvn -B clean verify`).

.PARAMETER RepoRoot
  Path to the jci-hrms-dev-env repo root on THIS machine. Defaults to the parent of this script's own
  deploy\windows directory, which is correct when you run it from a checkout of this repo.

.PARAMETER JavaExe
  Full path to java.exe (Java 21, matching pom.xml's <java.version>). Defaults to whatever `java` resolves
  to on PATH - pass an explicit path if this machine has more than one JDK installed.

.PARAMETER NssmExe
  Full path to nssm.exe. Download from https://nssm.cc/download (there is no NSSM entry in winget/choco
  that's guaranteed current - verify the checksum against the project's own site). This script does not
  install NSSM itself; it only orchestrates it once it's present on this machine.

.PARAMETER EnvFile
  Path to a Windows-native environment-variables file (KEY=VALUE per line, no quoting) that NSSM will
  inject into the service process - see hrms-backend.env.example in this same directory for the required
  keys (datasource credentials, JWT config, SERVER_SSL_ENABLED). Never commit the real file - it holds
  the database password.

.EXAMPLE
  .\install-hrms-backend-service.ps1 -NssmExe 'C:\nssm\nssm.exe' -EnvFile 'C:\hrms\hrms-backend.env'
#>
[CmdletBinding()]
param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')),
    [string]$JavaExe = (Get-Command java -ErrorAction Stop).Source,
    [Parameter(Mandatory = $true)][string]$NssmExe,
    [Parameter(Mandatory = $true)][string]$EnvFile,
    [string]$ServiceName = 'JciHrmsBackend',
    [string]$SpringProfile = 'default'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $NssmExe)) { throw "NSSM not found at '$NssmExe' - download it from https://nssm.cc/download first." }
if (-not (Test-Path $EnvFile)) { throw "Env file not found at '$EnvFile' - copy hrms-backend.env.example and fill in real values first." }

$backendDir = Join-Path $RepoRoot 'backend'
$pomPath = Join-Path $backendDir 'pom.xml'
if (-not (Test-Path $pomPath)) { throw "backend\pom.xml not found under '$RepoRoot' - pass -RepoRoot explicitly if this isn't a checkout of jci-hrms-dev-env." }

# Discovered, not hardcoded: artifactId/version come from the actual pom.xml on this checkout, so this
# script keeps working across version bumps without editing it.
[xml]$pom = Get-Content $pomPath
$artifactId = $pom.project.artifactId
$version = $pom.project.version
$jarPath = Join-Path $backendDir "target\$artifactId-$version.jar"

if (-not (Test-Path $jarPath)) {
    throw "Jar not found at '$jarPath' - build it first: cd backend; mvn -B clean package -DskipTests"
}

Write-Host "Installing Windows service '$ServiceName'"
Write-Host "  Java:    $JavaExe"
Write-Host "  Jar:     $jarPath"
Write-Host "  Profile: $SpringProfile"
Write-Host "  EnvFile: $EnvFile"

& $NssmExe install $ServiceName $JavaExe
& $NssmExe set $ServiceName AppParameters "-jar `"$jarPath`" --spring.profiles.active=$SpringProfile"
& $NssmExe set $ServiceName AppDirectory $backendDir
& $NssmExe set $ServiceName AppEnvironmentExtra "@$EnvFile"

# Crash recovery (Phase 20.B of the closure task): restart on any non-zero-or-unexpected exit, with a
# short back-off so a persistently-crashing process (e.g. DB genuinely down) doesn't spin the CPU, but
# still comes back automatically once the underlying condition clears - matching ECS Fargate's own
# desired_count restart behavior for the AWS-deployed environment.
& $NssmExe set $ServiceName AppExit Default Restart
& $NssmExe set $ServiceName AppRestartDelay 5000
& $NssmExe set $ServiceName AppThrottle 1500

# Auto-start after reboot with no developer/administrator action required.
& $NssmExe set $ServiceName Start SERVICE_AUTO_START

# Persistent logs survive a crash even though the process's own console does not - see
# docs/DEPLOYMENT.md's Logging section for rotation guidance (NSSM's own log rotation is size-based
# and configured here; combine with the JSON/file appender in backend logback-spring.xml for structured
# logs).
$logDir = Join-Path $RepoRoot 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
& $NssmExe set $ServiceName AppStdout (Join-Path $logDir 'hrms-backend.out.log')
& $NssmExe set $ServiceName AppStderr (Join-Path $logDir 'hrms-backend.err.log')
& $NssmExe set $ServiceName AppRotateFiles 1
& $NssmExe set $ServiceName AppRotateOnline 1
& $NssmExe set $ServiceName AppRotateSeconds 86400
& $NssmExe set $ServiceName AppRotateBytes 10485760

Write-Host ""
Write-Host "Service installed but not yet running. Verify, then start it:"
Write-Host "  Get-Service $ServiceName"
Write-Host "  Start-Service $ServiceName"
Write-Host "  Get-Service $ServiceName   # should show 'Running'"
Write-Host "  Get-Content '$logDir\hrms-backend.out.log' -Tail 50 -Wait"
Write-Host ""
Write-Host "To uninstall: nssm remove $ServiceName confirm"
