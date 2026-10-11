import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:in_app_purchase/in_app_purchase.dart';

import '../config.dart';
import '../data/app_state.dart';

/// Compra única "A medias Plus" con Google Play Billing.
///
/// Lo esencial de la app (grupos, gastos, repartos, saldos, enlaces,
/// exportar) es gratis y sin límites. Plus desbloquea extras: escanear
/// tiques, gráficas y temas de color.
class Billing extends ChangeNotifier {
  Billing(this._state);

  final AppState _state;
  StreamSubscription<List<PurchaseDetails>>? _sub;

  bool available = false;
  bool busy = false;
  ProductDetails? product;
  String? message;

  bool get supported => !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  Future<void> init() async {
    if (!supported) return;
    final iap = InAppPurchase.instance;
    _sub = iap.purchaseStream.listen(_onPurchases, onError: (Object e) {
      message = 'No se pudo completar la compra.';
      busy = false;
      notifyListeners();
    });
    try {
      available = await iap.isAvailable();
      if (!available) return;
      final res = await iap.queryProductDetails({AppConfig.plusProductId});
      if (res.productDetails.isNotEmpty) product = res.productDetails.first;
      // Recupera la compra si se reinstaló la app o se cambió de móvil.
      await iap.restorePurchases();
    } catch (e) {
      debugPrint('Billing: $e');
    }
    notifyListeners();
  }

  Future<void> buy() async {
    final p = product;
    if (p == null) {
      message = 'La tienda no está disponible ahora mismo.';
      notifyListeners();
      return;
    }
    busy = true;
    message = null;
    notifyListeners();
    try {
      await InAppPurchase.instance.buyNonConsumable(
        purchaseParam: PurchaseParam(productDetails: p),
      );
    } catch (e) {
      busy = false;
      message = 'No se pudo iniciar la compra.';
      notifyListeners();
    }
  }

  Future<void> restore() async {
    if (!supported) return;
    busy = true;
    notifyListeners();
    try {
      await InAppPurchase.instance.restorePurchases();
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  Future<void> _onPurchases(List<PurchaseDetails> list) async {
    for (final p in list) {
      if (p.productID != AppConfig.plusProductId) continue;
      switch (p.status) {
        case PurchaseStatus.purchased:
        case PurchaseStatus.restored:
          // Play solo entrega compras firmadas por Google a esta app; si en el
          // futuro hay servidor propio, aquí se verificaría el token con la
          // API de Google Play Developer antes de desbloquear.
          _state.setPlus(true);
          message = '¡Gracias! Ya tienes A medias Plus.';
        case PurchaseStatus.pending:
          message = 'Pago pendiente. Se activará en cuanto se confirme.';
        case PurchaseStatus.error:
          message = 'La compra no se completó.';
        case PurchaseStatus.canceled:
          message = null;
      }
      // Hay que confirmar la compra o Google la reembolsa a los 3 días.
      if (p.pendingCompletePurchase) {
        await InAppPurchase.instance.completePurchase(p);
      }
    }
    busy = false;
    notifyListeners();
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }
}
