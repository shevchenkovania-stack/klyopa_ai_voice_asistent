Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$cfg = Get-Content -Raw ($root + '\assets\klyopa\klyopa_config.json') | ConvertFrom-Json
$parts = $cfg.parts
$cw = [int]$cfg.canvas.width; $ch = [int]$cfg.canvas.height
$SCALE = 2

$ref = New-Object System.Drawing.Bitmap ($root + '\build\ref_original.jpg')
$PANEL = $cw * $SCALE
$OW = 3 * $PANEL + 24
$OH = [Math]::Max($ch * $SCALE, $ref.Height * $SCALE)
$out = New-Object System.Drawing.Bitmap $OW, $OH
$g = [System.Drawing.Graphics]::FromImage($out)
$g.Clear([System.Drawing.Color]::FromArgb(90, 90, 110))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

function DrawPart($p) {
  if ($null -eq $p) { return }
  $file = $p.file
  if (-not $file) { return }
  $path = $root + '\assets\klyopa\' + $file
  if (-not (Test-Path $path)) { Write-Output ('   MISSING ' + $file); return }
  $img = [System.Drawing.Image]::FromFile($path)
  $x = [double]$p.x * $SCALE; $y = [double]$p.y * $SCALE
  $w = [double]$p.width * $SCALE; $h = [double]$p.height * $SCALE
  $g.DrawImage($img, [single]$x, [single]$y, [single]$w, [single]$h)
  $img.Dispose()
}

function DrawHairLayer($name) {
  if (-not $parts.hair_variants) { return }
  foreach ($v in $parts.hair_variants) {
    if (($v.layer | Out-String).Trim() -ne $name) { continue }
    DrawPart $v
  }
}

# paint order must match _KlyopaPainter
DrawPart $parts.body
DrawPart $parts.neck
DrawPart $parts.hair_back
DrawHairLayer 'back'
DrawPart $parts.face_base
DrawPart $parts.left_ear
DrawPart $parts.right_ear
DrawPart $parts.face_shadow
DrawPart $parts.left_eye
DrawPart $parts.right_eye
DrawPart $parts.left_brow
DrawPart $parts.right_brow
DrawPart $parts.nose
if ($parts.mouth_variants) { DrawPart ($parts.mouth_variants | Where-Object { $_.id -eq 'mouth_neutral' } | Select-Object -First 1) }
DrawHairLayer 'front'

$g.DrawImage($ref, [single]($PANEL + 12), [single]0, [single]($ref.Width * $SCALE), [single]($ref.Height * $SCALE))

# panel 3: 50% reference overlaid on the composite, aligned to the canvas origin
# (the config places artwork at reference px + PADX/PADY, so ref(0,0) -> canvas(10,12))
$cm = New-Object System.Drawing.Imaging.ColorMatrix
$cm.Matrix33 = 0.5
$ia = New-Object System.Drawing.Imaging.ImageAttributes
[void]$ia.SetColorMatrix($cm)
$ox = (2 * $PANEL + 12) + 10 * $SCALE
$oy = 12 * $SCALE
$dst = New-Object System.Drawing.Rectangle ([int]$ox, [int]$oy, [int]($ref.Width * $SCALE), [int]($ref.Height * $SCALE))
$g.DrawImage($ref, $dst, 0, 0, $ref.Width, $ref.Height, ([System.Drawing.GraphicsUnit]::Pixel), $ia)

$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::Lime), 2
$g.DrawRectangle($pen, 0, 0, ($PANEL) - 1, ($ch * $SCALE) - 1)
$g.DrawRectangle($pen, [single](2 * $PANEL + 12), [single]0, [single]($PANEL) - 1, [single]($ch * $SCALE) - 1)
$g.Dispose()
$out.Save(($root + '\build\klyopa_compare.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$out.Dispose(); $ref.Dispose()
Write-Output ('rendered ' + $OW + 'x' + $OH + ' -> build\klyopa_compare.png  (canvas ' + $cw + 'x' + $ch + ')')
