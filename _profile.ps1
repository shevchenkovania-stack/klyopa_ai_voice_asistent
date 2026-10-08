Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$cfg = Get-Content -Raw ($root + '\assets\klyopa\klyopa_config.json') | ConvertFrom-Json
$parts = $cfg.parts
$cw = [int]$cfg.canvas.width; $ch = [int]$cfg.canvas.height

# ---- render composite into its own canvas-sized bitmap -------------------
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
DrawPart $parts.left_eye
DrawPart $parts.right_eye
DrawPart $parts.left_brow
DrawPart $parts.right_brow
DrawPart $parts.nose
DrawPart ($parts.mouth_variants | Where-Object { $_.id -eq 'mouth_neutral' } | Select-Object -First 1)
foreach ($v in $parts.hair_variants) { DrawPart $v }
$g.Dispose()

$rect = New-Object System.Drawing.Rectangle 0, 0, $cw, $ch
$d = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$cs = [Math]::Abs($d.Stride); $cb = New-Object byte[] ($cs * $ch)
[System.Runtime.InteropServices.Marshal]::Copy($d.Scan0, $cb, 0, $cb.Length)
$bmp.UnlockBits($d); $bmp.Dispose()

# ---- reference ------------------------------------------------------------
$ref = New-Object System.Drawing.Bitmap ($root + '\build\ref_original.jpg')
$rw = $ref.Width; $rh = $ref.Height
$rr = New-Object System.Drawing.Rectangle 0, 0, $rw, $rh
$rd = $ref.LockBits($rr, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$rs = [Math]::Abs($rd.Stride); $rb = New-Object byte[] ($rs * $rh)
[System.Runtime.InteropServices.Marshal]::Copy($rd.Scan0, $rb, 0, $rb.Length)
$ref.UnlockBits($rd); $ref.Dispose()

# PADX/PADY used by _gen.ps1: canvas = ref + (10,12)
$PX = 10; $PY = 12

Write-Output 'refY |    REF (non-white)    |  W  |   COMPOSITE (opaque)    |  W  | SKIN run (ref)      | SKIN run (comp)'
for ($ry = 10; $ry -le 290; $ry += 10) {
  $cy = $ry + $PY
  # reference: any pixel that is not near-white
  $rl = -1; $rrt = -1
  for ($x = 0; $x -lt $rw; $x++) {
    $i = $cy * $rs + $x * 4
    if ($i -ge $rb.Length) { break }
    $b = [int]$rb[$i]; $gg = [int]$rb[$i + 1]; $r = [int]$rb[$i + 2]
    if (-not ($r -gt 238 -and $gg -gt 238 -and $b -gt 238)) { if ($rl -lt 0) { $rl = $x }; $rrt = $x }
  }
  # composite: alpha > 40
  $cl = -1; $cr = -1
  if ($cy -lt $ch) {
    for ($x = 0; $x -lt $cw; $x++) {
      if ([int]$cb[$cy * $cs + $x * 4 + 3] -gt 40) { if ($cl -lt 0) { $cl = $x }; $cr = $x }
    }
  }
  # skin run: longest contiguous skin-coloured stretch (skin = high R, G notably > B)
  $srl = -1; $srr = -1; $run = ''; $best = ''
  $rx0 = [Math]::Max(0, $rl - 5); $rx1 = [Math]::Min($rw - 1, $rrt + 5)
  if ($rx1 -ge $rx0) {
    for ($x = $rx0; $x -le $rx1; $x++) {
      $i = $cy * $rs + $x * 4
      $b = [int]$rb[$i]; $gg = [int]$rb[$i + 1]; $r = [int]$rb[$i + 2]
      if ($r -gt 235 -and ($gg - $b) -ge 8 -and $gg -gt 195) {
        if ($run -eq '') { $run = "$x" } else { $run += "-$x" }
        if ($run.Length -gt $best.Length) { $best = $run }
      } else { $run = '' }
    }
  }
  $scl = ''; $scr = ''; $run2 = ''; $best2 = ''
  if ($cy -lt $ch) {
    for ($x = 0; $x -lt $cw; $x++) {
      $i = $cy * $cs + $x * 4
      $a = [int]$cb[$i + 3]; $b = [int]$cb[$i]; $gg = [int]$cb[$i + 1]; $r = [int]$cb[$i + 2]
      if ($a -gt 200 -and $r -gt 235 -and ($gg - $b) -ge 8 -and $gg -gt 195) {
        if ($run2 -eq '') { $run2 = "$x" } else { $run2 += "-$x" }
        if ($run2.Length -gt $best2.Length) { $best2 = $run2 }
      } else { $run2 = '' }
    }
  }
  if ($best -match '^(\d+)') { $scl = $Matches[1] }
  if ($best -match '(\d+)$') { $scr = $Matches[1] }
  if ($best2 -match '^(\d+)') { $scl2 = $Matches[1] } else { $scl2 = '' }
  if ($best2 -match '(\d+)$') { $scr2 = $Matches[1] } else { $scr2 = '' }
  $rwid = ''; if ($rl -ge 0) { $rwid = $rrt - $rl + 1 }
  $cwid = ''; if ($cl -ge 0) { $cwid = $cr - $cl + 1 }
  $sw = ''; if ($scl -ne '' -and $scr -ne '') { $sw = [int]$scr - [int]$scl + 1 }
  $cw2 = ''; if ($scl2 -ne '' -and $scr2 -ne '') { $cw2 = [int]$scr2 - [int]$scl2 + 1 }
  Write-Output ('{0,4} | x[{1,3}..{2,3}]              | {3,4} | x[{4,3}..{5,3}]              | {6,4} | x[{7,3}..{8,3}] w={9,3}  | x[{10,3}..{11,3}] w={12,3}' -f `
    $ry, $rl, $rrt, $rwid, $cl, $cr, $cwid, $scl, $scr, $sw, $scl2, $scr2, $cw2)
}
