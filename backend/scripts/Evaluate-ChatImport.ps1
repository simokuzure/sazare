[CmdletBinding(DefaultParameterSetName = 'Fixtures')]
param(
    [string] $BaseUrl = 'http://localhost:8080/api',
    [Parameter(ParameterSetName = 'Fixtures')]
    [string[]] $CaseId = @(),
    [Parameter(Mandatory, ParameterSetName = 'File')]
    [string] $MarkdownPath,
    [Parameter(ParameterSetName = 'File')]
    [ValidateSet('ZH_TO_JA', 'EN_TO_JA')]
    [string] $LearningMode = 'ZH_TO_JA',
    [string] $OutputPath = (Join-Path $PSScriptRoot '../target/chat-import-evaluation.json'),
    [switch] $ValidateOnly
)

$ErrorActionPreference = 'Stop'
if ($PSCmdlet.ParameterSetName -eq 'File') {
    $filePath = (Resolve-Path -LiteralPath $MarkdownPath).Path
    $cases = @([pscustomobject]@{
        id = 'local-conversation'
        learningMode = $LearningMode
        markdown = [System.Text.UTF8Encoding]::new($false, $true).GetString([System.IO.File]::ReadAllBytes($filePath))
        requiredFocuses = @()
        optionalFocuses = @('按原文逐项人工复核，不能仅比较候选总数')
        mustNotFlag = @('原助手的纠正不能作为标准答案')
    })
} else {
    $fixturePath = Join-Path $PSScriptRoot '../src/test/resources/chat-import/evaluation-cases.json'
    $cases = @(Get-Content -LiteralPath $fixturePath -Raw -Encoding utf8 | ConvertFrom-Json)
    $unknown = @($CaseId | Where-Object { $_ -notin $cases.id })
    if ($unknown.Count) { throw "未知评估样例：$($unknown -join ', ')" }
    if ($CaseId.Count) { $cases = @($cases | Where-Object { $_.id -in $CaseId }) }
}

$ids = [System.Collections.Generic.HashSet[string]]::new()
foreach ($case in $cases) {
    if (-not $ids.Add($case.id)) { throw "重复样例 ID：$($case.id)" }
    if ($case.learningMode -notin @('ZH_TO_JA', 'EN_TO_JA')) { throw "无效学习模式：$($case.id)" }
    if ([string]::IsNullOrWhiteSpace($case.markdown)) { throw "样例正文为空：$($case.id)" }
    if ([System.Text.Encoding]::UTF8.GetByteCount($case.markdown) -gt 131072) { throw "样例超过 128 KiB：$($case.id)" }
}
if ($ValidateOnly) {
    Write-Output "已读取并校验 $($cases.Count) 个样例，未调用 API。正文结构与字符数仍由后端校验。"
    return
}

$reportPath = [System.IO.Path]::GetFullPath($OutputPath)
$reportDirectory = Split-Path -Parent $reportPath
[System.IO.Directory]::CreateDirectory($reportDirectory) | Out-Null
$results = [System.Collections.Generic.List[object]]::new()
$hadErrors = $false

foreach ($case in $cases) {
    $uri = "$($BaseUrl.TrimEnd('/'))/chat-imports/analyses?learningMode=$($case.learningMode)"
    $entry = [ordered]@{
        caseId = $case.id
        learningMode = $case.learningMode
        requiredFocuses = @($case.requiredFocuses)
        optionalFocuses = @($case.optionalFocuses)
        mustNotFlag = @($case.mustNotFlag)
        reviewStatus = 'PENDING_MANUAL_REVIEW'
        analysis = $null
        error = $null
    }
    try {
        # 每个样例只发一次分析请求；不调用确认接口，也不自动重试。
        $response = Invoke-RestMethod -Method Post -Uri $uri -ContentType 'text/markdown' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($case.markdown)) -TimeoutSec 360 -MaximumRetryCount 0
        if ($null -eq $response.code -or $response.code -ne 0 -or $null -eq $response.data) {
            throw "分析未成功：$($response.message)"
        }
        $entry.analysis = $response.data
        Write-Output "$($case.id)：返回 $(@($response.data.candidates).Count) 项，待人工复核。"
    } catch {
        $entry.reviewStatus = 'REQUEST_FAILED'
        $entry.error = $_.Exception.Message
        $hadErrors = $true
        Write-Warning "$($case.id)：请求失败；本工具不会重试。"
    }
    $results.Add([pscustomobject]$entry)
    $report = [ordered]@{
        generatedAt = [DateTimeOffset]::Now.ToString('o')
        baseUrl = $BaseUrl
        note = '仅记录分析响应和人工检查项；不含完整输入聊天。候选数量和结构校验通过不代表语言判断正确。需确认服务已加载最新代码且使用真实模型。'
        results = @($results.ToArray())
    }
    [System.IO.File]::WriteAllText($reportPath, ($report | ConvertTo-Json -Depth 30), [System.Text.UTF8Encoding]::new($false))
}

Write-Output "评估报告：$reportPath"
if ($hadErrors) { throw '存在未成功的分析请求，详见评估报告。' }
