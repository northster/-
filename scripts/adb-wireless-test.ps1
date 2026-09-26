# DT Keyboard: install + smoke test over wireless debugging (Windows PowerShell)
# Usage:
#   .\scripts\adb-wireless-test.ps1 -Pair 192.168.0.12:37123 -Code 123456   # first time only
#   .\scripts\adb-wireless-test.ps1 -Target 192.168.0.12:41234 -Apk .\DTKeyboard-debug.apk
param(
    [string]$Target,
    [string]$Apk,
    [string]$Pair,
    [string]$Code
)
$ErrorActionPreference = "Stop"
$Pkg = "io.github.northster.dtkeyboard.debug"
$Ime = "$Pkg/helium314.keyboard.latin.LatinIME"

if ($Pair) { adb pair $Pair $Code; exit }
if (-not $Target -or -not (Test-Path $Apk)) { Write-Host "usage: -Target ip:port -Apk path"; exit 1 }

function Notify([string]$msg) {
    Write-Host ">>> $msg"
    # phone notification shade
    adb -s $Target shell cmd notification post -S bigtext -t "'DT Keyboard test'" dt_test "'$msg'" | Out-Null
    # Windows balloon notification
    try {
        Add-Type -AssemblyName System.Windows.Forms
        $n = New-Object System.Windows.Forms.NotifyIcon
        $n.Icon = [System.Drawing.SystemIcons]::Information
        $n.Visible = $true
        $n.ShowBalloonTip(5000, "DT Keyboard", $msg, "Info")
    } catch {}
}

if (-not ((adb connect $Target) -match "connected")) { Write-Host "adb connect failed"; exit 1 }
$model = (adb -s $Target shell getprop ro.product.model).Trim()
Notify "테스트 시작: $model 에 $(Split-Path $Apk -Leaf) 설치 중"

adb -s $Target install -r -d $Apk
adb -s $Target shell ime enable $Ime | Out-Null
adb -s $Target shell ime set $Ime | Out-Null
adb -s $Target logcat -c

Read-Host "폰에서 아무 텍스트 칸을 눌러 키보드를 띄운 뒤 Enter"

$size = ((adb -s $Target shell wm size) | Select-Object -Last 1).Split(" ")[-1].Trim()
$w, $h = $size.Split("x") | ForEach-Object { [int]$_ }
$x = [int]($w / 2); $ky = [int]($h * 0.85)
function Swipe($y1, $y2, $ms) { adb -s $Target shell input swipe $x $y1 $x $y2 $ms; Start-Sleep -Milliseconds 800 }
function Tap { adb -s $Target shell input tap $x $ky; Start-Sleep -Milliseconds 500 }

Tap
Swipe $ky ($ky - [int]($h / 12)) 80           # fast up   -> expand
Swipe ($ky - [int]($h / 30)) ($ky + [int]($h / 20)) 80   # fast down -> collapse
Swipe $ky ($ky - [int]($h / 12)) 900          # slow up   -> must NOT expand
Tap

New-Item -ItemType Directory -Force -Path test-logs | Out-Null
$log = "test-logs\logcat-$(Get-Date -Format yyyyMMdd-HHmmss).txt"
adb -s $Target logcat -d | Out-File -Encoding utf8 $log
$exp = (Select-String -Path $log -Pattern "toolbar expanded").Count
$col = (Select-String -Path $log -Pattern "toolbar collapsed").Count
$crash = ((adb -s $Target logcat -d -b crash) | Select-String $Pkg).Count
$result = "expand=$exp (1 기대), collapse=$col (1 기대), crash=$crash"
if ($exp -eq 1 -and $col -eq 1 -and $crash -eq 0) { Notify "테스트 완료 ✅ $result" }
else { Notify "테스트 완료 ⚠️ 확인 필요: $result (로그: $log)" }
