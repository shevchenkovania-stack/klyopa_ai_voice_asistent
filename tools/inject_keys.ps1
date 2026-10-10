# AutoPilot key loader: pushes Groq/OpenAI/Gemini keys from .env into the running
# debug app via the debug broadcast receiver (EngineManager.setDebugKeys). Works on a
# fresh device without typing keys into the encrypted Flutter UI. The app must be up
# and the receiver registered (MainActivity.registerAutoPilotReceiver).
param([string]$Device = "2b4a6e3e0205")
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$pkg = "com.aiagent.ai_voice_agent"
$act = "com.aiagent.ai_voice_agent.TEST_UTTERANCE"

function Read-EnvKey([string]$name) {
    $line = (Get-Content .env | Where-Object { $_ -match "^\s*$name=" } | Select-Object -First 1)
    if (-not $line) { return "" }
    return ($line -replace "^\s*$name=", "").Trim().Trim('"')
}
$gk = Read-EnvKey "GROQ_API_KEY"
$ok = Read-EnvKey "OPENAI_API_KEY"
$mk = Read-EnvKey "GEMINI_API_KEY"
if (-not $gk -and -not $ok) { throw "No Groq/OpenAI key in .env" }

# make sure app is up
$appPid = adb -s $Device shell pidof $pkg
if (-not $appPid) {
    adb -s $Device shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep -Seconds 12
}

# send keys (empty extras are ignored by the receiver)
if ($gk) { adb -s $Device shell am broadcast -a $act --es k_groq   $gk | Out-Null }
if ($ok) { adb -s $Device shell am broadcast -a $act --es k_openai $ok | Out-Null }
if ($mk) { adb -s $Device shell am broadcast -a $act --es k_gemini $mk | Out-Null }
Start-Sleep -Seconds 2

adb -s $Device logcat -d -s AutoPilot:V | Select-String "SET_KEYS|приёмник зарегистрирован" | Select-Object -Last 3
