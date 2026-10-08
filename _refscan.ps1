Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$src = New-Object System.Drawing.Bitmap 'C:\Users\isevcenco\AppData\Roaming\Qoder\SharedClientCache\cache\images\480c5669\preview - Copy-43c98252.jpg'
$w = $src.Width; $h = $src.Height
function RGB($x, $y) { $c = $src.GetPixel($x, $y); return ('{0},{1},{2}' -f $c.R, $c.G, $c.B) }
Write-Output ('size ' + $w + 'x' + $h)
foreach ($x in 130, 158, 185) {
  Write-Output ('=== column x=' + $x + ' ===')
  $out = ''
  for ($y = 100; $y -lt $h; $y += 3) { $out += ('y{0}:[{1}] ' -f $y, (RGB $x $y)) }
  Write-Output $out
}
Write-Output '=== row y=167 (eye line) ==='
$s = ''
for ($x = 20; $x -lt $w; $x += 6) { $s += ('x{0}:[{1}] ' -f $x, (RGB $x 167)) }
Write-Output $s
Write-Output '=== row y=230 (mouth/chin zone) ==='
$s = ''
for ($x = 60; $x -lt 280; $x += 6) { $s += ('x{0}:[{1}] ' -f $x, (RGB $x 230)) }
Write-Output $s
Write-Output '=== row y=250 ==='
$s = ''
for ($x = 60; $x -lt 280; $x += 6) { $s += ('x{0}:[{1}] ' -f $x, (RGB $x 250)) }
Write-Output $s
$src.Dispose()
