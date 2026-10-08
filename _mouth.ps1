Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$dir = 'c:\WORK\AI\New folder (2)\ai_voice_agent\assets\klyopa\klepa_parts'
$out = 'c:\WORK\AI\New folder (2)\ai_voice_agent\build\mouth_zoom.png'
$names = 'mouth_smile_1.png', 'mouth_smile_3.png', 'mouth_smile_5.png', 'mouth_smirk_2.png', 'mouth_line_variant_2.png', 'mouth_open_1.png'
$S = 3; $CELLH = 210; $CELLW = 430
$bmp = New-Object System.Drawing.Bitmap $CELLW, ($CELLH * $names.Count)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::White)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::Gray), 1
$font = New-Object System.Drawing.Font('Arial', 11)
$r = 0
foreach ($n in $names) {
  $img = New-Object System.Drawing.Bitmap ($dir + '\' + $n)
  $oy = $r * $CELLH
  $g.DrawImage($img, [single]10, [single]($oy + 20), [single]($img.Width * $S), [single]($img.Height * $S))
  $g.DrawString($n, $font, [System.Drawing.Brushes]::Black, [single]10, [single]($oy + 2))
  $g.DrawLine($pen, 0, $oy, $CELLW, $oy)
  # horizontal guide lines every 10 source px so curvature direction is obvious
  for ($yy = 0; $yy -lt $img.Height; $yy += 10) {
    $g.DrawLine($pen, [single]10, [single]($oy + 20 + $yy * $S), [single](10 + $img.Width * $S), [single]($oy + 20 + $yy * $S))
  }
  $img.Dispose(); $r++
}
$g.Dispose()
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output ('zoom -> ' + $out)
