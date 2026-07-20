import SwiftUI

// ColorHunt iOS — feature parity with the Android app, sharing the KMP colour
// and collage-geometry core (framework `SharedColor`). Build from Xcode on
// macOS; see iosApp/README.md for the one-time framework wiring.
@main
struct ColorHuntApp: App {
    @StateObject private var state = AppState()

    var body: some Scene {
        WindowGroup {
            RootTabView()
                .environmentObject(state)
                .preferredColorScheme(.dark) // 映え-first dark base, like Android
        }
    }
}

struct RootTabView: View {
    @EnvironmentObject private var state: AppState

    var body: some View {
        TabView(selection: $state.selectedTab) {
            HuntView()
                .tabItem { Label("ハント", systemImage: "photo.on.rectangle") }
                .tag(AppTab.hunt)
            CollageView()
                .tabItem { Label("コラージュ", systemImage: "square.grid.2x2") }
                .tag(AppTab.collage)
            TodayColorView()
                .tabItem { Label("今日の色", systemImage: "dice") }
                .tag(AppTab.today)
            GridPreviewView()
                .tabItem { Label("グリッド", systemImage: "square.grid.3x3") }
                .tag(AppTab.grid)
            HuntMapView()
                .tabItem { Label("マップ", systemImage: "mappin.and.ellipse") }
                .tag(AppTab.map)
        }
    }
}
