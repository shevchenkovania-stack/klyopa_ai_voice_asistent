Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = 'c:\WORK\AI\New folder (2)\ai_voice_agent'
$names = 'hair_variant_1.png', 'hair_variant_2.png', 'hair_variant_3.png', 'hair_variant_4.png', 'hair_variant_5.png', 'hair_variant_6.png', 'head_base.png', 'body_shoulders.png', 'mouth_smile_5.png', 'mouth_smile_1.png', 'brow_left_style1.png', 'eye_left_full_1.png'
$S = 3
$cellW = 200; $cellH = 175
$cols = 4
$rows = [Math]::Ceiling($names.Count / $cols)
$bmp = New-Object System.Drawing.Bitmap ($cols * $cellW), ($rows * $cellH)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::FromArgb(255, 60, 60, 70))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$font = New-Object System.Drawing.Font('Arial', 10)
$i = 0
foreach ($n in $names) {
  $img = [System.Drawing.Image]::FromFile(($root + '\assets\klyopa\klepa_parts\' + $n))
  $cx = ($i % $cols) * $cellW; $cy = [Math]::Floor($i / $cols) * $cellH
  $dw = $img.Width * $S; $dh = $img.Height * $S
  $fit = [Math]::Min(($cellW - 14) / $dw, ($cellH - 30) / $dh)
  $g.DrawImage($img, [single]($cx + 7), [single]($cy + 20), [single]($dw * $fit), [single]($dh * $fit))
  $g.DrawString(($n.Replace('.png', '') + '  ' + $img.Width + 'x' + $img.Height), $font, [System.Drawing.Brushes]::White, [single]($cx + 4), [single]($cy + 2))
  $img.Dispose(); $i++
}
$g.Dispose()
$bmp.Save(($root + '\build\hair_sheet.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output '-> build\hair_sheet.png'
