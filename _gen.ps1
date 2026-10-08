$ErrorActionPreference = 'Stop'
# Canvas = reference image (335x290) + padding so no coordinate goes negative.
$PADX = 10; $PADY = 12
$CW = 335 + 2 * $PADX; $CH = 330

# file            cropW cropH  content x0,y0,x1,y1        target box in REFERENCE px (where content lands)
# Landmarks measured off build\ref_original.jpg (335x290) with _face.ps1 /
# _p2.ps1 (widest continuous SKIN run per row = the visible face oval):
#   hair silhouette  x[24..293]  y[13..247]   (widest 270 at y=135..145)
#   hairline: centre V (158,86); x=130 -> y91, x=186 -> y93, x=196 -> y131,
#              x=110..120 and x>=216 are fully covered by hair
#   brows  x[95..135] y[118..133]  /  x[180..218] y[117..132]
#   eyes   x[95..140] y[143..190]  /  x[175..222] y[143..190]
#   visible face  y140 x[97..228] w132 | y190 x[94..230] w137 | y220 w127
#   ears (skin islands outside the face) x[57..88] / x[228..258] y[163..208]
#   nose tip (157,196)   mouth x[128..190] y[208..222]   chin y=250
#   neck x[128..190] (62 wide) y250..265, shoulders 113@270 139@275
# head_base.png is ROUNDER than the reference head (its ears add only 9px per
# side vs 32px in the artwork), so face_base is fitted on the visible face oval
# (132 wide at eye level), NOT on ear-to-ear - otherwise the head balloons.
#
# hair_variant_1/2 = the solid hair CAP (no face opening) -> goes BEHIND the
# head.  hair_variant_6 = the widow's-peak bangs: a band whose bottom edge is
# the hairline plus two temple tabs hanging lower.  Its own landmarks are fitted
# onto the measured reference hairline:
#   band bottom (src y58, centre)  -> ref y87   (ref hairline centre V 86..89)
#   tab bottom   (src y88)         -> ref y132  (ref temple hairline 131..135)
#   tab inner gap (src x48..86)    -> ref x125..192 (ref forehead opening)
#   -> sx = 67/38 = 1.763, sy = 45/30 = 1.50
$rows = @(
  @{ k='body';            f='klepa_parts/body_shoulders.png'; cw=114; ch=73;  c=@(14,12,101,69);  t=@(96,238,218,295) },
  # hair cap volume: widened/raised at the crown so the silhouette reads as a
  # rounded dome instead of the flat-topped "square head" of the previous pass
  @{ k='hair_back';       f='klepa_parts/hair_variant_1.png'; cw=146; ch=139; c=@(3,9,135,129);   t=@(18,4,299,250) },
  @{ k='face_base';       f='klepa_parts/head_base.png';      cw=142; ch=129; c=@(8,5,133,121);   t=@(75,86,241,250) },
  # ears: ref skin islands are x[57..88]/x[228..258] y[163..208].  Kept near the
  # reference width but dropped ~14px lower so they peek out of the hair = floppy.
  # ear_left content x[25..67] y[33..101], ear_right x[21..62] y[31..100].
  @{ k='left_ear';        f='klepa_parts/ear_left.png';       cw=84;  ch=127; c=@(25,33,67,101);  t=@(56,180,92,232) },
  @{ k='right_ear';       f='klepa_parts/ear_right.png';      cw=89;  ch=125; c=@(21,31,62,100);  t=@(222,180,258,232) },
  @{ k='left_eye';        f='klepa_parts/eye_left_full_1.png';cw=111; ch=83;  c=@(28,11,84,74);   t=@(94,140,142,192) },
  @{ k='right_eye';       f='klepa_parts/eye_left_full_2.png';cw=100; ch=80;  c=@(22,10,77,69);   t=@(174,140,223,192) },
  @{ k='left_eye_closed'; f='klepa_parts/eye_or_mouth_line_1.png'; cw=117; ch=55; c=@(19,26,88,37); t=@(100,163,137,176) },
  @{ k='right_eye_closed';f='klepa_parts/eye_or_mouth_line_2.png'; cw=118; ch=56; c=@(19,25,87,38); t=@(180,163,217,176) },
  @{ k='left_brow';       f='klepa_parts/brow_left_style1.png';  cw=95; ch=126; c=@(17,38,75,59); t=@(95,118,135,133) },
  @{ k='right_brow';      f='klepa_parts/brow_right_style1.png'; cw=95; ch=124; c=@(22,36,80,57); t=@(180,117,218,132) },
  @{ k='nose';            f='klepa_parts/nose_variant_1.png';    cw=120; ch=81; c=@(52,36,67,46); t=@(150,190,166,202) },
  @{ k='mouth_neutral';   f='klepa_parts/mouth_smile_5.png';     cw=98;  ch=54; c=@(24,23,60,33);  t=@(128,209,190,221) },
  @{ k='mouth_smile';     f='klepa_parts/mouth_smile_1.png';     cw=124; ch=64; c=@(22,23,101,43); t=@(126,206,192,228) },
  @{ k='mouth_open';      f='klepa_parts/mouth_open_1.png';      cw=90;  ch=65; c=@(25,16,58,45);  t=@(134,204,182,232) },
  # bangs lowered by 4px vs the measured hairline (user: "верхние волосы чуть
  # ниже"); the squareness was fixed on the cap, not by lifting the hairline.
  @{ k='hair_front';      f='klepa_parts/hair_variant_6.png';    cw=136; ch=133; c=@(13,22,122,93); t=@(63,37,256,142); layer='front' },
  # side locks - BACK layer only, they widen the hair at cheek/jaw level so the
  # face stops floating inside a too-narrow shell (ref hair is 243 wide at y=200,
  # hair_variant_1 alone only reaches 193)
  @{ k='hair_side_left';  f='klepa_parts/hair_variant_4.png';    cw=136; ch=139; c=@(14,18,99,124); t=@(22,132,106,252); layer='back' },
  @{ k='hair_side_right'; f='klepa_parts/hair_variant_5.png';    cw=132; ch=131; c=@(28,10,112,119); t=@(210,132,294,252); layer='back' }
)

function Box($r) {
  $cx0 = $r.c[0]; $cy0 = $r.c[1]; $cx1 = $r.c[2]; $cy1 = $r.c[3]
  $contentW = $cx1 - $cx0 + 1; $contentH = $cy1 - $cy0 + 1
  $tx0 = $r.t[0]; $ty0 = $r.t[1]; $tx1 = $r.t[2]; $ty1 = $r.t[3]
  $targetW = $tx1 - $tx0 + 1; $targetH = $ty1 - $ty0 + 1
  $sx = $targetW / $contentW; $sy = $targetH / $contentH
  if ($r.uniform) {
    # keep the artwork's own proportions, anchored at the target box centre
    $s = [Math]::Min($sx, $sy); $sx = $s; $sy = $s
    $tx0 = [Math]::Round((($tx0 + $tx1) / 2) - ($contentW * $sx) / 2, 1)
    $ty0 = [Math]::Round((($ty0 + $ty1) / 2) - ($contentH * $sy) / 2, 1)
  }
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
