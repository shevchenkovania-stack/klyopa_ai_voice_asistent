class Contact {
  final String id;
  final String displayName;
  final String? phoneNumber;
  final String? email;

  const Contact({
    required this.id,
    required this.displayName,
    this.phoneNumber,
    this.email,
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'displayName': displayName,
        'phoneNumber': phoneNumber,
        'email': email,
      };
}
