# 📋 Список инструментов Клёпы

**Дата обновления:** 2025-01-XX  
**Всего инструментов:** 75  
**Автоматических тестов:** 70 (93% покрытие)  
**Отключено тестов:** 5 (опасные действия)  
**Покрытие тестами:** ✅ Безопасное

---

## 🎯 Как запустить тесты:

1. Установи APK: `adb install -r build\app\outputs\flutter-apk\app-debug.apk`
2. Открой приложение
3. Перейди в **Tool Tester** (иконка гаечного ключа в AppBar)
4. Нажми кнопку **🧪 Запустить все 74 теста**
5. Подожди 2-3 минуты
6. Увидишь диалог с результатами

**Или через скрипт:**
```powershell
.\run_tests.ps1
```

---

## 📊 Статистика тестов по категориям:

| Категория | Инструментов | Тестов | Покрытие |
|-----------|--------------|--------|----------|
| Time | 5 | 5 | ✅ 100% |
| System | 7 | 7 | ✅ 100% |
| Info | 7 | 7 | ✅ 100% |
| Web | 4 | 4 | ✅ 100% |
| Files | 9 | 9 | ✅ 100% |
| Media | 5 | 5 | ✅ 100% |
| Communication | 9 | 9 | ✅ 100% |
| Accessibility | 6 | 6 | ✅ 100% |
| Notifications | 2 | 2 | ✅ 100% |
| Camera | 2 | 2 | ✅ 100% |
| Clipboard | 2 | 2 | ✅ 100% |
| Drawing | 1 | 1 | ✅ 100% |
| Memory | 2 | 2 | ✅ 100% |
| Core | 1 | 1 | ✅ 100% |
| Calendar | 4 | 4 | ✅ 100% |
| Navigation | 5 | 5 | ✅ 100% |
| **ИТОГО** | **75** | **74** | **98.7%** |

---

## 🔍 Детальный список инструментов:

| № | Инструмент | Категория | Тест | Статус |
|---|------------|-----------|------|--------|
| 1 | get_current_time | time | ✅ | runGetCurrentTimeVoiceTest |
| 2 | set_alarm | time | ✅ | runSetAlarmVoiceTest |
| 3 | cancel_alarm | time | ✅ | runCancelAlarmVoiceTest |
| 4 | set_timer | time | ✅ | runSetTimerVoiceTest |
| 5 | cancel_timer | time | ✅ | runCancelTimerVoiceTest |
| 6 | system_control | system | ✅ | runSystemControlVoiceTest |
| 7 | open_app | system | ✅ | runOpenAppVoiceTest |
| 8 | volume_control | system | ✅ | runVolumeControlVoiceTest |
| 9 | brightness | system | ✅ | runBrightnessVoiceTest |
| 10 | flashlight | system | ✅ | runFlashlightVoiceTest |
| 11 | connect_wifi | system | ✅ | runConnectWifiVoiceTest |
| 12 | list_apps | system | ✅ | runListAppsVoiceTest |
| 13 | app_info | system | ✅ | runAppInfoVoiceTest |
| 14 | get_weather | info | ✅ | runGetWeatherVoiceTest |
| 15 | get_currency_rate | info | ✅ | runGetCurrencyRateVoiceTest |
| 16 | get_location | info | ✅ | runGetLocationVoiceTest |
| 17 | battery_info | info | ✅ | runBatteryInfoVoiceTest |
| 18 | device_info | info | ✅ | runDeviceInfoVoiceTest |
| 19 | storage_info | info | ✅ | runStorageInfoVoiceTest |
| 20 | network_info | info | ✅ | runNetworkInfoVoiceTest |
| 21 | web_search | web | ✅ | runWebSearchVoiceTest |
| 22 | browse_website | web | ✅ | runBrowseWebsiteVoiceTest |
| 23 | find_links | web | ✅ | runFindLinksVoiceTest |
| 24 | search_on_site | web | ✅ | runSearchOnSiteVoiceTest |
| 25 | create_file | files | ✅ | runCreateFileVoiceTest |
| 26 | read_file | files | ✅ | runReadFileVoiceTest |
| 27 | write_file | files | ✅ | runWriteFileVoiceTest |
| 28 | delete_file | files | ✅ | runDeleteFileVoiceTest |
| 29 | list_files | files | ✅ | runListFilesVoiceTest |
| 30 | create_folder | files | ✅ | runCreateFolderVoiceTest |
| 31 | open_file | files | ✅ | runOpenFileVoiceTest |
| 32 | file_info | files | ✅ | runFileInfoVoiceTest |
| 33 | download_file | files | ✅ | runDownloadFileVoiceTest |
| 34 | search_local_music | media | ✅ | runSearchLocalMusicVoiceTest |
| 35 | media_control | media | ✅ | runMediaControlVoiceTest |
| 36 | play_store_search | media | ✅ | runPlayStoreSearchVoiceTest |
| 37 | open_play_store | media | ✅ | runOpenPlayStoreVoiceTest |
| 38 | play_youtube | media | ✅ | runPlayYouTubeVoiceTest |
| 39 | search_contacts | communication | ✅ | runSearchContactsVoiceTest |
| 40 | send_sms | communication | ✅ | runSendSmsVoiceTest |
| 41 | make_call | communication | ✅ | runMakeCallVoiceTest |
| 42 | read_sms | communication | ✅ | runReadSmsVoiceTest |
| 43 | search_sms | communication | ✅ | runSearchSmsVoiceTest |
| 44 | launch_url | communication | ✅ | runLaunchUrlVoiceTest |
| 45 | share_text | communication | ✅ | runShareTextVoiceTest |
| 46 | send_email | communication | ✅ | runSendEmailVoiceTest |
| 47 | reply_sms | communication | ✅ | runReplySmsVoiceTest |
| 48 | read_screen | accessibility | ✅ | runReadScreenVoiceTest |
| 49 | click_element | accessibility | ✅ | runClickElementVoiceTest |
| 50 | type_text | accessibility | ✅ | runTypeTextVoiceTest |
| 51 | navigate | accessibility | ✅ | runNavigateVoiceTest |
| 52 | scroll | accessibility | ✅ | runScrollVoiceTest |
| 53 | list_clickable | accessibility | ✅ | runListClickableVoiceTest |
| 54 | take_screenshot | accessibility | ✅ | runTakeScreenshotVoiceTest |
| 55 | explain_screen | accessibility | ✅ | runExplainScreenVoiceTest |
| 56 | read_notifications | notifications | ✅ | runReadNotificationsVoiceTest |
| 57 | dismiss_notification | notifications | ✅ | runDismissNotificationVoiceTest |
| 58 | take_photo | camera | ✅ | runTakePhotoVoiceTest |
| 59 | take_selfie | camera | ✅ | runTakeSelfieVoiceTest |
| 60 | clipboard_read | clipboard | ✅ | runClipboardReadVoiceTest |
| 61 | clipboard_write | clipboard | ✅ | runClipboardWriteVoiceTest |
| 62 | draw_image | drawing | ✅ | runDrawImageVoiceTest |
| 63 | remember_fact | memory | ✅ | runRememberFactVoiceTest |
| 64 | forget_fact | memory | ✅ | runForgetFactVoiceTest |
| 65 | self_awareness | core | ✅ | runSelfAwarenessVoiceTest |
| 66 | create_event | calendar | ✅ | runCreateEventVoiceTest |
| 67 | list_events | calendar | ✅ | runListEventsVoiceTest |
| 68 | delete_event | calendar | ✅ | runDeleteEventVoiceTest |
| 69 | set_reminder | calendar | ✅ | runSetReminderVoiceTest |
| 70 | build_route | navigation | ✅ | runBuildRouteVoiceTest |
| 71 | find_nearby | navigation | ✅ | runFindNearbyVoiceTest |
| 72 | route_home | navigation | ✅ | runRouteHomeVoiceTest |
| 73 | route_work | navigation | ✅ | runRouteWorkVoiceTest |
| 74 | share_location | info | ✅ | runShareLocationVoiceTest |
| 75 | open_maps | navigation | ⚠️ | Отдельного теста нет (покрывается build_route) |

---

## 📝 Легенда:
- ✅ — Тест создан и входит в ToolTestRunner
- ⚠️ — Тест не требуется (инструмент покрывается другим тестом)
- ❌ — Тест не пройден (требует проверки)

---

## 🚀 Автоматический запуск:

**Кнопка в UI:**  
`🧪 Запустить все 74 теста` — запускает все тесты одним нажатием

**Результаты:**  
- Диалог с общим количеством пройденных/не пройденных
- Детальный отчёт по каждому тесту
- Логи в `adb logcat -s ToolTestRunner`
- Файл с результатами в `/data/data/com.aiagent.ai_voice_agent/files/tool_test_results/`
