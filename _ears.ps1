Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$dir = 'c:\WORK\AI\New folder (2)\ai_voice_agent\assets\klyopa\klepa_parts'
foreach ($n in 'ear_left.png', 'ear_right.png') {
  $bmp = New-Object System.Drawing.Bitmap ($dir + '\' + $n)
  $w = $bmp.Width; $h = $bmp.Height
  $d = $bmp.LockBits((New-Object System.Drawing.Rectangle 0, 0, $w, $h), [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $st = [Math]::Abs($d.Stride); $b = New-Object byte[] ($st * $h)
  [System.Runtime.InteropServices.Marshal]::Copy($d.Scan0, $b, 0, $b.Length)
  $bmp.UnlockBits($d); $bmp.Dispose()
  $x0 = $w; $x1 = -1; $y0 = $h; $y1 = -1
  for ($y = 0; $y -lt $h; $y++) {
    $row = $y * $st
    for ($x = 0; $x -lt $w; $x++) {
      if ([int]$b[$row + $x * 4 + 3] -gt 60) {
        if ($x -lt $x0) { $x0 = $x }; if ($x -gt $x1) { $x1 = $x }
        if ($y -lt $y0) { $y0 = $y }; if ($y -gt $y1) { $y1 = $y }
      }
    }
  }
  Write-Output ('=== ' + $n + ' ' + $w + 'x' + $h + '  content x[' + $x0 + '..' + $x1 + '] y[' + $y0 + '..' + $y1 + ']  (' + ($x1 - $x0 + 1) + 'x' + ($y1 - $y0 + 1) + ')')
  # per-row segments so I can see the ear shape (top-heavy = floppy?)
  $step = [Math]::Max(1, [Math]::Floor(($y1 - $y0 + 1) / 18))
  for ($y = $y0; $y -le $y1; $y += $step) {
    $row = $y * $st; $segs = ''; $runLo = -1
    for ($x = $x0; $x -le $x1 + 1; $x++) {
      $s = 0
      if ($x -le $x1 -and [int]$b[$row + $x * 4 + 3] -gt 60) { $s = 1 }
      if ($s -eq 1 -and $runLo -lt 0) { $runLo = $x }
      if ($s -eq 0 -and $runLo -ge 0) { $segs += ('[{0}..{1}] ' -f $runLo, ($x - 1)); $runLo = -1 }
    }
    Write-Output ('  y={0,3} ({1,4:N0}%)  {2}' -f $y, (100 * ($y - $y0) / [Math]::Max(1, $y1 - $y0)), $segs)
  }
}
