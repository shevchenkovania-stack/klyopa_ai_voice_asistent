/// Voice Activity Detection based on silence duration.
/// Detects when the user finishes speaking by monitoring amplitude.
class VadDetector {
  static const double silenceThresholdDb = -40.0;
  static const Duration silenceDuration = Duration(milliseconds: 1500);
  static const Duration maxRecordingDuration = Duration(seconds: 30);

  bool _isSpeaking = false;
  DateTime? _silenceStart;
  DateTime? _recordingStart;

  /// Returns true when speech has ended (silence detected).
  bool processSample(double currentDb) {
    _recordingStart ??= DateTime.now();

    // Check max duration
    if (DateTime.now().difference(_recordingStart!) > maxRecordingDuration) {
      reset();
      return true;
    }

    if (currentDb > silenceThresholdDb) {
      // User is speaking
      _isSpeaking = true;
      _silenceStart = null;
    } else if (_isSpeaking) {
      // User stopped speaking, count silence
      _silenceStart ??= DateTime.now();
      if (DateTime.now().difference(_silenceStart!) >= silenceDuration) {
        reset();
        return true; // Speech ended
      }
    }

    return false;
  }

  void reset() {
    _isSpeaking = false;
    _silenceStart = null;
    _recordingStart = null;
  }

  bool get isSpeaking => _isSpeaking;
}
