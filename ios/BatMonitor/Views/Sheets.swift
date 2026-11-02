import SwiftUI

/// Every value in the app that deserves an explanation. The sheet shows
/// the title plus a short subtext, both translated.
enum InfoTopic: String, Identifiable, CaseIterable {
    case soc
    case voltage
    case current
    case power
    case cycles
    case maxTemp
    case imbalance
    case cells
    case temperatures
    case protections
    case charge
    case discharge
    case balance
    case clear

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .soc: "help.soc.title"
        case .voltage: "help.voltage.title"
        case .current: "help.current.title"
        case .power: "help.power.title"
        case .cycles: "help.cycles.title"
        case .maxTemp: "help.maxtemp.title"
        case .imbalance: "help.imbalance.title"
        case .cells: "help.cells.title"
        case .temperatures: "help.temperatures.title"
        case .protections: "help.protections.title"
        case .charge: "help.charge.title"
        case .discharge: "help.discharge.title"
        case .balance: "help.balance.title"
        case .clear: "help.clear.title"
        }
    }

    var body: LocalizedStringKey {
        switch self {
        case .soc: "help.soc.body"
        case .voltage: "help.voltage.body"
        case .current: "help.current.body"
        case .power: "help.power.body"
        case .cycles: "help.cycles.body"
        case .maxTemp: "help.maxtemp.body"
        case .imbalance: "help.imbalance.body"
        case .cells: "help.cells.body"
        case .temperatures: "help.temperatures.body"
        case .protections: "help.protections.body"
        case .charge: "help.charge.body"
        case .discharge: "help.discharge.body"
        case .balance: "help.balance.body"
        case .clear: "help.clear.body"
        }
    }
}

struct InfoSheet: View {
    let topic: InfoTopic
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.white)
                }
                .accessibilityLabel(Text("device.cancel"))
            }
            Image(systemName: "info.circle.fill")
                .font(.system(size: 44))
                .foregroundStyle(Theme.charge)
            Text(topic.title)
                .font(.headline)
            Text(topic.body)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }
}
