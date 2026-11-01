import SwiftUI

struct HomeView: View {
    @Environment(BatteryStore.self) private var store
    @State private var showingAdd = false

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
                    Button("home.add") { showingAdd = true }
                        .buttonStyle(.borderedProminent)
                }
            } else {
                List {
                    Section {
                        ForEach(store.batteries) { battery in
                            NavigationLink(value: battery) {
                                BatteryRow(battery: battery)
                            }
                            .contextMenu {
                                Button(role: .destructive) {
                                    store.remove(id: battery.id)
                                } label: {
                                    Label("device.delete", systemImage: "trash")
                                }
                            }
                        }
                        .onDelete { store.remove(at: $0) }
                    }
                }
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
    }
}

private struct BatteryRow: View {
    let battery: SavedBattery

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "battery.75percent")
                .font(.title3)
                .foregroundStyle(Theme.charge)
                .frame(width: 44, height: 44)
                .background(Theme.surfaceHigh, in: .circle)
            VStack(alignment: .leading, spacing: 2) {
                Text(battery.name)
                    .font(.headline)
                Text("device.tapToMonitor")
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
            }
        }
        .padding(.vertical, 4)
    }
}
