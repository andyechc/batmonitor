import SwiftUI

struct HomeView: View {
    @Environment(BatteryStore.self) private var store
    @State private var showingAdd = false

    var body: some View {
        Group {
            if store.batteries.isEmpty {
                ContentUnavailableView {
                    Label("home.empty.title", systemImage: "battery.75percent")
                } description: {
                    Text("home.empty.text")
                } actions: {
                    Button("home.add") { showingAdd = true }
                        .buttonStyle(.borderedProminent)
                }
            } else {
                List {
                    ForEach(store.batteries) { battery in
                        NavigationLink(value: battery) {
                            BatteryRow(battery: battery)
                        }
                        .listRowBackground(Theme.surface)
                    }
                    .onDelete { store.remove(at: $0) }
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
                .font(.title2)
                .foregroundStyle(Theme.charge)
                .frame(width: 48, height: 48)
                .background(Theme.surfaceHigh, in: Circle())
            Text(battery.name)
                .font(.headline)
        }
        .padding(.vertical, 6)
    }
}
