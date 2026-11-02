import SwiftUI

/// Dashboard-shaped placeholders with one shared shimmer sweep while BLE
/// connects. The explicit status label outside this view distinguishes these
/// shapes from loaded values. Reduce Motion keeps the same static skeleton.
struct BatteryDashboardSkeleton: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shimmerPhase: CGFloat = -1

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
        .onAppear {
            guard !reduceMotion else { return }
            shimmerPhase = 1
        }
        .animation(.linear(duration: 1.4).repeatForever(autoreverses: false), value: shimmerPhase)
    }

    private var heroPlaceholder: some View {
        VStack(spacing: 14) {
            block(Capsule(), width: 92, height: 25)

            HStack(spacing: 5) {
                block(RoundedRectangle(cornerRadius: 20, style: .continuous), width: 190, height: 100, fill: Theme.surface)
                    .overlay {
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .strokeBorder(Theme.border, lineWidth: 3)
                            .frame(width: 190, height: 100)
                    }
                    .overlay {
                        block(Capsule(), width: 70, height: 20)
                    }
                block(RoundedRectangle(cornerRadius: 4), width: 9, height: 40)
            }

            block(Capsule(), width: 118, height: 16)
            block(Capsule(), width: 150, height: 12, opacity: 0.75)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
    }

    private var metricPlaceholder: some View {
        VStack(alignment: .leading, spacing: 12) {
            block(Capsule(), width: 78, height: 12)
            block(Capsule(), width: 102, height: 25, opacity: 0.8)
        }
        .frame(maxWidth: .infinity, minHeight: 82, alignment: .leading)
        .padding(16)
        .panel()
    }

    private var cellPlaceholder: some View {
        VStack(alignment: .leading, spacing: 14) {
            block(Capsule(), width: 96, height: 12)
            ForEach(0..<3, id: \.self) { index in
                HStack(spacing: 12) {
                    block(Capsule(), width: 24, height: 10)
                    block(Capsule(), height: 10, opacity: index == 1 ? 0.9 : 0.65)
                    block(Capsule(), width: 54, height: 10)
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .panel()
    }

    private func block<S: Shape>(
        _ shape: S,
        width: CGFloat? = nil,
        height: CGFloat,
        fill: Color = Theme.surfaceHigh,
        opacity: Double = 1
    ) -> some View {
        shape
            .fill(fill.opacity(opacity))
            .overlay {
                if !reduceMotion {
                    GeometryReader { geometry in
                        let sweepWidth = max(36, geometry.size.width * 0.42)
                        LinearGradient(
                            colors: [.clear, Color.white.opacity(0.13), .clear],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                        .frame(width: sweepWidth)
                        .offset(x: (geometry.size.width + sweepWidth) * shimmerPhase / 2)
                    }
                    .mask(shape.fill(.white))
                }
            }
            .frame(width: width, height: height)
            .clipShape(shape)
    }
}
