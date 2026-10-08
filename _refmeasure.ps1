Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$path = 'C:\Users\isevcenco\AppData\Roaming\Qoder\SharedClientCache\cache\images\480c5669\preview - Copy-43c98252.jpg'
$src = New-Object System.Drawing.Bitmap $path
$w = $src.Width; $h = $src.Height
$rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
$data = $src.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = [Math]::Abs($data.Stride)
$bytes = New-Object byte[] ($stride * $h)
[System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
$src.UnlockBits($data); $src.Dispose()

# class per pixel: 0 none, 1 hair, 2 skin, 3 dark, 4 blueIris, 5 mouthRed, 6 blush
$cls = New-Object byte[] ($w * $h)
for ($y = 0; $y -lt $h; $y++) {
  $row = $y * $stride
  for ($x = 0; $x -lt $w; $x++) {
    $o = $row + $x * 4
    $b = [int]$bytes[$o]; $g = [int]$bytes[$o + 1]; $r = [int]$bytes[$o + 2]
    $i = $y * $w + $x
    $lum = ($r + $g + $b) / 3
    $c = 0
    if ($lum -lt 115) { $c = 3 }
    elseif (($b - $r) -gt 8) { $c = 4 }
    elseif (($r - $g) -gt 60 -and ($r - $b) -gt 60 -and $g -lt 120) { $c = 5 }
    elseif (($r - $g) -gt 34 -and ($r - $b) -gt 44) { $c = 1 }
    elseif (($r - $b) -gt 14 -and ($r - $g) -le 34 -and $r -gt 210) { $c = 2 }
    elseif (($r - $g) -gt 40 -and ($b - $g) -gt 12) { $c = 6 }
    $cls[$i] = $c
  }
}

function BBox($want) {
  $minX = $w; $minY = $h; $maxX = -1; $maxY = -1; $n = 0; $sx = 0; $sy = 0
  for ($y = 0; $y -lt $h; $y++) { $base = $y * $w
    for ($x = 0; $x -lt $w; $x++) {
      if ($cls[$base + $x] -eq $want) {
        $n++; $sx += $x; $sy += $y
        if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
        if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
      }
    }
  }
  if ($n -eq 0) { return $null }
  return [pscustomobject]@{ n = $n; minX = $minX; maxX = $maxX; minY = $minY; maxY = $maxY; cx = [Math]::Round($sx / $n, 1); cy = [Math]::Round($sy / $n, 1) }
}

$names = @{ 1 = 'HAIR'; 2 = 'SKIN'; 3 = 'DARK'; 4 = 'BLUE_IRIS'; 5 = 'MOUTH_RED'; 6 = 'BLUSH' }
foreach ($k in 1, 2, 3, 4, 5, 6) {
  $bb = BBox $k
  if ($bb) {
    Write-Output ('{0,-10} n={1,-6} x[{2}..{3}] w={4}  y[{5}..{6}] h={7}  center=({8},{9})' -f $names[$k], $bb.n, $bb.minX, $bb.maxX, ($bb.maxX - $bb.minX + 1), $bb.minY, $bb.maxY, ($bb.maxY - $bb.minY + 1), $bb.cx, $bb.cy)
  }
}

# connected components of DARK (pupil/lash clusters) to locate eyes
$seen = New-Object bool[] ($w * $h)
$comps = New-Object System.Collections.ArrayList
for ($y = 0; $y -lt $h; $y++) {
  for ($x = 0; $x -lt $w; $x++) {
    $i = $y * $w + $x
    if ($cls[$i] -ne 3 -or $seen[$i]) { continue }
    $q = New-Object System.Collections.Generic.Queue[int]; $q.Enqueue($i)
    $seen[$i] = $true
    $pix = New-Object System.Collections.ArrayList
    $minX = $x; $maxX = $x; $minY = $y; $maxY = $y
    while ($q.Count -gt 0) {
      $j = $q.Dequeue(); [void]$pix.Add($j)
      $px = $j % $w; $py = [Math]::Floor($j / $w)
      if ($px -lt $minX) { $minX = $px }; if ($px -gt $maxX) { $maxX = $px }
      if ($py -lt $minY) { $minY = $py }; if ($py -gt $maxY) { $maxY = $py }
      foreach ($d in @(-1, 1, (-$w), $w)) {
        $nj = $j + $d
        if ($nj -lt 0 -or $nj -ge $w * $h) { continue }
        if ($seen[$nj] -or $cls[$nj] -ne 3) { continue }
        $seen[$nj] = $true; $q.Enqueue($nj)
      }
    }
    [void]$comps.Add([pscustomobject]@{ n = $pix.Count; minX = $minX; maxX = $maxX; minY = $minY; maxY = $maxY })
  }
}
Write-Output '--- DARK components (n>=25) ---'
$comps | Where-Object { $_.n -ge 25 } | Sort-Object -Descending n | ForEach-Object {
  Write-Output ('  n={0,-6} x[{1}..{2}] w={3}  y[{4}..{5}] h={6}  cx={7}  cy={8}' -f $_.n, $_.minX, $_.maxX, ($_.maxX - $_.minX + 1), $_.minY, $_.maxY, ($_.maxY - $_.minY + 1), [Math]::Round(($_.minX + $_.maxX) / 2, 1), [Math]::Round(($_.minY + $_.maxY) / 2, 1))
}
