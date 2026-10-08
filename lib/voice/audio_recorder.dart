import 'dart:async';
import 'package:record/record.dart';
import 'package:path_provider/path_provider.dart';

class AgentAudioRecorder {
  final AudioRecorder _recorder = AudioRecorder();
  String? _currentPath;

  Future<bool> hasPermission() async {
    return await _recorder.hasPermission();
  }

  Future<String> startRecording() async {
    final dir = await getTemporaryDirectory();
    _currentPath = '${dir.path}/voice_command_${DateTime.now().millisecondsSinceEpoch}.m4a';

    await _recorder.start(
      const RecordConfig(
        encoder: AudioEncoder.aacLc,
        sampleRate: 16000,
        numChannels: 1,
      ),
      path: _currentPath!,
    );

    return _currentPath!;
  }

  Future<String?> stopRecording() async {
    final path = await _recorder.stop();
    return path;
  }

  Future<bool> isRecording() async {
    return await _recorder.isRecording();
  }

  Stream<Amplitude> get amplitudeStream {
    return Stream.periodic(const Duration(milliseconds: 100)).asyncMap((_) async {
      return await _recorder.getAmplitude();
    });
  }

  Future<void> dispose() async {
    await _recorder.dispose();
  }
}
