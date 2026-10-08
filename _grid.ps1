Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$ref = New-Object System.Drawing.Bitmap ($root + '\build\ref_original.jpg')
$S = 3
$bmp = New-Object System.Drawing.Bitmap ($ref.Width * $S), ($ref.Height * $S)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.DrawImage($ref, 0, 0, ($ref.Width * $S), ($ref.Height * $S))
$thin = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(110, 0, 160, 255)), 1
$bold = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(200, 0, 90, 255)), 2
$font = New-Object System.Drawing.Font('Arial', 9)
for ($x = 0; $x -le $ref.Width; $x += 10) {
  $p = if ($x % 50 -eq 0) { $bold } else { $thin }
  $g.DrawLine($p, [single]($x * $S), 0, [single]($x * $S), ($ref.Height * $S))
  if ($x % 50 -eq 0) { $g.DrawString("$x", $font, [System.Drawing.Brushes]::Blue, [single]($x * $S + 2), [single]2) }
}
for ($y = 0; $y -le $ref.Height; $y += 10) {
  $p = if ($y % 50 -eq 0) { $bold } else { $thin }
  $g.DrawLine($p, 0, [single]($y * $S), ($ref.Width * $S), [single]($y * $S))
  if ($y % 50 -eq 0) { $g.DrawString("$y", $font, [System.Drawing.Brushes]::Blue, [single]2, [single]($y * $S + 2)) }
}
$g.Dispose()
$bmp.Save(($root + '\build\ref_grid.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose(); $ref.Dispose()
Write-Output ('grid -> build\ref_grid.png  (3x, blue lines every 10 src px, labels every 50)')
