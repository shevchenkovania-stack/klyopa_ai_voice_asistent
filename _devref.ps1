Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$src = New-Object System.Drawing.Bitmap ($root + '\build\ref_device.jpg')

# Crop just the head area (approximate from visual inspection of the 575x1280 screenshot)
# The face is roughly x[50..530] y[230..730]
$cx0 = 50; $cy0 = 230; $cx1 = 530; $cy1 = 730
$cw = $cx1 - $cx0 + 1; $ch = $cy1 - $cy0 + 1
$crop = New-Object System.Drawing.Bitmap $cw, $ch
$g = [System.Drawing.Graphics]::FromImage($crop)
$srcRect = New-Object System.Drawing.Rectangle -ArgumentList @($cx0, $cy0, $cw, $ch)
$dstRect = New-Object System.Drawing.Rectangle -ArgumentList @(0, 0, $cw, $ch)
$g.DrawImage($src, $dstRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()
$crop.Save(($root + '\build\ref_face_crop.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$crop.Dispose(); $src.Dispose()
Write-Output "cropped head -> build\ref_face_crop.png ($cw x $ch)"

# Now measure landmarks on the crop
$bmp = New-Object System.Drawing.Bitmap ($root + '\build\ref_face_crop.png')
$w = $bmp.Width; $h = $bmp.Height
$d = $bmp.LockBits((New-Object System.Drawing.Rectangle 0, 0, $w, $h), [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$st = [Math]::Abs($d.Stride); $b = New-Object byte[] ($st * $h)
[System.Runtime.InteropServices.Marshal]::Copy($d.Scan0, $b, 0, $b.Length)
$bmp.UnlockBits($d); $bmp.Dispose()

# Classify pixels: skin, hair, dark(eye/pupil), background(dark navy)
function Classify($x, $y) {
  $i = $y * $st + $x * 4
  $bb = [int]$b[$i]; $gg = [int]$b[$i+1]; $r = [int]$b[$i+2]
  # background is dark navy (~15-30, 15-30, 40-60)
  if ($r -lt 60 -and $bb -lt 80 -and $gg -lt 60) { return 'bg' }
  # skin: high R, moderate G, lower B, R > G > B
  if ($r -gt 180 -and $gg -gt 120 -and $bb -gt 80 -and ($r - $bb) -gt 30) { return 'skin' }
  # hair: pinkish-brown, R>150, G<160, B<140, not too saturated
  if ($r -gt 120 -and $r -lt 220 -and $gg -gt 60 -and $bb -gt 60 -and ($r - $gg) -gt 20) { return 'hair' }
  # dark (pupils, brows, outline)
  if ($r -lt 80 -and $gg -lt 80 -and $bb -lt 100) { return 'dark' }
  # eye white / highlight
  if ($r -gt 200 -and $gg -gt 200 -and $bb -gt 200) { return 'white' }
  # blue iris
  if ($bb -gt 140 -and $bb -gt $r) { return 'iris' }
  return 'other'
}

# Find leftmost/rightmost/topmost/bottommost non-bg pixel
$minX = $w; $maxX = -1; $minY = $h; $maxY = -1
for ($y = 0; $y -lt $h; $y += 2) {
  for ($x = 0; $x -lt $w; $x += 2) {
    $c = Classify $x $y
    if ($c -ne 'bg') {
      if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
      if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
    }
  }
}
Write-Output "Content bbox: x[$minX..$maxX] y[$minY..$maxY]  ($(($maxX-$minX+1))x$(($maxY-$minY+1)))"

# Per-row: find skin span (ears + face) and dark span (eyes)
Write-Output ""
Write-Output "=== Per-row landmarks (every 10px) ==="
for ($y = 0; $y -lt $h; $y += 10) {
  $skinLo = -1; $skinHi = -1; $darkLo = -1; $darkHi = -1
  for ($x = 0; $x -lt $w; $x++) {
    $c = Classify $x $y
    if ($c -eq 'skin') { if ($skinLo -lt 0) { $skinLo = $x }; $skinHi = $x }
    if ($c -eq 'dark' -or $c -eq 'iris') { if ($darkLo -lt 0) { $darkLo = $x }; $darkHi = $x }
  }
  $sStr = if ($skinLo -ge 0) { "skin x[$skinLo..$skinHi] w=$($skinHi-$skinLo+1)" } else { '' }
  $dStr = if ($darkLo -ge 0) { "dark x[$darkLo..$darkHi] w=$($darkHi-$darkLo+1)" } else { '' }
  Write-Output ("  y={0,3}  {1}  {2}" -f $y, $sStr, $dStr)
}
