$ErrorActionPreference = 'Stop'
# Canvas uses the ORIGINAL ARTWORK (ref_original.jpg 335x290) as coordinate system.
# PADX/PADY allow sprites to extend slightly beyond the artwork bounds.
$PADX = 20; $PADY = 20
$CW = 335 + 2 * $PADX; $CH = 290 + 2 * $PADY

# Landmarks measured from build\ref_original.jpg (335x290):
#   hair silhouette: x[24..292] y[8..240]  (268 wide, 232 tall)
#   hairline centre V: (158, 88)
#   face oval (visible skin): x[90..245] y[86..250]  (156 wide, 164 tall)
#   ear-to-ear: x[57..258] = 202
#   ears: x[57..88] y[163..208] (32x46) / x[228..258] y[163..208] (31x46)
#   eyes: x[95..140] y[143..190] (46x48) / x[175..222] y[143..190] (48x48)
#   brows: x[95..135] y[118..133] / x[180..218] y[117..132]
#   nose: (157, 196)  mouth: x[128..190] y[206..226]
#   chin: y=250  neck: x[128..190] y[250..262]  shoulders: x[90..214] y[270..290]
#
# DEVICE screenshot shows ears ~50% bigger and eyes ~30% bigger relative to face.
# Adjusted targets:
#   ears: 50x66 (from 32x46) positioned at eye level
#   eyes: 62x60 (from 46x48)
#   face: 176x170 (between visible oval and ear-to-ear)
#
# LAYER ORDER: body -> hair_back -> hair(back) -> face -> features -> bangs -> EARS
# Ears are drawn LAST (in front of everything including hair).

$rows = @(
  @{ k='body';            f='klepa_parts/body_shoulders.png'; cw=114; ch=73;  c=@(14,12,101,69);  t=@(100,248,215,290) },
  @{ k='hair_back';       f='klepa_parts/hair_variant_1.png'; cw=146; ch=139; c=@(3,9,135,129);   t=@(20,5,295,245) },
  @{ k='face_base';       f='klepa_parts/head_base.png';      cw=142; ch=129; c=@(8,5,133,121);   t=@(84,86,232,250) },
  @{ k='left_eye';        f='klepa_parts/eye_left_full_1.png';cw=111; ch=83;  c=@(28,11,84,74);   t=@(92,138,146,192) },
  @{ k='right_eye';       f='klepa_parts/eye_left_full_2.png';cw=100; ch=80;  c=@(22,10,77,69);   t=@(174,138,228,192) },
  @{ k='left_eye_closed'; f='klepa_parts/eye_or_mouth_line_1.png'; cw=117; ch=55; c=@(19,26,88,37); t=@(95,155,145,170) },
  @{ k='right_eye_closed';f='klepa_parts/eye_or_mouth_line_2.png'; cw=118; ch=56; c=@(19,25,87,38); t=@(175,155,225,170) },
  @{ k='left_brow';       f='klepa_parts/brow_left_style1.png';  cw=95; ch=126; c=@(17,38,75,59); t=@(90,112,148,130) },
  @{ k='right_brow';      f='klepa_parts/brow_right_style1.png'; cw=95; ch=124; c=@(22,36,80,57); t=@(172,112,230,130) },
  @{ k='nose';            f='klepa_parts/nose_variant_1.png';    cw=120; ch=81; c=@(52,36,67,46); t=@(150,192,166,204) },
  @{ k='mouth_neutral';   f='klepa_parts/mouth_smile_5.png';     cw=98;  ch=54; c=@(24,23,60,33);  t=@(130,212,192,226) },
  @{ k='mouth_smile';     f='klepa_parts/mouth_smile_1.png';     cw=124; ch=64; c=@(22,23,101,43); t=@(127,208,195,228) },
  @{ k='mouth_open';      f='klepa_parts/mouth_open_1.png';      cw=90;  ch=65; c=@(25,16,58,45);  t=@(132,207,190,228) },
  # bangs: front hair covering forehead down to brow level
  @{ k='hair_front';      f='klepa_parts/hair_variant_6.png';    cw=136; ch=133; c=@(13,22,122,93); t=@(55,5,265,115); layer='front' },
  # side locks: behind ears, overlapping face sides to narrow visible face
  @{ k='hair_side_left';  f='klepa_parts/hair_variant_4.png';    cw=136; ch=139; c=@(14,18,99,124); t=@(12,50,105,238); layer='back' },
  @{ k='hair_side_right'; f='klepa_parts/hair_variant_5.png';    cw=132; ch=131; c=@(28,10,112,119); t=@(210,50,303,238); layer='back' }
)

# Ears: drawn LAST (in front of hair). Round, at eye level.
# ear_left.png is actually the RIGHT ear (character perspective) and vice versa.
$earRows = @(
  @{ k='left_ear';        f='klepa_parts/ear_right.png';      cw=89;  ch=125; c=@(21,31,62,100);  t=@(46,148,94,198) },
  @{ k='right_ear';       f='klepa_parts/ear_left.png';       cw=84;  ch=127; c=@(25,33,67,101);  t=@(221,148,269,198) }
)

function Box($r) {
  $cx0 = $r.c[0]; $cy0 = $r.c[1]; $cx1 = $r.c[2]; $cy1 = $r.c[3]
  $contentW = $cx1 - $cx0 + 1; $contentH = $cy1 - $cy0 + 1
  $tx0 = $r.t[0]; $ty0 = $r.t[1]; $tx1 = $r.t[2]; $ty1 = $r.t[3]
  $targetW = $tx1 - $tx0 + 1; $targetH = $ty1 - $ty0 + 1
  $sx = $targetW / $contentW; $sy = $targetH / $contentH
  $x = $tx0 - $cx0 * $sx + $PADX
  $y = $ty0 - $cy0 * $sy + $PADY
  $w = $r.cw * $sx; $h = $r.ch * $sy
  return @{ x = [Math]::Round($x, 1); y = [Math]::Round($y, 1); w = [Math]::Round($w, 1); h = [Math]::Round($h, 1); sx = $sx; sy = $sy }
}

$lines = New-Object System.Collections.ArrayList
[void]$lines.Add('{')
[void]$lines.Add('  "canvas": { "width": ' + $CW + ', "height": ' + $CH + ' },')
[void]$lines.Add('  "parts": {')

function Emit($key, $r, $indent, $tail) {
  $b = Box $r
  Write-Output ('  {0,-16} {1,-36} box=({2},{3}) {4}  sx={5:N3} sy={6:N3}' -f $key, $r.f, $b.x, $b.y, ('{0:N1}x{1:N1}' -f $b.w, $b.h), $b.sx, $b.sy)
  $s = $indent + '"' + $key + '": { "file": "' + $r.f + '", "x": ' + $b.x + ', "y": ' + $b.y + ', "width": ' + $b.w + ', "height": ' + $b.h + ' }' + $tail
  [void]$lines.Add($s)
}

$byKey = @{}
foreach ($r in $rows) { $byKey[$r.k] = $r }
foreach ($r in $earRows) { $byKey[$r.k] = $r }

$order = 'body', 'hair_back', 'face_base', 'left_ear', 'right_ear', 'left_eye', 'left_eye_closed', 'right_eye', 'right_eye_closed', 'left_brow', 'right_brow', 'nose'
foreach ($k in $order) {
  Emit $k $byKey[$k] '    ' ','
}
[void]$lines.Add('    "mouth_variants": [')
$mk = @('mouth_neutral', 'mouth_smile', 'mouth_open'); $mlast = $mk[-1]
foreach ($k in $mk) {
  $b = Box $byKey[$k]
  Write-Output ('  {0,-16} {1,-36} box=({2},{3}) {4}  sx={5:N3} sy={6:N3}' -f $k, $byKey[$k].f, $b.x, $b.y, ('{0:N1}x{1:N1}' -f $b.w, $b.h), $b.sx, $b.sy)
  $tail = if ($k -eq $mlast) { '' } else { ',' }
  [void]$lines.Add('      { "id": "' + $k + '", "file": "' + $byKey[$k].f + '", "x": ' + $b.x + ', "y": ' + $b.y + ', "width": ' + $b.w + ', "height": ' + $b.h + ' }' + $tail)
}
[void]$lines.Add('    ],')
[void]$lines.Add('    "hair_variants": [')
$hk = @('hair_front', 'hair_side_left', 'hair_side_right'); $hlast = $hk[-1]
foreach ($k in $hk) {
  $b = Box $byKey[$k]
  Write-Output ('  {0,-16} {1,-36} box=({2},{3}) {4}  sx={5:N3} sy={6:N3}' -f $k, $byKey[$k].f, $b.x, $b.y, ('{0:N1}x{1:N1}' -f $b.w, $b.h), $b.sx, $b.sy)
  $tail = if ($k -eq $hlast) { '' } else { ',' }
  [void]$lines.Add('      { "id": "' + $k + '", "file": "' + $byKey[$k].f + '", "x": ' + $b.x + ', "y": ' + $b.y + ', "width": ' + $b.w + ', "height": ' + $b.h + ', "layer": "' + $byKey[$k].layer + '" }' + $tail)
}
[void]$lines.Add('    ]')
[void]$lines.Add('  }')
[void]$lines.Add('}')

$out = 'c:\WORK\AI\New folder (2)\ai_voice_agent\assets\klyopa\klyopa_config.json'
$lines -join "`n" | Set-Content -Path $out -Encoding ASCII
Write-Output ('wrote ' + $out + '  canvas ' + $CW + 'x' + $CH)
