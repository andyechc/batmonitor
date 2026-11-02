import SwiftUI

/// Static placeholders mirror the real dashboard layout while BLE connects.
/// The explicit status label prevents the shapes from reading as loaded data.
struct BatteryDashboardSkeleton: View {
    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        VStack(spacing: 18) {
            heroPlaceholder
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(0..<6, id: \.self) { _ in
                    metricPlaceholder
                }
            }
            cellPlaceholder
        }
        .accessibilityHidden(true)
    }

    private var heroPlaceholder: some View {
        VStack(spacing: 14) {
            Capsule()
                .fill(Theme.surfaceHigh)
                .frame(width: 92, height: 25)

            VStack(spacing: 5) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Theme.surfaceHigh)
                    .frame(width: 38, height: 9)
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .strokeBorder(Theme.border, lineWidth: 3)
                    }
                    .frame(width: 120, height: 170)
                    .overlay {
                        Capsule()
                            .fill(Theme.surfaceHigh)
                            .frame(width: 70, height: 20)
                    }
            }

            Capsule()
                .fill(Theme.surfaceHigh)
                .frame(width: 118, height: 16)
            Capsule()
                .fill(Theme.surfaceHigh.opacity(0.75))
                .frame(width: 150, height: 12)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
    }

    private var metricPlaceholder: some View {
        VStack(alignment: .leading, spacing: 12) {
            Capsule()
                .fill(Theme.surfaceHigh)
                .frame(width: 78, height: 12)
            Capsule()
                .fill(Theme.surfaceHigh.opacity(0.8))
                .frame(width: 102, height: 25)
        }
        .frame(maxWidth: .infinity, minHeight: 82, alignment: .leading)
        .padding(16)
        .panel()
    }

    private var cellPlaceholder: some View {
        VStack(alignment: .leading, spacing: 14) {
            Capsule()
                .fill(Theme.surfaceHigh)
                .frame(width: 96, height: 12)
            ForEach(0..<3, id: \.self) { index in
                HStack(spacing: 12) {
                    Capsule().fill(Theme.surfaceHigh).frame(width: 24, height: 10)
                    Capsule()
                        .fill(Theme.surfaceHigh.opacity(index == 1 ? 0.9 : 0.65))
                        .frame(height: 10)
                    Capsule().fill(Theme.surfaceHigh).frame(width: 54, height: 10)
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .panel()
    }
}
