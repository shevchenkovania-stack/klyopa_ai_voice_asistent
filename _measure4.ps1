Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

function Get-Locked($path) {
  $bmp = New-Object System.Drawing.Bitmap $path
  $w = $bmp.Width; $h = $bmp.Height
  $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
  $d = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $stride = [Math]::Abs($d.Stride)
  $bytes = New-Object byte[] ($stride * $h)
  [System.Runtime.InteropServices.Marshal]::Copy($d.Scan0, $bytes, 0, $bytes.Length)
  $bmp.UnlockBits($d); $bmp.Dispose()
  return @{ w = $w; h = $h; stride = $stride; bytes = $bytes }
}

$ref = Get-Locked 'C:\Users\isevcenco\AppData\Roaming\Qoder\SharedClientCache\cache\images\480c5669\preview - Copy-43c98252.jpg'
$rw = $ref.w; $rh = $ref.h; $rs = $ref.stride; $rb = $ref.bytes

$skin = New-Object byte[] ($rw * $rh)
$hair = New-Object byte[] ($rw * $rh)
for ($y = 0; $y -lt $rh; $y++) {
  $row = $y * $rs; $base = $y * $rw
  for ($x = 0; $x -lt $rw; $x++) {
    $o = $row + $x * 4
    $b = [int]$rb[$o]; $g = [int]$rb[$o + 1]; $r = [int]$rb[$o + 2]
    if ($r -gt 235 -and $g -gt 195 -and ($g - $b) -ge 8 -and ($r - $b) -ge 12 -and ($r - $b) -le 62 -and ($r - $g) -le 40) { $skin[$base + $x] = 1 }
    elseif ($r -gt 120 -and ($r - $g) -ge 34 -and ($r - $b) -ge 40) { $hair[$base + $x] = 1 }
  }
}
Write-Output '  y  : longestSkinRun start..end len | hairRun start..end'
for ($y = 0; $y -lt $rh; $y += 3) {
  $bestS = -1; $bestE = -1; $bestLen = 0; $cur = -1
  $hF = -1; $hL = -1
  $base = $y * $rw
  for ($x = 0; $x -lt $rw; $x++) {
    if ($skin[$base + $x] -eq 1) {
      if ($cur -lt 0) { $cur = $x }
      $len = $x - $cur + 1
      if ($len -gt $bestLen) { $bestLen = $len; $bestS = $cur; $bestE = $x }
    } else { $cur = -1 }
    if ($hair[$base + $x] -eq 1) { if ($hF -lt 0) { $hF = $x }; $hL = $x }
  }
  Write-Output ('  ' + $y.ToString('D3') + ' : ' + $bestS.ToString('D3') + '..' + $bestE.ToString('D3') + ' len=' + $bestLen.ToString('D3') + ' | hair ' + $hF.ToString('D3') + '..' + $hL.ToString('D3'))
}

# global stats
$sk = @{ minX = $rw; maxX = -1; minY = $rh; maxY = -1; n = 0 }
$hr = @{ minX = $rw; maxX = -1; minY = $rh; maxY = -1; n = 0 }
for ($y = 0; $y -lt $rh; $y++) { $base = $y * $rw
  for ($x = 0; $x -lt $rw; $x++) {
    if ($skin[$base + $x] -eq 1) { $sk.n++; if ($x -lt $sk.minX) { $sk.minX = $x }; if ($x -gt $sk.maxX) { $sk.maxX = $x }; if ($y -lt $sk.minY) { $sk.minY = $y }; if ($y -gt $sk.maxY) { $sk.maxY = $y } }
    if ($hair[$base + $x] -eq 1) { $hr.n++; if ($x -lt $hr.minX) { $hr.minX = $x }; if ($x -gt $hr.maxX) { $hr.maxX = $x }; if ($y -lt $hr.minY) { $hr.minY = $y }; if ($y -gt $hr.maxY) { $hr.maxY = $y } }
  }
}
Write-Output ('SKIN bbox x[' + $sk.minX + '..' + $sk.maxX + '] y[' + $sk.minY + '..' + $sk.maxY + '] n=' + $sk.n)
Write-Output ('HAIR bbox x[' + $hr.minX + '..' + $hr.maxX + '] y[' + $hr.minY + '..' + $hr.maxY + '] n=' + $hr.n)

# ---- parts alpha content ----
function Part($rel) {
  $p = 'c:\WORK\AI\New folder (2)\ai_voice_agent\assets\klyopa\' + $rel
  if (-not (Test-Path $p)) { Write-Output ($rel + ' MISSING'); return }
  $i = Get-Locked $p
  $pw = $i.w; $ph = $i.h; $ps = $i.stride; $pb = $i.bytes
  $minX = $pw; $minY = $ph; $maxX = -1; $maxY = -1; $n = 0
  for ($y = 0; $y -lt $ph; $y++) { $row = $y * $ps
    for ($x = 0; $x -lt $pw; $x++) {
      if ([int]$pb[$row + $x * 4 + 3] -gt 40) { $n++
        if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
        if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y } }
    }
  }
  Write-Output ('PART ' + $rel + ' crop=' + $pw + 'x' + $ph + ' content x[' + $minX + '..' + $maxX + '] w=' + ($maxX - $minX + 1) + ' y[' + $minY + '..' + $maxY + '] h=' + ($maxY - $minY + 1) + ' opaque=' + $n)
  $stepY = [Math]::Max(1, [Math]::Floor(($maxY - $minY + 1) / 14))
  for ($y = $minY; $y -le $maxY; $y += $stepY) {
    $f = -1; $l = -1; $c = 0
    for ($x = 0; $x -lt $pw; $x++) { if ([int]$pb[$y * $ps + $x * 4 + 3] -gt 40) { $c++; if ($f -lt 0) { $f = $x }; $l = $x } }
    Write-Output ('     y=' + $y + ' x[' + $f + '..' + $l + '] n=' + $c)
  }
}
foreach ($f in 'klepa_parts/head_base.png', 'klepa_parts/hair_variant_1.png', 'klepa_parts/hair_variant_6.png', 'klepa_parts/hair_variant_3.png') { Part $f }
