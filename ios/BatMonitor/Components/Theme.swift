import SwiftUI
import UIKit

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}

/// Adaptive palette: every token resolves from the system appearance, so
/// nothing in the views has to branch on `colorScheme`. The dark column is the
/// original design, byte for byte. The light column keeps the same hue family
/// with a neutral, almost-white background, white cards, and text-safe
/// contrast (all text pairs are >= 4.5:1 on their own surface).
///
/// token        | dark     | light    | usage
/// ------------ | -------- | -------- | -------------------------------------
/// background   | 080808   | F2F2F5   | app canvas, pressed/inset areas
/// surface      | 151515   | FFFFFF   | cards and list rows
/// surfaceHigh  | 202020   | E7E7EC   | chips, icon badges, bar tracks
/// border       | 2C2C2E   | D3D3DA   | dividers and skeleton outlines
/// muted        | 8E8E93   | 63636B   | labels, units, captions
/// neutral      | D8D8DC   | 3A3A40   | idle values, balanced cells
/// charge       | 2EE6A6   | 066B49   | brand green (figures, icons, fill)
/// discharge    | FFB547   | 8A5300   | amber for discharge / warnings
/// danger       | FF453A   | C21A12   | errors and destructive actions
/// onAccent     | 080808   | FFFFFF   | text drawn on top of the brand green
///
/// Contrast notes (light): muted 5.98:1 on surface and 4.84:1 on surfaceHigh,
/// neutral 11.2:1 on surface, charge 7.35:1 on surface and 6.56:1 on
/// background, discharge 6.27:1 on surface, danger 6.09:1 on surface. On top
/// of the liquid battery fill, onAccent keeps 4.9:1 or better.
enum Theme {
    static let background = adaptive(light: 0xF2F2F5, dark: 0x080808)
    static let surface = adaptive(light: 0xFFFFFF, dark: 0x151515)
    static let surfaceHigh = adaptive(light: 0xE7E7EC, dark: 0x202020)
    static let border = adaptive(light: 0xD3D3DA, dark: 0x2C2C2E)
    static let muted = adaptive(light: 0x63636B, dark: 0x8E8E93)
    static let neutral = adaptive(light: 0x3A3A40, dark: 0xD8D8DC)
    static let charge = adaptive(light: 0x066B49, dark: 0x2EE6A6)
    static let discharge = adaptive(light: 0x8A5300, dark: 0xFFB547)
    static let danger = adaptive(light: 0xC21A12, dark: 0xFF453A)
    static let onAccent = adaptive(light: 0xFFFFFF, dark: 0x080808)

    /// Highlight that sweeps over the loading skeleton: light on dark
    /// surfaces, a soft shadow on light ones.
    static let shimmer = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(white: 1, alpha: 0.13)
            : UIColor(white: 0, alpha: 0.07)
    })

    /// M PLUS 1p ExtraBold for figures: a Japanese gothic with a techno
    /// edge. Used at full weight everywhere numbers appear.
    static func numeric(size: CGFloat, bold: Bool = true) -> Font {
        .custom("MPLUS1p-ExtraBold", size: size)
    }

    private static func adaptive(light: UInt32, dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

extension View {
    func panel(cornerRadius: CGFloat = 24) -> some View {
        background(Theme.surface, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}
