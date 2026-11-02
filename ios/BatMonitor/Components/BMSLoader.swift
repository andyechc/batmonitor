import SwiftUI

/// One loading treatment used for BLE scanning and connection. The subtitle
/// gives the spinner context for VoiceOver and makes long BLE waits legible.
struct BMSLoader: View {
    let title: LocalizedStringKey
    var detail: LocalizedStringKey?
    var compact = false

    var body: some View {
        HStack(spacing: 12) {
            ProgressView()
                .controlSize(compact ? .small : .regular)
                .tint(Theme.charge)
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(compact ? .subheadline.weight(.medium) : .body.weight(.semibold))
                    .foregroundStyle(.primary)
                if let detail {
                    Text(detail)
                        .font(.footnote)
                        .foregroundStyle(Theme.muted)
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}
