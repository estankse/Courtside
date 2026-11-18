param(
    [string]$BaseUrl = 'http://127.0.0.1:8080/api',
    [string]$Date = '',
    [string]$Batch = '',
    [string]$OrganizerUsername = 'organizer',
    [string]$RefereeUsername = 'referee',
    [string]$Password = 'Courtside2026!',
    [string]$OutputDirectory = ''
)
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$chinaNow = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'China Standard Time')
if (!$Date) { $Date = $chinaNow.ToString('yyyy-MM-dd') }
[void][DateTime]::ParseExact($Date, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)
if (!$Batch) { $Batch = $Date.Replace('-', '') }
if (!$OutputDirectory) {
    $workspace = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $OutputDirectory = Join-Path $workspace ('tmp/test-matches-' + $Batch)
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

function Invoke-Courtside([string]$Method, [string]$Path, $Body, [string]$Token = '') {
    $request = @{ Uri = "$BaseUrl$Path"; Method = $Method; TimeoutSec = 15 }
    if ($Token) { $request.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json; charset=utf-8'
        $request.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 12 -Compress))
    }
    # Invoke-RestMethod emits a JSON array as one pipeline object. Unroll it so
    # callers can safely filter existing events instead of creating duplicates.
    foreach ($item in (Invoke-RestMethod @request)) { $item }
}

# Use normal application APIs. Existing matches, scores and device bindings are never reset.
$org = Invoke-Courtside POST '/auth/login' @{ username = $OrganizerUsername; password = $Password }
$ref = Invoke-Courtside POST '/auth/login' @{ username = $RefereeUsername; password = $Password }
$events = @(Invoke-Courtside GET '/tournaments' $null $org.token)
$sets = @(
    @{
        title = '标准规则体验赛'; target = 21; cap = 30; games = 2; hour = 13; minute = 0; slot = 50; duration = 40
        cases = @(
            @{ round = '体验01 · 常规计分与横竖屏'; a = '林一鸣'; b = '周知远'; status = 'PUBLISHED' },
            @{ round = '体验02 · 入场动画与记分'; a = '许嘉宁'; b = '陈星野'; status = 'ACCEPTED' },
            @{ round = '体验03 · 离线续赛与撤销'; a = '沈清越'; b = '江屿'; status = 'PUBLISHED' },
            @{ round = '体验04 · 接受任务与赛前抽签'; a = '陆亦辰'; b = '程予安'; status = 'PUBLISHED' }
        )
    },
    @{
        title = '快速流程体验赛'; target = 5; cap = 7; games = 1; hour = 16; minute = 20; slot = 20; duration = 15
        cases = @(
            @{ round = '体验05 · 快速完赛与核验'; a = '苏承宇'; b = '顾明轩'; status = 'PUBLISHED' },
            @{ round = '体验06 · 退回更正与重提'; a = '叶景行'; b = '沈予白'; status = 'ACCEPTED' },
            @{ round = '体验07 · 弃权与成绩核验'; a = '张博文'; b = '许清和'; status = 'PUBLISHED' },
            @{ round = '体验08 · 编排发布（赛事组）'; a = '陈宇航'; b = '周若辰'; status = 'DRAFT' }
        )
    }
)
$rows = [Collections.Generic.List[object]]::new()
foreach ($set in $sets) {
    $name = "$($set.title) · $Batch"
    $event = $events | Where-Object name -EQ $name | Select-Object -First 1
    if (!$event) {
        $event = Invoke-Courtside POST '/tournaments' @{
            name = $name; location = '羽序体验体育馆'; date = $Date
            description = "交互体验批次 $Batch；所有比赛由当前设备手动开始。"
            rules = @{ target = $set.target; cap = $set.cap; gamesToWin = $set.games; lead = 2 }
        } $org.token
    }
    if ($event.status -EQ 'ARCHIVED') { throw "批次赛事已归档，请换一个 -Batch：$name" }
    $tid = $event.id
    [void](Invoke-Courtside POST "/tournaments/$tid/members" @{ userId = $ref.user.id } $org.token)
    $workspace = Invoke-Courtside GET "/tournaments/$tid/workspace" $null $org.token
    $entrants = @{}
    foreach ($entry in $workspace.entrants) { $entrants[$entry.name] = $entry }
    $courts = @($workspace.courts)
    for ($courtIndex = $courts.Count; $courtIndex -lt 2; $courtIndex++) {
        $courts += Invoke-Courtside POST "/tournaments/$tid/courts" @{ name = ('体验 {0:00} 号场地' -f ($courtIndex + 1)) } $org.token
    }
    for ($i = 0; $i -lt $set.cases.Count; $i++) {
        $case = $set.cases[$i]
        foreach ($side in @('a', 'b')) {
            $player = $case[$side]
            if (!$entrants.ContainsKey($player)) {
                $unit = if ($side -EQ 'a') { '计算机学院' } else { '信息与电子学院' }
                $entrants[$player] = Invoke-Courtside POST "/tournaments/$tid/entrants" @{ name = $player; unit = $unit } $org.token
            }
        }
        $match = $workspace.matches | Where-Object round -EQ $case.round | Select-Object -First 1
        if (!$match) {
            $start = [DateTimeOffset]::Parse(('{0}T{1:00}:{2:00}:00+08:00' -f $Date, $set.hour, $set.minute)).AddMinutes($i * $set.slot)
            $match = Invoke-Courtside POST "/tournaments/$tid/matches" @{
                sideAId = $entrants[$case.a].id; sideBId = $entrants[$case.b].id
                courtId = $courts[$i % 2].id; refereeId = $ref.user.id; round = $case.round
                startsAt = $start.UtcDateTime.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
                endsAt = $start.AddMinutes($set.duration).UtcDateTime.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
            } $org.token
        }
        if ($case.status -NE 'DRAFT' -and $match.status -EQ 'DRAFT') {
            $match = Invoke-Courtside POST "/matches/$($match.id)/publish" @{} $org.token
        }
        if ($case.status -EQ 'ACCEPTED' -and $match.status -EQ 'PUBLISHED') {
            $match = Invoke-Courtside POST "/matches/$($match.id)/accept" @{ scheduleVersion = $match.scheduleVersion } $ref.token
        }
        $rows.Add([pscustomobject]@{
            tournament = $name; tournamentId = $tid; round = $match.round
            matchId = $match.id; sideA = $match.sideA; sideB = $match.sideB
            status = $match.status; referee = $RefereeUsername; startsAt = $match.startsAt
            rules = $match.rules
        })
    }
}
$feed = Invoke-Courtside GET '/me/matches' $null $ref.token
$visibleIds = @($feed.matches | ForEach-Object id)
foreach ($row in $rows) {
    if ($row.status -NE 'DRAFT' -and $row.matchId -notin $visibleIds) {
        throw "裁判任务列表缺少比赛：$($row.round)"
    }
}
$rows | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'matches.json') -Encoding utf8
$rows | Select-Object tournament,round,sideA,sideB,status,referee | Format-Table -AutoSize
Write-Output "已准备 $($rows.Count) 场比赛；同批次重复执行会沿用现有场次。清单：$OutputDirectory"
