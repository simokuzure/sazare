#requires -Version 7.4
[CmdletBinding()]
param(
    [switch] $PauseOnError
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$processes = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
$lock = $null
$exitCode = 0
$logDirectory = Join-Path $PSScriptRoot ".codex-tmp/start/$(Get-Date -Format 'yyyyMMdd-HHmmss')-$PID"

function ConvertTo-PowerShellLiteral([string] $Value) {
    return "'" + $Value.Replace("'", "''") + "'"
}

function Read-BackendEnvironment([string] $Path) {
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $values }

    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
        $lineNumber++
        $line = $line.Trim()
        if (-not $line -or $line.StartsWith('#')) { continue }
        if ($line -notmatch '^(?<name>[A-Za-z_][A-Za-z0-9_]*)\s*=(?<value>.*)$') {
            throw ".env 第 $lineNumber 行格式错误，请使用 NAME=value。"
        }
        $name = $Matches.name
        $value = $Matches.value.Trim()
        if ($name -notmatch '^(AI_|OPENAI_|GOOGLE_AI_)') {
            throw ".env 第 $lineNumber 行不是受支持的 AI 配置项。"
        }
        if ($value.StartsWith('"') -or $value.StartsWith("'")) {
            if ($value.Length -lt 2 -or $value[-1] -ne $value[0]) {
                throw ".env 第 $lineNumber 行的引号未闭合。"
            }
            $value = $value.Substring(1, $value.Length - 2)
        }
        # 已在终端设置的环境变量优先；文件内容只作为字面值读取。
        if ($null -eq [Environment]::GetEnvironmentVariable($name, 'Process')) {
            $values[$name] = $value
        }
    }
    return $values
}

function Start-LoggedProcess {
    param(
        [string] $Name,
        [string] $FilePath,
        [string[]] $Arguments = @(),
        [string] $WorkingDirectory = $PSScriptRoot,
        [hashtable] $Environment = @{},
        [string] $InputPath
    )

    $command = '& ' + ((@($FilePath) + $Arguments | ForEach-Object {
        ConvertTo-PowerShellLiteral $_
    }) -join ' ')
    if ($InputPath) {
        $command = 'Get-Content -Raw -Encoding utf8 -LiteralPath ' +
            (ConvertTo-PowerShellLiteral $InputPath) + ' | ' + $command
    }
    $command = '$ErrorActionPreference = ''Stop''; ' +
        '$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false); ' + $command + '; exit $LASTEXITCODE'
    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
    $process = Start-Process -FilePath (Join-Path $PSHOME 'pwsh.exe') -ArgumentList @(
        '-NoLogo', '-NoProfile', '-NonInteractive', '-EncodedCommand', $encodedCommand
    ) -WorkingDirectory $WorkingDirectory -Environment $Environment -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDirectory "$Name.log") `
        -RedirectStandardError (Join-Path $logDirectory "$Name.error.log") -PassThru
    $processes.Add($process)
    return $process
}

function Invoke-CheckedCommand {
    param(
        [string] $Name,
        [string] $FilePath,
        [string[]] $Arguments = @(),
        [string] $WorkingDirectory = $PSScriptRoot,
        [int] $TimeoutSeconds = 120,
        [string] $InputPath,
        [switch] $AllowFailure
    )

    $process = Start-LoggedProcess -Name $Name -FilePath $FilePath -Arguments $Arguments `
        -WorkingDirectory $WorkingDirectory -InputPath $InputPath
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while (-not $process.WaitForExit(250)) {
        if ([DateTime]::UtcNow -ge $deadline) {
            $process.Kill($true)
            $process.WaitForExit()
            if ($AllowFailure) { return $null }
            throw "$Name 超时，请查看 $logDirectory 下的 $Name 日志。"
        }
    }
    if ($process.ExitCode -ne 0) {
        if ($AllowFailure) { return $null }
        throw "$Name 失败（退出码 $($process.ExitCode)），请查看 $logDirectory 下的 $Name 日志。"
    }
    return (Get-Content -LiteralPath (Join-Path $logDirectory "$Name.log") -Raw -Encoding utf8)
}

function Wait-ServiceReady {
    param(
        [System.Diagnostics.Process] $Process,
        [string] $Name,
        [string] $Url,
        [int] $TimeoutSeconds = 180,
        [switch] $Backend
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $lastError = '尚未返回就绪状态'
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($Process.HasExited) {
            throw "$Name 已退出（退出码 $($Process.ExitCode)），请查看 $logDirectory。"
        }
        try {
            $response = Invoke-WebRequest -Uri $Url -TimeoutSec 5 -NoProxy
            if ($response.StatusCode -eq 200) {
                if (-not $Backend) { return }
                $health = $response.Content | ConvertFrom-Json
                if ($health.code -eq 0 -and $health.data.status -eq 'UP' -and $health.data.service -eq 'sazare') {
                    return
                }
            }
        } catch {
            # 服务启动期间连接失败属于正常等待。
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Milliseconds 500
    }
    throw "等待 $Name 就绪超时：$lastError。请查看 $logDirectory。"
}

try {
    if (-not $IsWindows) { throw '此脚本用于 Windows，请使用 PowerShell 7.4 或更新版本。' }
    [IO.Directory]::CreateDirectory((Join-Path $PSScriptRoot '.codex-tmp')) | Out-Null
    try {
        $lock = [IO.File]::Open((Join-Path $PSScriptRoot '.codex-tmp/start.lock'),
            [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch {
        throw '已有启动脚本正在运行，请先在原终端按 Ctrl+C 停止。'
    }
    [IO.Directory]::CreateDirectory($logDirectory) | Out-Null
    Write-Host "日志目录：$logDirectory"

    $backendEnvironment = Read-BackendEnvironment -Path (Join-Path $PSScriptRoot '.env')
    # 部分 Windows 默认临时目录无法建立 JDK 的 Unix-domain socket；使用项目内专用目录。
    $backendTempDirectory = Join-Path $PSScriptRoot '.codex-tmp/backend-temp'
    [IO.Directory]::CreateDirectory($backendTempDirectory) | Out-Null
    $backendEnvironment['TEMP'] = $backendTempDirectory
    $backendEnvironment['TMP'] = $backendTempDirectory
    foreach ($tool in @('node.exe', 'npm.cmd', 'docker.exe')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "未找到 $tool，请先安装并加入 PATH。" }
    }
    $javaPath = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
    $javacPath = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { 'javac.exe' }
    if (-not (Get-Command $javacPath -ErrorAction SilentlyContinue)) { throw '未找到 JDK，请检查 JDK 25 和 JAVA_HOME。' }
    $javaVersion = Invoke-CheckedCommand -Name 'java-version' -FilePath $javaPath -Arguments @('--version')
    if ($javaVersion -notmatch '(?m)^(?:openjdk|java) 25(?:[.\s]|$)') { throw '本项目需要 JDK 25，请检查 JAVA_HOME 和 PATH。' }
    $nodeVersion = [version](Invoke-CheckedCommand -Name 'node-version' -FilePath 'node.exe' -Arguments @('--version')).Trim().TrimStart('v')
    if (-not (($nodeVersion.Major -eq 20 -and $nodeVersion.Minor -ge 19) -or $nodeVersion -ge [version]'22.12.0')) {
        throw 'Node.js 版本不符合要求，请使用 ^20.19.0 或 >=22.12.0。'
    }
    $addresses = @([Net.IPAddress]::Loopback)
    if ([Net.Sockets.Socket]::OSSupportsIPv6) { $addresses += [Net.IPAddress]::IPv6Loopback }
    foreach ($port in @(8080, 5173)) {
        foreach ($address in $addresses) {
            $listener = [Net.Sockets.TcpListener]::new($address, $port)
            try {
                $listener.ExclusiveAddressUse = $true
                $listener.Start()
            } catch {
                throw "无法绑定本地端口 $port，请检查端口占用或访问权限。"
            } finally {
                $listener.Stop()
            }
        }
    }

    Write-Host '检查 Docker 引擎……'
    $engine = Invoke-CheckedCommand -Name 'docker-info' -FilePath 'docker.exe' `
        -Arguments @('info', '--format', '{{.OSType}}') -TimeoutSeconds 10 -AllowFailure
    if (-not $engine) {
        Write-Host '自动启动 Docker Desktop，最多等待 120 秒……'
        try {
            $null = Invoke-CheckedCommand -Name 'docker-start' -FilePath 'docker.exe' `
                -Arguments @('desktop', 'start', '--timeout', '120') -TimeoutSeconds 130
        } catch {
            throw "Docker Desktop 未能启动，请先处理其窗口中的错误再重试。$($_.Exception.Message)"
        }
        $engine = Invoke-CheckedCommand -Name 'docker-ready' -FilePath 'docker.exe' `
            -Arguments @('info', '--format', '{{.OSType}}') -TimeoutSeconds 10
    }
    if ($engine.Trim() -ne 'linux') { throw '本项目需要 Linux 容器，请将 Docker Desktop 切换到 Linux 容器模式。' }

    Write-Host '启动 PostgreSQL 和 Redis，并等待健康检查……'
    $null = Invoke-CheckedCommand -Name 'compose-up' -FilePath 'docker.exe' `
        -Arguments @('compose', 'up', '-d', '--wait', '--wait-timeout', '120') -TimeoutSeconds 600

    $psqlArguments = @('compose', 'exec', '-T', 'postgres', 'psql', '-X', '-U', 'sazare_user', '-d', 'sazare', '-v', 'ON_ERROR_STOP=1')
    $tableCount = Invoke-CheckedCommand -Name 'database-check' -FilePath 'docker.exe' -Arguments ($psqlArguments + @(
        '-t', '-A', '-c', "select count(*) from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE';"
    ))
    if ($tableCount.Trim() -notmatch '^\d+$') { throw '无法确认数据库是否为空，已停止启动。' }
    if ([int]$tableCount.Trim() -eq 0) {
        Write-Host '空数据库：在同一事务内初始化结构和默认数据……'
        $initPath = Join-Path $logDirectory 'init.sql'
        $schema = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'backend/src/main/resources/db/schema.sql') -Raw -Encoding utf8
        $seed = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'backend/src/main/resources/db/seed.sql') -Raw -Encoding utf8
        [IO.File]::WriteAllText($initPath, "$schema`n$seed", [Text.UTF8Encoding]::new($false))
        $null = Invoke-CheckedCommand -Name 'database-init' -FilePath 'docker.exe' `
            -Arguments ($psqlArguments + @('--single-transaction', '-f', '-')) -InputPath $initPath
    } else {
        Write-Host '已有数据库：跳过初始化。'
    }

    $frontendDirectory = Join-Path $PSScriptRoot 'frontend'
    if (-not (Test-Path -LiteralPath (Join-Path $frontendDirectory 'node_modules/.bin/vite.cmd'))) {
        Write-Host '首次安装前端依赖……'
        $null = Invoke-CheckedCommand -Name 'npm-ci' -FilePath 'npm.cmd' -Arguments @('ci') `
            -WorkingDirectory $frontendDirectory -TimeoutSeconds 600
    }

    Write-Host '启动后端，等待健康检查（首次编译最多等待 300 秒）……'
    $backend = Start-LoggedProcess -Name 'backend' -FilePath (Join-Path $PSScriptRoot 'backend/mvnw.cmd') `
        -Arguments @('spring-boot:run') -WorkingDirectory (Join-Path $PSScriptRoot 'backend') -Environment $backendEnvironment
    Wait-ServiceReady -Process $backend -Name '后端' -Url 'http://localhost:8080/api/health' -TimeoutSeconds 300 -Backend

    Write-Host '启动前端……'
    $frontend = Start-LoggedProcess -Name 'frontend' -FilePath 'npm.cmd' `
        -Arguments @('run', 'dev', '--', '--strictPort') -WorkingDirectory $frontendDirectory
    Wait-ServiceReady -Process $frontend -Name '前端' -Url 'http://localhost:5173'

    Write-Host '项目已启动：http://localhost:5173' -ForegroundColor Green
    Write-Host '后端：http://localhost:8080/api | 按 Ctrl+C 停止前后端；PostgreSQL 和 Redis 保留运行。'
    while ($true) {
        foreach ($service in @($backend, $frontend)) {
            if ($service.HasExited) { throw "服务意外退出（退出码 $($service.ExitCode)），请查看 $logDirectory。" }
        }
        Start-Sleep -Milliseconds 500
    }
} catch {
    Write-Host "启动失败：$($_.Exception.Message)" -ForegroundColor Red
    $exitCode = 1
} finally {
    foreach ($process in $processes) {
        try {
            if (-not $process.HasExited) {
                $process.Kill($true)
                $null = $process.WaitForExit(5000)
            }
        } catch {
            Write-Warning "无法停止本次启动的进程 $($process.Id)，请检查日志。"
        } finally {
            $process.Dispose()
        }
    }
    if ($lock) { $lock.Dispose() }
}
if ($PauseOnError -and $exitCode -ne 0) {
    $null = Read-Host '启动失败，按回车关闭窗口'
}
exit $exitCode
