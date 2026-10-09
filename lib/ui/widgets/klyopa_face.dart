import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

enum KlyopaState { idle, listening, talking, thinking, happy, error, blink }

class KlyopaFace extends StatefulWidget {
  final KlyopaState state;

  const KlyopaFace({super.key, required this.state});

  @override
  State<KlyopaFace> createState() => _KlyopaFaceState();
}

class _KlyopaFaceState extends State<KlyopaFace> with TickerProviderStateMixin {
  late AnimationController _blinkController;
  late AnimationController _breathController;
  Map<String, dynamic>? _config;
  final Map<String, ui.Image> _images = {};
  bool _loaded = false;
  bool _loadFailed = false;
  Timer? _blinkTimer;

  @override
  void initState() {
    super.initState();
    _blinkController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 150),
    );
    // Breathing animation - slow pulse
    _breathController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2000),
    )..repeat(reverse: true);
    _loadAssets();
    _startAutoBlink();
  }

  void _startAutoBlink() {
    _blinkTimer = Timer.periodic(const Duration(seconds: 4), (_) {
      if (mounted) {
        _blinkController.forward().then((_) {
          if (mounted) _blinkController.reverse();
        });
      }
    });
  }

  Future<void> _loadAssets() async {
    try {
      final jsonString = await rootBundle.loadString('assets/klyopa/klyopa_config.json');
      _config = json.decode(jsonString);

      final parts = _config!['parts'] as Map<String, dynamic>;
      final filesToLoad = <String>{};

      void collect(dynamic item) {
        if (item is Map<String, dynamic> && item.containsKey('file')) {
          filesToLoad.add(item['file'] as String);
        } else if (item is List) {
          for (var child in item) collect(child);
        }
      }

      parts.forEach((key, value) => collect(value));

      for (var filePath in filesToLoad) {
        try {
          final byteData = await rootBundle.load('assets/klyopa/$filePath');
          final codec = await ui.instantiateImageCodec(byteData.buffer.asUint8List());
          final frame = await codec.getNextFrame();
          _images[filePath] = frame.image;
        } catch (e) {
          debugPrint('Не удалось загрузить $filePath: $e');
        }
      }

      debugPrint('Загружено слоёв: ${_images.length} из ${filesToLoad.length}');

      if (mounted) {
        setState(() => _loaded = true);
      }
    } catch (e) {
      _loadFailed = true;
      debugPrint('Ошибка загрузки Klyopa: $e');
    }
  }

  @override
  void dispose() {
    _blinkTimer?.cancel();
    _blinkController.dispose();
    _breathController.dispose();
    _images.values.forEach((img) => img.dispose());
    super.dispose();
  }

  /// Vector face used when the PNG layers fail to load. Keeps the avatar alive
  /// and state-aware instead of an endless spinner.
  Widget _buildFallbackFace() {
    return AnimatedBuilder(
      animation: Listenable.merge([_blinkController, _breathController]),
      builder: (context, _) {
        return AspectRatio(
          aspectRatio: 415 / 408,
          child: CustomPaint(
            painter: _FallbackFacePainter(
              state: widget.state,
              blinkValue: _blinkController.value,
              breathValue: _breathController.value,
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    if (!_loaded) {
      return _loadFailed
          ? _buildFallbackFace()
          : const Center(child: CircularProgressIndicator());
    }
    if (_images.isEmpty) {
      return _buildFallbackFace();
    }

    return AnimatedBuilder(
      animation: _blinkController,
      builder: (context, child) {
        return AnimatedBuilder(
          animation: _breathController,
          builder: (context, child) {
            // Breathing scale: 1.0 to 1.03
            final breathScale = 1.0 + (_breathController.value * 0.03);
            
            return LayoutBuilder(
              builder: (context, constraints) {
                final canvasW = (_config!['canvas']['width'] as num).toDouble();
                final canvasH = (_config!['canvas']['height'] as num).toDouble();
                // "contain": the whole artwork (hair sticks out above the head)
                // must stay inside the box, never overlap the AppBar.
                final maxW = constraints.hasBoundedWidth
                    ? constraints.maxWidth
                    : canvasW;
                final maxH = constraints.hasBoundedHeight
                    ? constraints.maxHeight
                    : canvasH;
                final scale = math.min(maxW / canvasW, maxH / canvasH);

                return Transform.scale(
                  scale: breathScale,
                  child: CustomPaint(
                    size: Size(canvasW * scale, canvasH * scale),
                    painter: _KlyopaPainter(
                      images: _images,
                      config: _config!,
                      scale: scale,
                      blinkValue: _blinkController.value,
                      state: widget.state,
                    ),
                  ),
                );
              },
            );
          },
        );
      },
    );
  }
}

class _KlyopaPainter extends CustomPainter {
  final Map<String, ui.Image> images;
  final Map<String, dynamic> config;
  final double scale;
  final double blinkValue;
  final KlyopaState state;

  _KlyopaPainter({
    required this.images,
    required this.config,
    required this.scale,
    required this.blinkValue,
    required this.state,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final parts = config['parts'] as Map<String, dynamic>;

    // Determine brow raise based on state
    double browValue = 0;
    if (state == KlyopaState.thinking || state == KlyopaState.error) {
      browValue = 1.0;
    } else if (state == KlyopaState.happy) {
      browValue = 0.5;
    }

    // 0. Neck / shoulders (hair is drawn after the face so it sits IN FRONT of
    // the ears, which are baked into face_base).
    _drawPart(canvas, parts['body']);
    _drawPart(canvas, parts['hair_back']);

    // 1. Face base
    _drawPart(canvas, parts['face_base']);

    // 1b. Hair volume - drawn IN FRONT of the face/ears (ears are part of
    // face_base), but BEHIND the eyes/brows so the features stay visible.
    _drawHairLayer(canvas, parts, 'back');

    // 2. Face shadow / cheeks (blush)
    _drawPart(canvas, parts['face_shadow']);
    _drawPart(canvas, parts['left_cheek']);
    _drawPart(canvas, parts['right_cheek']);

    // 3. Eyes (with blink + scale_hint)
    _drawEye(canvas, parts['left_eye'], parts['left_eye_closed'], isLeft: true);
    _drawEye(canvas, parts['right_eye'], parts['right_eye_closed'], isLeft: false);

    // 4. Brows
    _drawPart(canvas, parts['left_brow'], offsetY: -browValue * 10 * scale);
    _drawPart(canvas, parts['right_brow'], offsetY: -browValue * 10 * scale);

    // 5. Nose
    _drawPart(canvas, parts['nose']);

    // 6. Mouth
    _drawMouth(canvas, parts);

    // 7. Front hair - bangs overlap the forehead
    _drawHairLayer(canvas, parts, 'front');
    // NOTE: ears are baked into head_base.png - no separate ear layer.
  }

  void _drawHairLayer(Canvas canvas, Map<String, dynamic> parts, String layer) {
    final variants = parts['hair_variants'] as List<dynamic>?;
    if (variants == null || variants.isEmpty) return;
    for (var variant in variants) {
      final hair = variant as Map<String, dynamic>;
      // entries without an explicit layer are bangs, i.e. front hair
      if ((hair['layer'] as String? ?? 'front') != layer) continue;
      _drawPart(canvas, hair);
    }
  }

  void _drawPart(Canvas canvas, dynamic partData, {double offsetX = 0, double offsetY = 0, double scaleY = 1.0}) {
    if (partData == null) return;

    final part = partData as Map<String, dynamic>;
    final filePath = part['file'] as String?;
    if (filePath == null || !images.containsKey(filePath)) return;

    final image = images[filePath]!;
    var x = (part['x'] as num).toDouble();
    var y = (part['y'] as num).toDouble();
    var w = (part['width'] as num).toDouble();
    var h = (part['height'] as num).toDouble();

    // Apply scale_hint if present (numeric value)
    if (part.containsKey('scale_hint')) {
      final scaleHint = (part['scale_hint'] as num).toDouble();
      final newW = w * scaleHint;
      final newH = h * scaleHint;
      x = x + (w - newW) / 2;
      y = y + (h - newH) / 2;
      w = newW;
      h = newH;
    }

    // Apply scaleY for mouth animation
    if (scaleY != 1.0) {
      final newH = h * scaleY;
      y = y + (h - newH) / 2;
      h = newH;
    }

    final src = Rect.fromLTWH(0, 0, image.width.toDouble(), image.height.toDouble());
    final dst = Rect.fromLTWH(
      x * scale + offsetX,
      y * scale + offsetY,
      w * scale,
      h * scale,
    );

    canvas.drawImageRect(image, src, dst, Paint());
  }

  void _drawEye(
    Canvas canvas,
    Map<String, dynamic>? openEye,
    Map<String, dynamic>? closedEye, {
    required bool isLeft,
  }) {
    if (openEye == null) return;
    if (blinkValue > 0.85) {
      _drawPart(canvas, closedEye);
      return;
    }

    final part = openEye;
    final filePath = part['file'] as String?;
    if (filePath == null || !images.containsKey(filePath)) return;

    final image = images[filePath]!;
    var x = (part['x'] as num).toDouble();
    var y = (part['y'] as num).toDouble();
    var w = (part['width'] as num).toDouble();
    var h = (part['height'] as num).toDouble();

    // Apply scale_hint if present
    if (part.containsKey('scale_hint')) {
      final scaleHint = (part['scale_hint'] as num).toDouble();
      final newW = w * scaleHint;
      final newH = h * scaleHint;
      x = x + (w - newW) / 2;
      y = y + (h - newH) / 2;
      w = newW;
      h = newH;
    }

    final squish = 1.0 - (blinkValue * 0.75);
    final centerY = (y + h / 2) * scale;

    canvas.save();
    canvas.translate(0, centerY);
    canvas.scale(1.0, squish);
    canvas.translate(0, -centerY);

    final src = Rect.fromLTWH(0, 0, image.width.toDouble(), image.height.toDouble());
    final dst = Rect.fromLTWH(x * scale, y * scale, w * scale, h * scale);
    canvas.drawImageRect(image, src, dst, Paint());

    canvas.restore();
  }

  void _drawMouth(Canvas canvas, Map<String, dynamic> parts) {
    final variants = parts['mouth_variants'] as List<dynamic>?;
    if (variants == null || variants.isEmpty) return;

    // State -> mouth variant id from the config
    String targetId;
    if (state == KlyopaState.talking) {
      targetId = 'mouth_open';
    } else if (state == KlyopaState.happy) {
      targetId = 'mouth_smile';
    } else {
      targetId = 'mouth_neutral';
    }

    Map<String, dynamic>? target;
    for (var v in variants) {
      if ((v as Map<String, dynamic>)['id'] == targetId) {
        target = v;
        break;
      }
    }
    if (target == null) {
      // Config has no variant for this state - fall back to the neutral one
      for (var v in variants) {
        if ((v as Map<String, dynamic>)['id'] == 'mouth_neutral') {
          target = v;
          break;
        }
      }
      if (target == null) return;
    }

    // Animate mouth during talking - scale Y to simulate opening/closing
    if (state == KlyopaState.talking) {
      final time = DateTime.now().millisecondsSinceEpoch;
      final mouthAnim = ((time % 300) / 300.0 * 2 - 1); // oscillate -1 to 1
      final scaleY = 0.6 + (mouthAnim.abs() * 0.4); // scale between 0.6 and 1.0
      _drawPart(canvas, target, scaleY: scaleY);
    } else {
      _drawPart(canvas, target);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}

/// Minimal vector portrait of Клёпа drawn with primitives only. Used while the
/// PNG layer stack is missing so the avatar still blinks, breathes and reacts
/// to the agent state.
class _FallbackFacePainter extends CustomPainter {
  final KlyopaState state;
  final double blinkValue;
  final double breathValue;

  _FallbackFacePainter({
    required this.state,
    required this.blinkValue,
    required this.breathValue,
  });

  static const _skin = Color(0xFFE8DAFB);
  static const _skinEdge = Color(0xFFC0A6EE);
  static const _ink = Color(0xFF3B2A5F);
  static const _brow = Color(0xFF6A4FA3);
  static const _blush = Color(0x55FF9EB5);

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = math.min(size.width, size.height) * 0.42;
    final faceRadius = radius * (1.0 + breathValue * 0.02);

    // Ears behind the face
    final earPaint = Paint()..color = _skinEdge;
    canvas.drawCircle(Offset(center.dx - radius, center.dy - radius * 0.2), radius * 0.22, earPaint);
    canvas.drawCircle(Offset(center.dx + radius, center.dy - radius * 0.2), radius * 0.22, earPaint);

    // Face
    canvas.drawCircle(
      center,
      faceRadius,
      Paint()
        ..shader = RadialGradient(
          colors: [_skin, _skinEdge],
          center: Alignment(-0.2, -0.3),
        ).createShader(Rect.fromCircle(center: center, radius: faceRadius)),
    );

    final eyeDx = radius * 0.38;
    final eyeY = center.dy - radius * 0.12;
    final eyeRadius = radius * 0.16;
    final eyeOpen = blinkValue > 0.85 ? 0.1 : 1.0;
    final eyePaint = Paint()..color = _ink;

    // Brows lift while thinking or on error
    final browRaise = (state == KlyopaState.thinking || state == KlyopaState.error)
        ? radius * 0.1
        : 0.0;
    final browPaint = Paint()
      ..color = _brow
      ..strokeWidth = radius * 0.06
      ..strokeCap = StrokeCap.round
      ..style = PaintingStyle.stroke;

    // Cheeks
    final blushPaint = Paint()..color = _blush;

    for (final sign in [-1.0, 1.0]) {
      final eyeCenter = Offset(center.dx + sign * eyeDx, eyeY);
      canvas.save();
      canvas.translate(eyeCenter.dx, eyeCenter.dy);
      canvas.scale(1.0, eyeOpen);
      canvas.drawCircle(Offset.zero, eyeRadius, eyePaint);
      canvas.restore();
      if (eyeOpen == 1.0) {
        canvas.drawCircle(
          Offset(eyeCenter.dx - eyeRadius * 0.3, eyeCenter.dy - eyeRadius * 0.35),
          eyeRadius * 0.28,
          Paint()..color = Colors.white,
        );
      }

      canvas.drawLine(
        Offset(center.dx + sign * eyeDx - radius * 0.16, eyeY - radius * 0.34 - browRaise),
        Offset(center.dx + sign * eyeDx + radius * 0.16, eyeY - radius * 0.38 - browRaise),
        browPaint,
      );
      canvas.drawCircle(
        Offset(center.dx + sign * radius * 0.6, center.dy + radius * 0.35),
        radius * 0.14,
        blushPaint,
      );
    }

    _paintMouth(canvas, center, radius);
  }

  void _paintMouth(Canvas canvas, Offset center, double radius) {
    final mouthY = center.dy + radius * 0.45;
    final stroke = Paint()
      ..color = _ink
      ..strokeWidth = radius * 0.07
      ..strokeCap = StrokeCap.round
      ..style = PaintingStyle.stroke;
    final fill = Paint()..color = _ink;

    switch (state) {
      case KlyopaState.talking:
        final phase = (DateTime.now().millisecondsSinceEpoch % 300) / 300.0;
        final openness = radius * (0.12 + 0.2 * (phase < 0.5 ? phase : 1 - phase) * 2);
        canvas.drawOval(
          Rect.fromCenter(
            center: Offset(center.dx, mouthY),
            width: radius * 0.28,
            height: openness,
          ),
          fill,
        );
      case KlyopaState.happy:
        canvas.drawArc(
          Rect.fromCenter(
            center: Offset(center.dx, mouthY - radius * 0.05),
            width: radius * 0.5,
            height: radius * 0.35,
          ),
          0.15 * math.pi,
          0.7 * math.pi,
          false,
          stroke,
        );
      case KlyopaState.error:
        canvas.drawArc(
          Rect.fromCenter(
            center: Offset(center.dx, mouthY + radius * 0.15),
            width: radius * 0.4,
            height: radius * 0.3,
          ),
          1.15 * math.pi,
          0.7 * math.pi,
          false,
          stroke,
        );
      case KlyopaState.listening:
        canvas.drawOval(
          Rect.fromCenter(
            center: Offset(center.dx, mouthY),
            width: radius * 0.18,
            height: radius * 0.18,
          ),
          stroke,
        );
      default:
        canvas.drawArc(
          Rect.fromCenter(
            center: Offset(center.dx, mouthY - radius * 0.02),
            width: radius * 0.4,
            height: radius * 0.22,
          ),
          0.2 * math.pi,
          0.6 * math.pi,
          false,
          stroke,
        );
    }
  }

  @override
  bool shouldRepaint(covariant _FallbackFacePainter oldDelegate) => true;
}
