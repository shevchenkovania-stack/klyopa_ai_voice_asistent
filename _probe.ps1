Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$cfg = Get-Content -Raw ($root + '\assets\klyopa\klyopa_config.json') | ConvertFrom-Json
$parts = $cfg.parts
$cw = [int]$cfg.canvas.width; $ch = [int]$cfg.canvas.height
$PX = 10; $PY = 12

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
DrawPart $parts.face_base
foreach ($v in $parts.hair_variants) { DrawPart $v }
$g.Dispose()
$rc = New-Object System.Drawing.Rectangle 0, 0, $cw, $ch
$dc = $bmp.LockBits($rc, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$cs = [Math]::Abs($dc.Stride); $cb = New-Object byte[] ($cs * $ch)
[System.Runtime.InteropServices.Marshal]::Copy($dc.Scan0, $cb, 0, $cb.Length)
$bmp.UnlockBits($dc); $bmp.Dispose()

$ref = New-Object System.Drawing.Bitmap ($root + '\build\ref_original.jpg')
$rs = $ref.Width * 4
$rr = New-Object System.Drawing.Rectangle 0, 0, $ref.Width, $ref.Height
$rd = $ref.LockBits($rr, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$rs = [Math]::Abs($rd.Stride); $rb = New-Object byte[] ($rs * $ref.Height)
[System.Runtime.InteropServices.Marshal]::Copy($rd.Scan0, $rb, 0, $rb.Length)
$ref.UnlockBits($rd); $ref.Dispose()

$pts = @(@(158,100), @(158,120), @(158,200), @(158,230), @(120,200), @(200,200), @(158,250), @(158,262), @(100,170), @(60,150), @(158,30), @(158,60))
foreach ($p in $pts) {
  $rx = $p[0]; $ry = $p[1]; $cx = $rx + $PX; $cy = $ry + $PY
  $i = $ry * $rs + $rx * 4
  $j = $cy * $cs + $cx * 4
  Write-Output ('ref({0},{1})  REF rgb=({2},{3},{4})   COMP rgb=({5},{6},{7}) a={8}' -f $rx, $ry, `
    ([int]$rb[$i + 2]), ([int]$rb[$i + 1]), ([int]$rb[$i]), ([int]$cb[$j + 2]), ([int]$cb[$j + 1]), ([int]$cb[$j]), ([int]$cb[$j + 3]))
}
