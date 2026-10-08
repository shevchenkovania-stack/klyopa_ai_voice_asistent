Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$cfg = Get-Content -Raw ($root + '\assets\klyopa\klyopa_config.json') | ConvertFrom-Json
$parts = $cfg.parts
$cw = [int]$cfg.canvas.width; $ch = [int]$cfg.canvas.height
$SCALE = 2
$PADX = 20; $PADY = 20

$ref = New-Object System.Drawing.Bitmap ($root + '\build\ref_original.jpg')
$REF_SCALE = 1.0  # ref_original.jpg IS the coordinate system (335x290)
$PANEL = $cw * $SCALE
$OW = 3 * $PANEL + 24
$OH = [Math]::Max($ch * $SCALE, [int]($ref.Height * $REF_SCALE * $SCALE) + $PADY * $SCALE)
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
DrawPart $parts.hair_back
DrawHairLayer 'back'
DrawPart $parts.face_base
DrawPart $parts.face_shadow
DrawPart $parts.left_eye
DrawPart $parts.right_eye
DrawPart $parts.left_brow
DrawPart $parts.right_brow
DrawPart $parts.nose
if ($parts.mouth_variants) { DrawPart ($parts.mouth_variants | Where-Object { $_.id -eq 'mouth_neutral' } | Select-Object -First 1) }
DrawHairLayer 'front'
DrawPart $parts.left_ear
DrawPart $parts.right_ear

$refW = [int]($ref.Width * $REF_SCALE * $SCALE); $refH = [int]($ref.Height * $REF_SCALE * $SCALE)
$g.DrawImage($ref, [single]($PANEL + 12 + $PADX * $SCALE), [single]($PADY * $SCALE), [single]$refW, [single]$refH)

# panel 3: 50% reference overlaid on the composite, aligned to canvas PADX/PADY
$cm = New-Object System.Drawing.Imaging.ColorMatrix
$cm.Matrix33 = 0.5
$ia = New-Object System.Drawing.Imaging.ImageAttributes
[void]$ia.SetColorMatrix($cm)
$ox = (2 * $PANEL + 12) + $PADX * $SCALE
$oy = $PADY * $SCALE
$dst = New-Object System.Drawing.Rectangle ([int]$ox, [int]$oy, $refW, $refH)
$g.DrawImage($ref, $dst, 0, 0, $ref.Width, $ref.Height, ([System.Drawing.GraphicsUnit]::Pixel), $ia)

$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::Lime), 2
$g.DrawRectangle($pen, 0, 0, ($PANEL) - 1, ($ch * $SCALE) - 1)
$g.DrawRectangle($pen, [single](2 * $PANEL + 12), [single]0, [single]($PANEL) - 1, [single]($ch * $SCALE) - 1)
$g.Dispose()
$out.Save(($root + '\build\klyopa_compare.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$out.Dispose(); $ref.Dispose()
Write-Output ('rendered ' + $OW + 'x' + $OH + ' -> build\klyopa_compare.png  (canvas ' + $cw + 'x' + $ch + ')')
