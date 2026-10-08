Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$cfg = Get-Content -Raw ($root + '\assets\klyopa\klyopa_config.json') | ConvertFrom-Json
$parts = $cfg.parts
$cw = [int]$cfg.canvas.width; $ch = [int]$cfg.canvas.height
$PX = 10; $PY = 12; $S = 2

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
$rc = New-Object System.Drawing.Rectangle 0, 0, $cw, $ch
$dc = $bmp.LockBits($rc, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$cs = [Math]::Abs($dc.Stride); $cb = New-Object byte[] ($cs * $ch)
[System.Runtime.InteropServices.Marshal]::Copy($dc.Scan0, $cb, 0, $cb.Length)
$bmp.UnlockBits($dc); $bmp.Dispose()

$ref = New-Object System.Drawing.Bitmap ($root + '\build\ref_original.jpg')
$rw = $ref.Width; $rh = $ref.Height
$rr = New-Object System.Drawing.Rectangle 0, 0, $rw, $rh
$rd = $ref.LockBits($rr, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$rs = [Math]::Abs($rd.Stride); $rb = New-Object byte[] ($rs * $rh)
[System.Runtime.InteropServices.Marshal]::Copy($rd.Scan0, $rb, 0, $rb.Length)
$ref.UnlockBits($rd); $ref.Dispose()

# skin = r>235 and (g-b)>=8 and g>195
$outW = $rw * $S; $outH = $rh * $S
$ob = New-Object System.Drawing.Bitmap $outW, $outH, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$ro = $ob.LockBits((New-Object System.Drawing.Rectangle 0, 0, $outW, $outH), [System.Drawing.Imaging.ImageLockMode]::WriteOnly, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb))
$os = [Math]::Abs($ro.Stride); $oBuf = New-Object byte[] ($os * $outH)
$cnt = New-Object int[] (16 * 7)
for ($y = 0; $y -lt $rh; $y++) {
  $rowBase = ($y * $S) * $os
  for ($x = 0; $x -lt $rw; $x++) {
    $i = $y * $rs + $x * 4
    $b = [int]$rb[$i]; $gg = [int]$rb[$i + 1]; $r = [int]$rb[$i + 2]
    $refHair = -not ($r -gt 236 -and $gg -gt 236 -and $b -gt 236)
    $refSkin = ($r -gt 244 -and ($gg - $b) -ge 6 -and $gg -gt 186)
    $cy = $y + $PY; $cx = $x + $PX
    $cOp = $false; $cSkin = $false
    if ($cy -ge 0 -and $cy -lt $ch -and $cx -ge 0 -and $cx -lt $cw) {
      $j = $cy * $cs + $cx * 4
      $ca = [int]$cb[$j + 3]; $cbb = [int]$cb[$j]; $cgg = [int]$cb[$j + 1]; $cr = [int]$cb[$j + 2]
      $cOp = $ca -gt 40
      $cSkin = ($ca -gt 200 -and $cr -gt 244 -and ($cgg - $cbb) -ge 6 -and $cgg -gt 186)
    }
    $B = 40; $G = 40; $R = 40   # dark slate background
    $cls = 0
    if ($refSkin -and $cSkin) { $B = 0;   $G = 255; $R = 0;   $cls = 1 }
    elseif ($refSkin) { $B = 0;   $G = 0;   $R = 255; $cls = 2 }
    elseif ($cSkin) { $B = 255; $G = 0;   $R = 0;   $cls = 3 }
    elseif ($refHair -and $cOp) { $B = 0;   $G = 255; $R = 255; $cls = 4 }
    elseif ($refHair) { $B = 0;   $G = 165; $R = 255; $cls = 5 }
    elseif ($cOp) { $B = 255; $G = 255; $R = 0;   $cls = 6 }
    $cnt[([Math]::Floor($y / 20)) * 7 + $cls]++
    for ($dy = 0; $dy -lt $S; $dy++) {
      $p = $rowBase + $dy * $os + ($x * $S) * 4
      for ($dx = 0; $dx -lt $S; $dx++) {
        $oBuf[$p] = $B; $oBuf[$p + 1] = $G; $oBuf[$p + 2] = $R; $oBuf[$p + 3] = 255
        $p += 4
      }
    }
  }
}
[System.Runtime.InteropServices.Marshal]::Copy($oBuf, 0, $ro.Scan0, $oBuf.Length)
$ob.UnlockBits($ro)
$ob.Save(($root + '\build\sil_diff.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$ob.Dispose()
Write-Output 'band y-range   | lime | REDrefOnly | BLUEcompOnly | yellow | ORANGEREFO | CYANcomp'
for ($bi = 0; $bi -lt 15; $bi++) {
  $tot = 0
  for ($q = 1; $q -le 6; $q++) { $tot += $cnt[$bi * 7 + $q] }
  if ($tot -eq 0) { continue }
  $line = '  {0,3} {1,3}-{2,3}  |' -f $bi, ($bi * 20), ($bi * 20 + 19)
  for ($q = 1; $q -le 6; $q++) { $line += (' {0,6} |' -f $cnt[$bi * 7 + $q]) }
  Write-Output $line
}
Write-Output 'legend: YELLOW=hair both | ORANGE=hair ref-only | CYAN=hair comp-only | LIME=skin both | RED=skin ref-only | BLUE=skin comp-only'
Write-Output ('diff -> build\sil_diff.png')
