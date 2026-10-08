Add-Type -AssemblyName System.Drawing
$path = 'C:\Users\isevcenco\AppData\Roaming\Qoder\SharedClientCache\cache\images\480c5669\preview - Copy-43c98252.jpg'
$src = New-Object System.Drawing.Bitmap $path
$w = $src.Width; $h = $src.Height
Write-Output ('ref size = ' + $w + 'x' + $h)
$rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
$data = $src.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = [Math]::Abs($data.Stride)
$bytes = New-Object byte[] ($stride * $h)
[System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)

function Classify($x, $y) {
  $o = $y * $stride + $x * 4
  $b = [int]$bytes[$o]; $g = [int]$bytes[$o + 1]; $r = [int]$bytes[$o + 2]
  $lum = ($r + $g + $b) / 3
  if ($lum -gt 246 -and ($r - $b) -lt 6) { return '.' }          # white background
  if ($lum -lt 95) { return 'K' }                                 # very dark: pupil / lash
  if ($b -gt $r + 12 -and $b -gt 90) { return 'B' }               # blue iris
  if ($r -gt 150 -and ($r - $b) -gt 55 -and ($r - $g) -gt 35) { return 'H' }   # hair red-pink
  if ($r -gt 120 -and ($r - $b) -gt 25 -and ($r - $b) -le 55 -and ($g -lt $r - 25)) { return 'h' }  # hair darker / shadow
  if ($r -gt 235 -and $g -gt 195 -and $g -lt 232 -and $b -gt 170 -and $b -lt 215) { return 'S' }    # skin peach
  if ($r -gt 240 -and ($r - $b) -gt 30 -and ($g - $b) -gt 18) { return 's' }   # skin lighter
  if ($r -gt 235 -and ($r - $g) -gt 45 -and ($b - $g) -gt 15) { return 'P' }   # pink blush
  if ($r -gt 200 -and ($r - $g) -gt 90 -and ($g - $b) -gt 20) { return 'M' }   # red-orange mouth
  if ($r -gt 110 -and $r -lt 200 -and ($r - $b) -gt 45 -and ($r - $g) -gt 25 -and $g -gt 60) { return 'W' } # brown brow
  return '?'
}

# coarse map: sample every N px, majority class in the block
$step = [Math]::Max(1, [Math]::Floor($w / 64))
$gw = [Math]::Ceiling($w / $step); $gh = [Math]::Ceiling($h / $step)
for ($gy = 0; $gy -lt $gh; $gy++) {
  $line = ''
  for ($gx = 0; $gx -lt $gw; $gx++) {
    $x0 = $gx * $step; $x1 = [Math]::Min($w, $x0 + $step) - 1
    $y0 = $gy * $step; $y1 = [Math]::Min($h, $y0 + $step) - 1
    $counts = @{}
    for ($yy = $y0; $yy -le $y1; $yy += 2) {
      for ($xx = $x0; $xx -le $x1; $xx += 2) {
        $c = Classify $xx $yy
        if ($counts.ContainsKey($c)) { $counts[$c] = $counts[$c] + 1 } else { $counts[$c] = 1 }
      }
    }
    $best = '.'; $bestN = 0
    foreach ($k in $counts.Keys) { if ($counts[$k] -gt $bestN -and $k -ne '.') { $bestN = $counts[$k]; $best = $k } }
    if ($counts.ContainsKey('.') -and $counts['.'] -gt ($bestN * 1.2)) { $best = '.' }
    $line += $best
  }
  Write-Output (('{0,3} ' -f ($gy * $step)) + $line)
}
Write-Output ('legend: . white  K dark  B blue  H hair  h hair-shadow  S skin  s skin-light  P blush  M mouth  W brow')
Write-Output ('step = ' + $step)
$src.UnlockBits($data); $src.Dispose()
