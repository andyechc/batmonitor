import SwiftUI

struct Card<Content: View>: View {
    var title: String?
    var icon: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let title {
                HStack(spacing: 8) {
                    if let icon {
                        Image(systemName: icon)
                    }
                    Text(title)
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

struct StatTile: View {
    let label: LocalizedStringKey
    let value: String
    var unit: String?
    let icon: String
    var accent: Color = .primary

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .foregroundStyle(accent)
                Text(label)
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
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

struct SocRing: View {
    let soc: Int
    let color: Color
    var size: CGFloat = 200
    var lineWidth: CGFloat = 14

    var body: some View {
        let progress = CGFloat(min(100, max(0, soc))) / 100
        ZStack {
            Circle()
                .stroke(Theme.surfaceHigh, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: progress)
                .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.easeOut(duration: 0.4), value: progress)
            VStack(spacing: -4) {
                Text("\(soc)")
                    .font(.system(size: size * 0.26, weight: .bold))
                    .monospacedDigit()
                    .contentTransition(.numericText(value: Double(soc)))
                    .animation(.smooth, value: soc)
                Text("device.stat.charge")
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
            }
        }
        .frame(width: size, height: size)
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
