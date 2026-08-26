import SwiftUI
import PhotosUI
import Photos
import SharedColor

/// ハント — pick photos, auto-sort by dominant colour (shared KMP classifier),
/// tap to select for the collage, long-press (context menu) to re-file a colour.
struct HuntView: View {
    @EnvironmentObject private var state: AppState
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var showGrid = false
    @State private var showClearConfirm = false
    @State private var showDeselectConfirm = false
    @State private var recolorTarget: HuntPhoto?

    private let columns = [GridItem(.adaptive(minimum: 104), spacing: 8)]

    /// Read once per body evaluation; the picker below needs it to decide
    /// whether it may bind to the shared library.
    private var libraryAuthorized: Bool {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        return status == .authorized || status == .limited
    }

    private var pickPhotosLabel: some View {
        Label("写真を選ぶ", systemImage: "photo.on.rectangle.angled")
            .font(.callout.bold())
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(.white, in: Capsule())
            .foregroundStyle(Color(argb: 0xFF7C4DFF))
    }

    /// Buckets that currently have photos, in shared display order.
    private var availableFilters: [String] { state.groupedByBucket.map { $0.key } }
    /// Groups to show, honouring the active filter.
    private var visibleGroups: [(key: String, photos: [HuntPhoto])] {
        guard let f = state.huntFilter else { return state.groupedByBucket }
        return state.groupedByBucket.filter { $0.key == f }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                // Each bucket = a full-width header followed by its own grid, mirroring
                // Android's SortScreen (a spanning SectionHeader then adaptive tiles).
                // Sections inside a single LazyVGrid overflow in SwiftUI, so we nest
                // one LazyVGrid per bucket inside a LazyVStack instead.
                LazyVStack(alignment: .leading, spacing: 8) {
                    heroHeader

                    if !availableFilters.isEmpty {
                        filterRow
                    }

                    if state.huntFilter == nil, !state.photos.isEmpty {
                        colorCollectionCard
                    }

                    if !state.photos.isEmpty {
                        Text("色は自動判定です。イメージと違うときは写真を長押しで変更できます。")
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.6))
                            .padding(.top, 2)
                    }

                    ForEach(visibleGroups, id: \.key) { group in
                        bucketHeader(group.key, count: group.photos.count)
                        LazyVGrid(columns: columns, spacing: 8) {
                            ForEach(group.photos) { photo in thumb(photo) }
                        }
                    }

                    if state.huntFilter == nil, !state.unanalysed.isEmpty {
                        Text("解析中…")
                            .font(.subheadline.bold())
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, 8)
                        LazyVGrid(columns: columns, spacing: 8) {
                            ForEach(state.unanalysed) { photo in thumb(photo) }
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 96)
            }
            .background(Color(argb: 0xFF101014))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .overlay(alignment: .bottom) { makeCollageBar }
            // Auto-sort is the one action whose result is invisible: photos land
            // inside colour buckets further down the page, and duplicates land
            // nowhere at all. The toast says what actually happened.
            .overlay(alignment: .top) {
                if let summary = state.importSummary {
                    SaveToast(message: summary.message,
                              icon: "wand.and.stars",
                              tint: Color(argb: 0xFF9E7CFF))
                        .padding(.top, 8)
                }
            }
            .animation(.spring(response: 0.35, dampingFraction: 0.85), value: state.importSummary)
            .onChange(of: pickerItems) { items in
                Task { await load(items) }
            }
        }
        .sheet(item: $recolorTarget) { photo in
            RecolorSheet(photo: photo) { picked in
                state.recolor(photo.id, to: picked)
            }
            .preferredColorScheme(.dark)
        }
        .fullScreenCover(isPresented: $showGrid) {
            GridPreviewView()
                .environmentObject(state)
                .overlay(alignment: .topTrailing) {
                    Button { showGrid = false } label: {
                        Image(systemName: "xmark")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.white)
                            .padding(12)
                            .background(.black.opacity(0.4), in: Circle())
                    }
                    .accessibilityLabel("閉じる")
                    .padding(.top, 8).padding(.trailing, 12)
                }
        }
    }

    // MARK: pieces

    private var heroHeader: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("ColorHunt")
                        .font(.system(.largeTitle, design: .rounded).weight(.heavy))
                        .foregroundStyle(.white)
                    Text("好きな色を、集めよう。")
                        .font(.subheadline).foregroundStyle(.white.opacity(0.9))
                }
                Spacer()
                HStack(spacing: 8) {
                    Button {
                        showGrid = true
                    } label: {
                        Image(systemName: "square.grid.3x3")
                            .padding(10)
                            .background(.white.opacity(0.18), in: Circle())
                            .foregroundStyle(.white)
                    }
                    .accessibilityLabel("グリッド")
                    if !state.photos.isEmpty {
                        Button(role: .destructive) {
                            showClearConfirm = true
                        } label: {
                            Image(systemName: "trash")
                                .padding(10)
                                .background(.white.opacity(0.18), in: Circle())
                                .foregroundStyle(.white)
                        }
                        .accessibilityLabel("すべて削除")
                        // Confirm before wiping — it fired instantly, and beta
                        // testers assumed it also deletes from the photo library.
                        .confirmationDialog("すべての写真を削除しますか？",
                                            isPresented: $showClearConfirm,
                                            titleVisibility: .visible) {
                            Button("すべて削除", role: .destructive) {
                                state.clearAll()
                                state.huntFilter = nil
                            }
                            Button("キャンセル", role: .cancel) {}
                        } message: {
                            Text("ColorHuntの一覧から消えるだけです。iPhoneの「写真」アプリの写真はそのまま残ります。")
                        }
                    }
                }
            }

            if !state.photos.isEmpty {
                HStack(spacing: 8) {
                    statPill("\(state.photos.count)", "枚")
                    statPill("\(availableFilters.count)", "色")
                }
            }

            HStack(spacing: 10) {
                // Bound to the shared library when we are allowed to, because
                // that is the only way the picker returns asset identifiers —
                // and those are what stop a photo from landing twice once
                // auto-sort has already brought it in. Without permission the
                // picker still works out-of-process, just without identifiers.
                if libraryAuthorized {
                    PhotosPicker(selection: $pickerItems, maxSelectionCount: 50,
                                 matching: .images, photoLibrary: .shared()) {
                        pickPhotosLabel
                    }
                } else {
                    PhotosPicker(selection: $pickerItems, maxSelectionCount: 50,
                                 matching: .images) {
                        pickPhotosLabel
                    }
                }
                Button {
                    state.importRecentLibraryPhotos()
                } label: {
                    Label("自動で仕分け", systemImage: "sparkles")
                        .font(.callout.bold())
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(.white.opacity(0.14), in: Capsule())
                        .overlay(Capsule().stroke(.white.opacity(0.6), lineWidth: 1))
                        .foregroundStyle(.white)
                }
            }
            HStack(spacing: 6) {
                if state.importing {
                    ProgressView().tint(.white).scaleEffect(0.8)
                }
                Text("自動仕分けは直近200枚を読み込みます")
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.75))
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(
                colors: [Color(argb: 0xFF7C4DFF), Color(argb: 0xFFEC407A), Color(argb: 0xFFFFA726)],
                startPoint: .topLeading, endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 22)
        )
    }

    private func statPill(_ value: String, _ label: String) -> some View {
        HStack(spacing: 4) {
            Text(value).font(.subheadline.bold()).foregroundStyle(.white)
            Text(LocalizedStringKey(label)).font(.caption).foregroundStyle(.white.opacity(0.85))
        }
        .padding(.horizontal, 12).padding(.vertical, 6)
        .background(.white.opacity(0.16), in: RoundedCornerStyle.pill)
        // "12" and "枚" are two Texts; combined they read as one "12 photos".
        .accessibilityElement(children: .combine)
    }

    private var filterRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                filterChip(nil, "すべて", active: state.huntFilter == nil)
                ForEach(availableFilters, id: \.self) { key in
                    filterChip(key, bucketLabel(key), active: state.huntFilter == key)
                }
            }
            .padding(.vertical, 4)
            .padding(.horizontal, 1)
        }
    }

    private func filterChip(_ key: String?, _ label: String, active: Bool) -> some View {
        HStack(spacing: 6) {
            if let key {
                Circle()
                    .fill(Color(packed: Int32(truncatingIfNeeded: ColorBridge.shared.swatchOf(key: key))))
                    .frame(width: 12, height: 12)
                    .overlay(Circle().stroke(.white.opacity(0.4), lineWidth: 0.5))
            }
            Text(LocalizedStringKey(label)).font(.subheadline.weight(.medium))
        }
        .padding(.horizontal, 14).padding(.vertical, 8)
        .foregroundStyle(.white)
        .background(active ? Color(argb: 0xFF7C4DFF).opacity(0.35) : .white.opacity(0.06), in: Capsule())
        .overlay(Capsule().stroke(active ? Color(argb: 0xFF7C4DFF) : .white.opacity(0.15), lineWidth: 1))
        .onTapGesture {
            withAnimation(.easeInOut(duration: 0.15)) {
                state.huntFilter = (state.huntFilter == key ? nil : key)
            }
        }
        // onTapGesture carries no button trait of its own, so VoiceOver would
        // announce a plain label with no hint that it can be activated.
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(active ? [.isButton, .isSelected] : .isButton)
    }

    /// カラーコレクション — the 12-bucket collection tracker that replaced the old
    /// hue wheel. Collected colours fill in as vivid swatches (tap one to filter);
    /// missing ones stay as dashed slots. Filling the board is the game loop that
    /// keeps hunters opening the app.
    private var colorCollectionCard: some View {
        let allKeys = ColorBridge.shared.bucketKeys()
        let collected = Set(availableFilters)
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                Text("カラーコレクション")
                    .font(.system(.subheadline, design: .rounded).bold())
                    .foregroundStyle(.white)
                Spacer()
                HStack(alignment: .firstTextBaseline, spacing: 2) {
                    Text("\(collected.count)")
                        .font(.system(.title3, design: .rounded).weight(.heavy))
                        .foregroundStyle(Brand.gradient)
                    Text("/ \(allKeys.count)")
                        .font(.system(.footnote, design: .rounded).bold())
                        .foregroundStyle(.white.opacity(0.6))
                }
            }

            HStack(spacing: 6) {
                ForEach(allKeys, id: \.self) { key in
                    collectionDot(key, collected: collected.contains(key))
                }
            }

            Text(collected.count >= allKeys.count
                 ? "全色コンプリート！🎉"
                 : "全\(allKeys.count)色コンプリートを目指そう")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.55))
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 20))
    }

    private func collectionDot(_ key: String, collected: Bool) -> some View {
        Group {
            if collected {
                Circle()
                    .fill(Color(packed: Int32(truncatingIfNeeded: ColorBridge.shared.swatchOf(key: key))))
                    .overlay(Circle().stroke(.white.opacity(0.6), lineWidth: 1))
                    .transition(.scale.combined(with: .opacity))
                    .onTapGesture {
                        withAnimation(.easeInOut(duration: 0.15)) {
                            state.huntFilter = key
                        }
                    }
            } else {
                Circle()
                    .strokeBorder(style: StrokeStyle(lineWidth: 1.2, dash: [3, 3]))
                    .foregroundStyle(.white.opacity(0.25))
            }
        }
        .frame(maxWidth: .infinity)
        .aspectRatio(1, contentMode: .fit)
        .animation(.spring(response: 0.4, dampingFraction: 0.6), value: collected)
        // Twelve bare circles carry no text at all. bucketLabel returns the
        // already-localised name, so Text(String) (verbatim) is what we want —
        // LocalizedStringKey would look the translation up a second time.
        .accessibilityElement()
        .accessibilityLabel(Text(bucketLabel(key)))
        .accessibilityValue(collected ? Text("収集済み") : Text("未収集"))
        .accessibilityAddTraits(collected ? .isButton : [])
    }

    private func bucketHeader(_ key: String, count: Int) -> some View {
        HStack(spacing: 8) {
            Circle()
                .fill(Color(packed: Int32(truncatingIfNeeded: ColorBridge.shared.swatchOf(key: key))))
                .frame(width: 16, height: 16)
            Text(bucketLabel(key)).font(.headline)
            Text("\(count)")
                .font(.caption.bold())
                .padding(.horizontal, 8).padding(.vertical, 2)
                .background(.white.opacity(0.12), in: Capsule())
            Spacer()
        }
        .padding(.top, 10)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private func thumb(_ photo: HuntPhoto) -> some View {
        let selected = state.selection.contains(photo.id)
        // Square tile that never overflows its grid cell: a clear 1:1 spacer sets
        // the cell size, the image fills it via overlay, then we clip. (scaledToFill
        // + aspectRatio(.fill) directly on the Image bleeds past the cell.)
        return Color.clear
            .aspectRatio(1, contentMode: .fit)
            .overlay {
                Image(uiImage: photo.thumb)
                    .resizable()
                    .scaledToFill()
            }
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(alignment: .bottomLeading) {
                if let c = photo.dominantColor {
                    HStack(spacing: 4) {
                        Circle().fill(Color(packed: c)).frame(width: 10, height: 10)
                        Text(hexString(c)).font(.system(size: 9, design: .monospaced))
                    }
                    .padding(.horizontal, 6).padding(.vertical, 3)
                    .background(.black.opacity(0.45), in: Capsule())
                    .padding(5)
                }
            }
            .overlay {
                RoundedRectangle(cornerRadius: 14)
                    .stroke(selected ? Color(argb: 0xFF7C4DFF) : .white.opacity(0.1),
                            lineWidth: selected ? 3 : 1)
            }
            .onTapGesture { state.toggleSelection(photo.id) }
            // One element per tile. The image carries no label of its own, and the
            // HEX chip on its own ("#E8442A") says nothing about which photo this
            // is — the colour name is what the user hunted by.
            .accessibilityElement()
            .accessibilityLabel(photo.bucketKey.map { Text(bucketLabel($0)) } ?? Text("解析中…"))
            .accessibilityValue(photo.dominantColor.map { Text(hexString($0)) } ?? Text(verbatim: ""))
            .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
            .contextMenu {
                Button {
                    recolorTarget = photo
                } label: {
                    Label("写真から色を選ぶ…", systemImage: "eyedropper")
                }
                Divider()
                // Quick re-file: the canned swatch becomes the photo's colour
                // too, so the HEX chip and the palette agree with the bucket
                // (they used to keep the old extracted colour).
                ForEach(ColorBridge.shared.bucketKeys(), id: \.self) { key in
                    Button {
                        state.recolor(photo.id,
                                      to: Int32(truncatingIfNeeded: ColorBridge.shared.swatchOf(key: key)),
                                      bucket: key)
                    } label: {
                        Label(bucketLabel(key),
                              systemImage: photo.bucketKey == key ? "checkmark.circle" : "circle")
                    }
                }
            }
    }

    private var makeCollageBar: some View {
        Group {
            if !state.selection.isEmpty {
                HStack(spacing: 10) {
                    Button {
                        withAnimation(.easeInOut(duration: 0.25)) { state.selectedTab = .collage }
                    } label: {
                        Label("コラージュを作成 (\(state.selection.count))", systemImage: "checkmark.circle.fill")
                            .font(.system(.callout, design: .rounded).bold())
                            .padding(.horizontal, 24).padding(.vertical, 15)
                            .background(Brand.gradient, in: Capsule())
                            .foregroundStyle(.white)
                            .shadow(color: Brand.purple.opacity(0.55), radius: 16, y: 6)
                    }
                    .buttonStyle(PopButtonStyle())
                    // Testers asked how to start over without un-ticking each
                    // photo one by one — this releases the whole selection.
                    Button {
                        showDeselectConfirm = true
                    } label: {
                        Image(systemName: "xmark")
                            .font(.callout.weight(.bold))
                            .padding(15)
                            .background(.white.opacity(0.16), in: Circle())
                            .overlay(Circle().stroke(.white.opacity(0.22), lineWidth: 1))
                            .foregroundStyle(.white)
                    }
                    .buttonStyle(PopButtonStyle())
                    .accessibilityLabel("選択をすべて解除")
                    .confirmationDialog("選択をすべて解除しますか？",
                                        isPresented: $showDeselectConfirm,
                                        titleVisibility: .visible) {
                        Button("全解除", role: .destructive) {
                            withAnimation(.easeInOut(duration: 0.25)) { state.clearSelection() }
                        }
                        Button("キャンセル", role: .cancel) {}
                    } message: {
                        Text("写真は一覧に残ります。タップすればまた選べます。")
                    }
                }
                // Clear of the raised camera button, which pokes above the tab
                // bar right where this CTA sits.
                .padding(.bottom, 34)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private func load(_ items: [PhotosPickerItem]) async {
        // One at a time, all the way in. The picker allows 50 originals, and
        // holding them together was hundreds of megabytes that existed only to
        // be handed over in one call. 2400px is what the store keeps anyway.
        for item in items {
            guard let data = try? await item.loadTransferable(type: Data.self),
                  let image = downsampled(data: data, maxSide: 2400) else { continue }
            // Non-nil only for the in-process picker (see libraryAuthorized).
            // It is what lets a photo already brought in by auto-sort be
            // recognised here — the two paths decode different pixels, so a
            // pixel hash can never match across them.
            await state.addOne(image: image, assetID: item.itemIdentifier)
        }
        pickerItems = []
    }
}

/// Capsule shape shorthand used by the hero buttons.
enum RoundedCornerStyle {
    static let pill = Capsule()
}

/// 色の選び直し — drag on the photo itself to pick the colour you meant.
///
/// The extractor guesses the subject, but only the photographer knows which
/// colour the shot was *about* — so the override points at the photo, not at
/// an abstract wheel. Applying updates `dominantColor` (the printed HEX) and
/// derives the bucket from it, so chip, palette and filing always agree.
/// Lives in this file because the Xcode project is not in the repo — a new
/// .swift file would need a manual project edit on the Mac.
private struct RecolorSheet: View {
    let photo: HuntPhoto
    let onApply: (Int32) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var picked: Int32

    init(photo: HuntPhoto, onApply: @escaping (Int32) -> Void) {
        self.photo = photo
        self.onApply = onApply
        _picked = State(initialValue: photo.dominantColor ?? Int32(truncatingIfNeeded: 0xFF808080))
    }

    private var derivedBucket: String {
        ColorBridge.shared.classifyKey(colorInt: picked)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("写真をなぞって、見せたい色を選べます。")
                    .font(.subheadline).foregroundStyle(.secondary)

                samplableImage

                HStack(spacing: 10) {
                    Circle()
                        .fill(Color(packed: picked))
                        .overlay(Circle().stroke(.white.opacity(0.35), lineWidth: 1))
                        .frame(width: 34, height: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(hexString(picked))
                            .font(.system(.body, design: .monospaced).bold())
                        Text(bucketLabel(derivedBucket))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    ColorPicker("細かく調整", selection: pickerBinding, supportsOpacity: false)
                        .labelsHidden()
                        .accessibilityLabel("細かく調整")
                }
                .padding(.horizontal, 4)

                // The 12 canned swatches, for a fast "just file it as 赤".
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 40), spacing: 10)], spacing: 10) {
                    ForEach(ColorBridge.shared.bucketKeys(), id: \.self) { key in
                        let swatch = Int32(truncatingIfNeeded: ColorBridge.shared.swatchOf(key: key))
                        Button {
                            picked = swatch
                        } label: {
                            Circle()
                                .fill(Color(packed: swatch))
                                .overlay(Circle().stroke(
                                    picked == swatch ? Color.white : .white.opacity(0.25),
                                    lineWidth: picked == swatch ? 2.5 : 1))
                                .frame(width: 40, height: 40)
                        }
                        .accessibilityLabel(Text(bucketLabel(key)))
                    }
                }

                Button {
                    onApply(picked)
                    dismiss()
                } label: {
                    Text("この色にする")
                        .font(.system(.callout, design: .rounded).bold())
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Brand.gradient, in: Capsule())
                        .foregroundStyle(.white)
                }
                .buttonStyle(PopButtonStyle())
            }
            .padding()
            .background(Color(argb: 0xFF101014))
            .navigationTitle("色を選び直す")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("キャンセル") { dismiss() }
                }
            }
        }
        .presentationDetents([.large])
    }

    /// The photo with a drag-to-sample gesture. The image is `scaledToFit`, so
    /// the touch point has to be mapped through the fitted rect before it can
    /// index a pixel — touches on the letterbox clamp to the nearest edge.
    private var samplableImage: some View {
        GeometryReader { geo in
            let fitted = fittedRect(for: photo.thumb.size, in: geo.size)
            Image(uiImage: photo.thumb)
                .resizable()
                .scaledToFit()
                .frame(width: geo.size.width, height: geo.size.height)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { g in
                            let nx = min(max((g.location.x - fitted.minX) / fitted.width, 0), 1)
                            let ny = min(max((g.location.y - fitted.minY) / fitted.height, 0), 1)
                            if let c = pixelColor(of: photo.thumb, atNormalized: CGPoint(x: nx, y: ny)) {
                                picked = c
                            }
                        }
                )
        }
        .frame(maxHeight: 340)
    }

    private func fittedRect(for image: CGSize, in container: CGSize) -> CGRect {
        guard image.width > 0, image.height > 0,
              container.width > 0, container.height > 0 else { return .zero }
        let scale = min(container.width / image.width, container.height / image.height)
        let size = CGSize(width: image.width * scale, height: image.height * scale)
        return CGRect(x: (container.width - size.width) / 2,
                      y: (container.height - size.height) / 2,
                      width: size.width, height: size.height)
    }

    private var pickerBinding: Binding<Color> {
        Binding(
            get: { Color(packed: picked) },
            set: { newValue in
                var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
                UIColor(newValue).getRed(&r, green: &g, blue: &b, alpha: &a)
                let clamp = { (v: CGFloat) in Int(min(max(v, 0), 1) * 255) }
                let packed = (0xFF << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b)
                picked = Int32(truncatingIfNeeded: packed)
            }
        )
    }
}

/// Reads one pixel of [image] at a normalized (0…1, top-left origin) point by
/// drawing that single source pixel into a 1×1 RGBA8 context we own — the same
/// "repaint into a known format" trick as DominantColor.rgbaBuffer, so it works
/// for any source colour space or bit depth.
private func pixelColor(of image: UIImage, atNormalized p: CGPoint) -> Int32? {
    guard let cg = image.cgImage else { return nil }
    let w = cg.width, h = cg.height
    guard w > 0, h > 0 else { return nil }
    let x = min(max(Int(p.x * CGFloat(w)), 0), w - 1)
    let y = min(max(Int(p.y * CGFloat(h)), 0), h - 1)
    var pixel = [UInt8](repeating: 0, count: 4)
    let ok = pixel.withUnsafeMutableBytes { raw -> Bool in
        guard let ctx = CGContext(
            data: raw.baseAddress,
            width: 1, height: 1,
            bitsPerComponent: 8, bytesPerRow: 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return false }
        ctx.interpolationQuality = .none
        // CGContext is bottom-left origin: shift the image so the wanted pixel
        // (top-left coords) lands on the context's single cell.
        ctx.draw(cg, in: CGRect(x: -CGFloat(x),
                                y: CGFloat(y) - CGFloat(h) + 1,
                                width: CGFloat(w), height: CGFloat(h)))
        return true
    }
    guard ok else { return nil }
    let packed = (0xFF << 24) | (Int(pixel[0]) << 16) | (Int(pixel[1]) << 8) | Int(pixel[2])
    return Int32(truncatingIfNeeded: packed)
}

