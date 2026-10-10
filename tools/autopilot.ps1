# =====================================================================
#  AUTOPILOT — self-test for the Klyopa voice pipeline.
#  Generates Russian phrases via Windows TTS, pushes the WAVs to the
#  phone, triggers the debug broadcast receiver, then parses logcat for
#  the AutoPilot markers (STT:/RESPONSE:/HALLUCINATION:/TEST_DONE),
#  scores each case and writes report.json.
#
#  Usage:  powershell -ExecutionPolicy Bypass -File tools\autopilot.ps1
#    -Device <serial>   default 3A191FDJG0021L
#    -Only  time,name   run a subset of cases (comma-separated names)
#    -Regen             force re-synthesis of the WAV files
#    -Tail  <lines>     logcat pull window, default 400
#    -TimeoutSec <n>    per-case wait, default 60
# =====================================================================
param(
    [string]$Device = "3A191FDJG0021L",
    [string]$Only = "",
    [switch]$Tools,
    [switch]$Regen,
    [int]$Tail = 400,
    [int]$TimeoutSec = 90
)

$ErrorActionPreference = "Stop"
# adb writes UTF-8 bytes; PowerShell decodes with OEM codepage by default -> mojibake.
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$root   = Split-Path -Parent $PSScriptRoot
$wavDir = Join-Path $root "tools\autopilot_wav"
$report = Join-Path $root "tools\autopilot_report.json"
$remote = "/sdcard/Android/data/com.aiagent.ai_voice_agent/files"
if (-not (Test-Path $wavDir)) { New-Item -ItemType Directory -Path $wavDir | Out-Null }

function Run-Adb([string[]]$a, [int]$retry = 2) {
    for ($i = 0; $i -le $retry; $i++) {
        $out = & adb.exe -s $Device @a 2>$null
        if ($LASTEXITCODE -eq 0) { return ($out -join "`n") }
        Start-Sleep -Seconds 2
    }
    return ""
}

# ---------- 1. Phrase bank ----------
# MAIN GOAL: conversational skills — Клёпа must talk like a friend and a good
# storyteller, and NEVER answer «Ошибка соединения». Tool cases (-Tools switch)
# are secondary. expectStt = what Whisper should hear; expect = regex in answer.
$convCases = @(
    @{ name="greet";    phrase="Привет, Клёпа! Как настроение?";              expectStt="настроение"; expect='(привет|здравств|настроен|рад|хорош)' },
    @{ name="howareyou";phrase="Как ты сам сегодня?";                          expectStt="сам";        expect='(я|себя|нормал|отличн|хорош|спасибо)' },
    @{ name="name";     phrase="Как тебя зовут?";                              expectStt="зовут";      expect='Кл' },
    @{ name="about";    phrase="Расскажи что-нибудь о себе.";                  expectStt="себе";       expect='(я|Клёпа|ассистент|умею|люблю)' },
    @{ name="joke";     phrase="Расскажи анекдот.";                            expectStt="анекдот";    expect='.' },
    @{ name="story";    phrase="Расскажи любую историю.";                      expectStt="истори";     expect='(истори|рассказ|однажды|давным)' },
    @{ name="fairy";    phrase="Придумай сказку на ночь.";                     expectStt="сказку";     expect='(сказк|жили|давным|однажды|леса)' },
    @{ name="favorites";phrase="Какая у тебя любимая еда?";                    expectStt="любимая";    expect='(люблю|еда|пиц|каш|вкусн|нравится)' },
    @{ name="advice";   phrase="Мне грустно, подбодри меня.";                  expectStt="грустно";    expect='(не груст|обнима|держись|всё будет|хуже|станет)' },
    @{ name="opinion";  phrase="Что думаешь про дождливые дни?";               expectStt="дождли";     expect='(дожд|погоду|дум|любл|нрав|уют)' },
    @{ name="game";     phrase="Давай поиграем, что умеешь?";                  expectStt="играем";     expect='(игр|могу|умею|давай|слова|город)' },
    @{ name="thanks";   phrase="Спасибо, было классно!";                       expectStt="спасибо";    expect='(пожалуйста|рад|обращай|всегда|классно)' }
)
$toolCases = @(
    @{ name="time";     phrase="Который час?";                          expectStt="час";    expect='(\d|сутк|врем|час)' },
    @{ name="weather";  phrase="Какая сегодня погода?";                 expectStt="погода"; expect='.' },
    @{ name="currency"; phrase="Сколько сейчас евро в рублях?";         expectStt="евро";   expect='(\d|рубл|курс)' },
    @{ name="timer";    phrase="Поставь таймер на пять минут.";         expectStt="таймер"; expect='(таймер|минут|постав|готов)' },
    @{ name="farewell"; phrase="Всё, пока!";                            expectStt="пока";   expect='(пока|до встречи|прощай)' }
)
$cases = if ($Tools) { $toolCases } else { $convCases }

# ---------- 2. Speech synthesis via Windows TTS (voice: Irina, 16 kHz mono) ----------
function New-TestWav([string]$text, [string]$path) {
    Add-Type -AssemblyName System.Speech
    $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
    $wanted = $synth.GetInstalledVoices() |
              Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -like 'ru-*' } |
              Select-Object -First 1
    if ($wanted) { $synth.SelectVoice($wanted.VoiceInfo.Name) }
    $fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
    $synth.SetOutputToWaveFile($path, $fmt)
    $synth.Speak($text)
    $synth.Dispose()
}

foreach ($c in $cases) {
    $f = Join-Path $wavDir ("t_" + $c.name + ".wav")
    if ($Regen -or -not (Test-Path $f)) {
        Write-Host "  [tts] $($c.phrase)" -ForegroundColor DarkGray
        New-TestWav $c.phrase $f
    }
}

# ---------- 3. Push everything ----------
Run-Adb ("shell", "mkdir", "-p", $remote) | Out-Null
foreach ($c in $cases) {
    Run-Adb ("push", (Join-Path $wavDir ("t_" + $c.name + ".wav")), ($remote + "/t_" + $c.name + ".wav")) | Out-Null
}

$appPid = (Run-Adb ("shell", "pidof", "com.aiagent.ai_voice_agent")).Trim()
if (-not $appPid) {
    Write-Host "App not running — starting it…" -ForegroundColor Yellow
    Run-Adb ("shell", "am", "start", "-n", "com.aiagent.ai_voice_agent/.MainActivity") | Out-Null
    Start-Sleep -Seconds 8
}

# ---------- 4. Run the cases ----------
$selected = if ($Only) { $cases | Where-Object { $Only -split ',' -contains $_.name } } else { $cases }
$results = @()
foreach ($c in $selected) {
    Write-Host ""
    Write-Host ("=== [{0}] `"{1}`"" -f $c.name, $c.phrase) -ForegroundColor Cyan
    Run-Adb ("logcat", "-c") | Out-Null
    Run-Adb ("shell", "am", "broadcast", "-a", "com.aiagent.ai_voice_agent.TEST_UTTERANCE",
          "--es", "wav", ($remote + "/t_" + $c.name + ".wav")) | Out-Null

    # Poll logcat for TEST_DONE (or timeout).
    $done = $false
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 5
        $log = Run-Adb ("logcat", "-d", "-s", "AutoPilot:V")
        if ($log -match 'TEST_DONE')        { $done = $true; break }
        if ($log -match 'TEST_FAIL|TEST_ERROR') { break }
    }

    $log    = Run-Adb ("logcat", "-d", "-s", "AutoPilot:V")
    $lines  = $log -split "`n" | Select-Object -Last $Tail
    $stt    = [regex]::Match(($lines -join "`n"), '(?m)^.*STT: (.+)').Groups[1].Value.Trim()
    $resp   = [regex]::Match(($lines -join "`n"), '(?m)^.*RESPONSE: (.+)').Groups[1].Value.Trim()
    $hall   = [regex]::Match(($lines -join "`n"), '(?m)^.*HALLUCINATION: (.+)').Groups[1].Value.Trim()
    $done   = ($lines -join "`n") -match 'TEST_DONE'
    $noErr  = $resp -notmatch 'Ошибка соединения|Ошибка обработки'

    $sttOk  = ($stt -ne "") -and ($stt.ToLower() -match [regex]::Escape($c.expectStt.ToLower()))
    $respOk = ($resp -match $c.expect) -and $noErr
    $pass   = $done -and $sttOk -and $respOk

    Write-Host ("    STT        : {0}  {1}"   -f $stt, $(if ($sttOk) { "OK" } else { "FAIL" }))
    Write-Host ("    RESPONSE   : {0}"        -f $resp)
    if ($hall)  { Write-Host ("    HALLUCIN.  : {0}" -f $hall) -ForegroundColor DarkYellow }
    Write-Host ("    RESULT     : {0}" -f $(if ($pass) { "PASS" } else { "FAIL" }),
                 $(if ($pass) { "GREEN" } else { "RED" }))

    $results += [pscustomobject]@{ name=$c.name; phrase=$c.phrase; stt=$stt; sttOk=$sttOk;
                                  response=$resp; hallucination=$hall; done=$done; pass=$pass }
    # Пауза как у живого человека: Groq free = 8000 токенов/мин (~4.3k на запрос),
    # чаще двух раз в минуту нельзя — начнём ловить 429.
    Start-Sleep -Seconds 30
}

# ---------- 5. Report ----------
$results | ConvertTo-Json -Depth 3 | Set-Content -Path $report -Encoding UTF8
$passCount = ($results | Where-Object pass).Count
$connErrors = ($results | Where-Object { $_.response -match 'Ошибка соединения' }).Count
Write-Host ""
Write-Host "===== AUTOPILOT SUMMARY =====" -ForegroundColor Cyan
$results | ForEach-Object { Write-Host ("  {0,-11} {1}" -f $_.name, $(if ($_.pass) { "PASS" } else { "FAIL" })) }
Write-Host ("Total: {0}/{1} passed. «Ошибка соединения»: {2} раз(а). Report: {3}" -f $passCount, $results.Count, $connErrors, $report) -ForegroundColor Cyan
