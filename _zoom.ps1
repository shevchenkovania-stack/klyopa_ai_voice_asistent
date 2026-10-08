Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$ref = New-Object System.Drawing.Bitmap ($root + '\build\ref_original.jpg')
# crops: label, x0,y0,x1,y1 in ref px
$crops = @(
  @('LEFT EAR  x50..95  y150..215', 50, 150, 95, 215),
  @('RIGHT EAR x225..270 y150..215', 225, 150, 270, 215),
  @('CHIN/NECK x110..210 y225..275', 110, 225, 210, 275),
  @('HAIRLINE  x90..230 y60..145',   90,  60, 230, 145)
)
$S = 6
$CELLW = 0
foreach ($c in $crops) { $w = (($c[3] - $c[1] + 1) * $S) + 30; if ($w -gt $CELLW) { $CELLW = $w } }
$CELLH = 0
foreach ($c in $crops) { $h = (($c[4] - $c[2] + 1) * $S) + 30; if ($h -gt $CELLH) { $CELLH = $h } }
$bmp = New-Object System.Drawing.Bitmap $CELLW, ($CELLH * $crops.Count)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::White)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$font = New-Object System.Drawing.Font('Arial', 11)
$thin = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(120, 0, 160, 255)), 1
$r = 0
foreach ($c in $crops) {
  $label = $c[0]; $x0 = $c[1]; $y0 = $c[2]; $x1 = $c[3]; $y1 = $c[4]
  $oy = $r * $CELLH
  $dw = [int](($x1 - $x0 + 1) * $S); $dh = [int](($y1 - $y0 + 1) * $S)
  $dstR = New-Object System.Drawing.Rectangle -ArgumentList @(10, [int]($oy + 22), $dw, $dh)
  $srcR = New-Object System.Drawing.Rectangle -ArgumentList @([int]$x0, [int]$y0, [int]($x1 - $x0 + 1), [int]($y1 - $y0 + 1))
  $g.DrawImage($ref, $dstR, $srcR, [System.Drawing.GraphicsUnit]::Pixel)
  $g.DrawString($label, $font, [System.Drawing.Brushes]::Black, [single]10, [single]$oy)
  for ($x = $x0; $x -le $x1; $x += 5) {
    $px = 10 + ($x - $x0) * $S
    $g.DrawLine($thin, [single]$px, [single]($oy + 22), [single]$px, [single]($oy + 22 + ($y1 - $y0 + 1) * $S))
    if ($x % 25 -eq 0) { $g.DrawString("$x", $font, [System.Drawing.Brushes]::Blue, [single]($px + 1), [single]($oy + 22)) }
  }
  for ($y = $y0; $y -le $y1; $y += 5) {
    $py = $oy + 22 + ($y - $y0) * $S
    $g.DrawLine($thin, [single]10, [single]$py, [single](10 + ($x1 - $x0 + 1) * $S), [single]$py)
    if ($y % 25 -eq 0) { $g.DrawString("$y", $font, [System.Drawing.Brushes]::Blue, [single]10, [single]$py) }
  }
  $r++
}
$g.Dispose()
$bmp.Save(($root + '\build\ref_zoom.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose(); $ref.Dispose()
Write-Output 'zoom -> build\ref_zoom.png'
