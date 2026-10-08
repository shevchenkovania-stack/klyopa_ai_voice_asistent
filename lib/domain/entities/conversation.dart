class Conversation {
  final List<ConversationMessage> messages;
  static const int maxHistoryLength = 5;

  Conversation() : messages = [];

  void addUserMessage(String text) {
    messages.add(ConversationMessage(role: 'user', content: text));
    _trimHistory();
  }

  void addAssistantMessage(String text) {
    messages.add(ConversationMessage(role: 'assistant', content: text));
    _trimHistory();
  }

  void _trimHistory() {
    while (messages.length > maxHistoryLength * 2) {
      messages.removeAt(0);
    }
  }

  List<Map<String, String>> toApiFormat() {
    return messages.map((m) => {'role': m.role, 'content': m.content}).toList();
  }

  void clear() => messages.clear();
}

class ConversationMessage {
  final String role;
  final String content;
  final DateTime timestamp;

  ConversationMessage({
    required this.role,
    required this.content,
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();
}
