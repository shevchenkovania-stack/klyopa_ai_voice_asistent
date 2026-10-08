enum ActionStatus { success, failed, unsupported, permissionDenied, cancelled }

class ActionResult {
  final ActionStatus status;
  final String message;
  final Map<String, dynamic>? data;

  const ActionResult({
    required this.status,
    required this.message,
    this.data,
  });

  factory ActionResult.success(String message, {Map<String, dynamic>? data}) =>
      ActionResult(status: ActionStatus.success, message: message, data: data);

  factory ActionResult.failed(String message) =>
      ActionResult(status: ActionStatus.failed, message: message);

  factory ActionResult.unsupported(String message) =>
      ActionResult(status: ActionStatus.unsupported, message: message);

  factory ActionResult.permissionDenied(String message) =>
      ActionResult(status: ActionStatus.permissionDenied, message: message);

  factory ActionResult.cancelled(String message) =>
      ActionResult(status: ActionStatus.cancelled, message: message);

  bool get isSuccess => status == ActionStatus.success;
}
