import SwiftUI
import MapKit
import SharedColor // Kotlin Multiplatform :shared framework (baseName "SharedColor")

/// Demonstrates that the shared Kotlin colour engine runs on iOS: each sample
/// colour is classified by the SAME `ColorClassifier` the Android app uses.
///
/// Kotlin `object ColorClassifier` is exposed to Swift as `ColorClassifier.shared`.
/// `ColorBucket` exposes `.name` (String) and `.swatch` (Int32 ARGB), so we read
/// those instead of switching on enum cases (robust to name bridging).
struct ContentView: View {
    // Sample ARGB colours to classify (0xAARRGGBB).
    private let samples: [Int64] = [
        0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF9ACD32,
        0xFF43A047, 0xFF26C6DA, 0xFF1E88E5, 0xFF8E24AA,
        0xFFEC407A, 0xFFFAFAFA, 0xFF212121, 0xFF9E9E9E,
    ]

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 12)]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(samples, id: \.self) { argb in
                        let bucket = ColorClassifier.shared.classify(colorInt: Int32(truncatingIfNeeded: argb))
                        SwatchCard(argb: argb, label: bucket.name)
                    }
                }
                .padding()

                WalkMap()
                    .frame(height: 260)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .padding()
            }
            .navigationTitle("ColorHunt")
        }
    }
}

private struct SwatchCard: View {
    let argb: Int64
    let label: String

    var body: some View {
        VStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(argb: argb))
                .frame(height: 64)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(.white.opacity(0.15)))
            Text(label)
                .font(.caption).bold()
        }
    }
}

/// Phase 3 equivalent on iOS uses MapKit (chosen over osmdroid/Google Maps).
private struct WalkMap: View {
    @State private var position: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 35.681, longitude: 139.767),
            span: MKCoordinateSpan(latitudeDelta: 0.2, longitudeDelta: 0.2)
        )
    )

    var body: some View {
        Map(position: $position)
    }
}

private extension Color {
    /// Build a SwiftUI Color from a packed 0xAARRGGBB integer.
    init(argb: Int64) {
        let r = Double((argb >> 16) & 0xFF) / 255.0
        let g = Double((argb >> 8) & 0xFF) / 255.0
        let b = Double(argb & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: 1.0)
    }
}
