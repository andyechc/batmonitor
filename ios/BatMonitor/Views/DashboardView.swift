import SwiftUI

struct DashboardView: View {
    let reading: BatteryReading

    private static let deadband = 0.05

    var body: some View {
        let basic = reading.basic
        let cells = reading.cells
        let power = basic.totalVoltage * basic.current
        let charging = basic.current > Self.deadband
        let discharging = basic.current < -Self.deadband
        let accent = charging ? Theme.charge : discharging ? Theme.discharge : Theme.info
        let stateKey: LocalizedStringKey = charging
            ? "device.state.charging"
            : discharging ? "device.state.discharging" : "device.state.idle"
        let stateIcon = charging ? "bolt.fill" : discharging ? "arrow.down" : "pause.fill"
        let cellDeltaMv = cells.isEmpty ? 0 : (cells.max()! - cells.min()!) * 1000
        let maxTemp = basic.temperatures.max()
        let activeProtections = basic.protections.filter(\.active)

        VStack(spacing: 14) {
            Card {
                VStack(spacing: 12) {
                    SocRing(soc: basic.soc, color: accent)
                    HStack(spacing: 6) {
                        Image(systemName: stateIcon)
                        Text(stateKey)
                    }
                    .font(.title3.weight(.bold))
                    .foregroundStyle(accent)
                    Text(String(format: "%.2f de %.0f Ah", basic.remainingCapacity, basic.nominalCapacity))
                        .font(.subheadline)
                        .foregroundStyle(Theme.muted)
                        .monospacedDigit()
                }
                .frame(maxWidth: .infinity)
            }

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                StatTile(label: "device.stat.voltage", value: String(format: "%.2f", basic.totalVoltage), unit: "V", icon: "bolt")
                StatTile(
                    label: "device.stat.current",
                    value: String(format: "%@%.2f", basic.current > 0 ? "+" : "", basic.current),
                    unit: "A",
                    icon: "waveform.path.ecg",
                    accent: accent
                )
                StatTile(label: "device.stat.power", value: String(format: "%.0f", abs(power)), unit: "W", icon: "gauge.with.dots.needle.67percent", accent: accent)
                StatTile(label: "device.stat.cycles", value: "\(basic.cycles)", icon: "arrow.triangle.2.circlepath")
                StatTile(
                    label: "device.stat.maxTemp",
                    value: maxTemp.map { String(format: "%.1f", $0) } ?? "—",
                    unit: maxTemp == nil ? nil : "°C",
                    icon: "thermometer.medium"
                )
                StatTile(label: "device.stat.imbalance", value: String(format: "%.0f", cellDeltaMv), unit: "mV", icon: "scalemass")
            }

            if !cells.isEmpty {
                Card(title: "\(String(localized: "device.cells")) · \(cells.count)", icon: "battery.100") {
                    CellBarsView(cells: cells)
                }
            }

            if !basic.temperatures.isEmpty {
                Card(title: String(localized: "device.temperatures"), icon: "thermometer.medium") {
                    ForEach(Array(basic.temperatures.enumerated()), id: \.offset) { index, temperature in
                        row(
                            label: "\(String(localized: "device.sensor")) \(index + 1)",
                            value: String(format: "%.1f °C", temperature)
                        )
                    }
                }
            }

            Card(
                title: String(localized: "device.protections"),
                icon: activeProtections.isEmpty ? "checkmark.shield" : "exclamationmark.shield"
            ) {
                if activeProtections.isEmpty {
                    Text("device.noProtections")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Theme.charge)
                } else {
                    ForEach(activeProtections, id: \.labelKey) { protection in
                        Text(LocalizedStringKey(protection.labelKey))
                            .font(.body.weight(.semibold))
                            .foregroundStyle(Theme.danger)
                    }
                }
            }

            Card(title: String(localized: "device.status"), icon: "info.circle") {
                row(label: String(localized: "device.chargeSwitch"), value: String(localized: basic.chargeOn ? "device.on" : "device.off"))
                row(label: String(localized: "device.dischargeSwitch"), value: String(localized: basic.dischargeOn ? "device.on" : "device.off"))
                row(label: String(localized: "device.balancingCells"), value: "\(basic.balancing.filter { $0 }.count)")
                row(label: String(localized: "device.firmware"), value: basic.firmwareVersion)
                row(label: String(localized: "device.manufactured"), value: basic.productionDate)
            }
        }
    }

    private func row(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .foregroundStyle(Theme.muted)
            Spacer()
            Text(value)
                .font(.body.weight(.medium))
                .monospacedDigit()
        }
    }
}
