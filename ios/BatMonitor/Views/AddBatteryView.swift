import SwiftUI

struct AddBatteryView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(BatteryStore.self) private var store
    @State private var scanner = BatteryScanner()

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

                if !scanner.found.isEmpty {
                    Section {
                        ForEach(scanner.found) { device in
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

                if !scanner.isScanning {
                    Section {
                        Button("add.retry") {
                            Task { await scanner.start() }
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.large)
                        .frame(maxWidth: .infinity)
                        .tint(Theme.charge)
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("add.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("device.cancel") { dismiss() }
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
        HStack(spacing: 10) {
            if scanner.isScanning {
                ProgressView()
                    .controlSize(.small)
            } else {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Theme.muted)
            }
            Text(LocalizedStringKey(scanner.isScanning ? "add.scanning" : "add.done"))
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Theme.muted)
            Spacer()
        }
    }

    private func select(_ device: ScannedDevice) {
        scanner.stop()
        store.add(id: device.id, name: device.name)
        dismiss()
    }
}
