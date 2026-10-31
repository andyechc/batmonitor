import SwiftUI

@main
struct BatMonitorApp: App {
    @State private var store = BatteryStore()

    var body: some Scene {
        WindowGroup {
            NavigationStack {
                HomeView()
                    .navigationDestination(for: SavedBattery.self) { battery in
                        BatteryDetailView(battery: battery)
                    }
            }
            .environment(store)
            .preferredColorScheme(.dark)
            .tint(Theme.charge)
        }
    }
}
