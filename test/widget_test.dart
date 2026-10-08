import 'package:flutter_test/flutter_test.dart';
import 'package:ai_voice_agent/ui/app.dart';

void main() {
  testWidgets('App loads', (WidgetTester tester) async {
    await tester.pumpWidget(const VoiceAgentApp());
    expect(find.text('Готов к работе'), findsOneWidget);
  });
}
