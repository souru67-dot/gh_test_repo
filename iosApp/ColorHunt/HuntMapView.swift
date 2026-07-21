import SwiftUI
import MapKit
import Photos

/// マップ — Apple MapKit (the iOS counterpart of Android's osmdroid map). Recovers
/// GPS from the photo library (PhotosPicker strips it) and drops a colour pin per
/// geotagged photo, tinted by its dominant colour. Tap a pin for the photo + HEX.
///
/// Uses the region-based Map API so it runs on iOS 16 (the newer
/// `Map(position:)` / `Annotation` builder is iOS 17+).
struct HuntMapView: View {
    @EnvironmentObject private var state: AppState

    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 35.681, longitude: 139.767),
        span: MKCoordinateSpan(latitudeDelta: 0.4, longitudeDelta: 0.4)
    )
    @State private var selected: MapPin?

    var body: some View {
        NavigationStack {
            Map(coordinateRegion: $region, annotationItems: state.mapPhotos) { pin in
                MapAnnotation(coordinate: pin.coordinate) {
                    colorDot(pin)
                }
            }
            .ignoresSafeArea(edges: .bottom)
            .overlay(alignment: .bottom) { bottomBar }
            .navigationTitle("カラーマップ")
            .toolbar {
                Button {
                    state.loadMapPhotos()
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(state.mapLoading)
            }
            .onAppear {
                if state.mapPhotos.isEmpty, !state.mapLoading, libraryAuthorized {
                    state.loadMapPhotos()
                }
            }
            .onChange(of: state.mapPhotos.count) { _ in
                if let fitted = fittedRegion(state.mapPhotos) {
                    withAnimation { region = fitted }
                }
            }
            .sheet(item: $selected) { pin in
                pinDetail(pin)
            }
        }
    }

    private var libraryAuthorized: Bool {
        let s = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        return s == .authorized || s == .limited
    }

    private func colorDot(_ pin: MapPin) -> some View {
        Circle()
            .fill(Color(packed: pin.color))
            .frame(width: 22, height: 22)
            .overlay(Circle().stroke(.white, lineWidth: 2))
            .shadow(color: .black.opacity(0.4), radius: 2, y: 1)
            .onTapGesture { selected = pin }
    }

    /// A region that frames all pins with a little padding.
    private func fittedRegion(_ pins: [MapPin]) -> MKCoordinateRegion? {
        guard !pins.isEmpty else { return nil }
        let lats = pins.map { $0.coordinate.latitude }
        let lons = pins.map { $0.coordinate.longitude }
        guard let minLat = lats.min(), let maxLat = lats.max(),
              let minLon = lons.min(), let maxLon = lons.max() else { return nil }
        let center = CLLocationCoordinate2D(
            latitude: (minLat + maxLat) / 2,
            longitude: (minLon + maxLon) / 2
        )
        let span = MKCoordinateSpan(
            latitudeDelta: max((maxLat - minLat) * 1.4, 0.02),
            longitudeDelta: max((maxLon - minLon) * 1.4, 0.02)
        )
        return MKCoordinateRegion(center: center, span: span)
    }

    @ViewBuilder
    private var bottomBar: some View {
        if state.mapLoading {
            HStack(spacing: 8) {
                ProgressView()
                Text("写真ライブラリを読み込み中…").font(.caption)
            }
            .padding(12)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
            .padding()
        } else if state.mapPhotos.isEmpty {
            VStack(spacing: 8) {
                Text("位置情報つきの写真をマップに表示します。")
                    .font(.caption).multilineTextAlignment(.center)
                Button {
                    state.loadMapPhotos()
                } label: {
                    Label("写真から読み込む", systemImage: "photo.on.rectangle")
                        .font(.callout.bold())
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(14)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
            .padding()
        } else {
            Text("\(state.mapPhotos.count)枚の色をマップに表示中")
                .font(.caption)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(.ultraThinMaterial, in: Capsule())
                .padding(.bottom, 12)
        }
    }

    private func pinDetail(_ pin: MapPin) -> some View {
        VStack(spacing: 16) {
            Image(uiImage: pin.thumbnail)
                .resizable()
                .scaledToFill()
                .frame(width: 220, height: 220)
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .padding(.top, 24)
            HStack(spacing: 12) {
                Circle().fill(Color(packed: pin.color))
                    .frame(width: 32, height: 32)
                    .overlay(Circle().stroke(.white.opacity(0.4)))
                Text(hexString(pin.color))
                    .font(.system(.title3, design: .monospaced))
            }
            Spacer()
        }
        .presentationDetents([.medium])
    }
}
