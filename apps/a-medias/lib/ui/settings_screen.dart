import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../config.dart';
import '../data/app_state.dart';
import '../main.dart';
import 'plus_screen.dart';
import 'theme.dart';
import 'widgets.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appState,
      builder: (context, _) {
        final am = context.am;
        return Scaffold(
          appBar: AppBar(title: const Text('Ajustes')),
          body: ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 12, 40),
            children: [
              StickerCard(
                padding: EdgeInsets.zero,
                child: ListTile(
                  leading: const Icon(Icons.person_outline_rounded),
                  title: Text(appState.myName.isEmpty ? 'Sin nombre' : appState.myName),
                  subtitle: const Text('Tu nombre en los grupos nuevos'),
                  trailing: const Icon(Icons.edit_rounded),
                  onTap: () => _editName(context),
                ),
              ),
              const SizedBox(height: 20),
              Text('Aspecto', style: context.text.headlineSmall),
              const SizedBox(height: 8),
              SegmentedButton<ThemePref>(
                segments: const [
                  ButtonSegment(value: ThemePref.system, label: Text('Auto'), icon: Icon(Icons.brightness_auto_rounded)),
                  ButtonSegment(value: ThemePref.light, label: Text('Claro'), icon: Icon(Icons.light_mode_rounded)),
                  ButtonSegment(value: ThemePref.dark, label: Text('Oscuro'), icon: Icon(Icons.dark_mode_rounded)),
                ],
                selected: {appState.themePref},
                onSelectionChanged: (s) => appState.setThemePref(s.first),
              ),
              const SizedBox(height: 14),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  for (final p in palettes)
                    GestureDetector(
                      onTap: () {
                        if (p.plus && !appState.plus) {
                          Navigator.of(context).push(MaterialPageRoute(builder: (_) => const PlusScreen()));
                        } else {
                          appState.setTheme(p.id);
                        }
                      },
                      child: Column(
                        children: [
                          Container(
                            width: 58,
                            height: 58,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: am.line,
                                width: (appState.plus ? appState.themeId : 'tomate') == p.id ? 4 : 2,
                              ),
                              gradient: SweepGradient(colors: [p.primary, p.primary, p.accent, p.accent, p.primary]),
                            ),
                            child: p.plus && !appState.plus
                                ? const Icon(Icons.lock_rounded, color: Colors.white, size: 20)
                                : null,
                          ),
                          const SizedBox(height: 4),
                          Text(p.name, style: context.text.labelMedium),
                        ],
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 24),
              StickerCard(
                color: Theme.of(context).colorScheme.primary,
                onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const PlusScreen())),
                child: Row(
                  children: [
                    const Text('✨', style: TextStyle(fontSize: 28)),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('A medias Plus', style: context.text.titleLarge?.copyWith(color: Colors.white)),
                          Text(
                            appState.plus ? 'Activado. ¡Gracias por apoyar!' : 'Tiques, gráficas y temas. Un solo pago.',
                            style: context.text.bodyMedium?.copyWith(color: Colors.white),
                          ),
                        ],
                      ),
                    ),
                    const Icon(Icons.chevron_right_rounded, color: Colors.white),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              Text('Privacidad', style: context.text.headlineSmall),
              const SizedBox(height: 8),
              StickerCard(
                padding: EdgeInsets.zero,
                child: Column(
                  children: [
                    const ListTile(
                      leading: Icon(Icons.shield_outlined),
                      title: Text('Sin cuenta, sin anuncios, sin rastreo'),
                      subtitle: Text(
                        'Los grupos no compartidos solo están en tu móvil. Los compartidos se guardan cifrados en tránsito en Google Firebase (UE) y solo los ve quien tiene el enlace.',
                      ),
                    ),
                    const Divider(),
                    ListTile(
                      leading: const Icon(Icons.description_outlined),
                      title: const Text('Política de privacidad'),
                      onTap: () => launchUrl(Uri.parse(AppConfig.privacyUrl), mode: LaunchMode.externalApplication),
                    ),
                    if (AppConfig.supportEmail.isNotEmpty) ...[
                      const Divider(),
                      ListTile(
                        leading: const Icon(Icons.mail_outline_rounded),
                        title: const Text('Escríbenos'),
                        subtitle: const Text(AppConfig.supportEmail),
                        onTap: () => launchUrl(Uri(scheme: 'mailto', path: AppConfig.supportEmail)),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 24),
              Center(child: Text('A medias 1.0 · Hecho en España', style: context.text.bodySmall)),
            ],
          ),
        );
      },
    );
  }

  Future<void> _editName(BuildContext context) async {
    final c = TextEditingController(text: appState.myName);
    final name = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Tu nombre'),
        content: TextField(
          controller: c,
          autofocus: true,
          maxLength: 40,
          textCapitalization: TextCapitalization.words,
          decoration: const InputDecoration(counterText: ''),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(context, c.text), child: const Text('Guardar')),
        ],
      ),
    );
    c.dispose();
    if (name != null) appState.setMyName(name);
  }
}
