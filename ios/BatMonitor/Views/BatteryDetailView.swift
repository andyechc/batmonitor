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
    @State private var pendingConfirm: PendingConfirm?
    @State private var selectedInfo: InfoTopic?
    @State private var demoState: DemoBatteryState = .charging
    @State private var successTrigger = 0

    enum SwitchTarget: Identifiable {
        case charge
        case discharge

        var id: Self { self }
    }

    enum PendingConfirm: Identifiable {
        case turnOff(SwitchTarget)
        case remove

        var id: String {
            switch self {
            case .turnOff(.charge): "off-charge"
            case .turnOff(.discharge): "off-discharge"
            case .remove: "remove"
            }
        }
    }

    init(battery: SavedBattery) {
        self.battery = battery
        _monitor = State(initialValue: BatteryMonitor(id: battery.id))
    }

    private var currentName: String {
        if isDemo { return String(localized: "demo.batteryName") }
        return store.battery(id: battery.id)?.name ?? battery.name
    }

    private var isDemo: Bool { battery.isDemo == true }

    private var displayReading: BatteryReading? {
        isDemo ? demoState.reading : monitor.reading
    }

    private var displayStatus: BatteryMonitor.Status {
        isDemo ? demoState.status : monitor.status
    }

    private var displayMessage: String? {
        isDemo ? demoState.message : monitor.message
    }

    private var isLive: Bool {
        !isDemo && monitor.status == .live && !busy
    }

    private func showInfo(_ topic: InfoTopic) {
        selectedInfo = topic
    }

    var body: some View {
        TabView(selection: $tab) {
            ScrollView {
                VStack(spacing: 16) {
                    overview
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 120)
            }
            .scrollIndicators(.hidden)
            .tabItem { Label(DetailTab.overview.title, systemImage: "speedometer") }
            .tag(DetailTab.overview)

            ScrollView {
                VStack(spacing: 16) {
                    cells
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 120)
            }
            .scrollIndicators(.hidden)
            .tabItem { Label(DetailTab.cells.title, systemImage: "battery.100") }
            .tag(DetailTab.cells)

            ScrollView {
                VStack(spacing: 16) {
                    status
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 120)
            }
            .scrollIndicators(.hidden)
            .tabItem { Label(DetailTab.status.title, systemImage: "shield.lefthalf.filled") }
            .tag(DetailTab.status)

            ScrollView {
                VStack(spacing: 16) {
                    control
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 120)
            }
            .scrollIndicators(.hidden)
            .tabItem { Label(DetailTab.control.title, systemImage: "slider.horizontal.3") }
            .tag(DetailTab.control)
        }
        .background(Theme.background)
        .safeAreaInset(edge: .top, spacing: 0) {
            if isDemo {
                HStack(spacing: 8) {
                    Image(systemName: "play.rectangle")
                    Text("demo.previewLabel")
                    Spacer()
                    Text(demoState.title)
                        .foregroundStyle(Theme.neutral)
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(Theme.muted)
                .padding(.horizontal, 16)
                .padding(.vertical, 9)
                .background(Theme.surface)
            }
        }
        .navigationTitle(currentName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { moreMenu }
        .task {
            if !isDemo { await monitor.run() }
        }
        .onChange(of: monitor.reading) { _, reading in
            guard !isDemo, let reading else { return }
            store.record(reading, for: battery.id)
        }
        .animation(.smooth(duration: 0.35), value: demoState)
        .sheet(isPresented: $renaming) {
            RenameSheet(currentName: currentName) { name in
                try await monitor.rename(to: name)
                store.rename(id: battery.id, to: name)
                message = String(localized: "device.renameOk")
                successTrigger += 1
            }
        }
        .sheet(item: $selectedInfo) { topic in
            InfoSheet(topic: topic)
        }
        .sheet(item: $pendingConfirm) { item in
            switch item {
            case .turnOff(let target):
                ConfirmSheet(
                    icon: "power",
                    title: target == .discharge ? "device.dischargeSwitch" : "device.chargeSwitch",
                    message: target == .discharge
                        ? String(localized: "device.confirmDischargeOff")
                        : String(localized: "device.confirmChargeOff"),
                    confirmTitle: "device.confirm"
                ) {
                    Task { await applySwitch(target) }
                }
            case .remove:
                ConfirmSheet(
                    icon: "trash",
                    title: "device.removeTitle",
                    message: String(format: String(localized: "device.removeConfirm"), currentName),
                    confirmTitle: "device.delete"
                ) {
                    store.remove(id: battery.id)
                    dismiss()
                }
            }
        }
        .sensoryFeedback(.success, trigger: successTrigger)
    }

    // MARK: - Overview

    @ViewBuilder
    private var overview: some View {
        if let reading = displayReading {
            hero(reading: reading)

            LazyVGrid(columns: twoColumns, spacing: 12) {
                StatTile(
                    label: "device.stat.voltage",
                    value: String(format: "%.2f", reading.basic.totalVoltage),
                    unit: "V",
                    icon: "bolt",
                    info: .voltage,
                    onInfo: showInfo
                )
                StatTile(
                    label: "device.stat.current",
                    value: String(format: "%+.2f", reading.basic.current),
                    unit: "A",
                    icon: "waveform.path.ecg",
                    accent: accent(for: reading.basic.current),
                    info: .current,
                    onInfo: showInfo
                )
                StatTile(
                    label: "device.stat.power",
                    value: String(format: "%.0f", abs(reading.basic.totalVoltage * reading.basic.current)),
                    unit: "W",
                    icon: "gauge.with.dots.needle.67percent",
                    accent: accent(for: reading.basic.current),
                    info: .power,
                    onInfo: showInfo
                )
                StatTile(
                    label: "device.stat.cycles",
                    value: "\(reading.basic.cycles)",
                    icon: "arrow.triangle.2.circlepath",
                    info: .cycles,
                    onInfo: showInfo
                )
                StatTile(
                    label: "device.stat.maxTemp",
                    value: reading.basic.temperatures.max().map { String(format: "%.1f", $0) } ?? "—",
                    unit: reading.basic.temperatures.isEmpty ? nil : "°C",
                    icon: "thermometer.medium",
                    info: .maxTemp,
                    onInfo: showInfo
                )
                StatTile(
                    label: "device.stat.imbalance",
                    value: reading.cells.isEmpty
                        ? "—"
                        : String(format: "%.0f", (reading.cells.max()! - reading.cells.min()!) * 1000),
                    unit: reading.cells.isEmpty ? nil : "mV",
                    icon: "scalemass",
                    info: .imbalance,
                    onInfo: showInfo
                )
            }

            protectionSummary(reading.basic)
        } else {
            ConnectingView(status: displayStatus, message: displayMessage)
        }
    }

    /// Hero without a card: the battery floats directly on the background.
    private func hero(reading: BatteryReading) -> some View {
        let basic = reading.basic
        let charging = basic.current > 0.05
        let accent = accent(for: basic.current)
        return VStack(spacing: 18) {
            StatusBadge(status: displayStatus, message: displayMessage)
            FluidBatteryView(soc: basic.soc, current: basic.current, color: accent)
            VStack(spacing: 6) {
                HStack(spacing: 6) {
                    Image(systemName: charging ? "bolt.fill" : basic.current < -0.05 ? "arrow.down" : "pause.fill")
                    Text(stateKey(for: basic.current))
                    InfoButton(topic: .soc, action: showInfo)
                }
                .font(.title3.weight(.semibold))
                .foregroundStyle(accent)
                .contentTransition(.opacity)
                Text(String(format: "%.2f de %.0f Ah", basic.remainingCapacity, basic.nominalCapacity))
                    .font(Theme.numeric(size: 16))
                    .foregroundStyle(Theme.muted)
                    .monospacedDigit()
                    .contentTransition(.numericText())
                if basic.soc >= 100 {
                    Label("device.fullyCharged", systemImage: "checkmark.circle.fill")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.charge)
                } else if charging, let eta = timeToFull(basic: basic) {
                    HStack(spacing: 6) {
                        Image(systemName: "timer")
                        Text(String(format: String(localized: "device.timeToFull"), eta))
                    }
                    .font(Theme.numeric(size: 17))
                    .foregroundStyle(accent)
                    .contentTransition(.numericText())
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
    }

    /// Estimated remaining charge time from missing Ah over charge current.
    private func timeToFull(basic: BMSBasicInfo) -> String? {
        let missing = basic.nominalCapacity - basic.remainingCapacity
        guard basic.current > 0.05, missing > 0 else { return nil }
        let totalMinutes = Int((missing / basic.current * 60).rounded())
        guard totalMinutes >= 1 else { return nil }
        if totalMinutes >= 60 {
            return "\(totalMinutes / 60) h \(totalMinutes % 60) min"
        }
        return "\(totalMinutes) min"
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
        if let reading = displayReading {
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

            Card(
                title: "\(String(localized: "device.cells")) · \(reading.cells.count)",
                icon: "battery.100",
                info: .cells,
                onInfo: showInfo
            ) {
                CellBarsView(cells: reading.cells)
            }

            if !reading.basic.temperatures.isEmpty {
                Card(
                    title: String(localized: "device.temperatures"),
                    icon: "thermometer.medium",
                    info: .temperatures,
                    onInfo: showInfo
                ) {
                    ForEach(Array(reading.basic.temperatures.enumerated()), id: \.offset) { index, temperature in
                        LabeledValueRow(
                            label: "\(String(localized: "device.sensor")) \(index + 1)",
                            value: String(format: "%.1f °C", temperature)
                        )
                    }
                }
            }
        } else {
            ConnectingView(status: displayStatus, message: displayMessage)
        }
    }

    private func rangeFigure(title: LocalizedStringKey, value: String, unit: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.footnote)
                .foregroundStyle(Theme.muted)
            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value)
                    .font(Theme.numeric(size: 25))
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
        if let basic = displayReading?.basic {
            Card(
                title: String(localized: "device.protections"),
                icon: "shield.lefthalf.filled",
                info: .protections,
                onInfo: showInfo
            ) {
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
            ConnectingView(status: displayStatus, message: displayMessage)
        }
    }

    // MARK: - Control

    @ViewBuilder
    private var control: some View {
        if let basic = displayReading?.basic {
            Card(title: String(localized: "device.switches"), icon: "power") {
                switchRow(
                    target: .charge,
                    label: "device.chargeSwitch",
                    systemImage: "bolt.fill",
                    on: basic.chargeOn,
                    info: .charge
                )
                switchRow(
                    target: .discharge,
                    label: "device.dischargeSwitch",
                    systemImage: "arrow.down",
                    on: basic.dischargeOn,
                    info: .discharge
                )
            }
            .tint(Theme.charge)

            Card(title: String(localized: "device.maintenance"), icon: "wrench.and.screwdriver") {
                actionRow(
                    label: "device.balance",
                    systemImage: "scale.3d",
                    info: .balance,
                    enabled: isLive
                ) {
                    Task { await perform { try await monitor.balance() } }
                }
                actionRow(
                    label: "device.clearProtection",
                    systemImage: "shield.slash",
                    info: .clear,
                    enabled: isLive
                ) {
                    Task { await perform { try await monitor.clearProtection() } }
                }

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

            if !isDemo {
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
                    pendingConfirm = .remove
                } label: {
                    Label {
                        Text("device.remove")
                    } icon: {
                        Image(systemName: "trash")
                            .foregroundStyle(Theme.danger)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
            }
        } else {
            ConnectingView(status: displayStatus, message: displayMessage)
        }
    }

    private func switchRow(
        target: SwitchTarget,
        label: LocalizedStringKey,
        systemImage: String,
        on: Bool,
        info: InfoTopic
    ) -> some View {
        HStack {
            Label(label, systemImage: systemImage)
                .foregroundStyle(Theme.muted)
            InfoButton(topic: info, action: showInfo)
            Spacer()
            Toggle("", isOn: switchBinding(target, on: on))
                .labelsHidden()
                .disabled(!isLive || busy)
        }
    }

    private func actionRow(
        label: LocalizedStringKey,
        systemImage: String,
        info: InfoTopic,
        enabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        HStack {
            Button(action: action) {
                HStack {
                    Label(label, systemImage: systemImage)
                        .foregroundStyle(Theme.muted)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Theme.muted)
                }
            }
            .disabled(!enabled || busy)
            InfoButton(topic: info, action: showInfo)
        }
    }

    // MARK: - Toolbar

    private var moreMenu: some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            Menu {
                if isDemo {
                    Picker("demo.selector", selection: $demoState) {
                        ForEach(DemoBatteryState.allCases) { state in
                            Label(state.title, systemImage: state.icon)
                                .tag(state)
                        }
                    }
                } else {
                    Button {
                        renaming = true
                    } label: {
                        Label {
                            Text("device.rename")
                        } icon: {
                            Image(systemName: "pencil")
                                .foregroundStyle(.white)
                        }
                    }
                    Button {
                        Task { await perform { try await monitor.balance() } }
                    } label: {
                        Label {
                            Text("device.balance")
                        } icon: {
                            Image(systemName: "scale.3d")
                                .foregroundStyle(.white)
                        }
                    }
                    .disabled(!isLive)
                    Button {
                        Task { await perform { try await monitor.clearProtection() } }
                    } label: {
                        Label {
                            Text("device.clearProtection")
                        } icon: {
                            Image(systemName: "shield.slash")
                                .foregroundStyle(.white)
                        }
                    }
                    .disabled(!isLive)
                    Divider()
                    Button(role: .destructive) {
                        pendingConfirm = .remove
                    } label: {
                        Label {
                            Text("device.remove")
                        } icon: {
                            Image(systemName: "trash")
                                .foregroundStyle(Theme.danger)
                        }
                    }
                }
            } label: {
                Image(systemName: "ellipsis.circle")
                    .foregroundStyle(.white)
                    .accessibilityLabel(Text("device.more"))
            }
        }
    }

    // MARK: - Actions

    private func switchBinding(_ target: SwitchTarget, on: Bool) -> Binding<Bool> {
        Binding(
            get: { on },
            set: { newValue in
                if newValue {
                    Task { await applySwitch(target) }
                } else {
                    pendingConfirm = .turnOff(target)
                }
            }
        )
    }

    private func applySwitch(_ target: SwitchTarget) async {
        guard let basic = displayReading?.basic, !isDemo else { return }
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
        current > 0.05 ? Theme.charge : current < -0.05 ? Theme.discharge : Theme.neutral
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
            if status == .connecting {
                BMSLoader(title: "device.connecting", detail: "device.connectingHint", centered: true)
            } else {
                StatusBadge(status: status, message: message)
                if status == .error {
                    Text("device.retryHint")
                        .font(.footnote)
                        .foregroundStyle(Theme.muted)
                }
            }
        }
        .frame(maxWidth: .infinity, minHeight: 420, alignment: .center)
        .padding(.vertical, 32)
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
            (Theme.charge, "checkmark.circle.fill", String(localized: "device.live"))
        case .connecting:
            (Theme.muted, "arrow.triangle.2.circlepath", String(localized: "device.connectingPill"))
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
                .font(Theme.numeric(size: 19))
                .monospacedDigit()
                .contentTransition(.numericText())
        }
    }
}
