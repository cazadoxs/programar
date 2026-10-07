import ExpoModulesCore
import FamilyControls
import ManagedSettings
import SwiftUI

/**
 * iOS blocking through Apple's Screen Time API.
 *
 * Apple only allows blocking whole apps, categories and web domains — never a
 * section inside another app — so on iOS every block is treated as "app".
 * Apps are chosen with Apple's FamilyActivityPicker (we only get opaque
 * tokens, not bundle ids). Requires the com.apple.developer.family-controls
 * entitlement, which Apple must approve before App Store distribution.
 *
 * TODO(phase 2): DeviceActivityMonitor extension so schedules apply with the
 * app closed; today shields are applied/removed while the app runs and when
 * it returns to the foreground.
 */
public class FocoBlockerModule: Module {
  private let store = ManagedSettingsStore()
  private let defaults = UserDefaults.standard
  private let selectionKey = "foco.familyActivitySelection"

  public func definition() -> ModuleDefinition {
    Name("FocoBlocker")

    Function("getStatus") { () -> [String: Any] in
      let status = AuthorizationCenter.shared.authorizationStatus
      return [
        "platform": "ios",
        "screenTimeAuthorized": status == .approved,
        "selectedApps": self.loadSelection().applicationTokens.count,
        "selectedCategories": self.loadSelection().categoryTokens.count,
      ]
    }

    AsyncFunction("requestAuthorization") { () async throws -> Bool in
      try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
      return AuthorizationCenter.shared.authorizationStatus == .approved
    }

    AsyncFunction("presentAppPicker") { (promise: Promise) in
      DispatchQueue.main.async {
        guard let root = UIApplication.shared.connectedScenes
          .compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController }).first else {
          promise.reject("NO_UI", "No hay ventana activa")
          return
        }
        var host: UIViewController?
        let view = PickerView(initial: self.loadSelection()) { selection in
          self.saveSelection(selection)
          host?.dismiss(animated: true)
          promise.resolve(["apps": selection.applicationTokens.count, "categories": selection.categoryTokens.count])
        }
        host = UIHostingController(rootView: view)
        root.present(host!, animated: true)
      }
    }

    /** Same JSON as Android; iOS only needs to know whether anything should be shielded now. */
    Function("setState") { (json: String) in
      self.apply(json: json)
    }

    Function("getBlockStats") { () -> [String: Any] in [:] }
    Function("getUsageToday") { () -> [String: Any] in [:] }
  }

  private func apply(json: String) {
    guard let data = json.data(using: .utf8),
          let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let timeline = obj["timeline"] as? [[String: Any]] else { return }
    let now = Date().timeIntervalSince1970 * 1000
    let active = timeline.first { seg in
      guard let start = seg["start"] as? Double, let end = seg["end"] as? Double else { return false }
      return now >= start && now < end
    }
    let blocks = (active?["blocks"] as? [[String: Any]]) ?? []
    if blocks.isEmpty {
      store.clearAllSettings()
      return
    }
    let selection = loadSelection()
    store.shield.applications = selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
    store.shield.applicationCategories = selection.categoryTokens.isEmpty ? nil : .specific(selection.categoryTokens)
  }

  private func loadSelection() -> FamilyActivitySelection {
    guard let data = defaults.data(forKey: selectionKey),
          let selection = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data) else {
      return FamilyActivitySelection()
    }
    return selection
  }

  private func saveSelection(_ selection: FamilyActivitySelection) {
    if let data = try? JSONEncoder().encode(selection) {
      defaults.set(data, forKey: selectionKey)
    }
  }
}

private struct PickerView: View {
  @State var selection: FamilyActivitySelection
  let onDone: (FamilyActivitySelection) -> Void

  init(initial: FamilyActivitySelection, onDone: @escaping (FamilyActivitySelection) -> Void) {
    _selection = State(initialValue: initial)
    self.onDone = onDone
  }

  var body: some View {
    NavigationView {
      FamilyActivityPicker(selection: $selection)
        .navigationTitle("Apps a bloquear")
        .toolbar {
          ToolbarItem(placement: .confirmationAction) {
            Button("Listo") { onDone(selection) }
          }
        }
    }
  }
}
