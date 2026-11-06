import SwiftUI

@main
struct BatMonitorApp: App {
    @State private var store = BatteryStore()

    var body: some Scene {
        WindowGroup {
            HomeView()
            .environment(store)
            .tint(Theme.charge)
            .toolbarBackground(.ultraThinMaterial, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
        }
    }
}
