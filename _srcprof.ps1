Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$dir = 'c:\WORK\AI\New folder (2)\ai_voice_agent\assets\klyopa\klepa_parts'
$names = 'body_shoulders.png', 'hair_variant_1.png', 'hair_variant_6.png', 'head_base.png'
foreach ($n in $names) {
  $bmp = New-Object System.Drawing.Bitmap ($dir + '\' + $n)
  $w = $bmp.Width; $h = $bmp.Height
  $d = $bmp.LockBits((New-Object System.Drawing.Rectangle 0, 0, $w, $h), [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $st = [Math]::Abs($d.Stride); $b = New-Object byte[] ($st * $h)
  [System.Runtime.InteropServices.Marshal]::Copy($d.Scan0, $b, 0, $b.Length)
  $bmp.UnlockBits($d); $bmp.Dispose()
  Write-Output ('=== ' + $n + '  ' + $w + 'x' + $h)
  $step = [Math]::Max(1, [Math]::Floor($h / 30))
  for ($y = 0; $y -lt $h; $y += $step) {
    $row = $y * $st; $lo = -1; $hi = -1; $segs = ''; $runLo = -1
    for ($x = 0; $x -le $w; $x++) {
      $s = 0
      if ($x -lt $w -and [int]$b[$row + $x * 4 + 3] -gt 60) { $s = 1 }
      if ($s -eq 1 -and $runLo -lt 0) { $runLo = $x }
      if ($s -eq 0 -and $runLo -ge 0) {
        if ($lo -lt 0) { $lo = $runLo }
        $hi = $x - 1
        if (($x - $runLo) -gt 4) { $segs += ('[{0}..{1}] ' -f $runLo, ($x - 1)) }
        $runLo = -1
      }
    }
    if ($lo -lt 0) { continue }
    Write-Output ('  y={0,3} ({1,4:N0}%) span x[{2,3}..{3,3}] w={4,3}  segs: {5}' -f $y, (100 * $y / ($h - 1)), $lo, $hi, ($hi - $lo + 1), $segs)
  }
}
