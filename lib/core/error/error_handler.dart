import 'package:ai_voice_agent/core/error/failures.dart';

class ErrorHandler {
  static String userMessage(Object error) {
    if (error is Failure) return error.message;
    return 'Что-то пошло не так. Попробуй ещё раз.';
  }

  static String voiceMessage(Object error) {
    if (error is NetworkFailure) return 'Нет интернета.';
    if (error is SpeechFailure) return 'Не расслышал. Повтори.';
    if (error is ApiFailure) return 'Сервис недоступен. Попробуй позже.';
    if (error is PermissionFailure) return 'Нужно разрешение. Открой настройки.';
    if (error is Failure) return error.message;
    return 'Произошла ошибка.';
  }
}
