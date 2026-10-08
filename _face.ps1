Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$cfg = Get-Content -Raw ($root + '\assets\klyopa\klyopa_config.json') | ConvertFrom-Json
$parts = $cfg.parts
$cw = [int]$cfg.canvas.width; $ch = [int]$cfg.canvas.height
$PX = 10; $PY = 12

# ---------- render composite ----------
$bmp = New-Object System.Drawing.Bitmap $cw, $ch
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
function DrawPart($p) {
  if ($null -eq $p) { return }
  if (-not $p.file) { return }
  $path = $root + '\assets\klyopa\' + $p.file
  if (-not (Test-Path $path)) { return }
  $img = [System.Drawing.Image]::FromFile($path)
  $g.DrawImage($img, [single]$p.x, [single]$p.y, [single]$p.width, [single]$p.height)
  $img.Dispose()
}
DrawPart $parts.body
DrawPart $parts.hair_back
if ($parts.hair_variants) { foreach ($v in $parts.hair_variants) { if ("$($v.layer)" -eq 'back') { DrawPart $v } } }
DrawPart $parts.face_base
DrawPart $parts.left_eye
DrawPart $parts.right_eye
DrawPart $parts.left_brow
DrawPart $parts.right_brow
DrawPart $parts.nose
DrawPart ($parts.mouth_variants | Where-Object { $_.id -eq 'mouth_neutral' } | Select-Object -First 1)
if ($parts.hair_variants) { foreach ($v in $parts.hair_variants) { if ("$($v.layer)" -eq 'front') { DrawPart $v } } }
$g.Dispose()
$dc = $bmp.LockBits((New-Object System.Drawing.Rectangle 0, 0, $cw, $ch), [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$cs = [Math]::Abs($dc.Stride); $cb = New-Object byte[] ($cs * $ch)
[System.Runtime.InteropServices.Marshal]::Copy($dc.Scan0, $cb, 0, $cb.Length)
$bmp.UnlockBits($dc); $bmp.Dispose()

$ref = New-Object System.Drawing.Bitmap ($root + '\build\ref_original.jpg')
$rw = $ref.Width; $rh = $ref.Height
$rd = $ref.LockBits((New-Object System.Drawing.Rectangle 0, 0, $rw, $rh), [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$rs = [Math]::Abs($rd.Stride); $rb = New-Object byte[] ($rs * $rh)
[System.Runtime.InteropServices.Marshal]::Copy($rd.Scan0, $rb, 0, $rb.Length)
$ref.UnlockBits($rd); $ref.Dispose()

# ---------- inline masks ----------
# rSkinR / rHairR / rSkinC indexed [y*w + x], 1 = true
$rSkinR = New-Object byte[] ($rw * $rh)
$rHairR = New-Object byte[] ($rw * $rh)
for ($y = 0; $y -lt $rh; $y++) {
  $row = $y * $rs
  for ($x = 0; $x -lt $rw; $x++) {
    $i = $row + $x * 4
    $bl = [int]$rb[$i]; $gr = [int]$rb[$i + 1]; $r = [int]$rb[$i + 2]
    $sk = 0
    if ($r -gt 244 -and ($gr - $bl) -ge 6 -and $gr -gt 186) { $sk = 1 }
    $wh = 0
    if ($r -gt 236 -and $gr -gt 236 -and $bl -gt 236) { $wh = 1 }
    $rSkinR[$y * $rw + $x] = $sk
    if ($wh -eq 0 -and $sk -eq 0) { $rHairR[$y * $rw + $x] = 1 }
  }
}
$rSkinC = New-Object byte[] ($cw * $ch)
for ($y = 0; $y -lt $ch; $y++) {
  $row = $y * $cs
  for ($x = 0; $x -lt $cw; $x++) {
    $i = $row + $x * 4
    $bl = [int]$cb[$i]; $gr = [int]$cb[$i + 1]; $r = [int]$cb[$i + 2]; $a = [int]$cb[$i + 3]
    if ($a -gt 200 -and $r -gt 244 -and ($gr - $bl) -ge 6 -and $gr -gt 186) { $rSkinC[$y * $cw + $x] = 1 }
  }
}

# ---------- widest skin run per row band, in REFERENCE x space ----------
function BandRunR($y0, $y1) {
  $bestLo = -1; $bestHi = -1
  for ($y = $y0; $y -le $y1; $y++) {
    if ($y -lt 0 -or $y -ge $rh) { continue }
    $base = $y * $rw; $runLo = -1
    for ($x = 0; $x -le $rw; $x++) {
      $s = if ($x -lt $rw) { $rSkinR[$base + $x] } else { 0 }
      if ($s -eq 1 -and $runLo -lt 0) { $runLo = $x }
      if ($s -eq 0 -and $runLo -ge 0) {
        if ($bestHi -lt 0 -or ($x - 1 - $runLo) -gt ($bestHi - $bestLo)) { $bestLo = $runLo; $bestHi = $x - 1 }
        $runLo = -1
      }
    }
  }
  return @($bestLo, $bestHi)
}
function BandRunC($y0, $y1) {
  $bestLo = -1; $bestHi = -1
  for ($y = $y0; $y -le $y1; $y++) {
    $cy = $y + $PY
    if ($cy -lt 0 -or $cy -ge $ch) { continue }
    $base = $cy * $cw; $runLo = -1
    for ($x = 0; $x -le $cw; $x++) {
      $cx = $x + $PX
      $s = 0
      if ($x -lt $cw -and $cx -ge 0 -and $cx -lt $cw) { $s = $rSkinC[$base + $cx] }
      if ($s -eq 1 -and $runLo -lt 0) { $runLo = $x }
      if ($s -eq 0 -and $runLo -ge 0) {
        if ($bestHi -lt 0 -or ($x - 1 - $runLo) -gt ($bestHi - $bestLo)) { $bestLo = $runLo; $bestHi = $x - 1 }
        $runLo = -1
      }
    }
  }
  return @($bestLo, $bestHi)
}
function BandHairR($y0, $y1) {
  $bestLo = -1; $bestHi = -1
  for ($y = $y0; $y -le $y1; $y++) {
    if ($y -lt 0 -or $y -ge $rh) { continue }
    $base = $y * $rw; $runLo = -1
    for ($x = 0; $x -le $rw; $x++) {
      $s = if ($x -lt $rw) { $rHairR[$base + $x] } else { 0 }
      if ($s -eq 1 -and $runLo -lt 0) { $runLo = $x }
      if ($s -eq 0 -and $runLo -ge 0) {
        if ($bestHi -lt 0 -or ($x - 1 - $runLo) -gt ($bestHi - $bestLo)) { $bestLo = $runLo; $bestHi = $x - 1 }
        $runLo = -1
      }
    }
  }
  return @($bestLo, $bestHi)
}

Write-Output 'REF hairline per x (first y>=30 with 12 consecutive skin rows):'
$hl = ''
foreach ($x in 90, 100, 110, 120, 130, 140, 150, 158, 166, 176, 186, 196, 206, 216, 226) {
  $found = -1
  for ($y = 30; $y -lt 150; $y++) {
    if ($rSkinR[$y * $rw + $x] -ne 1) { continue }
    $ok = 1
    for ($k = 1; $k -le 12; $k++) { if ($rSkinR[($y + $k) * $rw + $x] -ne 1) { $ok = 0; break } }
    if ($ok -eq 1) { $found = $y; break }
  }
  $hl += ('x{0}:y{1}  ' -f $x, $found)
}
Write-Output $hl

Write-Output ''
Write-Output '  y  | REF skin x[lo..hi]  w   | COMP skin x[lo..hi] w    | REF HAIR x[lo..hi]  w'
foreach ($y in 60, 70, 80, 85, 90, 95, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270) {
  $a = BandRunR ($y - 2) ($y + 2)
  $b = BandRunC ($y - 2) ($y + 2)
  $h = BandHairR ($y - 2) ($y + 2)
  $wa = ''; if ($a[0] -ge 0) { $wa = $a[1] - $a[0] + 1 }
  $wb = ''; if ($b[0] -ge 0) { $wb = $b[1] - $b[0] + 1 }
  $wh = ''; if ($h[0] -ge 0) { $wh = $h[1] - $h[0] + 1 }
  Write-Output ('{0,4} | x[{1,3}..{2,3}] {3,5}     | x[{4,3}..{5,3}] {6,5}     | x[{7,3}..{8,3}] {9,5}' -f $y, $a[0], $a[1], $wa, $b[0], $b[1], $wb, $h[0], $h[1], $wh)
}
