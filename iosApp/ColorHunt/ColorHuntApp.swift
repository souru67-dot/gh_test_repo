import SwiftUI

/// Brand palette — a single source so every screen reads as one system.
enum Brand {
    static let purple = Color(red: 124 / 255, green: 77 / 255, blue: 255 / 255)   // 0xFF7C4DFF
    static let pink = Color(red: 236 / 255, green: 64 / 255, blue: 122 / 255)     // 0xFFEC407A
    static let amber = Color(red: 255 / 255, green: 167 / 255, blue: 38 / 255)    // 0xFFFFA726
    static let accent = Color(red: 158 / 255, green: 124 / 255, blue: 255 / 255)  // 0xFF9E7CFF
    static let base = Color(red: 0x10 / 255.0, green: 0x10 / 255.0, blue: 0x14 / 255.0)

    /// The signature diagonal brand gradient.
    static let gradient = LinearGradient(
        colors: [purple, pink, amber],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
}

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
                .tint(Brand.accent)          // brand-coloured controls + tab selection
                .preferredColorScheme(.dark) // 映え-first dark base, like Android
        }
    }
}

struct RootTabView: View {
    @EnvironmentObject private var state: AppState

    var body: some View {
        TabView(selection: $state.selectedTab) {
            HuntView()
                .tabItem { Label("ハント", systemImage: "camera.viewfinder") }
                .tag(AppTab.hunt)
            CollageView()
                .tabItem { Label("コラージュ", systemImage: "square.grid.2x2") }
                .tag(AppTab.collage)
            TodayColorView()
                .tabItem { Label("今日の色", systemImage: "sparkles") }
                .tag(AppTab.today)
            GridPreviewView()
                .tabItem { Label("グリッド", systemImage: "square.grid.3x3") }
                .tag(AppTab.grid)
            HuntMapView()
                .tabItem { Label("マップ", systemImage: "map") }
                .tag(AppTab.map)
        }
    }
}

/// A springy press-scale for primary CTAs — a small delight that reads as modern.
struct PopButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: configuration.isPressed)
    }
}
