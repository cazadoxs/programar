import 'package:flutter/material.dart';

import '../main.dart';
import 'theme.dart';
import 'widgets.dart';

/// Compra única de A medias Plus.
class PlusScreen extends StatelessWidget {
  const PlusScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([appState, billing]),
      builder: (context, _) {
        final am = context.am;
        final price = billing.product?.price;
        return Scaffold(
          appBar: AppBar(),
          body: ListView(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 32),
            children: [
              Row(
                children: [
                  const SplitLogo(size: 48),
                  const SizedBox(width: 10),
                  Text('Plus', style: context.text.displaySmall),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                'A medias es y será gratis y sin límites. Plus es un extra para quien quiera más, con un solo pago y sin suscripción.',
                style: context.text.bodyLarge?.copyWith(color: am.muted),
              ),
              const SizedBox(height: 20),
              const _Feature(emoji: '📸', title: 'Escanear tiques', text: 'Haz una foto y A medias rellena el importe, el comercio y la fecha. Se procesa en tu móvil, la foto no sale de él.'),
              const _Feature(emoji: '📊', title: 'Gráficas', text: 'En qué se os va el dinero, quién paga más y cómo evoluciona cada mes.'),
              const _Feature(emoji: '🎨', title: 'Temas de color', text: 'Menta, Uva, Mostaza, Chicle y Mar, en claro y oscuro.'),
              const _Feature(emoji: '💛', title: 'Apoyas una app sin anuncios', text: 'Sin publicidad, sin vender datos y sin un banco detrás.'),
              const SizedBox(height: 20),
              if (appState.plus)
                StickerCard(
                  color: am.positive.withValues(alpha: 0.18),
                  child: Row(
                    children: [
                      const Text('🎉', style: TextStyle(fontSize: 28)),
                      const SizedBox(width: 12),
                      Expanded(child: Text('Ya tienes Plus. ¡Gracias!', style: context.text.titleMedium)),
                    ],
                  ),
                )
              else if (!billing.supported)
                Text(
                  'La compra se hace desde la app de Android (Google Play).',
                  style: context.text.bodyMedium,
                  textAlign: TextAlign.center,
                )
              else ...[
                FilledButton(
                  onPressed: billing.busy ? null : billing.buy,
                  child: billing.busy
                      ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
                      : Text(price == null ? 'Conseguir Plus' : 'Conseguir Plus por $price'),
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: billing.busy ? null : billing.restore,
                  child: const Text('Ya lo compré: restaurar'),
                ),
              ],
              if (billing.message != null) ...[
                const SizedBox(height: 12),
                Text(billing.message!, textAlign: TextAlign.center, style: context.text.bodyMedium),
              ],
            ],
          ),
        );
      },
    );
  }
}

class _Feature extends StatelessWidget {
  const _Feature({required this.emoji, required this.title, required this.text});
  final String emoji;
  final String title;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: StickerCard(
        offset: 3,
        padding: const EdgeInsets.all(14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
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
