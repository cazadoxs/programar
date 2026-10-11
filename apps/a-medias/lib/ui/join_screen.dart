import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/invite.dart';
import '../main.dart';
import 'group_screen.dart';
import 'theme.dart';
import 'widgets.dart';

/// Entrar en un grupo con un enlace, sin registrarse.
class JoinScreen extends StatefulWidget {
  const JoinScreen({super.key, this.initial});
  final Invite? initial;

  @override
  State<JoinScreen> createState() => _JoinScreenState();
}

class _JoinScreenState extends State<JoinScreen> {
  final _link = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    if (widget.initial != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _join(widget.initial!));
    }
  }

  @override
  void dispose() {
    _link.dispose();
    super.dispose();
  }

  Future<void> _paste() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    if (data?.text != null) setState(() => _link.text = data!.text!);
  }

  Future<void> _join(Invite invite) async {
    if (!appState.onboarded) appState.finishOnboarding();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final d = await appState.join(invite);
      if (!mounted) return;
      Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => GroupScreen(groupId: d.group.id)));
    } catch (e) {
      setState(() {
        _busy = false;
        _error = e.toString().contains('permission-denied')
            ? 'Este enlace ya no funciona. Pide uno nuevo a alguien del grupo.'
            : appState.canShare
                ? 'No se pudo entrar. Comprueba la conexión e inténtalo otra vez.'
                : 'Esta versión de la app no puede unirse a grupos compartidos.';
      });
    }
  }

  void _submit() {
    final invite = Invite.parse(_link.text);
    if (invite == null) {
      setState(() => _error = 'Eso no parece un enlace de A medias.');
      return;
    }
    _join(invite);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Unirse a un grupo')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
        children: [
          const Center(child: SplitLogo(size: 64)),
          const SizedBox(height: 18),
          Text(
            _busy ? 'Entrando en el grupo…' : 'Pega el enlace que te han mandado',
            style: context.text.headlineSmall,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 8),
          Text(
            'No hace falta cuenta ni correo. Verás los gastos del grupo y podrás apuntar los tuyos.',
            style: context.text.bodyMedium?.copyWith(color: context.am.muted),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 24),
          if (_busy)
            const Center(child: CircularProgressIndicator())
          else ...[
            TextField(
              controller: _link,
              decoration: InputDecoration(
                labelText: 'Enlace',
                hintText: 'https://…/unirse#…',
                suffixIcon: IconButton(tooltip: 'Pegar', icon: const Icon(Icons.content_paste_rounded), onPressed: _paste),
              ),
              onSubmitted: (_) => _submit(),
            ),
            const SizedBox(height: 16),
            FilledButton(onPressed: _submit, child: const Text('Entrar')),
          ],
          if (_error != null) ...[
            const SizedBox(height: 16),
            Text(_error!, style: TextStyle(color: context.am.negative, fontWeight: FontWeight.w700), textAlign: TextAlign.center),
          ],
        ],
      ),
    );
  }
}
