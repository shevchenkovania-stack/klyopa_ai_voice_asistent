import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

class PermissionsScreen extends StatefulWidget {
  const PermissionsScreen({super.key});

  @override
  State<PermissionsScreen> createState() => _PermissionsScreenState();
}

class _PermissionsScreenState extends State<PermissionsScreen> {
  final Map<Permission, bool> _permissions = {};

  final _permissionsList = [
    (Permission.microphone, 'Микрофон', 'Для распознавания голоса'),
    (Permission.camera, 'Камера', 'Для селфи и фото'),
    (Permission.contacts, 'Контакты', 'Для поиска и управления контактами'),
    (Permission.sms, 'SMS', 'Для отправки и чтения сообщений'),
    (Permission.phone, 'Телефон', 'Для совершения звонков'),
    (Permission.storage, 'Хранилище', 'Для работы с файлами'),
    (Permission.notification, 'Уведомления', 'Для фонового режима'),
    (Permission.ignoreBatteryOptimizations, 'Батарея', 'Чтобы агент не засыпал'),
  ];

  @override
  void initState() {
    super.initState();
    _checkPermissions();
  }

  Future<void> _checkPermissions() async {
    for (final (perm, _, _) in _permissionsList) {
      _permissions[perm] = await perm.isGranted;
    }
    setState(() {});
  }

  Future<void> _requestPermission(Permission permission) async {
    final status = await permission.request();
    setState(() {
      _permissions[permission] = status.isGranted;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Разрешения')),
      body: ListView(
        children: _permissionsList.map((item) {
          final (permission, name, description) = item;
          final granted = _permissions[permission] ?? false;

          return ListTile(
            leading: Icon(
              granted ? Icons.check_circle : Icons.circle_outlined,
              color: granted ? Colors.green : Colors.grey,
            ),
            title: Text(name),
            subtitle: Text(description),
            trailing: granted
                ? const Text('Разрешено', style: TextStyle(color: Colors.green))
                : TextButton(
                    onPressed: () => _requestPermission(permission),
                    child: const Text('Разрешить'),
                  ),
          );
        }).toList(),
      ),
    );
  }
}
