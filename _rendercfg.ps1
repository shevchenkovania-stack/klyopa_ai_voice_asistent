$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$cfgPath = $root + '\assets\klyopa\klyopa_config.json'
$dir = $root + '\assets\klyopa\'
$cfg = Get-Content $cfgPath -Raw | ConvertFrom-Json
$parts = $cfg.parts
$CW = [int]$cfg.canvas.width; $CH = [int]$cfg.canvas.height
$SCALE = 1.6

$bmp = New-Object System.Drawing.Bitmap $CW, $CH
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::FromArgb(24, 26, 34))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

function DrawPart($p) {
  if ($null -eq $p) { return }
  $path = $dir + $p.file
  if (-not (Test-Path $path)) { Write-Output ('MISSING ' + $p.file); return }
  $img = [System.Drawing.Image]::FromFile($path)
  $x = [double]$p.x; $y = [double]$p.y; $w = [double]$p.width; $h = [double]$p.height
  if ($p.PSObject.Properties.Name -contains 'scale_hint') {
    $s = [double]$p.scale_hint; $nw = $w * $s; $nh = $h * $s
    $x += ($w - $nw) / 2; $y += ($h - $nh) / 2; $w = $nw; $h = $nh
  }
  $g.DrawImage($img, [single]$x, [single]$y, [single]$w, [single]$h)
  $img.Dispose()
}

function DrawHair($layer) {
  if ($null -eq $parts.hair_variants) { return }
  foreach ($hv in $parts.hair_variants) {
    $l = if ($hv.PSObject.Properties.Name -contains 'layer') { $hv.layer } else { 'front' }
    if ($l -eq $layer) { DrawPart $hv }
  }
}

# ---- painter order ----
DrawPart $parts.face_base
DrawHair 'back'
DrawPart $parts.left_eye
DrawPart $parts.right_eye
DrawPart $parts.left_brow
DrawPart $parts.right_brow
DrawHair 'front'

$OW = [int]($CW * $SCALE); $OH = [int]($CH * $SCALE)
$out = New-Object System.Drawing.Bitmap $OW, $OH
$go = [System.Drawing.Graphics]::FromImage($out)
$go.Clear([System.Drawing.Color]::FromArgb(24, 26, 34))
$go.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$go.DrawImage($bmp, 0, 0, $OW, $OH)
$go.Dispose(); $g.Dispose(); $bmp.Dispose()
$dest = $root + '\build\face_config_render.png'
$out.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
$out.Dispose()
Write-Output ('rendered ' + $OW + 'x' + $OH + ' -> ' + $dest)
