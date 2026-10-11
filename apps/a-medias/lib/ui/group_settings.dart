import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../config.dart';
import '../core/csv_export.dart';
import '../core/models.dart';
import '../core/money.dart';
import '../main.dart';
import 'group_editor.dart';
import 'group_screen.dart';
import 'theme.dart';
import 'widgets.dart';

class GroupSettings extends StatelessWidget {
  const GroupSettings({super.key, required this.groupId});
  final String groupId;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: appState,
      builder: (context, _) {
        final d = appState.group(groupId);
        if (d == null) return const Scaffold();
        final g = d.group;
        final am = context.am;
        return Scaffold(
          appBar: AppBar(title: const Text('Ajustes del grupo')),
          body: ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 12, 40),
            children: [
              StickerCard(
                padding: EdgeInsets.zero,
                child: Column(
                  children: [
                    ListTile(
                      leading: Text(g.emoji, style: const TextStyle(fontSize: 26)),
                      title: Text(g.name, style: context.text.titleMedium),
                      subtitle: const Text('Nombre e icono'),
                      trailing: const Icon(Icons.edit_rounded),
                      onTap: () => _editInfo(context, d),
                    ),
                    const Divider(),
                    ListTile(
                      leading: const Icon(Icons.payments_outlined),
                      title: Text(currencyLabel(g.currency)),
                      subtitle: Text(appState.canChangeCurrency(g.id)
                          ? 'Moneda del grupo'
                          : 'No se puede cambiar cuando ya hay gastos'),
                      onTap: appState.canChangeCurrency(g.id) ? () => _pickCurrency(context, d) : null,
                    ),
                    const Divider(),
                    ListTile(
                      leading: const Icon(Icons.badge_outlined),
                      title: Text(d.meId == null ? 'No has dicho quién eres' : 'Eres ${g.nameOf(d.meId!)}'),
                      subtitle: const Text('Solo en este móvil'),
                      onTap: () => showWhoAmI(context, d),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
              Text('Personas', style: context.text.headlineSmall),
              const SizedBox(height: 8),
              StickerCard(
                padding: EdgeInsets.zero,
                child: Column(
                  children: [
                    for (final p in g.people)
                      ListTile(
                        leading: Avatar(p),
                        title: Text(p.id == d.meId ? '${p.name} (tú)' : p.name),
                        trailing: PopupMenuButton<String>(
                          onSelected: (v) {
                            if (v == 'rename') _renamePerson(context, d, p);
                            if (v == 'remove') appState.removePerson(g.id, p.id);
                          },
                          itemBuilder: (_) => [
                            const PopupMenuItem(value: 'rename', child: Text('Cambiar nombre')),
                            PopupMenuItem(
                              value: 'remove',
                              enabled: appState.canRemovePerson(g.id, p.id),
                              child: const Text('Quitar del grupo'),
                            ),
                          ],
                        ),
                      ),
                    ListTile(
                      leading: const Icon(Icons.person_add_alt_1_rounded),
                      title: const Text('Añadir persona'),
                      onTap: () => _addPerson(context, d),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 6),
              Text('Solo se puede quitar a quien no aparece en ningún gasto.', style: context.text.bodySmall),
              const SizedBox(height: 20),
              Text('Compartir', style: context.text.headlineSmall),
              const SizedBox(height: 8),
              StickerCard(
                padding: EdgeInsets.zero,
                child: Column(
                  children: [
                    ListTile(
                      leading: const Icon(Icons.link_rounded),
                      title: Text(d.cloud ? 'Enlace de invitación' : 'Compartir el grupo'),
                      subtitle: Text(d.cloud
                          ? 'Cualquiera con el enlace puede entrar'
                          : 'Ahora solo está en este móvil'),
                      onTap: () => _share(context, d),
                    ),
                    if (d.cloud) ...[
                      const Divider(),
                      ListTile(
                        leading: const Icon(Icons.lock_reset_rounded),
                        title: const Text('Cambiar el enlace'),
                        subtitle: const Text('El anterior deja de funcionar; quien ya está dentro sigue dentro'),
                        onTap: () => _rotate(context, d),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(height: 20),
              Text('Datos', style: context.text.headlineSmall),
              const SizedBox(height: 8),
              StickerCard(
                padding: EdgeInsets.zero,
                child: Column(
                  children: [
                    ListTile(
                      leading: const Icon(Icons.table_view_rounded),
                      title: const Text('Exportar a CSV'),
                      subtitle: const Text('Para Excel o Google Sheets'),
                      onTap: () => _exportCsv(context, d),
                    ),
                    const Divider(),
                    ListTile(
                      leading: Icon(g.archived ? Icons.unarchive_outlined : Icons.archive_outlined),
                      title: Text(g.archived ? 'Desarchivar' : 'Archivar'),
                      subtitle: const Text('Para grupos terminados, como un viaje'),
                      onTap: () {
                        appState.setArchived(g.id, !g.archived);
                        Navigator.of(context).pop();
                      },
                    ),
                    const Divider(),
                    ListTile(
                      leading: Icon(Icons.logout_rounded, color: am.negative),
                      title: Text(d.cloud ? 'Salir del grupo en este móvil' : 'Borrar el grupo',
                          style: TextStyle(color: am.negative, fontWeight: FontWeight.w700)),
                      subtitle: Text(d.cloud
                          ? 'El resto del grupo lo conserva'
                          : 'Se borran todos sus gastos de este móvil'),
                      onTap: () => _remove(context, d),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Future<String?> _ask(BuildContext context, String title, {String initial = '', int max = 40}) {
    final c = TextEditingController(text: initial);
    return showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: c,
          autofocus: true,
          maxLength: max,
          textCapitalization: TextCapitalization.words,
          decoration: const InputDecoration(counterText: ''),
          onSubmitted: (v) => Navigator.pop(context, v),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(context, c.text), child: const Text('Guardar')),
        ],
      ),
    ).whenComplete(c.dispose);
  }

  Future<void> _editInfo(BuildContext context, GroupData d) async {
    final name = await _ask(context, 'Nombre del grupo', initial: d.group.name, max: Limits.groupNameLength);
    if (name == null || !context.mounted) return;
    final emoji = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
        child: Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final e in groupEmojis)
              InkWell(
                onTap: () => Navigator.pop(context, e),
                child: Padding(padding: const EdgeInsets.all(8), child: Text(e, style: const TextStyle(fontSize: 28))),
              ),
          ],
        ),
      ),
    );
    appState.updateGroupInfo(d.group.id, name: name, emoji: emoji);
  }

  Future<void> _pickCurrency(BuildContext context, GroupData d) async {
    final code = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => ListView(
        children: [
          for (final c in currencies.map((c) => c.code))
            ListTile(title: Text(currencyLabel(c)), onTap: () => Navigator.pop(context, c)),
        ],
      ),
    );
    if (code != null) appState.updateGroupInfo(d.group.id, currency: code);
  }

  Future<void> _addPerson(BuildContext context, GroupData d) async {
    final name = await _ask(context, 'Nombre');
    if (name == null) return;
    if (!appState.addPerson(d.group.id, name) && context.mounted) {
      toast(context, 'No se pudo añadir (máximo ${Limits.maxPeople} personas).');
    }
  }

  Future<void> _renamePerson(BuildContext context, GroupData d, Person p) async {
    final name = await _ask(context, 'Cambiar nombre', initial: p.name);
    if (name != null) appState.renamePerson(d.group.id, p.id, name);
  }

  Future<void> _share(BuildContext context, GroupData d) async {
    if (!appState.canShare) {
      toast(context, 'Esta versión no puede compartir grupos.');
      return;
    }
    try {
      final invite = await appState.share(d.group.id);
      if (!context.mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        builder: (_) => ShareSheet(groupName: d.group.name, link: invite.toUri(AppConfig.webHost).toString()),
      );
    } catch (_) {
      if (context.mounted) toast(context, 'No se pudo crear el enlace. ¿Tienes conexión?');
    }
  }

  Future<void> _rotate(BuildContext context, GroupData d) async {
    try {
      final invite = await appState.rotateInvite(d.group.id);
      if (!context.mounted) return;
      toast(context, 'Enlace cambiado. El anterior ya no sirve.');
      await showModalBottomSheet<void>(
        context: context,
        builder: (_) => ShareSheet(groupName: d.group.name, link: invite.toUri(AppConfig.webHost).toString()),
      );
    } catch (_) {
      if (context.mounted) toast(context, 'No se pudo cambiar el enlace. ¿Tienes conexión?');
    }
  }

  Future<void> _exportCsv(BuildContext context, GroupData d) async {
    final csv = exportCsv(d.group, d.expenses);
    final safeName = d.group.name.replaceAll(RegExp(r'[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ _-]'), '').trim();
    final fileName = '${safeName.isEmpty ? 'grupo' : safeName}.csv';
    await SharePlus.instance.share(
      ShareParams(
        files: [XFile.fromData(Uint8List.fromList(utf8.encode(csv)), mimeType: 'text/csv', name: fileName)],
        fileNameOverrides: [fileName],
        subject: 'Gastos de ${d.group.name}',
      ),
    );
  }

  Future<void> _remove(BuildContext context, GroupData d) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(d.cloud ? '¿Salir de «${d.group.name}»?' : '¿Borrar «${d.group.name}»?'),
        content: Text(d.cloud
            ? 'Dejarás de verlo en este móvil. Podrás volver con el enlace.'
            : 'Se borrarán el grupo y sus gastos. No se puede deshacer.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(d.cloud ? 'Salir' : 'Borrar')),
        ],
      ),
    );
    if (ok != true || !context.mounted) return;
    Navigator.of(context).popUntil((r) => r.isFirst);
    await appState.removeGroup(d.group.id);
  }
}
