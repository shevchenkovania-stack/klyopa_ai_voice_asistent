import 'dart:ui';
import 'package:flutter/material.dart';

/// Reusable API key card widget
class ApiKeyCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final Color iconColor;
  final TextEditingController controller;
  final bool isVisible;
  final bool isValid;
  final String hint;
  final VoidCallback onToggleVisibility;
  final VoidCallback onValidate;
  final bool isDark;

  const ApiKeyCard({
    super.key,
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.iconColor,
    required this.controller,
    required this.isVisible,
    required this.isValid,
    required this.hint,
    required this.onToggleVisibility,
    required this.onValidate,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(20),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
        child: Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            color: isDark
                ? Colors.white.withAlpha(13)
                : Colors.white.withAlpha(200),
            border: Border.all(
              color: isDark
                  ? Colors.white.withAlpha(25)
                  : Colors.grey.withAlpha(50),
            ),
            boxShadow: [
              if (!isDark)
                BoxShadow(
                  color: Colors.black.withAlpha(8),
                  blurRadius: 20,
                  offset: const Offset(0, 4),
                ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildHeader(),
              const SizedBox(height: 16),
              _buildTextField(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Row(
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            color: iconColor.withAlpha(25),
          ),
          child: Icon(icon, color: iconColor, size: 24),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                  color: isDark ? Colors.white : Colors.black87,
                ),
              ),
              Text(
                subtitle,
                style: TextStyle(
                  fontSize: 13,
                  color: isDark
                      ? Colors.white.withAlpha(120)
                      : Colors.black54,
                ),
              ),
            ],
          ),
        ),
        _buildStatusBadge(),
      ],
    );
  }

  Widget _buildStatusBadge() {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 300),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        color: isValid ? Colors.green.withAlpha(25) : Colors.red.withAlpha(25),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            isValid ? Icons.check_circle_rounded : Icons.error_outline_rounded,
            size: 14,
            color: isValid ? Colors.green : Colors.red.shade300,
          ),
          const SizedBox(width: 4),
          Text(
            isValid ? 'OK' : '---',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: isValid ? Colors.green : Colors.red.shade300,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTextField() {
    return TextField(
      controller: controller,
      obscureText: !isVisible,
      style: TextStyle(
        fontFamily: 'monospace',
        fontSize: 14,
        color: isDark ? Colors.white : Colors.black87,
      ),
      decoration: InputDecoration(
        hintText: hint,
        hintStyle: TextStyle(
          color: isDark ? Colors.white.withAlpha(60) : Colors.black26,
          fontFamily: 'monospace',
        ),
        filled: true,
        fillColor: isDark ? Colors.white.withAlpha(8) : Colors.grey.shade50,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(
            color: isDark ? Colors.white.withAlpha(15) : Colors.grey.withAlpha(40),
          ),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(
            color: iconColor.withAlpha(150),
            width: 2,
          ),
        ),
        suffixIcon: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              icon: Icon(
                isVisible ? Icons.visibility_off_rounded : Icons.visibility_rounded,
                size: 20,
                color: isDark ? Colors.white.withAlpha(100) : Colors.black45,
              ),
              onPressed: onToggleVisibility,
            ),
            if (controller.text.isNotEmpty)
              IconButton(
                icon: Icon(
                  Icons.clear_rounded,
                  size: 20,
                  color: isDark ? Colors.white.withAlpha(100) : Colors.black45,
                ),
                onPressed: () {
                  controller.clear();
                  onValidate();
                },
              ),
          ],
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      ),
    );
  }
}

/// Status indicator chip
class StatusChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool isActive;
  final bool isDark;

  const StatusChip({
    super.key,
    required this.icon,
    required this.label,
    required this.isActive,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color: isDark ? Colors.white.withAlpha(8) : Colors.white.withAlpha(200),
        border: Border.all(
          color: isActive
              ? Colors.green.withAlpha(80)
              : (isDark ? Colors.white.withAlpha(15) : Colors.grey.withAlpha(40)),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: isActive ? Colors.green : Colors.grey),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              label,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w500,
                color: isDark ? Colors.white.withAlpha(200) : Colors.black87,
              ),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}
