import SwiftUI

struct BatteryDetailView: View {
    let battery: SavedBattery

    @Environment(BatteryStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var monitor: BatteryMonitor
    @State private var busy = false
    @State private var message: String?
    @State private var renaming = false
    @State private var draftName = ""
    @State private var confirmRemove = false
    @State private var pendingOff: SwitchTarget?

    enum SwitchTarget: Identifiable {
        case charge
        case discharge

        var id: Self { self }
    }

    init(battery: SavedBattery) {
        self.battery = battery
        _monitor = State(initialValue: BatteryMonitor(id: battery.id))
    }

    private var currentName: String {
        store.battery(id: battery.id)?.name ?? battery.name
    }

    private var isLive: Bool {
        monitor.status == .live && !busy
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                StatusPill(status: monitor.status, message: monitor.message)

                if let reading = monitor.reading {
                    DashboardView(reading: reading)
                } else {
                    VStack(spacing: 12) {
                        ProgressView()
                        Text("device.connecting")
                            .foregroundStyle(Theme.muted)
                    }
                    .padding(.vertical, 60)
                }

                controlsCard

                Button(role: .destructive) {
                    confirmRemove = true
                } label: {
                    Label("device.remove", systemImage: "trash")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .padding(.top, 8)
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle(currentName)
        .navigationBarTitleDisplayMode(.inline)
        .task { await monitor.run() }
        .alert("device.renameTitle", isPresented: $renaming) {
            TextField("device.renameTitle", text: $draftName)
            Button("device.save") { Task { await rename() } }
            Button("device.cancel", role: .cancel) {}
        } message: {
            Text("device.renameHint")
        }
        .alert("device.removeTitle", isPresented: $confirmRemove) {
            Button("device.delete", role: .destructive) {
                store.remove(id: battery.id)
                dismiss()
            }
            Button("device.cancel", role: .cancel) {}
        } message: {
            Text(String(format: String(localized: "device.removeConfirm"), currentName))
        }
        .alert(offTitle, isPresented: Binding(
            get: { pendingOff != nil },
            set: { if !$0 { pendingOff = nil } }
        )) {
            Button("device.confirm", role: .destructive) {
                if let target = pendingOff {
                    Task { await applySwitch(target) }
                }
            }
            Button("device.cancel", role: .cancel) {}
        } message: {
            Text(LocalizedStringKey(pendingOff == .discharge ? "device.confirmDischargeOff" : "device.confirmChargeOff"))
        }
    }

    private var offTitle: LocalizedStringKey {
        pendingOff == .discharge ? "device.dischargeSwitch" : "device.chargeSwitch"
    }

    private var controlsCard: some View {
        let basic = monitor.reading?.basic
        return Card(title: String(localized: "device.controls"), icon: "slider.horizontal.3") {
            switchRow(
                label: "device.chargeSwitch",
                on: basic?.chargeOn ?? false,
                enabled: isLive && basic != nil
            ) {
                toggleSwitch(.charge)
            }
            switchRow(
                label: "device.dischargeSwitch",
                on: basic?.dischargeOn ?? false,
                enabled: isLive && basic != nil
            ) {
                toggleSwitch(.discharge)
            }
            actionRow(label: "device.balance", enabled: isLive) {
                Task { await perform { try await monitor.balance() } }
            }
            actionRow(label: "device.clearProtection", enabled: isLive) {
                Task { await perform { try await monitor.clearProtection() } }
            }
            actionRow(label: "device.rename", enabled: true) {
                draftName = currentName
                renaming = true
            }
            if let message {
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
            }
        }
    }

    private func switchRow(label: LocalizedStringKey, on: Bool, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(label)
                    .foregroundStyle(Theme.muted)
                Spacer()
                Text(LocalizedStringKey(on ? "device.on" : "device.off"))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(on ? Theme.background : Theme.muted)
                    .padding(.vertical, 4)
                    .padding(.horizontal, 12)
                    .background(on ? Theme.charge : .clear, in: Capsule())
                    .overlay(Capsule().stroke(on ? Theme.charge : Theme.border))
            }
        }
        .disabled(!enabled || busy)
    }

    private func actionRow(label: LocalizedStringKey, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(label)
                    .foregroundStyle(Theme.muted)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.muted)
            }
        }
        .disabled(!enabled || busy)
    }

    private func toggleSwitch(_ target: SwitchTarget) {
        guard let basic = monitor.reading?.basic else { return }
        let turningOn = target == .charge ? !basic.chargeOn : !basic.dischargeOn
        if turningOn {
            Task { await applySwitch(target) }
        } else {
            pendingOff = target
        }
    }

    private func applySwitch(_ target: SwitchTarget) async {
        guard let basic = monitor.reading?.basic else { return }
        let charge = target == .charge ? !basic.chargeOn : basic.chargeOn
        let discharge = target == .discharge ? !basic.dischargeOn : basic.dischargeOn
        await perform { try await monitor.setSwitches(charge: charge, discharge: discharge) }
    }

    private func rename() async {
        let trimmed = draftName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            message = String(localized: "device.renameEmpty")
            return
        }
        await perform(success: "device.renameOk") {
            try await monitor.rename(to: trimmed)
            store.rename(id: battery.id, to: trimmed)
        }
    }

    private func perform(success: String.LocalizationValue = "device.controlOk", _ action: () async throws -> Void) async {
        busy = true
        message = nil
        defer { busy = false }
        do {
            try await action()
            message = String(localized: success)
        } catch {
            message = error.localizedDescription
        }
    }
}

private struct StatusPill: View {
    let status: BatteryMonitor.Status
    let message: String?

    var body: some View {
        let style = palette
        HStack(spacing: 8) {
            Image(systemName: style.icon)
            Text(style.label)
        }
        .font(.footnote.weight(.semibold))
        .foregroundStyle(style.color)
        .padding(.vertical, 6)
        .padding(.horizontal, 14)
        .overlay(Capsule().stroke(style.color))
        .frame(maxWidth: .infinity)
    }

    private var palette: (color: Color, icon: String, label: String) {
        switch status {
        case .live:
            (Theme.charge, "bluetooth", String(localized: "device.live"))
        case .connecting:
            (Theme.info, "bluetooth", String(localized: "device.connectingPill"))
        case .error:
            (Theme.danger, "exclamationmark.triangle", message ?? String(localized: "device.noConnection"))
        }
    }
}
