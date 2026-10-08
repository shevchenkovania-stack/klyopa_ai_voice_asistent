abstract class Failure {
  final String message;
  const Failure(this.message);
}

class NetworkFailure extends Failure {
  const NetworkFailure([String message = 'Нет подключения к интернету. Проверь соединение.'])
      : super(message);
}

class ApiFailure extends Failure {
  const ApiFailure([String message = 'Не удалось получить ответ. Попробуй позже.'])
      : super(message);
}

class PermissionFailure extends Failure {
  const PermissionFailure(String permission)
      : super('Нужно разрешение для этого действия. Открой настройки и разреши доступ.');
}

class SpeechFailure extends Failure {
  const SpeechFailure([String message = 'Не удалось распознать речь. Повтори, пожалуйста.'])
      : super(message);
}

class ActionFailure extends Failure {
  const ActionFailure(String message) : super(message);
}

class TelegramFailure extends Failure {
  const TelegramFailure([String message = 'Проблема с Telegram. Проверь подключение.'])
      : super(message);
}

class StorageFailure extends Failure {
  const StorageFailure([String message = 'Ошибка сохранения данных.'])
      : super(message);
}
