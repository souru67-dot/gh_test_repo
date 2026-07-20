import SwiftUI
import SharedColor

/// コラージュ — renders the selected photos with the SAME geometry maths as
/// Android (shared `CollageBridge`/`CollageGeometry`), so both apps produce
/// pixel-compatible layouts. Export via ImageRenderer + share sheet.
struct CollageView: View {
    @EnvironmentObject private var state: AppState

    // Mirrors Android CollageStyle (subset v0; ordinals match shared enums).
    @State private var layoutOrdinal: Int32 = 0      // 0 GRID / 1 VERTICAL / 2 TWO_COLUMN
    @State private var placementOrdinal: Int32 = 1   // 0 NONE / 1 CENTER / 2 SIDE / 3 LEFT / 4 OVERLAY
    @State private var spacing: CGFloat = 4          // dp-equivalent on a 360 canvas
    @State private var aspect: CGFloat = 4.0 / 5.0   // 4:5 default
    @State private var shareImage: UIImage?
    @State private var showShare = false

    var body: some View {
        NavigationStack {
            Group {
                if state.selectedPhotos.isEmpty {
                    emptyState
                } else {
                    editor
                }
            }
            .background(Color(argb: 0xFF101014))
            .navigationTitle("コラージュ")
        }
        .sheet(isPresented: $showShare) {
            if let shareImage {
                ActivityView(items: [shareImage])
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Text("写真が未選択です").font(.headline)
            Text("「ハント」タブで写真を選んでください。")
                .font(.subheadline).foregroundStyle(.secondary)
            Button("ハントへ") { state.selectedTab = .hunt }
                .buttonStyle(.borderedProminent)
                .padding(.top, 8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var editor: some View {
        ScrollView {
            VStack(spacing: 16) {
                canvas(width: 340)
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                controls

                HStack(spacing: 12) {
                    Button {
                        share()
                    } label: {
                        Label("共有", systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    Button {
                        save()
                    } label: {
                        Label("保存", systemImage: "square.and.arrow.down")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .padding()
        }
    }

    private var controls: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("レイアウト").font(.caption.bold()).foregroundStyle(Color(argb: 0xFF9E7CFF))
            Picker("レイアウト", selection: $layoutOrdinal) {
                Text("グリッド").tag(Int32(0))
                Text("縦並び").tag(Int32(1))
                Text("2列").tag(Int32(2))
            }
            .pickerStyle(.segmented)

            Text("カラーパレット").font(.caption.bold()).foregroundStyle(Color(argb: 0xFF9E7CFF))
            Picker("パレット", selection: $placementOrdinal) {
                Text("なし").tag(Int32(0))
                Text("中央").tag(Int32(1))
                Text("重ねる").tag(Int32(4))
                Text("左").tag(Int32(3))
                Text("右").tag(Int32(2))
            }
            .pickerStyle(.segmented)

            Text("SNSサイズ").font(.caption.bold()).foregroundStyle(Color(argb: 0xFF9E7CFF))
            Picker("サイズ", selection: $aspect) {
                Text("1:1").tag(CGFloat(1))
                Text("4:5").tag(CGFloat(4.0 / 5.0))
                Text("9:16").tag(CGFloat(9.0 / 16.0))
            }
            .pickerStyle(.segmented)

            HStack {
                Text("余白").font(.caption.bold()).foregroundStyle(Color(argb: 0xFF9E7CFF))
                Slider(value: $spacing, in: 0...24)
            }
        }
        .padding(16)
        .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 20))
    }

    // MARK: rendering (shared geometry)

    /// Draws the collage at [width] pts; the same view renders the on-screen
    /// preview and, at export scale, the shared image.
    private func canvas(width: CGFloat) -> some View {
        let photos = state.selectedPhotos
        let height = width / aspect
        let layout = CollageBridge.shared.compute(
            cellCount: Int32(photos.count),
            layoutOrdinal: layoutOrdinal,
            placementOrdinal: placementOrdinal,
            spacingFrac: Float(spacing / 360.0),
            width: Float(width),
            height: Float(height),
            overlayHorizontal: false,
            overlayPosFrac: 0.5,
            overlayWidthFrac: 0.16,
        )
        let cells = (layout.cells as? [CollageGeometryRect]) ?? []

        return ZStack(alignment: .topLeading) {
            Color(argb: 0xFF0E0E12)

            ForEach(Array(photos.enumerated()), id: \.element.id) { index, photo in
                if index < cells.count {
                    let r = cells[index]
                    Image(uiImage: photo.image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: CGFloat(r.width), height: CGFloat(r.height))
                        .clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .offset(x: CGFloat(r.left), y: CGFloat(r.top))
                }
            }

            if let rail = layout.palette {
                paletteRail(rail, photos: photos, translucent: placementOrdinal == 4)
            }
        }
        .frame(width: width, height: height)
    }

    private func paletteRail(_ rect: CollageGeometryRect, photos: [HuntPhoto], translucent: Bool) -> some View {
        let colors = photos.compactMap { $0.dominantColor }
        let blockH = CGFloat(rect.height) / CGFloat(max(colors.count, 1))
        return VStack(spacing: 0) {
            ForEach(Array(colors.enumerated()), id: \.offset) { _, c in
                ZStack {
                    Color(packed: c).opacity(translucent ? 0.59 : 1.0)
                    Text(hexString(c))
                        .font(.system(size: max(blockH * 0.13, 7), design: .monospaced))
                        .foregroundStyle(.white.opacity(0.9))
                        .shadow(color: translucent ? .black.opacity(0.5) : .clear, radius: 2)
                }
                .frame(height: blockH)
            }
        }
        .frame(width: CGFloat(rect.width), height: CGFloat(rect.height))
        .offset(x: CGFloat(rect.left), y: CGFloat(rect.top))
    }

    // MARK: export

    @MainActor
    private func render() -> UIImage? {
        let renderer = ImageRenderer(content: canvas(width: 1080).environmentObject(state))
        renderer.scale = 1
        return renderer.uiImage
    }

    @MainActor private func share() {
        // Share assist parity: hashtag caption on the pasteboard, paste-and-go.
        UIPasteboard.general.string = "ColorHuntで色あつめ 🎨📸 #カラーハント #色集め #組写 #colorhunt"
        shareImage = render()
        showShare = shareImage != nil
    }

    @MainActor private func save() {
        guard let image = render() else { return }
        UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
    }
}

/// UIKit share sheet wrapper.
struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
