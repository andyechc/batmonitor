import Foundation
import Observation

@MainActor
@Observable
final class BatteryScanner {
    private(set) var found: [ScannedDevice] = []
    private(set) var isScanning = false
    private(set) var error: String?
    private var timeout: Task<Void, Never>?

    func start() async {
        stop()
        found = []
        error = nil
        do {
            try await BLECentral.shared.startScan { [weak self] device in
                Task { @MainActor in self?.add(device) }
            }
        } catch {
            self.error = error.localizedDescription
            return
        }
        isScanning = true
        timeout = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 12_000_000_000)
            self?.stop()
        }
    }

    func stop() {
        timeout?.cancel()
        timeout = nil
        BLECentral.shared.stopScan()
        isScanning = false
    }

    private func add(_ device: ScannedDevice) {
        guard !found.contains(where: { $0.id == device.id }) else { return }
        found.append(device)
    }
}
