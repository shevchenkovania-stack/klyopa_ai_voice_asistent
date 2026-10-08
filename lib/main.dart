import 'package:flutter/material.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/ui/app.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initDependencies();
  runApp(const VoiceAgentApp());
}
