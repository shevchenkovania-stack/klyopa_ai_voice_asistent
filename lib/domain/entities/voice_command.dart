class VoiceCommand {
  final String id;
  final String rawText;
  final String? actionId;
  final Map<String, dynamic>? params;
  final bool needsConfirmation;
  final String response;
  final DateTime timestamp;

  VoiceCommand({
    required this.id,
    required this.rawText,
    this.actionId,
    this.params,
    this.needsConfirmation = false,
    required this.response,
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();

  Map<String, dynamic> toJson() => {
        'id': id,
        'rawText': rawText,
        'actionId': actionId,
        'params': params,
        'needsConfirmation': needsConfirmation,
        'response': response,
        'timestamp': timestamp.toIso8601String(),
      };

  factory VoiceCommand.fromJson(Map<String, dynamic> json) => VoiceCommand(
        id: json['id'] as String,
        rawText: json['rawText'] as String,
        actionId: json['actionId'] as String?,
        params: json['params'] as Map<String, dynamic>?,
        needsConfirmation: json['needsConfirmation'] as bool? ?? false,
        response: json['response'] as String,
        timestamp: DateTime.parse(json['timestamp'] as String),
      );
}
