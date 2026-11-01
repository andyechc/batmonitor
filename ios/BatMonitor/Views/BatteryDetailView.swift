import SwiftUI

enum DetailTab: String, CaseIterable, Identifiable {
    case overview
    case cells
    case status
    case control

    var id: Self { self }

    var title: LocalizedStringKey {
        switch self {
        case .overview: "tab.overview"
        case .cells: "tab.cells"
        case .status: "tab.status"
        case .control: "tab.control"
        }
    }
}

struct BatteryDetailView: View {
    let battery: SavedBattery

    @Environment(BatteryStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var monitor: BatteryMonitor
    @State private var tab: DetailTab = .overview
    @State private var busy = false
    @State private var message: String?
    @State private var renaming = false
    @State private var confirmRemove = false
    @State private var pendingOff: SwitchTarget?
    @State private var successTrigger = 0

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
            VStack(spacing: 16) {
                switch tab {
                case .overview: overview
                case .cells: cells
                case .status: status
                case .control: control
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
            .animation(.smooth, value: tab)
        }
        .scrollIndicators(.hidden)
        .background(Theme.background)
        .navigationTitle(currentName)
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .top, spacing: 0) {
            tabPicker
        }
        .toolbar { moreMenu }
        .task { await monitor.run() }
        .sheet(isPresented: $renaming) {
            RenameSheet(currentName: currentName) { name in
                try await monitor.rename(to: name)
                store.rename(id: battery.id, to: name)
                message = String(localized: "device.renameOk")
                successTrigger += 1
            }
        }
        .confirmationDialog(
            offTitle,
            isPresented: isConfirmingOff,
            titleVisibility: .visible
        ) {
            Button("device.confirm", role: .destructive) {
                if let target = pendingOff {
                    Task { await applySwitch(target) }
                }
            }
            Button("device.cancel", role: .cancel) {}
        } message: {
            Text(LocalizedStringKey(pendingOff == .discharge ? "device.confirmDischargeOff" : "device.confirmChargeOff"))
        }
        .confirmationDialog(
            "device.removeTitle",
            isPresented: $confirmRemove,
            titleVisibility: .visible
        ) {
            Button("device.delete", role: .destructive) {
                store.remove(id: battery.id)
                dismiss()
            }
            Button("device.cancel", role: .cancel) {}
        } message: {
            Text(String(format: String(localized: "device.removeConfirm"), currentName))
        }
        .sensoryFeedback(.success, trigger: successTrigger)
    }

    // MARK: - Tabs

    private var tabPicker: some View {
        Picker("device.tabs", selection: $tab) {
            ForEach(DetailTab.allCases) { item in
                Text(item.title).tag(item)
            }
        }
        .pickerStyle(.segmented)
        .labelsHidden()
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    // MARK: - Overview

    @ViewBuilder
    private var overview: some View {
        if let reading = monitor.reading {
            HeroCard(reading: reading, status: monitor.status, message: monitor.message)

            LazyVGrid(columns: twoColumns, spacing: 12) {
                StatTile(
                    label: "device.stat.voltage",
                    value: String(format: "%.2f", reading.basic.totalVoltage),
                    unit: "V",
                    icon: "bolt"
                )
                StatTile(
                    label: "device.stat.current",
                    value: String(format: "%+.2f", reading.basic.current),
                    unit: "A",
                    icon: "waveform.path.ecg",
                    accent: accent(for: reading.basic.current)
                )
                StatTile(
                    label: "device.stat.power",
                    value: String(format: "%.0f", abs(reading.basic.totalVoltage * reading.basic.current)),
                    unit: "W",
                    icon: "gauge.with.dots.needle.67percent",
                    accent: accent(for: reading.basic.current)
                )
                StatTile(
                    label: "device.stat.cycles",
                    value: "\(reading.basic.cycles)",
                    icon: "arrow.triangle.2.circlepath"
                )
            }

            protectionSummary(reading.basic)
        } else {
            ConnectingView(status: monitor.status, message: monitor.message)
        }
    }

    private func protectionSummary(_ basic: BMSBasicInfo) -> some View {
        let active = basic.protections.filter(\.active).count
        return Button {
            tab = .status
        } label: {
            HStack(spacing: 12) {
                Image(systemName: active == 0 ? "checkmark.shield" : "exclamationmark.shield")
                    .foregroundStyle(active == 0 ? Theme.charge : Theme.danger)
                    .font(.title3)
                Text(active == 0
                    ? String(localized: "device.noProtections")
                    : String(format: String(localized: "device.protectionsActive"), active))
                    .font(.body.weight(.medium))
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.muted)
            }
            .padding(18)
            .frame(maxWidth: .infinity)
            .panel()
        }
        .buttonStyle(.plain)
        .contentShape(.rect)
    }

    // MARK: - Cells

    @ViewBuilder
    private var cells: some View {
        if let reading = monitor.reading {
            let low = reading.cells.min() ?? 0
            let high = reading.cells.max() ?? 0

            Card(title: String(localized: "device.cellRange"), icon: "arrow.up.and.down") {
                HStack(alignment: .firstTextBaseline) {
                    rangeFigure(title: "device.lowest", value: String(format: "%.3f", low), unit: "V")
                    Spacer()
                    rangeFigure(title: "device.highest", value: String(format: "%.3f", high), unit: "V")
                    Spacer()
                    rangeFigure(title: "device.stat.imbalance", value: String(format: "%.0f", (high - low) * 1000), unit: "mV")
                }
            }

            Card(title: "\(String(localized: "device.cells")) · \(reading.cells.count)", icon: "battery.100") {
                CellBarsView(cells: reading.cells)
            }

            if !reading.basic.temperatures.isEmpty {
                Card(title: String(localized: "device.temperatures"), icon: "thermometer.medium") {
                    ForEach(Array(reading.basic.temperatures.enumerated()), id: \.offset) { index, temperature in
                        LabeledValueRow(
                            label: "\(String(localized: "device.sensor")) \(index + 1)",
                            value: String(format: "%.1f °C", temperature)
                        )
                    }
                }
            }
        } else {
            ConnectingView(status: monitor.status, message: monitor.message)
        }
    }

    private func rangeFigure(title: LocalizedStringKey, value: String, unit: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.footnote)
                .foregroundStyle(Theme.muted)
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value)
                    .font(.title2.weight(.semibold))
                    .monospacedDigit()
                    .contentTransition(.numericText())
                Text(unit)
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
            }
        }
    }

    // MARK: - Status

    @ViewBuilder
    private var status: some View {
        if let basic = monitor.reading?.basic {
            Card(title: String(localized: "device.protections"), icon: "shield.lefthalf.filled") {
                let active = basic.protections.filter(\.active)
                if active.isEmpty {
                    Label("device.noProtections", systemImage: "checkmark.circle.fill")
                        .font(.body.weight(.medium))
                        .foregroundStyle(Theme.charge)
                } else {
                    ForEach(active, id: \.labelKey) { protection in
                        Label(LocalizedStringKey(protection.labelKey), systemImage: "exclamationmark.triangle.fill")
                            .font(.body.weight(.medium))
                            .foregroundStyle(Theme.danger)
                    }
                }
            }

            Card(title: String(localized: "device.equipment"), icon: "cpu") {
                LabeledValueRow(label: String(localized: "device.firmware"), value: basic.firmwareVersion)
                LabeledValueRow(label: String(localized: "device.manufactured"), value: basic.productionDate)
                LabeledValueRow(label: String(localized: "device.stat.cycles"), value: "\(basic.cycles)")
                LabeledValueRow(label: String(localized: "device.nominalCapacity"), value: String(format: "%.0f Ah", basic.nominalCapacity))
                LabeledValueRow(label: String(localized: "device.balancingCells"), value: "\(basic.balancing.filter { $0 }.count)")
            }
        } else {
            ConnectingView(status: monitor.status, message: monitor.message)
        }
    }

    // MARK: - Control

    @ViewBuilder
    private var control: some View {
        if let basic = monitor.reading?.basic {
            Card(title: String(localized: "device.switches"), icon: "power") {
                Toggle(isOn: switchBinding(.charge, on: basic.chargeOn)) {
                    Label("device.chargeSwitch", systemImage: "bolt.fill")
                }
                .disabled(!isLive)
                Toggle(isOn: switchBinding(.discharge, on: basic.dischargeOn)) {
                    Label("device.dischargeSwitch", systemImage: "arrow.down")
                }
                .disabled(!isLive)
            }
            .tint(Theme.charge)

            Card(title: String(localized: "device.maintenance"), icon: "wrench.and.screwdriver") {
                Button {
                    Task { await perform { try await monitor.balance() } }
                } label: {
                    Label("device.balance", systemImage: "scale.3d")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
                .disabled(!isLive)

                Button {
                    Task { await perform { try await monitor.clearProtection() } }
                } label: {
                    Label("device.clearProtection", systemImage: "shield.slash")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
                .disabled(!isLive)

                if busy {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                }
                if let message {
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(Theme.muted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }

            Card(title: String(localized: "device.name"), icon: "character.cursor.ibeam") {
                Button {
                    renaming = true
                } label: {
                    HStack {
                        Text(currentName)
                            .font(.body.weight(.medium))
                            .foregroundStyle(.primary)
                        Spacer()
                        Image(systemName: "pencil")
                            .foregroundStyle(Theme.muted)
                    }
                }
                .buttonStyle(.plain)
            }

            Button(role: .destructive) {
                confirmRemove = true
            } label: {
                Label("device.remove", systemImage: "trash")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
        } else {
            ConnectingView(status: monitor.status, message: monitor.message)
        }
    }

    // MARK: - Toolbar

    private var moreMenu: some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            Menu {
                Button {
                    renaming = true
                } label: {
                    Label("device.rename", systemImage: "pencil")
                }
                Button {
                    Task { await perform { try await monitor.balance() } }
                } label: {
                    Label("device.balance", systemImage: "scale.3d")
                }
                .disabled(!isLive)
                Button {
                    Task { await perform { try await monitor.clearProtection() } }
                } label: {
                    Label("device.clearProtection", systemImage: "shield.slash")
                }
                .disabled(!isLive)
                Divider()
                Button(role: .destructive) {
                    confirmRemove = true
                } label: {
                    Label("device.remove", systemImage: "trash")
                }
            } label: {
                Image(systemName: "ellipsis.circle")
                    .accessibilityLabel(Text("device.more"))
            }
        }
    }

    // MARK: - Actions

    private var isConfirmingOff: Binding<Bool> {
        Binding(
            get: { pendingOff != nil },
            set: { if !$0 { pendingOff = nil } }
        )
    }

    private var offTitle: LocalizedStringKey {
        pendingOff == .discharge ? "device.dischargeSwitch" : "device.chargeSwitch"
    }

    private func switchBinding(_ target: SwitchTarget, on: Bool) -> Binding<Bool> {
        Binding(
            get: { on },
            set: { newValue in
                if newValue {
                    Task { await applySwitch(target) }
                } else {
                    pendingOff = target
                }
            }
        )
    }

    private func applySwitch(_ target: SwitchTarget) async {
        guard let basic = monitor.reading?.basic else { return }
        let charge = target == .charge ? !basic.chargeOn : basic.chargeOn
        let discharge = target == .discharge ? !basic.dischargeOn : basic.dischargeOn
        await perform { try await monitor.setSwitches(charge: charge, discharge: discharge) }
    }

    private func perform(_ action: () async throws -> Void) async {
        busy = true
        message = nil
        defer { busy = false }
        do {
            try await action()
            message = String(localized: "device.controlOk")
            successTrigger += 1
        } catch {
            message = error.localizedDescription
        }
    }

    private var twoColumns: [GridItem] {
        Array(repeating: GridItem(.flexible(), spacing: 12), count: 2)
    }

    private func accent(for current: Double) -> Color {
        current > 0.05 ? Theme.charge : current < -0.05 ? Theme.discharge : Theme.info
    }
}

private struct HeroCard: View {
    let reading: BatteryReading
    let status: BatteryMonitor.Status
    let message: String?

    var body: some View {
        let basic = reading.basic
        let accent = accentColor(for: basic.current)
        VStack(spacing: 18) {
            StatusBadge(status: status, message: message)
            SocRing(soc: basic.soc, color: accent, size: 196)
            VStack(spacing: 6) {
                Text(stateKey(for: basic.current))
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(accent)
                    .contentTransition(.opacity)
                Text(String(format: "%.2f de %.0f Ah", basic.remainingCapacity, basic.nominalCapacity))
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
                    .monospacedDigit()
                    .contentTransition(.numericText())
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .padding(.horizontal, 18)
        .panel()
    }

    private func accentColor(for current: Double) -> Color {
        current > 0.05 ? Theme.charge : current < -0.05 ? Theme.discharge : Theme.info
    }

    private func stateKey(for current: Double) -> LocalizedStringKey {
        current > 0.05 ? "device.state.charging" : current < -0.05 ? "device.state.discharging" : "device.state.idle"
    }
}

private struct ConnectingView: View {
    let status: BatteryMonitor.Status
    let message: String?

    var body: some View {
        VStack(spacing: 16) {
            StatusBadge(status: status, message: message)
            if status == .connecting {
                ProgressView()
                    .controlSize(.large)
                Text("device.connecting")
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 60)
    }
}

struct StatusBadge: View {
    let status: BatteryMonitor.Status
    let message: String?

    var body: some View {
        let palette = palette
        HStack(spacing: 6) {
            Image(systemName: palette.icon)
                .symbolEffect(.pulse, isActive: status == .connecting)
            Text(palette.label)
                .contentTransition(.opacity)
        }
        .font(.footnote.weight(.semibold))
        .foregroundStyle(palette.color)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(palette.color.opacity(0.14), in: Capsule())
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

struct LabeledValueRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .foregroundStyle(Theme.muted)
            Spacer()
            Text(value)
                .font(.body.weight(.medium))
                .monospacedDigit()
                .contentTransition(.numericText())
        }
    }
}
