import SwiftUI

/// Brand bat drawn in code from the original SVG artwork (1024 box).
/// The wings flap briefly every few seconds. Static when Reduce Motion
/// is on.
struct BatLogoView: View {
    var size: CGFloat = 120
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var flap: CGFloat = 0

    private var leftShoulder: UnitPoint { UnitPoint(x: 470 / 1024, y: 503 / 1024) }
    private var rightShoulder: UnitPoint { UnitPoint(x: 554 / 1024, y: 503 / 1024) }

    var body: some View {
        ZStack {
            leftWing
            rightWing
            bodyAndHead
        }
        .frame(width: 1024, height: 1024)
        .scaleEffect(size / 1024)
        .frame(width: size, height: size)
        .accessibilityHidden(true)
        .task {
            if CommandLine.arguments.contains("UITEST_BAT_UP") {
                flap = 1
                return
            }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 3_500_000_000)
                guard !reduceMotion else { continue }
                for _ in 0 ..< 2 {
                    withAnimation(.easeInOut(duration: 0.18)) { flap = 1 }
                    try? await Task.sleep(nanoseconds: 200_000_000)
                    withAnimation(.easeInOut(duration: 0.22)) { flap = 0 }
                    try? await Task.sleep(nanoseconds: 200_000_000)
                }
            }
        }
    }

    private var bodyAndHead: some View {
        ZStack {
            earPath(p1: CGPoint(x: 440, y: 440), p2: CGPoint(x: 416, y: 372), p3: CGPoint(x: 484, y: 418))
                .fill(Theme.surfaceHigh)
            earPath(p1: CGPoint(x: 440, y: 440), p2: CGPoint(x: 416, y: 372), p3: CGPoint(x: 484, y: 418))
                .stroke(Theme.charge, lineWidth: 10)
            earPath(p1: CGPoint(x: 584, y: 440), p2: CGPoint(x: 608, y: 372), p3: CGPoint(x: 540, y: 418))
                .fill(Theme.surfaceHigh)
            earPath(p1: CGPoint(x: 584, y: 440), p2: CGPoint(x: 608, y: 372), p3: CGPoint(x: 540, y: 418))
                .stroke(Theme.charge, lineWidth: 10)
            Path(roundedRect: CGRect(x: 482, y: 398, width: 60, height: 40),
                 cornerSize: CGSize(width: 10, height: 10))
                .fill(Theme.charge)
            Path(roundedRect: CGRect(x: 426, y: 430, width: 172, height: 264),
                 cornerSize: CGSize(width: 40, height: 40))
                .fill(Theme.surfaceHigh)
            Path(roundedRect: CGRect(x: 426, y: 430, width: 172, height: 264),
                 cornerSize: CGSize(width: 40, height: 40))
                .stroke(Theme.charge, lineWidth: 12)
            Path(CGRect(x: 438, y: 562, width: 148, height: 130))
                .fill(Theme.charge)
                .clipShape(Path(roundedRect: CGRect(x: 438, y: 442, width: 148, height: 240),
                                    cornerSize: CGSize(width: 30, height: 30)))
            Path(ellipseIn: CGRect(x: 467, y: 495, width: 22, height: 22))
                .fill(Theme.background)
            Path(ellipseIn: CGRect(x: 535, y: 495, width: 22, height: 22))
                .fill(Theme.background)
        }
        .offset(y: -50 * flap)
    }

    private var leftWing: some View {
        ZStack {
            wingPath(mirrored: false).fill(Theme.surfaceHigh)
            wingPath(mirrored: false).stroke(Theme.charge, lineWidth: 10)
        }
        .rotationEffect(.degrees(-24 * flap), anchor: leftShoulder)
    }

    private var rightWing: some View {
        ZStack {
            wingPath(mirrored: true).fill(Theme.surfaceHigh)
            wingPath(mirrored: true).stroke(Theme.charge, lineWidth: 10)
        }
        .rotationEffect(.degrees(24 * flap), anchor: rightShoulder)
    }

    private func wingPath(mirrored: Bool) -> Path {
        let mx: (CGFloat) -> CGFloat = { mirrored ? 1024 - $0 : $0 }
        var path = Path()
        path.move(to: CGPoint(x: mx(470), y: 440))
        path.addCurve(to: CGPoint(x: mx(96), y: 382),
                     control1: CGPoint(x: mx(380), y: 330),
                     control2: CGPoint(x: mx(210), y: 300))
        path.addCurve(to: CGPoint(x: mx(224), y: 472),
                     control1: CGPoint(x: mx(172), y: 392),
                     control2: CGPoint(x: mx(206), y: 430))
        path.addCurve(to: CGPoint(x: mx(306), y: 494),
                     control1: CGPoint(x: mx(254), y: 452),
                     control2: CGPoint(x: mx(286), y: 462))
        path.addCurve(to: CGPoint(x: mx(374), y: 524),
                     control1: CGPoint(x: mx(326), y: 474),
                     control2: CGPoint(x: mx(356), y: 484))
        path.addCurve(to: CGPoint(x: mx(470), y: 566),
                     control1: CGPoint(x: mx(404), y: 504),
                     control2: CGPoint(x: mx(440), y: 514))
        path.closeSubpath()
        return path
    }

    private func earPath(p1: CGPoint, p2: CGPoint, p3: CGPoint) -> Path {
        var path = Path()
        path.move(to: p1)
        path.addLine(to: p2)
        path.addLine(to: p3)
        path.closeSubpath()
        return path
    }
}
