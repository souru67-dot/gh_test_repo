import SwiftUI
import MapKit

/// マップ — Apple MapKit (the iOS counterpart of Android's osmdroid map).
///
/// v0 limitation: PhotosPicker strips location metadata on iOS the same way the
/// Android Photo Picker does; wiring PHPhotoLibrary (limited-access) to recover
/// GPS lands with the persistence pass. Until then this shows the map shell and
/// an explanatory empty state.
struct HuntMapView: View {
    @EnvironmentObject private var state: AppState

    @State private var position: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 35.681, longitude: 139.767),
            span: MKCoordinateSpan(latitudeDelta: 0.3, longitudeDelta: 0.3),
        ),
    )

    var body: some View {
        NavigationStack {
            Map(position: $position)
                .overlay(alignment: .bottom) {
                    Text("位置情報つきの写真がまだありません。写真ライブラリ連携（次リリース）でExifのGPSを読み取ります。")
                        .font(.caption)
                        .padding(12)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
                        .padding()
                }
                .navigationTitle("カラーマップ")
        }
    }
}
