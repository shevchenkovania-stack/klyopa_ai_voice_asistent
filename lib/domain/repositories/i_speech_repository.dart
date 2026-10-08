abstract class ISpeechRepository {
  Future<String> transcribeAudio(String filePath, {String? language});
}
