import 'package:flutter/material.dart';

import '../main.dart';
import 'theme.dart';
import 'widgets.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final _name = TextEditingController();

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  void _start() {
    appState.setMyName(_name.text);
    appState.finishOnboarding();
  }

  @override
  Widget build(BuildContext context) {
    final am = context.am;
    final primary = Theme.of(context).colorScheme.primary;
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 32, 24, 24),
          children: [
            const SplitLogo(size: 72),
            const SizedBox(height: 24),
            Text.rich(
              TextSpan(children: [
                const TextSpan(text: 'Las cuentas claras,\n'),
                TextSpan(text: 'a medias.', style: TextStyle(color: primary)),
              ]),
              style: context.text.displayMedium,
            ),
            const SizedBox(height: 16),
            Text(
              'Apunta lo que pagáis en el piso, el viaje o la pareja y A medias te dice quién debe a quién. Sin límites, sin anuncios y sin pedirte una cuenta.',
              style: context.text.bodyLarge?.copyWith(color: am.muted),
            ),
            const SizedBox(height: 28),
            const _Point(emoji: '♾️', title: 'Sin límites', text: 'Todos los gastos que quieras, sin esperas entre uno y otro.'),
            const _Point(emoji: '🙅', title: 'Sin cuenta', text: 'Tus amigos entran con un enlace. Ni correo ni contraseña.'),
            const _Point(emoji: '🏦', title: 'Sin banco detrás', text: 'No movemos tu dinero ni te vendemos nada. Saldáis por Bizum como siempre.'),
            const SizedBox(height: 28),
            TextField(
              controller: _name,
              textCapitalization: TextCapitalization.words,
              maxLength: 40,
              decoration: const InputDecoration(
                labelText: '¿Cómo te llamas?',
                hintText: 'Así te verán en los grupos',
                counterText: '',
              ),
              onSubmitted: (_) => _start(),
            ),
            const SizedBox(height: 16),
            FilledButton(onPressed: _start, child: const Text('Empezar')),
          ],
        ),
      ),
    );
  }
}

class _Point extends StatelessWidget {
  const _Point({required this.emoji, required this.title, required this.text});
  final String emoji;
  final String title;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: StickerCard(
        offset: 3,
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            Text(emoji, style: const TextStyle(fontSize: 28)),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: context.text.titleMedium),
                  const SizedBox(height: 2),
                  Text(text, style: context.text.bodyMedium?.copyWith(color: context.am.muted)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
