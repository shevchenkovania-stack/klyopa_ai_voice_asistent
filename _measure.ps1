Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'

# Measure key sprites content bounds
$dir = $root + '\assets\klyopa\klepa_parts'
$keyParts = 'head_base.png','ear_left.png','ear_right.png','eye_left_full_1.png','eye_left_full_2.png','hair_variant_1.png','hair_variant_6.png','body_shoulders.png','nose_variant_1.png','mouth_smile_5.png','brow_left_style1.png','brow_right_style1.png'
foreach ($n in $keyParts) {
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
  $cw = $x1 - $x0 + 1; $ch = $y1 - $y0 + 1
  Write-Output ('{0,-25} {1}x{2}  content x[{3}..{4}] y[{5}..{6}]  {7}x{8}' -f $n, $w, $h, $x0, $x1, $y0, $y1, $cw, $ch)
}

# Now measure the REFERENCE artwork landmarks
Write-Output ''
Write-Output '=== REFERENCE ref_original.jpg landmarks ==='
$ref = New-Object System.Drawing.Bitmap ($root + '\build\ref_original.jpg')
$rw = $ref.Width; $rh = $ref.Height
$rd = $ref.LockBits((New-Object System.Drawing.Rectangle 0, 0, $rw, $rh), [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$rs = [Math]::Abs($rd.Stride); $rb = New-Object byte[] ($rs * $rh)
[System.Runtime.InteropServices.Marshal]::Copy($rd.Scan0, $rb, 0, $rb.Length)
$ref.UnlockBits($rd); $ref.Dispose()
Write-Output "Image size: $rw x $rh"

# Find skin pixels (R>240, G>180, G-B>=5) and hair (non-white, non-skin)
# Scan specific rows and columns for landmarks
function GetPixel($x, $y) {
  $i = $y * $rs + $x * 4
  return @([int]$rb[$i + 2], [int]$rb[$i + 1], [int]$rb[$i])  # R, G, B
}
function IsSkin($x, $y) {
  if ($x -lt 0 -or $x -ge $rw -or $y -lt 0 -or $y -ge $rh) { return $false }
  $p = GetPixel $x $y
  return ($p[0] -gt 240 -and $p[1] -gt 180 -and ($p[1] - $p[2]) -ge 5)
}
function IsWhite($x, $y) {
  if ($x -lt 0 -or $x -ge $rw -or $y -lt 0 -or $y -ge $rh) { return $true }
  $p = GetPixel $x $y
  return ($p[0] -gt 245 -and $p[1] -gt 245 -and $p[2] -gt 245)
}
function IsDark($x, $y) {
  # eyes/pupils - very dark pixels
  if ($x -lt 0 -or $x -ge $rw -or $y -lt 0 -or $y -ge $rh) { return $false }
  $p = GetPixel $x $y
  return ($p[0] -lt 80 -and $p[1] -lt 80 -and $p[2] -lt 100)
}
function IsBlue($x, $y) {
  # iris blue
  if ($x -lt 0 -or $x -ge $rw -or $y -lt 0 -or $y -ge $rh) { return $false }
  $p = GetPixel $x $y
  return ($p[2] -gt 140 -and $p[2] -gt $p[0] -and $p[1] -gt 100)
}

# Find face oval: scan center column for skin
Write-Output ''
Write-Output '--- Vertical scan at center (x=167) ---'
$prevSkin = $false
for ($y = 0; $y -lt $rh; $y++) {
  $sk = IsSkin 167 $y
  if ($sk -ne $prevSkin) {
    $label = if ($sk) { 'SKIN START' } else { 'SKIN END' }
    Write-Output ("  y={0}: {1}" -f $y, $label)
    $prevSkin = $sk
  }
}

# Find eyes: scan for dark pixels
Write-Output ''
Write-Output '--- Dark pixels (eyes) scan ---'
# Find bounding box of dark pixels
$dx0 = $rw; $dx1 = 0; $dy0 = $rh; $dy1 = 0
$ldx0 = $rw; $ldx1 = 0; $ldy0 = $rh; $ldy1 = 0  # left eye
$rdx0 = $rw; $rdx1 = 0; $rdy0 = $rh; $rdy1 = 0  # right eye
for ($y = 0; $y -lt $rh; $y++) {
  for ($x = 0; $x -lt $rw; $x++) {
    if (IsDark $x $y) {
      if ($x -lt $dx0) { $dx0 = $x }; if ($x -gt $dx1) { $dx1 = $x }
      if ($y -lt $dy0) { $dy0 = $y }; if ($y -gt $dy1) { $dy1 = $y }
      if ($x -lt 167) {
        if ($x -lt $ldx0) { $ldx0 = $x }; if ($x -gt $ldx1) { $ldx1 = $x }
        if ($y -lt $ldy0) { $ldy0 = $y }; if ($y -gt $ldy1) { $ldy1 = $y }
      } else {
        if ($x -lt $rdx0) { $rdx0 = $x }; if ($x -gt $rdx1) { $rdx1 = $x }
        if ($y -lt $rdy0) { $rdy0 = $y }; if ($y -gt $rdy1) { $rdy1 = $y }
      }
    }
  }
}
Write-Output "  ALL dark: x[$dx0..$dx1] y[$dy0..$dy1]"
Write-Output "  LEFT eye dark: x[$ldx0..$ldx1] y[$ldy0..$ldy1] ($($ldx1-$ldx0+1)x$($ldy1-$ldy0+1))"
Write-Output "  RIGHT eye dark: x[$rdx0..$rdx1] y[$rdy0..$rdy1] ($($rdx1-$rdx0+1)x$($rdy1-$rdy0+1))"

# Find blue iris pixels for full eye extent
Write-Output ''
Write-Output '--- Blue iris pixels ---'
$bdx0 = $rw; $bdx1 = 0; $bdy0 = $rh; $bdy1 = 0
$bldx0 = $rw; $bldx1 = 0; $bldy0 = $rh; $bldy1 = 0
$brdx0 = $rw; $brdx1 = 0; $brdy0 = $rh; $brdy1 = 0
for ($y = 0; $y -lt $rh; $y++) {
  for ($x = 0; $x -lt $rw; $x++) {
    if (IsBlue $x $y) {
      if ($x -lt $bdx0) { $bdx0 = $x }; if ($x -gt $bdx1) { $bdx1 = $x }
      if ($y -lt $bdy0) { $bdy0 = $y }; if ($y -gt $bdy1) { $bdy1 = $y }
      if ($x -lt 167) {
        if ($x -lt $bldx0) { $bldx0 = $x }; if ($x -gt $bldx1) { $bldx1 = $x }
        if ($y -lt $bldy0) { $bldy0 = $y }; if ($y -gt $bldy1) { $bldy1 = $y }
      } else {
        if ($x -lt $brdx0) { $brdx0 = $x }; if ($x -gt $brdx1) { $brdx1 = $x }
        if ($y -lt $brdy0) { $brdy0 = $y }; if ($y -gt $brdy1) { $brdy1 = $y }
      }
    }
  }
}
Write-Output "  ALL blue: x[$bdx0..$bdx1] y[$bdy0..$bdy1]"
Write-Output "  LEFT iris: x[$bldx0..$bldx1] y[$bldy0..$bldy1] ($($bldx1-$bldx0+1)x$($bldy1-$bldy0+1))"
Write-Output "  RIGHT iris: x[$brdx0..$brdx1] y[$brdy0..$brdy1] ($($brdx1-$brdx0+1)x$($brdy1-$brdy0+1))"

# Find skin islands OUTSIDE the face oval (ears!)
# Face oval is roughly x[90..245] - look for skin at x<90 and x>245
Write-Output ''
Write-Output '--- Ear detection (skin outside face oval) ---'
# Left ear: skin pixels at x[0..88]
$lex0 = 89; $lex1 = 0; $ley0 = $rh; $ley1 = 0
for ($y = 0; $y -lt $rh; $y++) {
  for ($x = 0; $x -lt 89; $x++) {
    if (IsSkin $x $y) {
      if ($x -lt $lex0) { $lex0 = $x }; if ($x -gt $lex1) { $lex1 = $x }
      if ($y -lt $ley0) { $ley0 = $y }; if ($y -gt $ley1) { $ley1 = $y }
    }
  }
}
Write-Output "  LEFT ear skin: x[$lex0..$lex1] y[$ley0..$ley1] ($($lex1-$lex0+1)x$($ley1-$ley0+1))"
# Right ear: skin pixels at x[246..334]
$rex0 = $rw; $rex1 = 245; $rey0 = $rh; $rey1 = 0
for ($y = 0; $y -lt $rh; $y++) {
  for ($x = 246; $x -lt $rw; $x++) {
    if (IsSkin $x $y) {
      if ($x -lt $rex0) { $rex0 = $x }; if ($x -gt $rex1) { $rex1 = $x }
      if ($y -lt $rey0) { $rey0 = $y }; if ($y -gt $rey1) { $rey1 = $y }
    }
  }
}
Write-Output "  RIGHT ear skin: x[$rex0..$rex1] y[$rey0..$rey1] ($($rex1-$rex0+1)x$($rey1-$rey0+1))"

# Hair extent (non-white, non-skin pixels)
Write-Output ''
Write-Output '--- Hair silhouette (non-white non-skin) ---'
$hx0 = $rw; $hx1 = 0; $hy0 = $rh; $hy1 = 0
for ($y = 0; $y -lt $rh; $y++) {
  for ($x = 0; $x -lt $rw; $x++) {
    if (-not (IsWhite $x $y) -and -not (IsSkin $x $y)) {
      if ($x -lt $hx0) { $hx0 = $x }; if ($x -gt $hx1) { $hx1 = $x }
      if ($y -lt $hy0) { $hy0 = $y }; if ($y -gt $hy1) { $hy1 = $y }
    }
  }
}
Write-Output "  HAIR+features: x[$hx0..$hx1] y[$hy0..$hy1] ($($hx1-$hx0+1)x$($hy1-$hy0+1))"

# Mouth: look for dark-ish pixels in the lower face area
Write-Output ''
Write-Output '--- Mouth (dark pixels in y[200..250] x[120..210]) ---'
$mdx0 = 210; $mdx1 = 120; $mdy0 = 250; $mdy1 = 200
for ($y = 200; $y -lt 250; $y++) {
  for ($x = 120; $x -lt 210; $x++) {
    $p = GetPixel $x $y
    # mouth is a darker line/curve, not skin, not white
    if ($p[0] -lt 200 -and -not (IsSkin $x $y)) {
      if ($x -lt $mdx0) { $mdx0 = $x }; if ($x -gt $mdx1) { $mdx1 = $x }
      if ($y -lt $mdy0) { $mdy0 = $y }; if ($y -gt $mdy1) { $mdy1 = $y }
    }
  }
}
Write-Output "  MOUTH: x[$mdx0..$mdx1] y[$mdy0..$mdy1]"

# Chin: last skin row at center
Write-Output ''
Write-Output '--- Chin (last skin at x=167) ---'
for ($y = $rh - 1; $y -ge 0; $y--) {
  if (IsSkin 167 $y) { Write-Output "  Chin y=$y"; break }
}

# Neck width at various rows below chin
Write-Output ''
Write-Output '--- Neck/shoulders width scan ---'
foreach ($y in 245..289) {
  $lo = -1; $hi = -1
  for ($x = 0; $x -lt $rw; $x++) {
    if (IsSkin $x $y) { if ($lo -lt 0) { $lo = $x }; $hi = $x }
  }
  if ($lo -ge 0 -and ($y % 5 -eq 0)) {
    Write-Output ("  y={0}: skin x[{1}..{2}] w={3}" -f $y, $lo, $hi, ($hi-$lo+1))
  }
}
