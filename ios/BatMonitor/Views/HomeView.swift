import SwiftUI

struct HomeView: View {
    @Environment(BatteryStore.self) private var store
    @State private var showingAdd = false
    @State private var pendingDelete: SavedBattery?
    @State private var path: [SavedBattery] = []

    var body: some View {
        NavigationStack(path: $path) {
            Group {
            if store.batteries.isEmpty {
                ContentUnavailableView {
                    Label {
                        Text("home.empty.title")
                    } icon: {
                        BatLogoView(size: 120)
                    }
                } description: {
                    Text("home.empty.text")
                } actions: {
                    VStack(spacing: 12) {
                        Button("home.add") { showingAdd = true }
                            .buttonStyle(.borderedProminent)
                            .accessibilityIdentifier("addBattery")
                        Button {
                            path.append(SavedBattery.demo)
                        } label: {
                            Label("demo.open", systemImage: "play.rectangle")
                        }
                        .buttonStyle(SecondaryButtonStyle())
                        .accessibilityIdentifier("openDemo")
                    }
                }
            } else {
                List {
                    Section {
                        ForEach(store.batteries) { battery in
                            Button {
                                path.append(battery)
                            } label: {
                                BatterySummaryCard(
                                    battery: battery,
                                    snapshot: store.snapshot(id: battery.id)
                                )
                            }
                            .buttonStyle(.plain)
                            .listRowBackground(Color.clear)
                            .listRowSeparator(.hidden)
                            .accessibilityIdentifier("batteryCard")
                            .contextMenu {
                                Button(role: .destructive) {
                                    pendingDelete = battery
                                } label: {
                                    Label {
                                        Text("device.delete")
                                    } icon: {
                                        Image(systemName: "trash")
                                            .foregroundStyle(Theme.danger)
                                    }
                                }
                            }
                        }
                        .onDelete { offsets in
                            if let first = offsets.first {
                                pendingDelete = store.batteries[first]
                            }
                        }
                    } header: {
                        Text("home.devices")
                            .font(.footnote)
                            .foregroundStyle(Theme.muted)
                    }

                    Section {
                        Button {
                            path.append(SavedBattery.demo)
                        } label: {
                            DemoBatteryRow()
                        }
                        .buttonStyle(.plain)
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                    } header: {
                        Text("demo.section")
                            .font(.footnote)
                            .foregroundStyle(Theme.muted)
                    } footer: {
                        Text("demo.safetyHint")
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .background(Theme.background)
        .navigationTitle("app.home")
        .navigationDestination(for: SavedBattery.self) { battery in
            BatteryDetailView(battery: battery)
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showingAdd = true
                } label: {
                    Label("home.add", systemImage: "plus")
                }
            }
        }
        .sheet(isPresented: $showingAdd) {
            AddBatteryView()
        }
        .alert(item: $pendingDelete) { battery in
            Alert(
                title: Text("device.removeTitle"),
                message: Text(String(format: String(localized: "device.removeConfirm"), battery.name)),
                primaryButton: .destructive(Text("device.delete")) {
                    store.remove(id: battery.id)
                },
                secondaryButton: .cancel(Text("device.cancel"))
            )
        }
        }
    }
}

private struct BatterySummaryCard: View {
    let battery: SavedBattery
    let snapshot: BatterySnapshot?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                Image(systemName: "battery.75percent")
                    .font(.title3)
                    .foregroundStyle(Theme.charge)
                    .frame(width: 44, height: 44)
                    .background(Theme.surfaceHigh, in: Circle())
                VStack(alignment: .leading, spacing: 3) {
                    Text(battery.name)
                        .font(.headline)
                        .foregroundStyle(.primary)
                    Text("device.directBluetooth")
                        .font(.footnote)
                        .foregroundStyle(Theme.muted)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.muted)
            }

            if let snapshot {
                Divider().overlay(Theme.border)
                HStack(alignment: .firstTextBaseline, spacing: 16) {
                    HStack(spacing: 5) {
                        Image(systemName: "bolt")
                            .foregroundStyle(Theme.charge)
                        Text(String(format: "%.2f", snapshot.voltage))
                            .foregroundStyle(.primary)
                        Text("V")
                            .foregroundStyle(Theme.muted)
                    }
                    .font(Theme.numeric(size: 17))
                    HStack(spacing: 5) {
                        Image(systemName: "arrow.left.arrow.right")
                            .foregroundStyle(Theme.muted)
                        Text(String(format: "%+.2f", snapshot.current))
                            .foregroundStyle(currentColor(snapshot.current))
                        Text("A")
                            .foregroundStyle(Theme.muted)
                    }
                    .font(Theme.numeric(size: 17))
                    Spacer(minLength: 8)
                    HStack(alignment: .firstTextBaseline, spacing: 3) {
                        Text("\(snapshot.soc)")
                            .font(Theme.numeric(size: 17))
                            .contentTransition(.numericText(value: Double(snapshot.soc)))
                        Text("%")
                            .font(Theme.numeric(size: 17))
                            .foregroundStyle(Theme.muted)
                    }
                }
                .lineLimit(1)
                .minimumScaleFactor(0.75)

                ProgressView(value: Double(snapshot.soc), total: 100)
                    .tint(Theme.charge)

                Text(String(format: String(localized: "device.lastReading"), snapshot.receivedAt.formatted(.relative(presentation: .named))))
                    .font(.caption2)
                    .foregroundStyle(Theme.muted)
            } else {
                HStack(spacing: 8) {
                    Image(systemName: "dot.radiowaves.left.and.right")
                        .foregroundStyle(Theme.charge)
                    Text("device.noRecentReading")
                        .font(.subheadline)
                        .foregroundStyle(Theme.muted)
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .panel(cornerRadius: 22)
        .contentShape(.rect)
    }

    private func currentColor(_ current: Double) -> Color {
        current > 0.05 ? Theme.charge : current < -0.05 ? Theme.discharge : Theme.muted
    }
}

private struct DemoBatteryRow: View {
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "play.rectangle")
                .font(.title3)
                .foregroundStyle(Theme.muted)
                .frame(width: 44, height: 44)
                .background(Theme.surfaceHigh, in: Circle())
            VStack(alignment: .leading, spacing: 3) {
                Text("demo.batteryName")
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text("demo.rowHint")
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
            }
            Spacer()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .panel(cornerRadius: 22)
    }
}
