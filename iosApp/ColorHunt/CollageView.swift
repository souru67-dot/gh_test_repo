import SwiftUI
import UniformTypeIdentifiers
import SharedColor

/// コラージュ — renders the selected photos with the SAME geometry maths as
/// Android (shared `CollageBridge`/`CollageGeometry`), so both apps produce
/// pixel-compatible layouts. Export via ImageRenderer + share sheet.
struct CollageView: View {
    @EnvironmentObject private var state: AppState

    // Mirrors Android CollageStyle (subset; ordinals match shared enums).
    @State private var layoutOrdinal: Int32 = 0      // 0 GRID / 1 VERTICAL / 2 TWO_COLUMN
    @State private var placementOrdinal: Int32 = 1   // 0 NONE / 1 CENTER / 2 SIDE / 3 LEFT / 4 OVERLAY
    @State private var spacing: CGFloat = 4          // dp-equivalent on a 360 canvas
    @State private var cornerRadius: CGFloat = 6
    @State private var borderWidth: CGFloat = 0
    @State private var borderColor: Int64 = 0xFFFFFFFF
    @State private var aspect: CGFloat = 4.0 / 5.0   // 4:5 default
    @State private var background: Int64 = 0xFF0E0E12
    @State private var bgFollowsTheme: Bool = true
    @State private var hexOverlay: Bool = false
    // OVERLAY palette band tuning.
    @State private var overlayHorizontal: Bool = false
    @State private var overlayPosFrac: CGFloat = 0.5
    @State private var overlayWidthFrac: CGFloat = 0.16
    // Per-cell crop windows, keyed by photo id (parity with Android's focals map).
    @State private var focals: [UUID: CellFocal] = [:]
    @State private var editTarget: EditTarget?
    // Cell being drag-reordered in the preview (long-press to pick up).
    @State private var dragCell: Int?
    @State private var shareImage: UIImage?
    @State private var showShare = false
    @State private var showPaywall = false

    private static let swatches: [Int64] = [
        0xFFFFFFFF, 0xFF000000, 0xFFF5F5F5, 0xFF212121,
        0xFF7C4DFF, 0xFF26C6DA, 0xFFEC407A, 0xFFFFC107,
    ]

    private var effectiveBackground: Int64 { bgFollowsTheme ? 0xFF0E0E12 : background }

    var body: some View {
        NavigationStack {
            Group {
                if state.orderedSelectedPhotos.isEmpty {
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
        .sheet(item: $editTarget) { target in
            if let photo = state.photos.first(where: { $0.id == target.photoID }) {
                CropEditorView(
                    image: photo.image,
                    cellRatio: target.ratio,
                    initial: focals[target.photoID] ?? CellFocal()
                ) { newFocal in
                    focals[target.photoID] = newFocal
                    editTarget = nil
                } onCancel: {
                    editTarget = nil
                }
            }
        }
        .sheet(isPresented: $showPaywall) {
            PaywallView(
                product: state.proProduct,
                onPurchase: { Task { await state.purchasePro(); if state.isPro { showPaywall = false } } },
                onRestore: { Task { await state.restorePro(); if state.isPro { showPaywall = false } } },
                onDebugUnlock: { state.debugUnlockPro(); showPaywall = false }
            )
        }
    }

    private var proBanner: some View {
        Button {
            showPaywall = true
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "crown.fill").foregroundStyle(Color(argb: 0xFFFFC107))
                Text("Proにアップグレード（透かしを削除）")
                    .font(.callout.weight(.semibold))
                    .foregroundStyle(.white)
                Spacer()
                Image(systemName: "chevron.right").font(.caption).foregroundStyle(.white.opacity(0.6))
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(Color(argb: 0xFF7C4DFF).opacity(0.18), in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
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

                previewHint

                if !state.isPro {
                    proBanner
                }

                controls

                HStack(spacing: 12) {
                    Button {
                        share()
                    } label: {
                        Label("共有", systemImage: "square.and.arrow.up")
                            .font(.callout.weight(.semibold))
                            .frame(maxWidth: .infinity).padding(.vertical, 13)
                            .foregroundStyle(.white)
                            .background(.white.opacity(0.10), in: Capsule())
                            .overlay(Capsule().stroke(.white.opacity(0.2), lineWidth: 1))
                    }
                    .buttonStyle(PopButtonStyle())
                    Button {
                        save()
                    } label: {
                        Label("保存", systemImage: "square.and.arrow.down")
                            .font(.system(.callout, design: .rounded).bold())
                            .frame(maxWidth: .infinity).padding(.vertical, 13)
                            .foregroundStyle(.white)
                            .background(Brand.gradient, in: Capsule())
                            .shadow(color: Brand.purple.opacity(0.5), radius: 12, y: 4)
                    }
                    .buttonStyle(PopButtonStyle())
                }
            }
            .padding()
        }
    }

    // Split into sub-sections to stay under SwiftUI's 10-view ViewBuilder limit.
    private var controls: some View {
        VStack(alignment: .leading, spacing: 14) {
            templateSection
            layoutSection
            paletteSection
            styleSection
        }
        .padding(16)
        .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 20))
    }

    private var templateSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel("テンプレート")
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(CollageTemplateVM.all) { tpl in
                        Button { apply(tpl) } label: {
                            Text(LocalizedStringKey(tpl.label))
                                .font(.callout)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(.white.opacity(0.10), in: Capsule())
                                .overlay(Capsule().stroke(.white.opacity(0.14), lineWidth: 1))
                                .foregroundStyle(.white)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 1)
            }
        }
    }

    private var layoutSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel("レイアウト")
            Picker("レイアウト", selection: $layoutOrdinal) {
                Text("グリッド").tag(Int32(0))
                Text("縦並び").tag(Int32(1))
                Text("2列").tag(Int32(2))
            }
            .pickerStyle(.segmented)
        }
    }

    private var paletteSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionLabel("カラーパレット")
            Picker("パレット", selection: $placementOrdinal) {
                Text("なし").tag(Int32(0))
                Text("中央").tag(Int32(1))
                Text("重ねる").tag(Int32(4))
                Text("左").tag(Int32(3))
                Text("右").tag(Int32(2))
            }
            .pickerStyle(.segmented)

            // OVERLAY (重ねる) fine-tuning: orientation, position, band width.
            if placementOrdinal == 4 {
                Picker("帯の向き", selection: $overlayHorizontal) {
                    Text("縦の帯").tag(false)
                    Text("横の帯").tag(true)
                }
                .pickerStyle(.segmented)
                sliderRow("帯の位置", value: $overlayPosFrac, range: 0...1)
                sliderRow("帯の幅", value: $overlayWidthFrac, range: 0.08...0.5)
            }

            Toggle(isOn: $hexOverlay) {
                Text("HEXチップを写真に重ねる").font(.callout)
            }
            .tint(Color(argb: 0xFF7C4DFF))

            Button { state.sortCollageByHue() } label: {
                Label("色相で自動整列", systemImage: "sparkles")
                    .font(.callout.weight(.medium))
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .foregroundStyle(.white)
                    .background(.white.opacity(0.10), in: Capsule())
                    .overlay(Capsule().stroke(.white.opacity(0.18), lineWidth: 1))
            }
            .buttonStyle(.plain)
        }
    }

    private var styleSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Divider().overlay(Color.white.opacity(0.08))
            sectionLabel("SNSサイズ")
            Picker("サイズ", selection: $aspect) {
                Text("1:1").tag(CGFloat(1))
                Text("4:5").tag(CGFloat(4.0 / 5.0))
                Text("9:16").tag(CGFloat(9.0 / 16.0))
                Text("16:9").tag(CGFloat(16.0 / 9.0))
            }
            .pickerStyle(.segmented)

            sliderRow("余白", value: $spacing, range: 0...24)
            sliderRow("角丸", value: $cornerRadius, range: 0...48)
            sliderRow("枠線の太さ", value: $borderWidth, range: 0...8)

            sectionLabel("枠線の色")
            swatchRow(selected: borderColor) { borderColor = $0 }

            HStack {
                sectionLabel("背景色")
                Spacer()
                Text("テーマ色を使う").font(.caption).foregroundStyle(.white.opacity(0.8))
                Toggle("", isOn: $bgFollowsTheme).labelsHidden().tint(Color(argb: 0xFF7C4DFF))
            }
            if !bgFollowsTheme {
                swatchRow(selected: background) { background = $0 }
            }
        }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(LocalizedStringKey(text)).font(.caption.bold()).foregroundStyle(Color(argb: 0xFF9E7CFF))
    }

    /// One-line hint under the preview explaining the direct gestures.
    private var previewHint: some View {
        HStack(spacing: 6) {
            Image(systemName: "hand.tap").font(.caption2)
            Text("タップでトリミング・長押しで並べ替え")
                .font(.caption2)
        }
        .foregroundStyle(.white.opacity(0.6))
        .frame(maxWidth: .infinity)
    }

    /// A labelled slider with a live integer read-out (Android's StyleSlider look).
    private func sliderRow(_ label: String, value: Binding<CGFloat>,
                           range: ClosedRange<CGFloat>) -> some View {
        VStack(spacing: 2) {
            HStack {
                Text(LocalizedStringKey(label)).font(.callout)
                Spacer()
                Text("\(Int((value.wrappedValue * (range.upperBound <= 1 ? 100 : 1)).rounded()))")
                    .font(.caption).foregroundStyle(.white.opacity(0.7))
            }
            Slider(value: value, in: range).tint(Color(argb: 0xFF7C4DFF))
        }
    }

    /// A row of preset colour swatches (border / background), Android's SwatchRow.
    private func swatchRow(selected: Int64, onSelect: @escaping (Int64) -> Void) -> some View {
        HStack(spacing: 8) {
            ForEach(Self.swatches, id: \.self) { c in
                Circle()
                    .fill(Color(argb: c))
                    .frame(width: 30, height: 30)
                    .overlay(
                        Circle().stroke(
                            selected == c ? Color(argb: 0xFF7C4DFF) : .white.opacity(0.25),
                            lineWidth: selected == c ? 3 : 1
                        )
                    )
                    .onTapGesture { onSelect(c) }
            }
        }
    }

    // MARK: rendering (shared geometry)

    /// Draws the collage at [width] pts; the same view renders the on-screen
    /// preview and, at export scale, the shared image. Consumes the flat float
    /// layout from the shared module (no nested Kotlin types to bridge).
    private func canvas(width: CGFloat) -> some View {
        let photos = state.orderedSelectedPhotos
        let height = width / aspect
        let flat = CollageBridge.shared.computeFlat(
            cellCount: Int32(photos.count),
            layoutOrdinal: layoutOrdinal,
            placementOrdinal: placementOrdinal,
            spacingFrac: Float(spacing / 360.0),
            width: Float(width),
            height: Float(height),
            overlayHorizontal: overlayHorizontal,
            overlayPosFrac: Float(overlayPosFrac),
            overlayWidthFrac: Float(overlayWidthFrac)
        )
        let layout = decodeLayout(flat)

        // Scale the tappable/editable state to whatever width we render at, so the
        // 340pt preview and the 1080px export share one code path (WYSIWYG).
        let interactive = width < 400
        let scale = width / 360.0

        return ZStack(alignment: .topLeading) {
            Color(argb: effectiveBackground)

            ForEach(Array(photos.enumerated()), id: \.element.id) { index, photo in
                if index < layout.cells.count {
                    let r = layout.cells[index]
                    let focal = focals[photo.id] ?? CellFocal()
                    cellView(photo.image, cell: r, focal: focal,
                             corner: cornerRadius * scale, border: borderWidth * scale)
                        .overlay(alignment: .bottomLeading) {
                            if hexOverlay, let c = photo.dominantColor {
                                hexChip(c, cellWidth: r.width)
                            }
                        }
                        .frame(width: r.width, height: r.height)
                        .offset(x: r.minX, y: r.minY)
                        .opacity(dragCell == index ? 0.35 : 1)
                        .contentShape(Rectangle())
                        .modifier(CellGestures(
                            enabled: interactive,
                            index: index,
                            dragCell: $dragCell,
                            order: $state.collageOrder,
                            onCrop: {
                                editTarget = EditTarget(index: index, photoID: photo.id, ratio: r.width / r.height)
                            }
                        ))
                }
            }

            if let rail = layout.palette {
                paletteRail(rail, photos: photos, translucent: placementOrdinal == 4)
            }
        }
        .frame(width: width, height: height)
        .overlay(alignment: .bottomTrailing) {
            if !state.isPro {
                watermarkPill(scale: scale).padding(10 * scale)
            }
        }
    }

    /// Free-tier brand watermark — a rainbow-dot pill bottom-right, echoing
    /// Android's drawWatermark. Rendered into the export too (WYSIWYG); Pro hides it.
    private func watermarkPill(scale: CGFloat) -> some View {
        HStack(spacing: 5 * scale) {
            Circle()
                .fill(AngularGradient(
                    colors: [.red, .yellow, .green, .blue, .purple, .red],
                    center: .center
                ))
                .frame(width: 7 * scale, height: 7 * scale)
            Text("ColorHunt")
                .font(.system(size: 11 * scale, weight: .medium))
                .foregroundStyle(.white)
        }
        .padding(.horizontal, 8 * scale)
        .padding(.vertical, 5 * scale)
        .background(.black.opacity(0.43), in: Capsule())
    }

    /// One collage cell: fill-crop positioned by [focal] (zoom shrinks the window),
    /// matching Android's CollageRenderer.drawCenterCropped so preview and export —
    /// and the crop editor — all agree pixel-for-pixel.
    private func cellView(_ image: UIImage, cell: CGRect, focal: CellFocal,
                          corner: CGFloat, border: CGFloat) -> some View {
        let m = cropMetrics(imageSize: image.size, boxW: cell.width, boxH: cell.height, focal: focal)
        return Color.clear
            .frame(width: cell.width, height: cell.height)
            .overlay {
                Image(uiImage: image)
                    .resizable()
                    .frame(width: m.dispW, height: m.dispH)
                    .offset(x: m.offsetX, y: m.offsetY)
            }
            .clipShape(RoundedRectangle(cornerRadius: corner))
            .overlay {
                if border > 0 {
                    RoundedRectangle(cornerRadius: corner)
                        .stroke(Color(argb: borderColor), lineWidth: border)
                }
            }
    }

    /// A small dot + HEX pill on the cell's bottom-left (Pro "HEX overlay" look).
    private func hexChip(_ packed: Int32, cellWidth: CGFloat) -> some View {
        let fs = min(max(cellWidth * 0.075, 6), 11)
        return HStack(spacing: fs * 0.4) {
            Circle().fill(Color(packed: packed))
                .overlay(Circle().stroke(.white.opacity(0.85), lineWidth: 0.5))
                .frame(width: fs * 0.95, height: fs * 0.95)
            Text(hexString(packed))
                .font(.system(size: fs, design: .monospaced))
                .foregroundStyle(.white)
        }
        .padding(.horizontal, fs * 0.5)
        .padding(.vertical, fs * 0.32)
        .background(.black.opacity(0.45), in: Capsule())
        .padding(fs * 0.5)
    }

    private func paletteRail(_ rect: CGRect, photos: [HuntPhoto], translucent: Bool) -> some View {
        let colors = photos.compactMap { $0.dominantColor }
        let n = max(colors.count, 1)
        let blockH = rect.height / CGFloat(n)
        // Match Android CollageRenderer.drawPalette: a quiet, even-spaced serif-mono
        // hex, sized to the block but never wider than the rail — a single centred
        // line (the old size scaled with block height and wrapped when tall).
        let fontSize = min(blockH * 0.14, rect.width * 0.16)
        return VStack(spacing: 0) {
            ForEach(Array(colors.enumerated()), id: \.offset) { idx, c in
                ZStack {
                    Color(packed: c).opacity(translucent ? 0.59 : 1.0)
                    Text(hexString(c))
                        .font(.system(size: fontSize, weight: .regular, design: .monospaced))
                        .tracking(fontSize * 0.04)
                        .foregroundStyle(paletteTextColor(c, translucent: translucent))
                        .lineLimit(1)
                        .minimumScaleFactor(0.4)
                        .shadow(color: translucent ? .black.opacity(0.5) : .clear, radius: 2, y: 1)
                        .frame(width: rect.width * 0.86)
                }
                .frame(height: blockH)
                .overlay(alignment: .top) {
                    if idx > 0 {
                        Rectangle()
                            .fill(.black.opacity(translucent ? 0.07 : 0.12))
                            .frame(height: 1)
                    }
                }
            }
        }
        .frame(width: rect.width, height: rect.height)
        .offset(x: rect.minX, y: rect.minY)
    }

    /// Mirrors Android's luminance rule: near-black ink on light swatches, off-white
    /// on dark ones (always off-white when the column floats over the photos).
    private func paletteTextColor(_ packed: Int32, translucent: Bool) -> Color {
        let v = Int(UInt32(bitPattern: packed))
        let r = Double((v >> 16) & 0xFF), g = Double((v >> 8) & 0xFF), b = Double(v & 0xFF)
        let luminance = 0.299 * r + 0.587 * g + 0.114 * b
        if !translucent && luminance > 135 {
            return Color(red: 25 / 255, green: 25 / 255, blue: 32 / 255).opacity(0.8)
        }
        return Color(red: 240 / 255, green: 240 / 255, blue: 244 / 255).opacity(translucent ? 0.92 : 0.8)
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

    // MARK: templates & crop maths

    /// Applies a one-tap magazine preset — rewrites style + size only; photo order
    /// and per-cell crop focals stay untouched (parity with CollageTemplates).
    private func apply(_ tpl: CollageTemplateVM) {
        layoutOrdinal = tpl.layout
        placementOrdinal = tpl.placement
        spacing = tpl.spacing
        cornerRadius = tpl.corner
        borderWidth = 0
        aspect = tpl.aspect
        background = tpl.background
        bgFollowsTheme = false
        hexOverlay = tpl.hexOverlay
    }

    /// The single fill-crop calculation shared by [cellView] and the crop editor.
    /// Matches Android's drawCenterCropped: fill the box preserving aspect, zoom by
    /// scale, then offset by the focal point (0.5/0.5 = centred).
    private func cropMetrics(imageSize: CGSize, boxW: CGFloat, boxH: CGFloat, focal: CellFocal)
        -> (dispW: CGFloat, dispH: CGFloat, offsetX: CGFloat, offsetY: CGFloat) {
        let boxRatio = boxW / max(boxH, 1)
        let imgRatio = imageSize.width / max(imageSize.height, 1)
        let baseW: CGFloat
        let baseH: CGFloat
        if imgRatio > boxRatio {
            baseH = boxH
            baseW = boxH * imgRatio
        } else {
            baseW = boxW
            baseH = boxW / imgRatio
        }
        let dispW = baseW * focal.scale
        let dispH = baseH * focal.scale
        let overX = max(dispW - boxW, 0)
        let overY = max(dispH - boxH, 0)
        let ox = (0.5 - focal.x) * overX
        let oy = (0.5 - focal.y) * overY
        return (dispW, dispH, ox, oy)
    }
}

/// A cell's crop window — normalised focal point (0..1, 0.5 = centre) + zoom.
/// Mirrors the shared `FocalPoint`, kept as plain Swift so no nested Kotlin type
/// has to cross the ObjC bridge.
struct CellFocal: Equatable {
    var x: CGFloat = 0.5
    var y: CGFloat = 0.5
    var scale: CGFloat = 1
    static let maxScale: CGFloat = 4
}

/// Identifies which cell the crop editor is editing.
struct EditTarget: Identifiable {
    let id = UUID()
    let index: Int
    let photoID: UUID
    let ratio: CGFloat
}

/// One-tap magazine presets, mirroring the shared `CollageTemplates`.
struct CollageTemplateVM: Identifiable {
    let id: String
    let label: String
    let aspect: CGFloat
    let layout: Int32       // 0 GRID / 1 VERTICAL / 2 TWO_COLUMN
    let placement: Int32    // 0 NONE / 1 CENTER / 2 SIDE / 3 LEFT / 4 OVERLAY
    let spacing: CGFloat
    let corner: CGFloat
    let background: Int64
    let hexOverlay: Bool

    static let all: [CollageTemplateVM] = [
        .init(id: "white", label: "ホワイト", aspect: 4.0 / 5.0, layout: 0, placement: 0,
              spacing: 14, corner: 0, background: 0xFFFAF8F4, hexOverlay: false),
        .init(id: "film", label: "フィルム", aspect: 4.0 / 5.0, layout: 1, placement: 0,
              spacing: 10, corner: 0, background: 0xFF121212, hexOverlay: true),
        .init(id: "kumisha", label: "組写", aspect: 4.0 / 5.0, layout: 2, placement: 1,
              spacing: 4, corner: 4, background: 0xFF0E0E12, hexOverlay: false),
        .init(id: "magazine", label: "マガジン", aspect: 4.0 / 5.0, layout: 0, placement: 3,
              spacing: 16, corner: 2, background: 0xFFF2EDE3, hexOverlay: false),
        .init(id: "seamless", label: "シームレス", aspect: 9.0 / 16.0, layout: 2, placement: 4,
              spacing: 0, corner: 0, background: 0xFF000000, hexOverlay: false),
    ]
}

/// Dedicated crop editor: the cell frame at its real aspect ratio; drag to pan and
/// pinch to zoom, WYSIWYG with the renderer's crop maths. Commits only on 完了.
struct CropEditorView: View {
    let image: UIImage
    let cellRatio: CGFloat
    let initial: CellFocal
    let onConfirm: (CellFocal) -> Void
    let onCancel: () -> Void

    @State private var focal: CellFocal
    @State private var lastPan: CGSize = .zero
    @State private var lastMag: CGFloat = 1

    init(image: UIImage, cellRatio: CGFloat, initial: CellFocal,
         onConfirm: @escaping (CellFocal) -> Void, onCancel: @escaping () -> Void) {
        self.image = image
        self.cellRatio = cellRatio
        self.initial = initial
        self.onConfirm = onConfirm
        self.onCancel = onCancel
        _focal = State(initialValue: initial)
    }

    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 4) {
                Text("トリミング").font(.headline)
                Text("ドラッグで移動・ピンチで拡大")
                    .font(.caption).foregroundStyle(.secondary)
            }
            .padding(.top, 20)

            GeometryReader { geo in
                // Fit the cell's real aspect ratio inside the fixed editor area,
                // portrait or landscape.
                let boxW = min(geo.size.width, geo.size.height * cellRatio)
                let boxH = boxW / cellRatio
                let m = metrics(boxW: boxW, boxH: boxH)
                ZStack {
                    Color.black
                    Image(uiImage: image)
                        .resizable()
                        .frame(width: m.dispW, height: m.dispH)
                        .offset(x: m.offsetX, y: m.offsetY)
                }
                .frame(width: boxW, height: boxH)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .contentShape(Rectangle())
                .gesture(
                    DragGesture()
                        .onChanged { value in
                            let dx = value.translation.width - lastPan.width
                            let dy = value.translation.height - lastPan.height
                            lastPan = value.translation
                            transform(panX: dx, panY: dy, zoom: 1, boxW: boxW, boxH: boxH)
                        }
                        .onEnded { _ in lastPan = .zero }
                )
                .simultaneousGesture(
                    MagnificationGesture()
                        .onChanged { value in
                            let zoom = value / lastMag
                            lastMag = value
                            transform(panX: 0, panY: 0, zoom: zoom, boxW: boxW, boxH: boxH)
                        }
                        .onEnded { _ in lastMag = 1 }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .frame(height: 380)
            .padding(.horizontal, 16)

            HStack {
                Button("リセット") { focal = CellFocal() }
                    .buttonStyle(.bordered)
                Spacer()
                Button("キャンセル") { onCancel() }
                    .buttonStyle(.bordered)
                Button("完了") { onConfirm(focal) }
                    .buttonStyle(.borderedProminent)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 20)
        }
        .presentationDetents([.medium, .large])
    }

    private func metrics(boxW: CGFloat, boxH: CGFloat)
        -> (dispW: CGFloat, dispH: CGFloat, offsetX: CGFloat, offsetY: CGFloat) {
        let imgRatio = image.size.width / max(image.size.height, 1)
        let baseW: CGFloat
        let baseH: CGFloat
        if imgRatio > cellRatio {
            baseH = boxH
            baseW = boxH * imgRatio
        } else {
            baseW = boxW
            baseH = boxW / imgRatio
        }
        let dispW = baseW * focal.scale
        let dispH = baseH * focal.scale
        let overX = max(dispW - boxW, 0)
        let overY = max(dispH - boxH, 0)
        return (dispW, dispH, (0.5 - focal.x) * overX, (0.5 - focal.y) * overY)
    }

    /// Same recomputation as Android's detectTransformGestures handler: fold the
    /// pan delta and zoom factor into the focal point + scale, clamped to bounds.
    private func transform(panX: CGFloat, panY: CGFloat, zoom: CGFloat, boxW: CGFloat, boxH: CGFloat) {
        let imgRatio = image.size.width / max(image.size.height, 1)
        let baseW: CGFloat
        let baseH: CGFloat
        if imgRatio > cellRatio {
            baseH = boxH
            baseW = boxH * imgRatio
        } else {
            baseW = boxW
            baseH = boxW / imgRatio
        }
        let curScale = focal.scale
        let curOverX = max(baseW * curScale - boxW, 0)
        let curOverY = max(baseH * curScale - boxH, 0)
        let newScale = min(max(curScale * zoom, 1), CellFocal.maxScale)
        let nOverX = max(baseW * newScale - boxW, 0)
        let nOverY = max(baseH * newScale - boxH, 0)
        let curOx = (0.5 - focal.x) * curOverX
        let curOy = (0.5 - focal.y) * curOverY
        let nOx = min(max(curOx + panX, -nOverX / 2), nOverX / 2)
        let nOy = min(max(curOy + panY, -nOverY / 2), nOverY / 2)
        focal.x = nOverX > 0 ? 0.5 - nOx / nOverX : 0.5
        focal.y = nOverY > 0 ? 0.5 - nOy / nOverY : 0.5
        focal.scale = newScale
    }
}

/// Pro upgrade sheet — StoreKit 2 purchase + restore, with a DEBUG-only manual
/// unlock so the flow is testable before the App Store Connect product exists.
struct PaywallView: View {
    let product: Product?
    let onPurchase: () -> Void
    let onRestore: () -> Void
    let onDebugUnlock: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "crown.fill")
                .font(.system(size: 44))
                .foregroundStyle(Color(argb: 0xFFFFC107))
                .padding(.top, 28)
            Text("ColorHunt Pro").font(.title2.bold())

            VStack(alignment: .leading, spacing: 10) {
                benefit("透かしなしで書き出し")
                benefit("すべてのテンプレート・パレット配置")
                benefit("今後のPro機能もすべて")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 8)

            Button {
                onPurchase()
            } label: {
                HStack(spacing: 4) {
                    Text("購入する")
                    if let product { Text(product.displayPrice) }
                }
                .font(.callout.bold())
                .frame(maxWidth: .infinity).padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .disabled(product == nil)

            Button("購入を復元") { onRestore() }
                .font(.subheadline)

            if product == nil {
                Text("※ 製品情報を読み込めませんでした（App Store Connect 設定後に有効）")
                    .font(.caption2).foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            #if DEBUG
            Button("デバッグ解除") { onDebugUnlock() }
                .font(.caption).foregroundStyle(.secondary)
            #endif

            Spacer()
        }
        .padding(.horizontal, 24)
        .presentationDetents([.medium, .large])
        .overlay(alignment: .topTrailing) {
            Button { dismiss() } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.title2).foregroundStyle(.secondary)
            }
            .padding()
        }
    }

    private func benefit(_ text: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "checkmark.circle.fill").foregroundStyle(Color(argb: 0xFF7C4DFF))
            Text(LocalizedStringKey(text)).font(.callout)
        }
    }
}

/// Direct per-cell gestures: tap to crop, long-press-drag to reorder. Only active
/// in the interactive preview (disabled for the export render).
private struct CellGestures: ViewModifier {
    let enabled: Bool
    let index: Int
    @Binding var dragCell: Int?
    let order: Binding<[UUID]>
    let onCrop: @MainActor () -> Void

    @ViewBuilder
    func body(content: Content) -> some View {
        if enabled {
            content
                .onTapGesture { onCrop() }
                .onDrag {
                    dragCell = index
                    return NSItemProvider(object: String(index) as NSString)
                }
                .onDrop(
                    of: [.text],
                    delegate: ReorderDropDelegate(item: index, items: order, current: $dragCell)
                )
        } else {
            content
        }
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
