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
Write-Output ('REF ' + $rw + 'x' + $rh)

# sample raw values to calibrate skin
foreach ($xy in @(@(158,120), @(158,200), @(150,167), @(120,230), @(158,255), @(60,167), @(25,25), @(158,60))) {
  $x = $xy[0]; $y = $xy[1]; $o = $y * $rs + $x * 4
  Write-Output ('  px(' + $x + ',' + $y + ') = B' + [int]$rb[$o] + ' G' + [int]$rb[$o + 1] + ' R' + [int]$rb[$o + 2])
}

# skin mask inline: warm light peach, r-b between 10 and 60, r>235, g>200, b>180, not hair (r-g<=40)
$skin = New-Object byte[] ($rw * $rh)
for ($y = 0; $y -lt $rh; $y++) {
  $row = $y * $rs; $base = $y * $rw
  for ($x = 0; $x -lt $rw; $x++) {
    $o = $row + $x * 4
    $b = [int]$rb[$o]; $g = [int]$rb[$o + 1]; $r = [int]$rb[$o + 2]
    if ($r -gt 235 -and $g -gt 195 -and $b -gt 170 -and ($r - $b) -ge 10 -and ($r - $b) -le 60 -and ($r - $g) -le 40) { $skin[$base + $x] = 1 }
  }
}
foreach ($x in 150, 158, 166) {
  $first = -1; $last = -1
  for ($y = 0; $y -lt $rh; $y++) { if ($skin[$y * $rw + $x] -eq 1) { if ($first -lt 0) { $first = $y }; $last = $y } }
  Write-Output ('  skin col x=' + $x + ' first=' + $first + ' last=' + $last)
}
Write-Output '  y  : skinRun first..last n'
for ($y = 55; $y -lt $rh; $y += 5) {
  $f = -1; $l = -1; $n = 0; $base = $y * $rw
  for ($x = 0; $x -lt $rw; $x++) { if ($skin[$base + $x] -eq 1) { $n++; if ($f -lt 0) { $f = $x }; $l = $x } }
  Write-Output ('  ' + $y.ToString('D3') + ' : ' + $f.ToString('D3') + '..' + $l.ToString('D3') + '  n=' + $n)
}
