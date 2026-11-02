import SwiftUI

struct HomeView: View {
    @Environment(BatteryStore.self) private var store
    @State private var showingAdd = false
    @State private var pendingDelete: SavedBattery?

    var body: some View {
        Group {
            if store.batteries.isEmpty {
                ContentUnavailableView {
                    Label {
                        Text("home.empty.title")
                    } icon: {
                        Image("BatLogo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 120, height: 120)
                    }
                } description: {
                    Text("home.empty.text")
                } actions: {
                    VStack(spacing: 12) {
                        Button("home.add") { showingAdd = true }
                            .buttonStyle(.borderedProminent)
                        NavigationLink(value: SavedBattery.demo) {
                            Label("demo.open", systemImage: "play.rectangle")
                        }
                        .buttonStyle(.bordered)
                    }
                }
            } else {
                List {
                    Section {
                        ForEach(store.batteries) { battery in
                            NavigationLink(value: battery) {
                                BatterySummaryCard(
                                    battery: battery,
                                    snapshot: store.snapshot(id: battery.id)
                                )
                            }
                            .listRowBackground(Color.clear)
                            .listRowSeparator(.hidden)
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
                        NavigationLink(value: SavedBattery.demo) {
                            DemoBatteryRow()
                        }
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
        .sheet(item: $pendingDelete) { battery in
            ConfirmSheet(
                icon: "trash",
                title: "device.removeTitle",
                message: String(format: String(localized: "device.removeConfirm"), battery.name),
                confirmTitle: "device.delete"
            ) {
                store.remove(id: battery.id)
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
                Spacer(minLength: 8)
            }

            if let snapshot {
                Divider().overlay(Theme.border)
                HStack(alignment: .center, spacing: 14) {
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(alignment: .firstTextBaseline, spacing: 2) {
                            Text("\(snapshot.soc)")
                                .font(Theme.numeric(size: 40))
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                                .fixedSize(horizontal: true, vertical: false)
                                .contentTransition(.numericText(value: Double(snapshot.soc)))
                            Text("%")
                                .font(Theme.numeric(size: 20))
                                .foregroundStyle(Theme.muted)
                        }
                        Text("device.stat.charge")
                            .font(.caption)
                            .foregroundStyle(Theme.muted)
                        ProgressView(value: Double(snapshot.soc), total: 100)
                            .tint(Theme.charge)
                            .frame(width: 104)
                    }
                    .layoutPriority(1)

                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 5) {
                            Image(systemName: "bolt")
                                .foregroundStyle(Theme.charge)
                            Text(String(format: "%.2f", snapshot.voltage))
                                .foregroundStyle(.primary)
                            Text("V")
                                .foregroundStyle(Theme.muted)
                        }
                        .font(Theme.numeric(size: 20))
                        HStack(spacing: 5) {
                            Image(systemName: "arrow.left.arrow.right")
                                .foregroundStyle(Theme.muted)
                            Text(String(format: "%+.2f", snapshot.current))
                                .foregroundStyle(currentColor(snapshot.current))
                            Text("A")
                                .foregroundStyle(Theme.muted)
                        }
                        .font(Theme.numeric(size: 18))
                    }
                    Spacer(minLength: 0)
                }

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
