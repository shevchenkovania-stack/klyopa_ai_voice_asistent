# Скрипт для запуска автоматических тестов Клёпы
# Запускает тесты через Flutter UI и собирает результаты из логов

Write-Host "=== Запуск автоматических тестов Клёпы ===" -ForegroundColor Cyan
Write-Host ""

# Очищаем логи
Write-Host "Очистка логов..." -ForegroundColor Yellow
adb logcat -c

# Запускаем приложение
Write-Host "Запуск приложения..." -ForegroundColor Yellow
adb shell am start -n com.aiagent.ai_voice_agent/.MainActivity

Write-Host ""
Write-Host "Приложение запущено!" -ForegroundColor Green
Write-Host ""
Write-Host "ИНСТРУКЦИЯ:" -ForegroundColor Yellow
Write-Host "1. Открой Tool Tester (иконка гаечного ключа)" -ForegroundColor White
Write-Host "2. Нажми кнопку 'Запустить все 74 теста'" -ForegroundColor White
Write-Host "3. Подожди 2-3 минуты пока тесты завершатся" -ForegroundColor White
Write-Host "4. Увидишь диалог с результатами" -ForegroundColor White
Write-Host ""
Write-Host "Для просмотра логов в реальном времени:" -ForegroundColor Cyan
Write-Host "adb logcat -s ToolTestRunner" -ForegroundColor White
Write-Host ""
Write-Host "Для сохранения результатов:" -ForegroundColor Cyan
Write-Host "adb logcat -s ToolTestRunner -d > test_results.txt" -ForegroundColor White
