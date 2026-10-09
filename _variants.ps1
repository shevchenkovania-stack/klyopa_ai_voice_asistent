$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$dir  = $root + '\assets\klyopa\face_layers\'

# Canvas = layer_18 native size 415x298. EVERY part is placed at its NATIVE size.
# NO resizing at all (per user request). Positions are the original hand-assembly
# coordinates recovered from git 0874931.
$CW = 415; $CH = 298
$SCALE = 1.6      # uniform zoom for the whole sheet (does NOT resize individual parts relative to each other)
$LABELH = 54
$TOPPAD = 130     # canvas units above the face so the hair (negative Y) is not clipped

# native draw op: file, x, y  (always drawn at the image's NATIVE width/height)
function Native([string]$f, [int]$x, [int]$y) { return @{ f=$f; x=$x; y=$y } }

# ---- Original hand-assembly positions (all NATIVE size) ----
$base      = Native 'layer_18.png'   0   0     # face + ears + default smile already drawn
$browL     = Native 'layer_06.png'  70  36
$browR     = Native 'layer_07.png' 234  37
$eyeL      = Native 'layer_10.png'  56  35
$eyeR      = Native 'layer_11.png' 219  35
$eyeCL     = Native 'layer_08.png'  84 102
$eyeCR     = Native 'layer_09.png' 249 102
$mNeut     = Native 'layer_14.png' 171 170
$mSmile    = Native 'layer_15.png' 150 160
$mOpen     = Native 'layer_16.png' 150 150
$cheekL    = Native 'layer_04.png'  90 150
$cheekR    = Native 'layer_05.png' 275 150
$hairBig   = Native 'layer_01.png' 107 -100
$hairSide  = Native 'layer_02.png'   7 -90
$hairBangs = Native 'layer_03.png' 102 -47

# ---- Variants: each is a labeled native-size assembly ----
$variants = @(
  @{ name='V1  bangs only + neutral';   ops=@( $base, $hairBangs, $eyeL, $eyeR, $browL, $browR, $mNeut ) },
  @{ name='V2  full hair + smile';      ops=@( $base, $hairSide, $hairBig, $hairBangs, $eyeL, $eyeR, $browL, $browR, $mSmile ) },
  @{ name='V3  big wavy + talking';     ops=@( $base, $hairBig, $eyeL, $eyeR, $browL, $browR, $mOpen ) },
  @{ name='V4  full hair + cheeks + smile'; ops=@( $base, $cheekL, $cheekR, $hairSide, $hairBig, $hairBangs, $eyeL, $eyeR, $browL, $browR, $mSmile ) }
)

$PW = [int]($CW * $SCALE); $PH = [int](($CH + $TOPPAD) * $SCALE)
$GAP = 24
$COLS = 2
$ROWS = [Math]::Ceiling($variants.Count / $COLS)
$OW = $COLS * $PW + ($COLS - 1) * $GAP
$OH = $ROWS * ($PH + $LABELH) + $GAP
$out = New-Object System.Drawing.Bitmap $OW, $OH
$g = [System.Drawing.Graphics]::FromImage($out)
$g.Clear([System.Drawing.Color]::FromArgb(24, 26, 34))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias

$font = New-Object System.Drawing.Font ('Segoe UI', 16, ([System.Drawing.FontStyle]::Bold))
$brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)

$idx = 0
foreach ($v in $variants) {
  $col = $idx % $COLS
  $row = [Math]::Floor($idx / $COLS)
  $ox = $col * ($PW + $GAP)
  $oy = $row * ($PH + $LABELH)
  foreach ($op in $v.ops) {
    $path = $dir + $op.f
    if (-not (Test-Path $path)) { Write-Output ('MISSING ' + $op.f); continue }
    $img = [System.Drawing.Image]::FromFile($path)
    $dx = $ox + $op.x * $SCALE
    $dy = $oy + ($op.y + $TOPPAD) * $SCALE
    $g.DrawImage($img, [single]$dx, [single]$dy, [single]($img.Width * $SCALE), [single]($img.Height * $SCALE))
    $img.Dispose()
  }
  # label marker
  $g.FillRectangle([System.Drawing.Brushes]::Black, [single]$ox, [single]($oy + $PH), [single]$PW, [single]$LABELH)
  $g.DrawString($v.name, $font, $brush, [single]($ox + 8), [single]($oy + $PH + 12))
  $idx++
}

$g.Dispose()
$dest = $root + '\build\face_variants.png'
$out.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
$out.Dispose()
Write-Output ('rendered ' + $OW + 'x' + $OH + ' -> ' + $dest)
