import SwiftUI

struct Card<Content: View>: View {
    var title: String?
    var icon: String?
    var info: InfoTopic?
    var onInfo: (InfoTopic) -> Void = { _ in }
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let title {
                HStack(spacing: 8) {
                    if let icon {
                        Image(systemName: icon)
                    }
                    Text(title)
                    if let info {
                        Spacer()
                        InfoButton(topic: info, action: onInfo)
                    }
                }
                .font(.footnote.weight(.semibold))
                .textCase(.uppercase)
                .foregroundStyle(Theme.muted)
            }
            content
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .panel()
    }
}

/// Small "i" button that opens an explanation sheet. The muted color keeps
/// it secondary to the value it annotates.
struct InfoButton: View {
    let topic: InfoTopic
    var action: (InfoTopic) -> Void

    var body: some View {
        Button {
            action(topic)
        } label: {
            Image(systemName: "info.circle")
                .font(.footnote)
                .foregroundStyle(Theme.muted)
                .accessibilityLabel(Text("device.info"))
        }
        .buttonStyle(.plain)
    }
}

struct StatTile: View {
    let label: LocalizedStringKey
    let value: String
    var unit: String?
    let icon: String
    var accent: Color = .primary
    var info: InfoTopic?
    var onInfo: (InfoTopic) -> Void = { _ in }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .foregroundStyle(accent)
                Text(label)
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
                if let info {
                    Spacer()
                    InfoButton(topic: info, action: onInfo)
                }
            }
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(value)
                    .font(Theme.numeric(size: 29, bold: true))
                    .monospacedDigit()
                    .foregroundStyle(accent)
                if let unit {
                    Text(unit)
                        .font(.subheadline)
                        .foregroundStyle(Theme.muted)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .panel()
    }
}

/// Battery drawn in code: rounded body, terminal cap, and a liquid fill
/// whose wave follows current direction while charging or discharging.
struct FluidBatteryView: View {
    let soc: Int
    let current: Double
    let color: Color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let level = Double(min(100, max(0, soc))) / 100
        if abs(current) > 0.05 && level < 1 && !reduceMotion {
            TimelineView(.animation(minimumInterval: 1 / 30)) { context in
                let direction = current > 0 ? 1.0 : -1.0
                battery(level: level, phase: context.date.timeIntervalSinceReferenceDate * 2.2 * direction)
            }
        } else {
            battery(level: level, phase: 0.8)
        }
    }

    private func battery(level: Double, phase: Double) -> some View {
        HStack(spacing: 5) {
            ZStack {
                filledShape(level: level, phase: phase)
                    .padding(7)
                    .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(color, lineWidth: 3)
                percentText(color: .white)
                percentText(color: Color(red: 0.02, green: 0.03, blue: 0.05))
                    .mask(
                        filledShape(level: level, phase: phase)
                            .frame(width: 190, height: 100)
                            .padding(7)
                            .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                    )
            }
            .frame(width: 190, height: 100)
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(color)
                .frame(width: 9, height: 40)
        }
        .shadow(color: abs(current) > 0.05 ? color.opacity(0.35) : .clear, radius: 14)
        .accessibilityLabel(Text("device.stat.charge"))
        .accessibilityValue("\(soc)%")
    }

    @ViewBuilder
    private func filledShape(level: Double, phase: Double) -> some View {
        if level >= 1 {
            RoundedRectangle(cornerRadius: 15, style: .continuous)
                .fill(color.opacity(0.85))
        } else {
            WaveShape(level: level, phase: phase)
                .fill(color.opacity(0.85))
        }
    }

    private func percentText(color: Color) -> some View {
        Text("\(soc)%")
            .font(Theme.numeric(size: 34, bold: true))
            .monospacedDigit()
            .contentTransition(.numericText(value: Double(soc)))
            .foregroundStyle(color)
    }
}

private struct WaveShape: Shape {
    var level: Double
    var phase: Double

    var animatableData: Double {
        get { phase }
        set { phase = newValue }
    }

    func path(in rect: CGRect) -> Path {
        let baseY = rect.height * (1 - CGFloat(level))
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.maxY))
        for x in stride(from: rect.minX, through: rect.maxX, by: 3) {
            let y = baseY + sin((x - rect.minX) / 22 + CGFloat(phase)) * 5
                + sin((x - rect.minX) / 11 + CGFloat(phase) * 1.7) * 3
            path.addLine(to: CGPoint(x: x, y: y))
        }
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

/// Secondary action: white text on a filled neutral surface. Used for
/// non-primary buttons so the brand green stays reserved for primary
/// actions and live values.
struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 22)
            .padding(.vertical, 13)
            .background(Theme.surfaceHigh, in: Capsule())
            .opacity(configuration.isPressed ? 0.65 : 1)
    }
}

struct CellBarsView: View {
    let cells: [Double]

    var body: some View {
        let low = cells.min() ?? 0
        let high = cells.max() ?? 0
        let span = high - low
        let balanced = span < 0.02
        VStack(spacing: 10) {
            ForEach(Array(cells.enumerated()), id: \.offset) { index, voltage in
                let ratio = balanced ? 1.0 : 0.2 + 0.8 * ((voltage - low) / span)
                let color = balanced ? Theme.neutral : voltage == high ? Theme.charge : voltage == low ? Theme.discharge : Theme.neutral
                HStack(spacing: 12) {
                    Text("C\(index + 1)")
                        .font(.footnote)
                        .monospacedDigit()
                        .foregroundStyle(Theme.muted)
                        .frame(width: 28, alignment: .leading)
                    GeometryReader { geometry in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Theme.surfaceHigh)
                            Capsule()
                                .fill(color)
                                .frame(width: geometry.size.width * ratio)
                                .animation(.smooth, value: ratio)
                        }
                    }
                    .frame(height: 10)
                    Text(String(format: "%.3f V", voltage))
                        .font(Theme.numeric(size: 17))
                        .monospacedDigit()
                        .frame(width: 84, alignment: .trailing)
                }
            }
        }
    }
}
