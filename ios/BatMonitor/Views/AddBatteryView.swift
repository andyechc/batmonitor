import SwiftUI

struct AddBatteryView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(BatteryStore.self) private var store
    @State private var scanner = BatteryScanner()

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 10) {
                        if scanner.isScanning {
                            ProgressView()
                        } else {
                            Image(systemName: "bluetooth")
                                .foregroundStyle(Theme.muted)
                        }
                        Text(LocalizedStringKey(scanner.isScanning ? "add.scanning" : "add.done"))
                            .font(.headline)
                    }
                    .listRowBackground(Theme.surface)
                }

                if let error = scanner.error {
                    Section {
                        Label(error, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(Theme.danger)
                            .listRowBackground(Theme.surface)
                    }
                }

                Section {
                    ForEach(scanner.found) { device in
                        Button {
                            select(device)
                        } label: {
                            HStack(spacing: 14) {
                                Image(systemName: "bluetooth")
                                    .foregroundStyle(Theme.info)
                                    .frame(width: 44, height: 44)
                                    .background(Theme.surfaceHigh, in: Circle())
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(device.name)
                                        .font(.headline)
                                    Text("\(device.rssi) dBm")
                                        .font(.caption)
                                        .foregroundStyle(Theme.muted)
                                }
                                Spacer()
                            }
                        }
                        .listRowBackground(Theme.surface)
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
                        .listRowBackground(Theme.surface)
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
    }

    private func select(_ device: ScannedDevice) {
        scanner.stop()
        store.add(id: device.id, name: device.name)
        dismiss()
    }
}
