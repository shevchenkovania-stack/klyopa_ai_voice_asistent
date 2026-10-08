import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/foundation.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:edge_tts/edge_tts.dart';

class TtsEngine {
  // Channel name MUST match Kotlin side exactly
  static const _ttsChannel = MethodChannel('com.aiagent.ai_voice_agent/tts_audio');
  
  final FlutterTts _fallbackTts = FlutterTts();
  bool _isSpeaking = false;
  
  // Only DmitryNeural - hardcoded, no options
  static const String voice = 'ru-RU-DmitryNeural';

  Future<void> initialize() async {
    await _fallbackTts.setLanguage('ru-RU');
    await _fallbackTts.setSpeechRate(1.0);
    await _fallbackTts.setPitch(1.0);
    await _fallbackTts.awaitSpeakCompletion(true);
  }

  Future<void> speak(String text) async {
    if (_isSpeaking) await stop();
    _isSpeaking = true;

    try {
      // ALWAYS use Edge TTS with DmitryNeural
      debugPrint('[TTS] Synthesizing with Edge TTS voice: $voice');
      final audioBytes = await _synthesizeWithEdge(text);
      if (audioBytes != null && audioBytes.isNotEmpty) {
        debugPrint('[TTS] Edge TTS success: ${audioBytes.length} bytes');
        await _playAudioBytes(audioBytes);
        _isSpeaking = false;
        return;
      }
      
      // Only fallback to system TTS if Edge TTS completely fails
      debugPrint('[TTS] Edge TTS FAILED, falling back to system TTS (female voice!)');
      await _fallbackTts.speak(text);
    } catch (e) {
      // Fallback to system TTS on error
      debugPrint('[TTS] Exception: $e — falling back to system TTS');
      await _fallbackTts.speak(text);
    } finally {
      _isSpeaking = false;
    }
  }

  Future<Uint8List?> _synthesizeWithEdge(String text) async {
    try {
      final tts = Communicate(
        text: text,
        voice: voice,
        rate: '+0%',
        pitch: '+0Hz',
        volume: '+0%',
      );
      
      final bytes = await tts.toBytes();
      debugPrint('[TTS] Edge synthesized ${bytes.length} bytes');
      return bytes;
    } catch (e) {
      debugPrint('[TTS] Edge synthesis error: $e');
      return null;
    }
  }

  Future<void> _playAudioBytes(Uint8List bytes) async {
    final dir = await getTemporaryDirectory();
    final file = File('${dir.path}/tts_response.mp3');
    await file.writeAsBytes(bytes);
    
    await _ttsChannel.invokeMethod('playAudio', {'path': file.path});
    
    // Wait for playback to complete
    await Future.delayed(Duration(seconds: (bytes.length / 16000).ceil() + 1));
  }

  Future<void> stop() async {
    await _ttsChannel.invokeMethod('stopAudio');
    await _fallbackTts.stop();
    _isSpeaking = false;
  }


  bool get isSpeaking => _isSpeaking;
}
