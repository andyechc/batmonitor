import SwiftUI

struct AddBatteryView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(BatteryStore.self) private var store
    @State private var scanner = BatteryScanner()
    @State private var searchQuery = ""

    private var filteredDevices: [ScannedDevice] {
        let tokens = searchQuery
            .split(whereSeparator: \.isWhitespace)
            .map(String.init)
        guard !tokens.isEmpty else { return scanner.found }

        return scanner.found.filter { device in
            let searchable = "\(device.name) \(device.id.uuidString) \(device.rssi) dBm"
                .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            return tokens.allSatisfy { token in
                searchable.contains(token.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current))
            }
        }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    statusRow
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }

                if let error = scanner.error {
                    Section {
                        Label(error, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(Theme.danger)
                    }
                }

                if !filteredDevices.isEmpty {
                    Section {
                        ForEach(filteredDevices) { device in
                            Button {
                                select(device)
                            } label: {
                                HStack(spacing: 14) {
                                    Image(systemName: "battery.75percent")
                                        .font(.title3)
                                        .foregroundStyle(Theme.charge)
                                        .frame(width: 44, height: 44)
                                        .background(Theme.surfaceHigh, in: Circle())
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(device.name)
                                            .font(.headline)
                                            .foregroundStyle(.primary)
                                        Text("\(device.rssi) dBm")
                                            .font(.caption)
                                            .foregroundStyle(Theme.muted)
                                            .monospacedDigit()
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.footnote.weight(.semibold))
                                        .foregroundStyle(Theme.muted)
                                }
                            }
                            .tint(.primary)
                            .listRowBackground(Theme.surface)
                        }
                    } header: {
                        Text("add.nearby")
                            .font(.footnote)
                            .foregroundStyle(Theme.muted)
                    }
                }

                if !scanner.isScanning && scanner.found.isEmpty && scanner.error == nil {
                    Section {
                        Text("add.empty")
                            .foregroundStyle(Theme.muted)
                            .listRowBackground(Theme.surface)
                    }
                }

                if !scanner.isScanning && !scanner.found.isEmpty && !searchQuery.isEmpty && filteredDevices.isEmpty {
                    Section {
                        Text("add.noSearchResults")
                            .foregroundStyle(Theme.muted)
                            .listRowBackground(Theme.surface)
                    }
                }

                if !scanner.isScanning {
                    Section {
                        Button("add.retry") {
                            Task { await scanner.start() }
                        }
                        .buttonStyle(SecondaryButtonStyle())
                        .frame(maxWidth: .infinity)
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .searchable(text: $searchQuery, prompt: Text("add.searchPrompt"))
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .navigationTitle("add.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel(Text("device.cancel"))
                }
            }
            .task { await scanner.start() }
            .onDisappear { scanner.stop() }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    /// Plain status line, deliberately not a card: it is information,
    /// not an action.
    private var statusRow: some View {
        Group {
            if scanner.isScanning {
                BMSLoader(title: "add.scanning", detail: "add.scanHint", compact: true)
            } else {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(Theme.charge)
                    Text("add.done")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Theme.muted)
                }
            }
        }
    }

    private func select(_ device: ScannedDevice) {
        scanner.stop()
        store.add(id: device.id, name: device.name)
        dismiss()
    }
}
