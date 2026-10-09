import 'package:flutter/material.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/core/config/app_config.dart';
import 'package:ai_voice_agent/core/engine/kotlin_engine_service.dart';
import 'package:ai_voice_agent/core/security/secure_storage.dart';
import 'package:ai_voice_agent/ui/widgets/api_key_widgets.dart';

class ApiKeysScreen extends StatefulWidget {
  final bool isOnboarding;
  const ApiKeysScreen({super.key, this.isOnboarding = false});

  @override
  State<ApiKeysScreen> createState() => _ApiKeysScreenState();
}

class _ApiKeysScreenState extends State<ApiKeysScreen>
    with TickerProviderStateMixin {
  late TextEditingController _geminiController;
  late TextEditingController _groqController;
  late TextEditingController _openaiController;
  late AnimationController _pulseController;
  late AnimationController _slideController;
  late Animation<double> _pulseAnim;
  late Animation<Offset> _slideAnim;

  bool _geminiVisible = false;
  bool _groqVisible = false;
  bool _openaiVisible = false;
  bool _saving = false;
  bool _geminiValid = false;
  bool _groqValid = false;
  bool _openaiValid = false;

  @override
  void initState() {
    super.initState();
    final config = sl<AppConfig>();
    _geminiController = TextEditingController(text: config.geminiApiKey);
    _groqController = TextEditingController(text: config.groqApiKey);
    _openaiController = TextEditingController(text: config.openaiApiKey);

    _geminiValid = _isGeminiKey(_geminiController.text);
    _groqValid = _groqController.text.startsWith('gsk_');
    _openaiValid = _openaiController.text.startsWith('sk-');

    _geminiController.addListener(_validateKeys);
    _groqController.addListener(_validateKeys);
    _openaiController.addListener(_validateKeys);

    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat(reverse: true);
    _pulseAnim = Tween<double>(begin: 0.6, end: 1.0).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );

    _slideController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    )..forward();
    _slideAnim = Tween<Offset>(begin: const Offset(0, 0.3), end: Offset.zero)
        .animate(CurvedAnimation(parent: _slideController, curve: Curves.easeOutCubic));
  }

  /// Gemini keys come in two shapes: the classic `AIza...` and the newer
  /// OAuth-style `AQ....` — both are accepted, anything short is not.
  bool _isGeminiKey(String value) {
    final v = value.trim();
    return (v.startsWith('AIza') || v.startsWith('AQ.')) && v.length > 20;
  }

  void _validateKeys() {
    setState(() {
      _geminiValid = _isGeminiKey(_geminiController.text);
      _groqValid = _groqController.text.startsWith('gsk_');
      _openaiValid = _openaiController.text.startsWith('sk-');
    });
  }

  @override
  void dispose() {
    _geminiController.dispose();
    _groqController.dispose();
    _openaiController.dispose();
    _pulseController.dispose();
    _slideController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    final config = sl<AppConfig>();
    final storage = sl<SecureStorageService>();
    config.geminiApiKey = _geminiController.text.trim();
    config.groqApiKey = _groqController.text.trim();
    config.openaiApiKey = _openaiController.text.trim();
    await config.save(storage);
    // Сразу отдаём ключи Kotlin-движку — иначе STT работает без ключа до перезапуска
    await sl<KotlinEngineService>().syncConfig(config);
    setState(() => _saving = false);

    if (mounted) {
      if (widget.isOnboarding) {
        Navigator.of(context).pushReplacementNamed('/');
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Row(
              children: [
                Icon(Icons.check_circle, color: Colors.white),
                SizedBox(width: 12),
                Text('Ключи сохранены'),
              ],
            ),
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            backgroundColor: Colors.green.shade700,
          ),
        );
        Navigator.pop(context);
      }
    }
  }

  /// Onboarding must never trap the user: even with no keys at all they can
  /// open the app (offline tools keep working, AI answers will be unavailable).
  void _skip() {
    Navigator.of(context).pushReplacementNamed('/');
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: isDark
                ? [const Color(0xFF1A1A2E), const Color(0xFF16213E), const Color(0xFF0F3460)]
                : [const Color(0xFFF8F9FF), const Color(0xFFEEF1FF), const Color(0xFFE8ECFF)],
          ),
        ),
        child: SafeArea(
          child: SlideTransition(
            position: _slideAnim,
            child: CustomScrollView(
              slivers: [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(24, 40, 24, 0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _buildHeader(colorScheme, isDark),
                        const SizedBox(height: 40),
                        ApiKeyCard(
                          title: 'Gemini',
                          subtitle: 'Основной AI-движок (Google)',
                          icon: Icons.explore,
                          iconColor: Colors.blue,
                          controller: _geminiController,
                          isVisible: _geminiVisible,
                          isValid: _geminiValid,
                          hint: 'AIza... / AQ....',
                          onToggleVisibility: () => setState(() => _geminiVisible = !_geminiVisible),
                          onValidate: _validateKeys,
                          isDark: isDark,
                        ),
                        const SizedBox(height: 20),
                        ApiKeyCard(
                          title: 'Groq AI',
                          subtitle: 'Основной AI-движок (быстрый)',
                          icon: Icons.bolt_rounded,
                          iconColor: Colors.orange,
                          controller: _groqController,
                          isVisible: _groqVisible,
                          isValid: _groqValid,
                          hint: 'gsk_...',
                          onToggleVisibility: () => setState(() => _groqVisible = !_groqVisible),
                          onValidate: _validateKeys,
                          isDark: isDark,
                        ),
                        const SizedBox(height: 20),
                        ApiKeyCard(
                          title: 'OpenAI',
                          subtitle: 'Fallback + Whisper STT',
                          icon: Icons.auto_awesome_rounded,
                          iconColor: Colors.green,
                          controller: _openaiController,
                          isVisible: _openaiVisible,
                          isValid: _openaiValid,
                          hint: 'sk-...',
                          onToggleVisibility: () => setState(() => _openaiVisible = !_openaiVisible),
                          onValidate: _validateKeys,
                          isDark: isDark,
                        ),
                        const SizedBox(height: 32),
                        _buildStatusRow(isDark),
                        const SizedBox(height: 32),
                        _buildSaveButton(colorScheme),
                        const SizedBox(height: 16),
                        if (widget.isOnboarding)
                          Center(
                            child: TextButton(
                              onPressed: _saving ? null : _skip,
                              child: Text(
                                'Пропустить настройку',
                                style: TextStyle(color: colorScheme.onSurface.withAlpha(150)),
                              ),
                            ),
                          ),
                        if (!widget.isOnboarding)
                          Center(
                            child: TextButton(
                              onPressed: () => Navigator.pop(context),
                              child: Text('Отмена', style: TextStyle(color: colorScheme.onSurface.withAlpha(150))),
                            ),
                          ),
                        const SizedBox(height: 40),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(ColorScheme colorScheme, bool isDark) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AnimatedBuilder(
          animation: _pulseAnim,
          builder: (context, child) {
            return Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  colors: [
                    Colors.deepPurple.withAlpha((200 * _pulseAnim.value).toInt()),
                    Colors.blue.withAlpha((150 * _pulseAnim.value).toInt()),
                  ],
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.deepPurple.withAlpha((80 * _pulseAnim.value).toInt()),
                    blurRadius: 20,
                    spreadRadius: 2,
                  ),
                ],
              ),
              child: const Icon(Icons.key_rounded, color: Colors.white, size: 30),
            );
          },
        ),
        const SizedBox(height: 24),
        Text('API Ключи', style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold, color: isDark ? Colors.white : const Color(0xFF1A1A2E), letterSpacing: -0.5)),
        const SizedBox(height: 8),
        Text('Настрой подключение к AI сервисам', style: TextStyle(fontSize: 16, color: isDark ? Colors.white.withAlpha(150) : const Color(0xFF1A1A2E).withAlpha(150))),
      ],
    );
  }

  Widget _buildStatusRow(bool isDark) {
    return Row(
      children: [
        Expanded(child: StatusChip(icon: Icons.explore, label: 'Gemini: ${_geminiValid ? "Ready" : "Missing"}', isActive: _geminiValid, isDark: isDark)),
        const SizedBox(width: 8),
        Expanded(child: StatusChip(icon: Icons.speed_rounded, label: 'Groq: ${_groqValid ? "Ready" : "Missing"}', isActive: _groqValid, isDark: isDark)),
        const SizedBox(width: 8),
        Expanded(child: StatusChip(icon: Icons.auto_awesome_rounded, label: 'OpenAI: ${_openaiValid ? "Ready" : "Missing"}', isActive: _openaiValid, isDark: isDark)),
      ],
    );
  }

  Widget _buildSaveButton(ColorScheme colorScheme) {
    final canSave = _geminiValid || _groqValid || _openaiValid;
    return SizedBox(
      width: double.infinity,
      height: 56,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          gradient: canSave ? const LinearGradient(colors: [Colors.deepPurple, Colors.blue]) : null,
          color: canSave ? null : Colors.grey.shade400,
          boxShadow: canSave ? [BoxShadow(color: Colors.deepPurple.withAlpha(80), blurRadius: 16, offset: const Offset(0, 6))] : null,
        ),
        child: ElevatedButton(
          onPressed: canSave && !_saving ? _save : null,
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.transparent,
            shadowColor: Colors.transparent,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          ),
          child: _saving
              ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
              : Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(widget.isOnboarding ? Icons.rocket_launch_rounded : Icons.save_rounded, color: Colors.white),
                    const SizedBox(width: 10),
                    Text(widget.isOnboarding ? 'Начать' : 'Сохранить', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Colors.white)),
                  ],
                ),
        ),
      ),
    );
  }
}
