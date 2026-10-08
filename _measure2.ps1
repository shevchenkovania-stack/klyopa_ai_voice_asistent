Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

function Lock($bmp) {
  $r = New-Object System.Drawing.Rectangle 0, 0, $bmp.Width, $bmp.Height
  $d = $bmp.LockBits($r, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $stride = [Math]::Abs($d.Stride)
  $bytes = New-Object byte[] ($stride * $bmp.Height)
  [System.Runtime.InteropServices.Marshal]::Copy($d.Scan0, $bytes, 0, $bytes.Length)
  $bmp.UnlockBits($d)
  return @{ bytes = $bytes; stride = $stride }
}
function IsSkinRGB($r, $g, $b) { return ($r -gt 240 -and $g -gt 205 -and $g -lt 248 -and $b -gt 185 -and $b -lt 238 -and ($r - $b) -gt 12 -and ($r - $b) -lt 55) }

# ---------- REFERENCE ----------
$ref = New-Object System.Drawing.Bitmap 'C:\Users\isevcenco\AppData\Roaming\Qoder\SharedClientCache\cache\images\480c5669\preview - Copy-43c98252.jpg'
$rw = $ref.Width; $rh = $ref.Height
$L = Lock $ref; $rb = $L.bytes; $rs = $L.stride
Write-Output ('REF ' + $rw + 'x' + $rh)
# vertical skin extent at center columns
foreach ($x in 150, 158, 166) {
  $first = -1; $last = -1
  for ($y = 0; $y -lt $rh; $y++) {
    $o = $y * $rs + $x * 4
    if (IsSkinRGB ([int]$rb[$o + 2]) [int]$rb[$o + 1] [int]$rb[$o]) { if ($first -lt 0) { $first = $y }; $last = $y }
  }
  Write-Output ('  skin col x=' + $x + ' first=' + $first + ' last=' + $last)
}
Write-Output '  row : skin x-run (first..last, count)   [every 4px]'
for ($y = 60; $y -lt $rh; $y += 4) {
  $f = -1; $l = -1; $n = 0
  for ($x = 0; $x -lt $rw; $x++) {
    $o = $y * $rs + $x * 4
    if (IsSkinRGB ([int]$rb[$o + 2]) [int]$rb[$o + 1] [int]$rb[$o]) { $n++; if ($f -lt 0) { $f = $x }; $l = $x }
  }
  Write-Output ('  {0,3} : {1,3}..{2,3}  n={3}' -f $y, $f, $l, $n)
}
$ref.Dispose()

# ---------- PARTS ----------
function PartInfo($rel) {
  $p = 'c:\WORK\AI\New folder (2)\ai_voice_agent\assets\klyopa\' + $rel
  if (-not (Test-Path $p)) { Write-Output ($rel + ' MISSING'); return }
  $img = New-Object System.Drawing.Bitmap $p
  $iw = $img.Width; $ih = $img.Height
  $L = Lock $img; $b = $L.bytes; $s = $L.stride
  $minX = $iw; $minY = $ih; $maxX = -1; $maxY = -1; $n = 0
  for ($y = 0; $y -lt $ih; $y++) { $row = $y * $s
    for ($x = 0; $x -lt $iw; $x++) {
      if ([int]$b[$row + $x * 4 + 3] -gt 40) { $n++
        if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
        if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y } }
    }
  }
  Write-Output ('--- ' + $rel + '  crop=' + $iw + 'x' + $ih + '  content x[' + $minX + '..' + $maxX + '] w=' + ($maxX - $minX + 1) + ' y[' + $minY + '..' + $maxY + '] h=' + ($maxY - $minY + 1) + '  opaque=' + $n)
  # width profile every 8 rows to spot ears / fringe shape
  $prof = ''
  for ($y = $minY; $y -le $maxY; $y += [Math]::Max(1, [Math]::Floor(($maxY - $minY) / 18))) {
    $f = -1; $l = -1; $c = 0
    for ($x = 0; $x -lt $iw; $x++) { if ([int]$b[$y * $s + $x * 4 + 3] -gt 40) { $c++; if ($f -lt 0) { $f = $x }; $l = $x } }
    $prof += ('   y=' + $y + ' x[' + $f + '..' + $l + '] n=' + $c + "`n")
  }
  Write-Output $prof
  $img.Dispose()
}
foreach ($f in 'klepa_parts/head_base.png', 'klepa_parts/hair_variant_1.png', 'klepa_parts/hair_variant_6.png') { PartInfo $f }
