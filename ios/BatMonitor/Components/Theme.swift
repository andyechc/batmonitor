import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

enum Theme {
    static let background = Color(hex: 0x06080C)
    static let surface = Color(hex: 0x0F141C)
    static let surfaceHigh = Color(hex: 0x161D27)
    static let border = Color(hex: 0x1F2935)
    static let muted = Color(hex: 0x7D8BA0)
    static let charge = Color(hex: 0x2EE6A6)
    static let discharge = Color(hex: 0xFFB547)
    static let danger = Color(hex: 0xFF5E6C)
    static let info = Color(hex: 0x5AA9FF)

    /// Rajdhani for figures: squared, techno letterforms give the readings
    /// a device-display feel that system rounded cannot.
    static func numeric(size: CGFloat, bold: Bool = false) -> Font {
        .custom(bold ? "Rajdhani-Bold" : "Rajdhani-SemiBold", size: size)
    }
}

extension View {
    func panel(cornerRadius: CGFloat = 24) -> some View {
        background(Theme.surface, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).stroke(Theme.border))
    }
}
