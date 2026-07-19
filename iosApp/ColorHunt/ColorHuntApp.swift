import SwiftUI

// Entry point for the iOS ColorHunt app.
//
// This is a starting scaffold that consumes the Kotlin Multiplatform `:shared`
// module (framework name `SharedColor`) so the colour-classification core is the
// exact same code as Android. Build/run it from Xcode on macOS — see
// iosApp/README.md for the one-time framework wiring.
@main
struct ColorHuntApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
