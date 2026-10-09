$ErrorActionPreference = 'Stop'
# Canvas = the ORIGINAL ARTWORK (ref_original.jpg 335x290) as coordinate system.
# Targets below are read directly off ref_original.jpg (the clean reference face).
$PADX = 15; $PADY = 15
$CW = 335 + 2 * $PADX; $CH = 290 + 2 * $PADY

# KEY FIXES vs the "horror" version:
#  - head_base.png ALREADY contains the small ears -> do NOT add ear sprites (removed).
#  - hair_variant_1 is the big fluffy back hair; scale it to FRAME the face (like the reference).
#  - brows are thin/light; keep their target box thin.
#  - mouth is a small soft smile (mouth_smile_5), not the bright red grin.
# LAYER ORDER: body -> hair_back -> face -> eyes -> brows -> nose -> mouth -> bangs(front hair)

$rows = @(
  @{ k='body';            f='klepa_parts/body_shoulders.png';  cw=114; ch=73;  c=@(14,12,101,69);   t=@(100,248,235,290) },
  @{ k='hair_back';       f='klepa_parts/hair_variant_1.png';  cw=146; ch=139; c=@(3,9,135,129);    t=@(18,4,296,250) },
  @{ k='face_base';       f='klepa_parts/head_base.png';       cw=142; ch=129; c=@(8,5,133,121);    t=@(95,92,240,250) },
  @{ k='left_eye';        f='klepa_parts/eye_left_full_1.png'; cw=111; ch=83;  c=@(28,11,84,74);    t=@(104,134,158,190) },
  @{ k='right_eye';       f='klepa_parts/eye_left_full_2.png'; cw=100; ch=80;  c=@(22,10,77,69);    t=@(180,134,233,190) },
  @{ k='left_eye_closed'; f='klepa_parts/eye_or_mouth_line_1.png'; cw=117; ch=55; c=@(19,26,88,37); t=@(108,156,156,170) },
  @{ k='right_eye_closed';f='klepa_parts/eye_or_mouth_line_2.png'; cw=118; ch=56; c=@(19,25,87,38); t=@(181,156,229,170) },
  @{ k='left_brow';       f='klepa_parts/brow_left_style1.png';  cw=95; ch=126; c=@(17,38,75,59);  t=@(106,116,154,125) },
  @{ k='right_brow';      f='klepa_parts/brow_right_style1.png'; cw=95; ch=124; c=@(22,36,80,57);  t=@(182,116,230,125) },
  @{ k='nose';            f='klepa_parts/nose_variant_1.png';    cw=120; ch=81; c=@(52,36,67,46);  t=@(160,190,176,202) },
  @{ k='mouth_neutral';   f='klepa_parts/mouth_smile_5.png';     cw=98;  ch=54; c=@(24,23,60,33);  t=@(146,214,190,226) },
  @{ k='mouth_smile';     f='klepa_parts/mouth_smile_5.png';     cw=98;  ch=54; c=@(24,23,60,33);  t=@(142,212,194,228) },
  @{ k='mouth_open';      f='klepa_parts/mouth_open_1.png';      cw=90;  ch=65; c=@(25,16,58,45);  t=@(150,205,186,232) },
  # bangs: front hair over the forehead
  @{ k='hair_front';      f='klepa_parts/hair_variant_6.png';    cw=136; ch=133; c=@(13,22,122,93); t=@(70,4,262,120); layer='front' }
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

$order = 'body', 'hair_back', 'face_base', 'left_eye', 'left_eye_closed', 'right_eye', 'right_eye_closed', 'left_brow', 'right_brow', 'nose'
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
$hk = @('hair_front'); $hlast = $hk[-1]
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
