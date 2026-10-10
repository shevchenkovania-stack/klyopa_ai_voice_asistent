import 'package:flutter/material.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/core/config/app_config.dart';
import 'package:ai_voice_agent/ui/screens/home_screen.dart';
import 'package:ai_voice_agent/ui/screens/settings_screen.dart';
import 'package:ai_voice_agent/ui/screens/history_screen.dart';
import 'package:ai_voice_agent/ui/screens/journal_screen.dart';
import 'package:ai_voice_agent/ui/screens/permissions_screen.dart';
import 'package:ai_voice_agent/ui/screens/api_keys_screen.dart';
import 'package:ai_voice_agent/ui/screens/tool_tester_screen.dart';
import 'package:ai_voice_agent/ui/screens/tools_inspector_screen.dart';
import 'package:ai_voice_agent/ui/theme/app_theme.dart';

class VoiceAgentApp extends StatelessWidget {
  const VoiceAgentApp({super.key});

  @override
  Widget build(BuildContext context) {
    final config = sl<AppConfig>();
    final needsOnboarding = !config.hasApiKeys;

    return MaterialApp(
      title: 'AI Voice Agent',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.system,
      initialRoute: needsOnboarding ? '/api-keys-setup' : '/',
      routes: {
        '/': (_) => const HomeScreen(),
        '/settings': (_) => const SettingsScreen(),
        '/history': (_) => const HistoryScreen(),
        '/journal': (_) => const JournalScreen(),
        '/permissions': (_) => const PermissionsScreen(),
        '/api-keys': (_) => const ApiKeysScreen(),
        '/api-keys-setup': (_) => const ApiKeysScreen(isOnboarding: true),
        '/tool-tester': (_) => const ToolTesterScreen(),
        '/tools-inspector': (_) => const ToolsInspectorScreen(),
      },
    );
  }
}
