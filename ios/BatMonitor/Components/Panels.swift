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
                    .font(.system(size: 26, weight: .bold))
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
/// whose wave only animates while charging, so idle screens stay still.
struct FluidBatteryView: View {
    let soc: Int
    let charging: Bool
    let color: Color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let level = Double(min(100, max(0, soc))) / 100
        if charging && level < 1 && !reduceMotion {
            TimelineView(.animation(minimumInterval: 1 / 30)) { context in
                battery(level: level, phase: context.date.timeIntervalSinceReferenceDate * 2.2)
            }
        } else {
            battery(level: level, phase: 0.8)
        }
    }

    private func battery(level: Double, phase: Double) -> some View {
        VStack(spacing: 5) {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(color)
                .frame(width: 40, height: 9)
            ZStack {
                if level >= 1 {
                    RoundedRectangle(cornerRadius: 15, style: .continuous)
                        .fill(color.opacity(0.85))
                        .padding(7)
                } else {
                    WaveShape(level: level, phase: phase)
                        .fill(color.opacity(0.85))
                        .padding(7)
                        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                }
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(color, lineWidth: 3)
                Text("\(soc)%")
                    .font(.system(size: 28, weight: .bold))
                    .monospacedDigit()
                    .contentTransition(.numericText(value: Double(soc)))
                    .foregroundStyle(.white)
            }
            .frame(width: 120, height: 190)
        }
        .shadow(color: charging ? color.opacity(0.45) : .clear, radius: 14)
        .accessibilityLabel(Text("device.stat.charge"))
        .accessibilityValue("\(soc)%")
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
                let color = balanced ? Theme.info : voltage == high ? Theme.charge : voltage == low ? Theme.discharge : Theme.info
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
                        .font(.subheadline)
                        .monospacedDigit()
                        .frame(width: 84, alignment: .trailing)
                }
            }
        }
    }
}
