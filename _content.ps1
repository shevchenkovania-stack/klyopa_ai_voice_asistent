Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$dir = 'c:\WORK\AI\New folder (2)\ai_voice_agent\assets\klyopa\'

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

$files = @(
  'klepa_parts/head_base.png',
  'klepa_parts/hair_variant_1.png', 'klepa_parts/hair_variant_2.png',
  'klepa_parts/hair_variant_3.png', 'klepa_parts/hair_variant_4.png',
  'klepa_parts/hair_variant_5.png', 'klepa_parts/hair_variant_6.png',
  'klepa_parts/eye_left_full_1.png', 'klepa_parts/eye_left_full_2.png',
  'klepa_parts/eye_right_simple_1.png',
  'klepa_parts/eye_or_mouth_line_1.png', 'klepa_parts/eye_or_mouth_line_2.png',
  'klepa_parts/brow_left_style1.png', 'klepa_parts/brow_right_style1.png',
  'klepa_parts/nose_variant_1.png',
  'klepa_parts/mouth_smile_1.png', 'klepa_parts/mouth_smile_2.png', 'klepa_parts/mouth_open_1.png',
  'klepa_parts/neck.png', 'klepa_parts/body_shoulders.png',
  'klepa_parts/ear_left.png', 'klepa_parts/ear_right.png',
  'klepa_parts/face_shadow.png'
)
foreach ($rel in $files) {
  $p = $dir + $rel
  if (-not (Test-Path $p)) { Write-Output ($rel + ' MISSING'); continue }
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
  Write-Output ('{0,-38} crop={1,4}x{2,-4} content x[{3}..{4}] w={5} y[{6}..{7}] h={8} fill={9:P0}' -f $rel, $pw, $ph, $minX, $maxX, ($maxX - $minX + 1), $minY, $maxY, ($maxY - $minY + 1), ($n / (($maxX - $minX + 1) * ($maxY - $minY + 1))))
}
