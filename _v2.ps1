$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$dir  = $root + '\assets\klyopa\face_layers\'

# V2 tuning: full hair (02+01+03) + base (its own small smile shows, NO big mouth layer),
# brows native, EYES only scaled down a bit. Three eye sizes to choose from.
$CW = 415; $CH = 298
$SCALE = 1.6      # uniform sheet zoom (keeps parts proportional to each other)
$LABELH = 54
$TOPPAD = 130

# draw op: file, x, y, scale (scale=1 -> native size, centered on the native box)
function Op([string]$f, [int]$x, [int]$y, [double]$s = 1.0) { return @{ f=$f; x=$x; y=$y; s=$s } }

# ---- Original hand-assembly positions (native) ----
$base      = Op 'layer_18.png'   0   0
$browL     = Op 'layer_06.png'  70  36
$browR     = Op 'layer_07.png' 234  37
$mNeut     = Op 'layer_14.png' 171 170     # (not used - base smile shows)
$hairSide  = Op 'layer_02.png'   7 -90
$hairBig   = Op 'layer_01.png' 107 -100
$hairBangs = Op 'layer_03.png' 102 -47

# eyes native boxes: left (56,35) 137x155 -> center (124,112); right (219,35) 138x155 -> center (288,112)
function EyePair([double]$s) {
  $lw = [int](137 * $s); $lh = [int](155 * $s)
  $rw = [int](138 * $s); $rh = [int](155 * $s)
  $lx = [int](124 - $lw / 2); $ly = [int](112 - $lh / 2)
  $rx = [int](288 - $rw / 2); $ry = [int](112 - $rh / 2)
  return @( (Op 'layer_10.png' $lx $ly 1.0), (Op 'layer_11.png' $rx $ry 1.0), $lw, $lh, $rw, $rh )
}

# build a variant with eyes drawn at a given pixel size (centered)
function Variant([string]$name, [int]$elw, [int]$elh, [int]$erw, [int]$erh) {
  $elx = [int](124 - $elw / 2); $ely = [int](112 - $elh / 2)
  $erx = [int](288 - $erw / 2); $ery = [int](112 - $erh / 2)
  return @{
    name = $name
    ops  = @(
      @{ f='layer_18.png'; x=0;   y=0;   w=415; h=298 },
      @{ f='layer_02.png'; x=7;   y=-90; w=202; h=237 },
      @{ f='layer_01.png'; x=107; y=-100; w=319; h=265 },
      @{ f='layer_10.png'; x=$elx; y=$ely; w=$elw; h=$elh },
      @{ f='layer_11.png'; x=$erx; y=$ery; w=$erw; h=$erh },
      @{ f='layer_06.png'; x=70;  y=36;  w=101; h=42 },
      @{ f='layer_07.png'; x=234; y=37;  w=101; h=41 },
      @{ f='layer_03.png'; x=102; y=-47; w=211; h=127 }
    )
  }
}

$variants = @(
  Variant 'Eyes 100% (native 137x155)'      137 155 138 155
  Variant 'Eyes 85%  (~116x132)'            116 132 117 132
  Variant 'Eyes 75%  (~103x116)'            103 116 104 116
)

$PW = [int]($CW * $SCALE); $PH = [int](($CH + $TOPPAD) * $SCALE)
$GAP = 24
$OW = $variants.Count * $PW + ($variants.Count - 1) * $GAP
$OH = $PH + $LABELH
$out = New-Object System.Drawing.Bitmap $OW, $OH
$g = [System.Drawing.Graphics]::FromImage($out)
$g.Clear([System.Drawing.Color]::FromArgb(24, 26, 34))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
$font = New-Object System.Drawing.Font ('Segoe UI', 15, ([System.Drawing.FontStyle]::Bold))
$brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)

$x0 = 0
foreach ($v in $variants) {
  foreach ($op in $v.ops) {
    $path = $dir + $op.f
    if (-not (Test-Path $path)) { Write-Output ('MISSING ' + $op.f); continue }
    $img = [System.Drawing.Image]::FromFile($path)
    $dx = $x0 + $op.x * $SCALE
    $dy = ($op.y + $TOPPAD) * $SCALE
    $g.DrawImage($img, [single]$dx, [single]$dy, [single]($op.w * $SCALE), [single]($op.h * $SCALE))
    $img.Dispose()
  }
  $g.FillRectangle([System.Drawing.Brushes]::Black, [single]$x0, [single]$PH, [single]$PW, [single]$LABELH)
  $g.DrawString($v.name, $font, $brush, [single]($x0 + 8), [single]($PH + 14))
  $x0 += $PW + $GAP
}

$g.Dispose()
$dest = $root + '\build\face_v2_eyes.png'
$out.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
$out.Dispose()
Write-Output ('rendered ' + $OW + 'x' + $OH + ' -> ' + $dest)
