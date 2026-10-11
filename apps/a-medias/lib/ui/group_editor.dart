import 'package:flutter/material.dart';

import '../core/models.dart';
import '../core/money.dart';
import '../main.dart';
import 'theme.dart';
import 'widgets.dart';

const groupEmojis = [
  '🏠', '✈️', '🏖️', '⛰️', '🍕', '🍻', '💑', '🎉', '🚗', '🎓',
  '⚽', '🎸', '🏕️', '🎿', '🛒', '🐶', '👶', '💼', '🎄', '🌮',
];

/// Crear un grupo nuevo: nombre, emoji, moneda y quién está.
class GroupEditor extends StatefulWidget {
  const GroupEditor({super.key});

  @override
  State<GroupEditor> createState() => _GroupEditorState();
}

class _GroupEditorState extends State<GroupEditor> {
  final _name = TextEditingController();
  final _person = TextEditingController();
  final _people = <String>[];
  String _emoji = '🏠';
  String _currency = 'EUR';
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    _person.dispose();
    super.dispose();
  }

  void _addPerson() {
    final n = cleanText(_person.text, Limits.nameLength);
    if (n.isEmpty) return;
    if (_people.length >= Limits.maxPeople - 1) {
      setState(() => _error = 'Máximo ${Limits.maxPeople} personas por grupo.');
      return;
    }
    setState(() {
      _people.add(n);
      _person.clear();
      _error = null;
    });
  }

  Future<void> _save() async {
    if (_person.text.trim().isNotEmpty) _addPerson();
    if (_name.text.trim().isEmpty) {
      setState(() => _error = 'Ponle nombre al grupo.');
      return;
    }
    final d = await appState.createGroup(
      name: _name.text,
      emoji: _emoji,
      currency: _currency,
      otherNames: _people,
    );
    if (mounted) Navigator.of(context).pop(d);
  }

  @override
  Widget build(BuildContext context) {
    final am = context.am;
    final me = appState.myName.isEmpty ? 'Tú' : appState.myName;
    return Scaffold(
      appBar: AppBar(title: const Text('Nuevo grupo')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
        children: [
          TextField(
            controller: _name,
            autofocus: true,
            maxLength: Limits.groupNameLength,
            textCapitalization: TextCapitalization.sentences,
            style: context.text.titleLarge,
            decoration: const InputDecoration(
              labelText: 'Nombre',
              hintText: 'Piso Lavapiés, Viaje a Lisboa…',
              counterText: '',
            ),
          ),
          const SizedBox(height: 20),
          Text('Icono', style: context.text.titleSmall),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final e in groupEmojis)
                GestureDetector(
                  onTap: () => setState(() => _emoji = e),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 150),
                    width: 48,
                    height: 48,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: _emoji == e ? am.accent : am.card,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: am.line, width: _emoji == e ? 2.5 : 1.5),
                    ),
                    child: Text(e, style: const TextStyle(fontSize: 24)),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 20),
          DropdownButtonFormField<String>(
            initialValue: _currency,
            decoration: const InputDecoration(labelText: 'Moneda del grupo'),
            items: [
              for (final c in currencies) DropdownMenuItem(value: c.code, child: Text(currencyLabel(c.code))),
            ],
            onChanged: (v) => setState(() => _currency = v ?? 'EUR'),
          ),
          const SizedBox(height: 6),
          Text(
            'Podrás apuntar gastos en otras monedas; se convierten a esta.',
            style: context.text.bodySmall,
          ),
          const SizedBox(height: 24),
          Text('¿Quién está?', style: context.text.titleSmall),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              Chip(
                avatar: const Icon(Icons.star_rounded, size: 18),
                label: Text('$me (tú)'),
              ),
              for (var i = 0; i < _people.length; i++)
                InputChip(
                  label: Text(_people[i]),
                  onDeleted: () => setState(() => _people.removeAt(i)),
                ),
            ],
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _person,
            maxLength: Limits.nameLength,
            textCapitalization: TextCapitalization.words,
            decoration: InputDecoration(
              labelText: 'Añadir persona',
              counterText: '',
              suffixIcon: IconButton(icon: const Icon(Icons.person_add_alt_1_rounded), onPressed: _addPerson),
            ),
            onSubmitted: (_) => _addPerson(),
          ),
          const SizedBox(height: 6),
          Text(
            'No necesitan la app ni cuenta. Si la tienen, les mandas un enlace y entran.',
            style: context.text.bodySmall,
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!, style: TextStyle(color: am.negative, fontWeight: FontWeight.w600)),
          ],
          const SizedBox(height: 24),
          FilledButton(onPressed: _save, child: const Text('Crear grupo')),
        ],
      ),
    );
  }
}
