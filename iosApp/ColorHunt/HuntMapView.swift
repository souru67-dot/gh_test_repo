import SwiftUI
import MapKit
import Photos
import SharedColor

/// マップ — the colour map. Recovers GPS from the photo library (PhotosPicker
/// strips it) and drops a colour pin per geotagged photo. Pins filter by colour
/// bucket, and the current view exports as an image (pins + brand pill drawn in).
///
/// Uses the region-based Map API so it runs on iOS 16.
struct HuntMapView: View {
    @EnvironmentObject private var state: AppState

    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 35.681, longitude: 139.767),
        span: MKCoordinateSpan(latitudeDelta: 0.4, longitudeDelta: 0.4)
    )
    @State private var selected: MapPin?
    @State private var filter: String?
    @State private var savingSnapshot = false
    @State private var saveDone = false

    /// Buckets present among the loaded pins, in shared display order.
    private var pinBuckets: [String] {
        let present = Set(state.mapPhotos.map { ColorBridge.shared.classifyKey(colorInt: $0.color) })
        return ColorBridge.shared.bucketKeys().filter { present.contains($0) }
    }

    private var filteredPins: [MapPin] {
        guard let f = filter else { return state.mapPhotos }
        return state.mapPhotos.filter { ColorBridge.shared.classifyKey(colorInt: $0.color) == f }
    }

    var body: some View {
        NavigationStack {
            Map(coordinateRegion: $region, annotationItems: filteredPins) { pin in
                MapAnnotation(coordinate: pin.coordinate) {
                    colorDot(pin)
                }
            }
            .ignoresSafeArea(edges: .bottom)
            .overlay(alignment: .top) { topOverlay }
            .overlay(alignment: .bottom) { bottomBar }
            .overlay(alignment: .top) {
                if saveDone { SaveToast().padding(.top, 60) }
            }
            .toolbar(.hidden, for: .navigationBar)
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

    // MARK: top overlay — floating title + colour filter chips

    private var topOverlay: some View {
        VStack(spacing: 8) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "map.fill").font(.footnote)
                    Text("カラーマップ")
                        .font(.system(.subheadline, design: .rounded).bold())
                }
                .padding(.horizontal, 14).padding(.vertical, 9)
                .background(.ultraThinMaterial, in: Capsule())

                Spacer()

                Button {
                    state.loadMapPhotos()
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .font(.subheadline.weight(.semibold))
                        .padding(10)
                        .background(.ultraThinMaterial, in: Circle())
                }
                .accessibilityLabel("写真から読み込む")
                .disabled(state.mapLoading)
            }

            if pinBuckets.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        mapFilterChip(nil, "すべて")
                        ForEach(pinBuckets, id: \.self) { key in
                            mapFilterChip(key, bucketLabel(key))
                        }
                    }
                    .padding(.horizontal, 2)
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.top, 6)
    }

    private func mapFilterChip(_ key: String?, _ label: String) -> some View {
        let active = filter == key
        return HStack(spacing: 6) {
            if let key {
                Circle()
                    .fill(Color(packed: Int32(truncatingIfNeeded: ColorBridge.shared.swatchOf(key: key))))
                    .frame(width: 11, height: 11)
                    .overlay(Circle().stroke(.white.opacity(0.5), lineWidth: 0.5))
            }
            Text(LocalizedStringKey(label)).font(.caption.weight(.semibold))
        }
        .padding(.horizontal, 12).padding(.vertical, 7)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(Capsule().stroke(active ? Brand.accent : .clear, lineWidth: 1.5))
        .onTapGesture {
            withAnimation(.easeInOut(duration: 0.15)) {
                filter = (filter == key ? nil : key)
            }
            if let fitted = fittedRegion(filteredPins) {
                withAnimation { region = fitted }
            }
        }
    }

    // MARK: pins

    private var libraryAuthorized: Bool {
        let s = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        return s == .authorized || s == .limited
    }

    /// Photo-bubble pin: the photo itself in a circle, ringed by its dominant
    /// colour with a small colour tail dot — instantly scannable and inviting.
    private func colorDot(_ pin: MapPin) -> some View {
        VStack(spacing: 1) {
            Image(uiImage: pin.thumbnail)
                .resizable()
                .scaledToFill()
                .frame(width: 38, height: 38)
                .clipShape(Circle())
                .overlay(Circle().stroke(Color(packed: pin.color), lineWidth: 3))
                .overlay(Circle().stroke(.white.opacity(0.9), lineWidth: 1))
            Circle()
                .fill(Color(packed: pin.color))
                .frame(width: 7, height: 7)
                .overlay(Circle().stroke(.white.opacity(0.8), lineWidth: 1))
        }
        .shadow(color: .black.opacity(0.4), radius: 3, y: 1)
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

    // MARK: bottom bar

    @ViewBuilder
    private var bottomBar: some View {
        if state.mapLoading {
            HStack(spacing: 8) {
                ProgressView()
                Text("写真ライブラリを読み込み中…").font(.caption)
            }
            .padding(12)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
            .padding(.bottom, 14)
        } else if state.mapPhotos.isEmpty {
            VStack(spacing: 10) {
                Text("位置情報つきの写真をマップに表示します。")
                    .font(.caption).multilineTextAlignment(.center)
                Button {
                    state.loadMapPhotos()
                } label: {
                    Label("写真から読み込む", systemImage: "photo.on.rectangle")
                        .font(.system(.callout, design: .rounded).bold())
                        .padding(.horizontal, 20).padding(.vertical, 11)
                        .foregroundStyle(.white)
                        .background(Brand.gradient, in: Capsule())
                        .shadow(color: Brand.purple.opacity(0.5), radius: 10, y: 4)
                }
                .buttonStyle(PopButtonStyle())
            }
            .padding(16)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
            .padding(.bottom, 14)
        } else {
            HStack(spacing: 10) {
                Text("\(filteredPins.count)枚")
                    .font(.system(.subheadline, design: .rounded).bold())
                    .padding(.horizontal, 12).padding(.vertical, 9)
                    .background(.ultraThinMaterial, in: Capsule())

                Spacer()

                Button {
                    saveSnapshot()
                } label: {
                    Group {
                        if savingSnapshot {
                            ProgressView().tint(.white)
                        } else {
                            Label("写真に保存", systemImage: "square.and.arrow.down")
                                .font(.system(.callout, design: .rounded).bold())
                        }
                    }
                    .padding(.horizontal, 18).padding(.vertical, 11)
                    .foregroundStyle(.white)
                    .background(Brand.gradient, in: Capsule())
                    .shadow(color: Brand.purple.opacity(0.5), radius: 10, y: 4)
                }
                .buttonStyle(PopButtonStyle())
                .disabled(savingSnapshot)
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 14)
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

    // MARK: snapshot export

    /// Renders the current region via MKMapSnapshotter, draws the (filtered)
    /// colour pins and the brand pill onto it, and saves to the photo library —
    /// a shareable "my colour map" image.
    private func saveSnapshot() {
        guard !savingSnapshot else { return }
        savingSnapshot = true
        let pins = filteredPins
        let options = MKMapSnapshotter.Options()
        options.region = region
        options.size = CGSize(width: 1080, height: 1350)

        MKMapSnapshotter(options: options).start { snapshot, _ in
            guard let snapshot else {
                DispatchQueue.main.async { savingSnapshot = false }
                return
            }
            let image = UIGraphicsImageRenderer(size: snapshot.image.size).image { _ in
                snapshot.image.draw(at: .zero)
                let bounds = CGRect(origin: .zero, size: snapshot.image.size)

                for pin in pins {
                    let p = snapshot.point(for: pin.coordinate)
                    guard bounds.insetBy(dx: -20, dy: -20).contains(p) else { continue }
                    let r: CGFloat = 13
                    let rect = CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)
                    let path = UIBezierPath(ovalIn: rect)
                    uiColor(pin.color).setFill()
                    path.fill()
                    UIColor.white.setStroke()
                    path.lineWidth = 3
                    path.stroke()
                }

                // Brand pill bottom-right — the share-funnel signature.
                let label = "ColorHunt" as NSString
                let font = UIFont.systemFont(ofSize: 30, weight: .semibold)
                let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.white]
                let textSize = label.size(withAttributes: attrs)
                let padH: CGFloat = 22
                let pill = CGRect(
                    x: bounds.width - textSize.width - padH * 2 - 26,
                    y: bounds.height - textSize.height - 24 - 26,
                    width: textSize.width + padH * 2,
                    height: textSize.height + 24
                )
                UIColor.black.withAlphaComponent(0.45).setFill()
                UIBezierPath(roundedRect: pill, cornerRadius: pill.height / 2).fill()
                label.draw(at: CGPoint(x: pill.minX + padH, y: pill.minY + 12), withAttributes: attrs)
            }

            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAsset(from: image)
            }) { success, _ in
                DispatchQueue.main.async {
                    savingSnapshot = false
                    guard success else { return }
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) { saveDone = true }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) {
                        withAnimation(.easeOut(duration: 0.3)) { saveDone = false }
                    }
                }
            }
        }
    }

    private func uiColor(_ packed: Int32) -> UIColor {
        let v = UInt32(bitPattern: packed)
        return UIColor(
            red: CGFloat((v >> 16) & 0xFF) / 255,
            green: CGFloat((v >> 8) & 0xFF) / 255,
            blue: CGFloat(v & 0xFF) / 255,
            alpha: 1
        )
    }
}
