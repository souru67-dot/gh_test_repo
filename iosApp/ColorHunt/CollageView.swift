import SwiftUI
import StoreKit
import Photos
import SharedColor

/// コラージュ — renders the selected photos with the SAME geometry maths as
/// Android (shared `CollageBridge`/`CollageGeometry`), so both apps produce
/// pixel-compatible layouts. Export via ImageRenderer + share sheet.
struct CollageView: View {
    @EnvironmentObject private var state: AppState

    // Mirrors Android CollageStyle (subset; ordinals match shared enums).
    @State private var layoutOrdinal: Int32 = 0      // 0 GRID / 1 VERTICAL / 2 TWO_COLUMN
    // 0 NONE / 1 CENTER / 2 SIDE / 3 LEFT / 4 OVERLAY — these match the shared
    // PalettePlacement enum. 5 (下帯) continues the sequence but is laid out on
    // this side; see canvas(width:).
    @State private var placementOrdinal: Int32 = 1
    @State private var spacing: CGFloat = 4          // dp-equivalent on a 360 canvas
    @State private var cornerRadius: CGFloat = 6
    @State private var borderWidth: CGFloat = 0
    @State private var borderColor: Int64 = 0xFFFFFFFF
    @State private var aspect: CGFloat = 4.0 / 5.0   // 4:5 default
    @State private var background: Int64 = 0xFF0E0E12
    @State private var bgFollowsTheme: Bool = true
    @State private var hexOverlay: Bool = false
    /// Retro film-camera date stamp on the collage corner (setlog/dazz vibe).
    @State private var dateStamp: Bool = false
    /// The OVERLAY palette band is vertical-only (see paletteSection).
    private let overlayHorizontal = false
    // Template browsing: active category filter + the last applied preset.
    @State private var templateCategory: TemplateCategory?
    @State private var appliedTemplateID: String?
    // OVERLAY palette band tuning.
    @State private var overlayPosFrac: CGFloat = 0.5
    @State private var overlayWidthFrac: CGFloat = 0.16
    // Per-cell crop windows, keyed by photo id (parity with Android's focals map).
    @State private var focals: [UUID: CellFocal] = [:]
    @State private var editTarget: EditTarget?
    // Long-press drag reorder state: picked-up cell and current drop target.
    @State private var dragCell: Int?
    @State private var dragTarget: Int?
    @State private var shareImage: UIImage?
    @State private var showShare = false
    @State private var showPaywall = false
    @State private var saving = false
    @State private var saveDone = false

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
            .overlay(alignment: .top) {
                if saveDone {
                    SaveToast()
                }
            }
            .onAppear { applyPendingTemplate() }
            .onChange(of: state.pendingCollageTemplateID) { _ in
                applyPendingTemplate()
            }
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
                // sheet(item:) reuses its content view on iOS 16, which froze the
                // editor on the first-ever photo; keying by photo forces a fresh
                // editor (image + focal state) per cell.
                .id(target.photoID)
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
        VStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(Brand.gradient)
                    .frame(width: 84, height: 84)
                    .opacity(0.25)
                    .blur(radius: 8)
                Image(systemName: "square.grid.2x2")
                    .font(.system(size: 36, weight: .semibold))
                    .foregroundStyle(Brand.gradient)
            }
            Text("写真が未選択です")
                .font(.system(.title3, design: .rounded).bold())
            Text("「ハント」タブで写真を選んでください。")
                .font(.subheadline).foregroundStyle(.secondary)
            Button {
                withAnimation(.easeInOut(duration: 0.25)) { state.selectedTab = .hunt }
            } label: {
                Label("ハントへ", systemImage: "camera.viewfinder")
                    .font(.system(.callout, design: .rounded).bold())
                    .padding(.horizontal, 26).padding(.vertical, 13)
                    .foregroundStyle(.white)
                    .background(Brand.gradient, in: Capsule())
                    .shadow(color: Brand.purple.opacity(0.5), radius: 12, y: 4)
            }
            .buttonStyle(PopButtonStyle())
            .padding(.top, 6)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var editor: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Cap the preview height so tall ratios (9:16) shrink in width and
                // stay in line with the other sizes instead of filling the screen.
                canvas(width: min(340, 440 * aspect))
                    // The export has square outer corners, so rounding only
                    // the preview made the 角丸 control look broken (it never
                    // moved the sheet's corners) and rounded the photos
                    // themselves whenever 余白 was 0. A hairline instead marks
                    // where the sheet ends — which the eye needs for dark
                    // presets like ハーフ against the dark screen — and it is
                    // outside canvas(), so it never reaches the export.
                    .overlay {
                        Rectangle().stroke(.white.opacity(0.14), lineWidth: 1)
                    }
                    .overlay(alignment: .topLeading) {
                        // Try-then-buy: Pro presets preview freely; this badge
                        // says why save/share will ask for Pro.
                        if proTemplateLock {
                            HStack(spacing: 4) {
                                Image(systemName: "crown.fill")
                                    .font(.system(size: 9, weight: .bold))
                                Text(verbatim: "PRO")
                                    .font(.system(size: 10, weight: .heavy, design: .rounded))
                            }
                            .foregroundStyle(.white)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(Color(argb: 0xFF7C4DFF).opacity(0.92), in: Capsule())
                            .padding(8)
                            .allowsHitTesting(false)
                        }
                    }
                    .frame(maxWidth: .infinity)

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
                        Group {
                            if saving {
                                ProgressView().tint(.white)
                            } else {
                                Label("保存", systemImage: "square.and.arrow.down")
                                    .font(.system(.callout, design: .rounded).bold())
                            }
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 13)
                        .foregroundStyle(.white)
                        .background(Brand.gradient, in: Capsule())
                        .shadow(color: Brand.purple.opacity(0.5), radius: 12, y: 4)
                    }
                    .buttonStyle(PopButtonStyle())
                    .disabled(saving)
                }
            }
            .padding()
        }
    }

    // Split into sub-sections to stay under SwiftUI's 10-view ViewBuilder limit.
    private var controls: some View {
        VStack(alignment: .leading, spacing: 14) {
            templateSection
            // ハーフ and チェキ ARE their arrangement — a half-frame is two
            // frames on one negative and an instax is one print — so the
            // layout picker would only offer ways to break them.
            if !formatLocked {
                layoutSection
            }
            paletteSection
            styleSection
        }
        .padding(16)
        .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 20))
    }

    private var templateSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel("テンプレート", icon: "wand.and.stars")
            // With 11 presets a single flat row stopped scanning well — filter
            // chips group them by vibe, and the active preset gets a ring.
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    categoryChip(nil, label: "すべて")
                    ForEach(TemplateCategory.allCases, id: \.self) { c in
                        categoryChip(c, label: c.rawValue)
                    }
                }
                .padding(.horizontal, 1)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(filteredTemplates) { tpl in
                        Button { applyAnimated(tpl) } label: {
                            templateCard(tpl, selected: appliedTemplateID == tpl.id)
                        }
                        .buttonStyle(PopButtonStyle())
                    }
                }
                .padding(.horizontal, 1)
                .padding(.vertical, 2)
            }
        }
    }

    private var filteredTemplates: [CollageTemplateVM] {
        guard let c = templateCategory else { return CollageTemplateVM.all }
        return CollageTemplateVM.all.filter { $0.category == c }
    }

    private func categoryChip(_ category: TemplateCategory?, label: String) -> some View {
        let active = templateCategory == category
        return Button {
            withAnimation(.easeInOut(duration: 0.2)) { templateCategory = category }
        } label: {
            Text(LocalizedStringKey(label))
                .font(.caption.weight(.semibold))
                .foregroundStyle(active ? Color.black : .white.opacity(0.85))
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(
                    active ? AnyShapeStyle(Color.white) : AnyShapeStyle(Color.white.opacity(0.10)),
                    in: Capsule()
                )
        }
        .buttonStyle(.plain)
    }

    /// A mini visual mock of the template — its background colour with a tiny
    /// layout glyph — so the row reads at a glance instead of as text chips.
    private func templateCard(_ tpl: CollageTemplateVM, selected: Bool) -> some View {
        let bg = Color(argb: tpl.background)
        // Legible glyph ink for light vs dark template backgrounds.
        let v = UInt32(bitPattern: Int32(truncatingIfNeeded: tpl.background))
        let lum = 0.299 * Double((v >> 16) & 0xFF) + 0.587 * Double((v >> 8) & 0xFF) + 0.114 * Double(v & 0xFF)
        let ink = lum > 135 ? Color.black.opacity(0.5) : Color.white.opacity(0.7)

        return VStack(spacing: 6) {
            templateGlyph(tpl, ink: ink)
                .frame(width: 58, height: 72)
                .background(bg, in: RoundedRectangle(cornerRadius: 10))
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(
                            selected ? Brand.accent : .white.opacity(0.18),
                            lineWidth: selected ? 2.5 : 1
                        )
                )
                .overlay(alignment: .topTrailing) {
                    // Crown = Pro preset (badge disappears once Pro is owned).
                    if tpl.isPro && !state.isPro {
                        Image(systemName: "crown.fill")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundStyle(Color(argb: 0xFFFFC107))
                            .padding(4)
                            .background(.black.opacity(0.55), in: Circle())
                            .padding(3)
                    }
                }
            Text(LocalizedStringKey(tpl.label))
                .font(.caption2.weight(selected ? .bold : .medium))
                .foregroundStyle(selected ? Brand.accent : .white.opacity(0.85))
        }
    }

    /// The tiny cell arrangement inside a template card, mirroring its layout
    /// and palette placement.
    private func templateGlyph(_ tpl: CollageTemplateVM, ink: Color) -> some View {
        let gap: CGFloat = tpl.spacing > 8 ? 4 : (tpl.spacing > 0 ? 2 : 0)
        // ハーフ is a pair, not a grid — show the two upright frames it makes.
        if tpl.id == "half" {
            return AnyView(
                HStack(spacing: 2) {
                    RoundedRectangle(cornerRadius: 2).fill(ink)
                    RoundedRectangle(cornerRadius: 2).fill(ink)
                }
                .padding(.horizontal, 5)
                .padding(.vertical, 16)
            )
        }
        return AnyView(HStack(spacing: gap) {
            if tpl.placement == 3 { // LEFT rail
                Brand.gradient.frame(width: 7).clipShape(RoundedRectangle(cornerRadius: 1.5))
            }
            if tpl.layout == 1 { // VERTICAL
                VStack(spacing: gap) {
                    ForEach(0..<3, id: \.self) { _ in
                        RoundedRectangle(cornerRadius: 2).fill(ink)
                    }
                }
            } else { // GRID / TWO_COLUMN
                VStack(spacing: gap) {
                    RoundedRectangle(cornerRadius: 2).fill(ink)
                    RoundedRectangle(cornerRadius: 2).fill(ink)
                }
                if tpl.placement == 1 { // CENTER palette stripe
                    Brand.gradient.frame(width: 6).clipShape(RoundedRectangle(cornerRadius: 1.5))
                }
                VStack(spacing: gap) {
                    RoundedRectangle(cornerRadius: 2).fill(ink)
                    RoundedRectangle(cornerRadius: 2).fill(ink)
                }
            }
        }
        .padding(gap == 0 ? 0 : 7)
        .overlay {
            if tpl.placement == 4 { // OVERLAY band floats over the photos
                Brand.gradient.opacity(0.75).frame(width: 8)
                    .clipShape(RoundedRectangle(cornerRadius: 1.5))
            }
        })
    }

    /// Template application with a light haptic + soft morph of the preview.
    private func applyAnimated(_ tpl: CollageTemplateVM) {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
            apply(tpl)
        }
    }

    private var layoutSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel("レイアウト", icon: "rectangle.3.group")
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
            sectionLabel("カラーパレット", icon: "paintpalette")
            Picker("パレット", selection: $placementOrdinal) {
                Text("なし").tag(Int32(0))
                // ハーフ and チェキ reproduce a print: their deco already owns the
                // bottom edge, so a band there would be buried under the mat.
                if !formatLocked {
                    Text("下帯").tag(Int32(5))
                }
                Text("中央").tag(Int32(1))
                Text("重ねる").tag(Int32(4))
                Text("左").tag(Int32(3))
                Text("右").tag(Int32(2))
            }
            .pickerStyle(.segmented)

            // OVERLAY (重ねる) fine-tuning: position and band width. The band is
            // always vertical — a horizontal one cut straight through the
            // photos and never produced a usable composition.
            if placementOrdinal == 4 {
                sliderRow("帯の位置", value: $overlayPosFrac, range: 0...1)
                sliderRow("帯の幅", value: $overlayWidthFrac, range: 0.08...0.5)
            }

            Toggle(isOn: $hexOverlay) {
                Text("HEXチップを写真に重ねる").font(.callout)
            }
            .tint(Color(argb: 0xFF7C4DFF))

            // チェキ is the exception: its date belongs to the print's chin and
            // the deco already draws it there. Everything else — ハーフ
            // included, where a date back burns into the frame itself — can
            // turn the quartz stamp on and off.
            if appliedTemplateID != "cheki" {
                Toggle(isOn: $dateStamp) {
                    Text("日付スタンプ").font(.callout)
                }
                .tint(Color(argb: 0xFF7C4DFF))
            }

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
            // チェキ (0.72) and ハーフ (3:2) have no entry here, so the
            // segmented control rendered with nothing selected and any tap
            // silently broke the format. Their sheet size IS the format, so
            // the picker steps aside for them — as the layout picker does.
            if formatLocked {
                sectionLabel("サイズ", icon: "aspectratio")
                Text(formatSizeNote)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.6))
            } else {
                sectionLabel("SNSサイズ", icon: "aspectratio")
                Picker("サイズ", selection: $aspect) {
                    Text("1:1").tag(CGFloat(1))
                    Text("4:5").tag(CGFloat(4.0 / 5.0))
                    Text("9:16").tag(CGFloat(9.0 / 16.0))
                    Text("16:9").tag(CGFloat(16.0 / 9.0))
                }
                .pickerStyle(.segmented)
            }

            // A film print and an instax card have fixed margins, square
            // corners and no drawn border — every one of these controls could
            // only pull the preset away from the format it reproduces, and on
            // ハーフ the 余白 slider fought the fixed rebate bar besides.
            if !formatLocked {
                sliderRow("余白", value: $spacing, range: 0...24)
                sliderRow("角丸", value: $cornerRadius, range: 0...48)
                sliderRow("枠線の太さ", value: $borderWidth, range: 0...8)

                sectionLabel("枠線の色")
                swatchRow(selected: borderColor) { borderColor = $0 }
            }

            // The sheet colour is part of a physical format: ハーフ's rebate
            // bars are drawn film-black, so a light background left the gaps
            // and the bars mismatched, and チェキ's mat covers it entirely.
            if !formatLocked {
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
    }

    private func sectionLabel(_ text: String, icon: String? = nil) -> some View {
        HStack(spacing: 5) {
            if let icon {
                Image(systemName: icon).font(.caption2)
            }
            Text(LocalizedStringKey(text)).font(.caption.bold())
        }
        .foregroundStyle(Brand.accent)
    }

    /// How many cells the applied preset renders, regardless of how many
    /// photos are selected. Int.max = as many as there are photos.
    private var templateCellCap: Int {
        switch appliedTemplateID {
        case "half": return 2
        case "cheki": return 1
        default: return Int.max
        }
    }

    /// True for presets that reproduce a physical format, where the sheet size
    /// and arrangement are the preset rather than settings on top of it.
    private var formatLocked: Bool { templateCellCap != Int.max }

    private var formatSizeNote: LocalizedStringKey {
        appliedTemplateID == "cheki"
            ? LocalizedStringKey("チェキの印画紙サイズで固定されています")
            : LocalizedStringKey("ハーフフレームの1コマ分で固定されています")
    }

    /// One-line hint under the preview explaining the direct gestures, or why
    /// a format-locked preset is showing fewer photos than are selected.
    private var previewHint: some View {
        let cap = templateCellCap
        let capped = cap < state.orderedSelectedPhotos.count
        return HStack(spacing: 6) {
            Image(systemName: capped ? "info.circle" : "hand.tap").font(.caption2)
            if capped {
                Text(cap == 1 ? "チェキは1枚で仕上がります" : "ハーフは2枚で1枚のフィルムになります")
                    .font(.caption2)
            } else {
                Text("タップでトリミング・長押しで並べ替え")
                    .font(.caption2)
            }
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
        // ハーフ is a two-frame negative and チェキ is a one-photo print, so
        // those presets render exactly that many cells however many photos are
        // selected — the format IS the point (previewHint says so on screen).
        let photos = Array(state.orderedSelectedPhotos.prefix(templateCellCap))
        let height = width / aspect
        // 下帯 reserves a strip along the bottom edge, so the photo grid is laid
        // out against a shorter canvas and the band is placed underneath it.
        // The shared module has no FOOTER case: the arithmetic is a band and a
        // shortened height, which both platforms can reproduce exactly, and
        // adding an enum case would mean changing Kotlin that cannot be built
        // or tested from here. See ARCHITECTURE_iOS.md §6.
        let footerH = placementOrdinal == footerPlacement ? height * Self.footerRatio : 0
        let flat = CollageBridge.shared.computeFlat(
            cellCount: Int32(photos.count),
            layoutOrdinal: layoutOrdinal,
            // The band is drawn here, so the shared geometry is asked for a
            // plain grid with no rail eating horizontal space.
            placementOrdinal: placementOrdinal == footerPlacement ? 0 : placementOrdinal,
            spacingFrac: Float(spacing / 360.0),
            width: Float(width),
            height: Float(height - footerH),
            overlayHorizontal: overlayHorizontal,
            overlayPosFrac: Float(overlayPosFrac),
            overlayWidthFrac: Float(overlayWidthFrac)
        )
        var layout = decodeLayout(flat)
        if footerH > 0 {
            layout.palette = CGRect(x: 0, y: height - footerH, width: width, height: footerH)
        }

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
                                    .allowsHitTesting(false)
                            }
                        }
                        .frame(width: r.width, height: r.height)
                        .offset(x: r.minX, y: r.minY)
                        .opacity(dragCell == index ? 0.35 : 1)
                }
            }

            if let rail = layout.palette {
                // Visual only — must never eat the cells' taps/drops (the OVERLAY
                // band floats right on top of the photos).
                paletteRail(rail, photos: photos, translucent: placementOrdinal == 4)
                    .allowsHitTesting(false)
            }

            // Drop-target ring while a long-press drag is in flight.
            if let to = dragTarget, to < layout.cells.count, dragCell != nil {
                let r = layout.cells[to]
                RoundedRectangle(cornerRadius: cornerRadius * scale)
                    .stroke(Brand.accent, lineWidth: 3)
                    .frame(width: r.width, height: r.height)
                    .offset(x: r.minX, y: r.minY)
                    .allowsHitTesting(false)
            }
        }
        .frame(width: width, height: height)
        // Pro presets carry a signature deco layer no manual control can
        // reproduce — the reason the preset is worth paying for even though
        // colours/corners/spacing are free sliders. Shared with the camera's
        // live frame guide so Pro owners shoot inside the same look.
        .overlay {
            TemplateSignatureDeco(templateID: appliedTemplateID, width: width, height: height,
                             scale: scale, matWidth: spacing * scale)
                .allowsHitTesting(false)
        }
        .overlay(alignment: .bottomTrailing) {
            // Retro film-camera date stamp (the setlog/dazz nostalgia cue) —
            // bottom-right, where every quartz camera printed it. The
            // watermark lives bottom-left so the two never collide. 下帯 owns
            // the bottom edge, so both lift clear of the band rather than
            // printing on top of a swatch.
            if dateStamp {
                QuartzDateStamp(scale: scale)
                    .padding(.bottom, footerInset(height))
            }
        }
        .overlay(alignment: .bottomLeading) {
            if !state.isPro {
                watermarkPill(scale: scale)
                    .padding(10 * scale)
                    .padding(.bottom, footerInset(height))
                    .allowsHitTesting(false)
            }
        }
        // Tap-to-crop is hit-tested here at container level, like the reorder
        // gesture below. A per-cell onTapGesture broke on-device: `.offset`
        // moves only the drawing, so every cell's tap area stayed stacked at
        // the canvas origin and the topmost (last) cell swallowed the tap —
        // "only the top-left corner reacts, and it always edits the last photo".
        .simultaneousGesture(tapToCrop(
            cells: layout.cells,
            photos: photos,
            enabled: interactive
        ))
        // Long-press then drag to reorder — a plain container gesture (the system
        // onDrag/onDrop session was unreliable inside this ScrollView). Cell taps
        // still win because a tap never survives the 0.3s hold.
        .simultaneousGesture(reorderGesture(
            cells: layout.cells,
            count: min(photos.count, layout.cells.count),
            enabled: interactive && photos.count > 1
        ))
    }

    /// Opens the crop editor for whichever cell contains the tap point.
    private func tapToCrop(cells: [CGRect], photos: [HuntPhoto], enabled: Bool) -> some Gesture {
        SpatialTapGesture()
            .onEnded { value in
                guard enabled else { return }
                let count = min(photos.count, cells.count)
                guard let i = (0..<count).first(where: { cells[$0].contains(value.location) })
                else { return }
                editTarget = EditTarget(
                    index: i,
                    photoID: photos[i].id,
                    // max() keeps a degenerate cell from producing an infinite
                    // ratio, which collapsed the crop editor to zero height.
                    ratio: cells[i].width / max(cells[i].height, 1)
                )
            }
    }

    private func reorderGesture(cells: [CGRect], count: Int, enabled: Bool) -> some Gesture {
        func cellAt(_ p: CGPoint) -> Int? {
            for i in 0..<count where cells[i].contains(p) { return i }
            return nil
        }
        return LongPressGesture(minimumDuration: 0.3)
            .sequenced(before: DragGesture(minimumDistance: 0))
            .onChanged { value in
                guard enabled else { return }
                if case .second(true, let drag?) = value {
                    if dragCell == nil {
                        if let picked = cellAt(drag.startLocation) {
                            dragCell = picked
                            dragTarget = picked
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        }
                    }
                    dragTarget = cellAt(drag.location)
                }
            }
            .onEnded { _ in
                defer {
                    dragCell = nil
                    dragTarget = nil
                }
                guard enabled, let from = dragCell, let to = dragTarget, from != to else { return }
                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                    state.moveCollage(from: from, to: to)
                }
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
    }

    /// Free-tier brand watermark — a rainbow-dot pill (bottom-left; the date
    /// stamp owns bottom-right), echoing Android's drawWatermark. Rendered
    /// into the export too (WYSIWYG); Pro hides it.
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
                    // clipShape clips drawing but NOT hit-testing: without this,
                    // the oversized fill image of the topmost cell silently eats
                    // taps/drops meant for every cell underneath it.
                    .allowsHitTesting(false)
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
                .font(.system(size: fs, design: .serif))
                .tracking(fs * 0.05)
                .foregroundStyle(.white)
        }
        .padding(.horizontal, fs * 0.5)
        .padding(.vertical, fs * 0.32)
        .background(.black.opacity(0.45), in: Capsule())
        .padding(fs * 0.5)
    }

    /// Placement ordinal for 下帯. The shared enum stops at 4 (OVERLAY); this one
    /// is drawn on the iOS side, so the number continues the same sequence
    /// without the Kotlin enum needing a new case.
    private var footerPlacement: Int32 { 5 }
    /// Height of the 下帯 band as a fraction of the canvas.
    private static let footerRatio: CGFloat = 0.15

    /// Space the 下帯 occupies along the bottom edge, so corner-anchored marks
    /// (date stamp, watermark) clear it. Zero for every other placement.
    private func footerInset(_ height: CGFloat) -> CGFloat {
        placementOrdinal == footerPlacement ? height * Self.footerRatio : 0
    }

    /// One catch in the colour record.
    private struct PaletteEntry {
        let color: Int32
        /// Bucket key from the shared classifier ("RED", "YELLOW_GREEN"), if the
        /// photo has been analysed. This is the one thing a generic palette tool
        /// cannot print — it only exists because the app classified the hunt.
        let bucket: String?
    }

    /// The colour record — 採集票, not a swatch chart.
    ///
    /// A stack of equal blocks with the hex centred in each is what every colour
    /// tool ships, so this reads the other way: the swatch keeps its full area,
    /// and the type is a left-aligned catalogue entry — classification, then hex
    /// — hung off a ruler tick. Runs down a rail or across the 下帯 band; the
    /// long/short edges swap and nothing else changes.
    private func paletteRail(_ rect: CGRect, photos: [HuntPhoto], translucent: Bool) -> some View {
        let entries = photos.compactMap { p in
            p.dominantColor.map { PaletteEntry(color: $0, bucket: p.bucketKey) }
        }
        let n = max(entries.count, 1)
        // A wider-than-tall rect is the 下帯: blocks run left to right.
        let horizontal = rect.width > rect.height
        let w = horizontal ? rect.width / CGFloat(n) : rect.width
        let h = horizontal ? rect.height : rect.height / CGFloat(n)
        return ZStack(alignment: .topLeading) {
            ForEach(Array(entries.enumerated()), id: \.offset) { idx, entry in
                paletteBlock(entry, w: w, h: h, translucent: translucent,
                             horizontal: horizontal, first: idx == 0)
                    .offset(x: horizontal ? w * CGFloat(idx) : 0,
                            y: horizontal ? 0 : h * CGFloat(idx))
            }
        }
        .frame(width: rect.width, height: rect.height, alignment: .topLeading)
        .offset(x: rect.minX, y: rect.minY)
    }

    private func paletteBlock(_ entry: PaletteEntry, w: CGFloat, h: CGFloat,
                              translucent: Bool, horizontal: Bool, first: Bool) -> some View {
        // Sized off the short edge so a block reads the same whether it is a tall
        // slice of a rail or a wide slice of the band, then capped by the width
        // so a seven-character hex never runs past the block.
        let short = min(w, h)
        let hexSize = min(max(short * 0.15, 5), w * 0.22)
        let tagSize = hexSize * 0.60
        let inset = short * 0.13
        let ink = paletteTextColor(entry.color, translucent: translucent)
        // The entry stacks downwards, so what it needs is height, in both
        // orientations. With many photos the blocks thin out and the record
        // sheds the classification; the hex is the line that never goes.
        let roomForTag = h >= hexSize * 3.2

        return ZStack(alignment: .bottomLeading) {
            Color(packed: entry.color).opacity(translucent ? 0.62 : 1.0)

            VStack(alignment: .leading, spacing: hexSize * 0.14) {
                if roomForTag, let bucket = entry.bucket {
                    Text(bucket.replacingOccurrences(of: "_", with: " "))
                        .font(.system(size: tagSize, weight: .semibold))
                        .tracking(tagSize * 0.20)
                        .foregroundStyle(ink.opacity(0.6))
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                }
                Text(hexString(entry.color))
                    .font(.system(size: hexSize, weight: .regular, design: .serif))
                    .tracking(hexSize * 0.05)
                    .foregroundStyle(ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.4)
            }
            .shadow(color: translucent ? .black.opacity(0.45) : .clear, radius: 2, y: 1)
            .padding(inset)
        }
        .frame(width: w, height: h)
        .overlay(alignment: .topLeading) {
            // A ruler tick, not a full rule: it marks where one catch ends and the
            // next begins without cutting the column into separate bars.
            if !first {
                Rectangle()
                    .fill(ink.opacity(translucent ? 0.22 : 0.32))
                    .frame(width: horizontal ? 1 : w * 0.40,
                           height: horizontal ? h * 0.40 : 1)
            }
        }
    }

    /// Near-black ink on light swatches, off-white on dark ones (always off-white
    /// when the column floats over the photos). Mirrors Android's rule.
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

    /// True when a Pro preset is applied without Pro — preview stays free,
    /// save/share raise the paywall (the conversion moment).
    private var proTemplateLock: Bool {
        guard !state.isPro, let id = appliedTemplateID,
              let tpl = CollageTemplateVM.all.first(where: { $0.id == id }) else { return false }
        return tpl.isPro
    }

    @MainActor
    private func render() -> UIImage? {
        // Pro exports at 2160px (crisper on retina feeds); free at 1080px.
        let width: CGFloat = state.isPro ? 2160 : 1080
        let renderer = ImageRenderer(content: canvas(width: width).environmentObject(state))
        renderer.scale = 1
        return renderer.uiImage
    }

    @MainActor private func share() {
        if proTemplateLock {
            showPaywall = true
            return
        }
        // Share assist parity: hashtag caption on the pasteboard, paste-and-go.
        UIPasteboard.general.string = "ColorHuntで色あつめ 🎨📸 #カラーハント #色集め #組写 #colorhunt"
        shareImage = render()
        showShare = shareImage != nil
    }

    /// Save with real feedback: spinner while writing, success haptic + toast when
    /// the photo actually lands in the library (silent fire-and-forget felt broken).
    @MainActor private func save() {
        if proTemplateLock {
            showPaywall = true
            return
        }
        guard !saving, let image = render() else { return }
        saving = true
        PHPhotoLibrary.shared().performChanges({
            PHAssetChangeRequest.creationRequestForAsset(from: image)
        }) { success, _ in
            DispatchQueue.main.async {
                saving = false
                guard success else { return }
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) { saveDone = true }
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) {
                    withAnimation(.easeOut(duration: 0.3)) { saveDone = false }
                }
            }
        }
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
        dateStamp = tpl.dateStamp
        appliedTemplateID = tpl.id
    }

    /// The camera's frame mode queues a template while handing over its shots;
    /// apply it once here (whenever the collage becomes visible) and clear.
    private func applyPendingTemplate() {
        guard let id = state.pendingCollageTemplateID,
              let tpl = CollageTemplateVM.all.first(where: { $0.id == id }) else { return }
        state.pendingCollageTemplateID = nil
        let shotLayout = state.pendingCollageLayout
        state.pendingCollageLayout = nil
        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) {
            apply(tpl)
            // Keep the shape the camera guide framed, which may differ from
            // the template's own layout.
            if let shotLayout { layoutOrdinal = shotLayout }
        }
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

/// Template browse groups — with 11 presets a flat row stopped scanning well,
/// so cards are filterable by vibe.
enum TemplateCategory: String, CaseIterable {
    case trend = "トレンド"
    case retro = "レトロ"
    case minimal = "ミニマル"
}

/// One-tap magazine presets, mirroring the shared `CollageTemplates`.
struct CollageTemplateVM: Identifiable {
    let id: String
    let label: String
    let category: TemplateCategory
    let aspect: CGFloat
    let layout: Int32       // 0 GRID / 1 VERTICAL / 2 TWO_COLUMN
    let placement: Int32    // 0 NONE / 1 CENTER / 2 SIDE / 3 LEFT / 4 OVERLAY
    let spacing: CGFloat
    let corner: CGFloat
    let background: Int64
    let hexOverlay: Bool
    let dateStamp: Bool
    let isPro: Bool

    // Ordered by current SNS pull. Trend research: the Korean photobooth strip
    // (人生4カット) and instant-camera nostalgia lead 2026 collages; setlog-style
    // casual day-logging ("映え疲れ" backlash) inspires デイログ; photo dumps,
    // soft pastels and bold Y2K colour round out the trend shelf.
    //
    // Pro boundary (value line): the viral hooks stay free — 4カット, the whole
    // camera frame mode and a solid basic shelf — so watermarked exports keep
    // advertising the app. The 5 highest-"映え" presets (デイログ/パステル/Y2K/
    // チェキ/マガジン) are Pro: free users can APPLY them and fall in love in
    // the preview, and the paywall appears at save/share (try-then-buy).
    static let all: [CollageTemplateVM] = [
        // No date by default: a photobooth strip comes out clean, and the
        // quartz imprint belongs to the film presets. The collage's stamp
        // toggle can still add one.
        .init(id: "fourcut", label: "4カット", category: .trend, aspect: 0.36, layout: 1,
              placement: 0, spacing: 12, corner: 0, background: 0xFFFFFFFF,
              hexOverlay: false, dateStamp: false, isPro: false),
        .init(id: "daylog", label: "デイログ", category: .trend, aspect: 4.0 / 5.0, layout: 0,
              placement: 0, spacing: 12, corner: 10, background: 0xFFF6F0E4,
              hexOverlay: false, dateStamp: true, isPro: true),
        .init(id: "pastel", label: "パステル", category: .trend, aspect: 1.0, layout: 0,
              placement: 0, spacing: 12, corner: 16, background: 0xFFFFE9F2,
              hexOverlay: false, dateStamp: false, isPro: true),
        .init(id: "dump", label: "フォトダンプ", category: .trend, aspect: 1.0, layout: 0,
              placement: 0, spacing: 10, corner: 18, background: 0xFF17171C,
              hexOverlay: false, dateStamp: false, isPro: false),
        .init(id: "y2k", label: "Y2K", category: .trend, aspect: 4.0 / 5.0, layout: 2,
              placement: 0, spacing: 12, corner: 20, background: 0xFFFF2D92,
              hexOverlay: true, dateStamp: false, isPro: true),
        // Half-frame cameras expose two upright frames inside one landscape
        // negative, separated by the film rebate — that black bar and the
        // 3:4 pair are the whole signature, exactly as a real print comes back.
        .init(id: "half", label: "ハーフ", category: .retro, aspect: 3.0 / 2.0, layout: 2,
              placement: 0, spacing: 5, corner: 0, background: 0xFF121212,
              hexOverlay: false, dateStamp: true, isPro: true),
        // A real instax print is one photo on a portrait card with the wide
        // chin at the bottom, so this is portrait and best used with 1 photo.
        .init(id: "cheki", label: "チェキ", category: .retro, aspect: 0.72, layout: 0,
              placement: 0, spacing: 20, corner: 1, background: 0xFFFDFBF5,
              hexOverlay: false, dateStamp: false, isPro: true),
        .init(id: "film", label: "フィルム", category: .retro, aspect: 4.0 / 5.0, layout: 1,
              placement: 0, spacing: 10, corner: 0, background: 0xFF121212,
              hexOverlay: true, dateStamp: true, isPro: false),
        .init(id: "magazine", label: "マガジン", category: .retro, aspect: 4.0 / 5.0, layout: 0,
              placement: 3, spacing: 16, corner: 2, background: 0xFFF2EDE3,
              hexOverlay: false, dateStamp: false, isPro: true),
        .init(id: "white", label: "ホワイト", category: .minimal, aspect: 4.0 / 5.0, layout: 0,
              placement: 0, spacing: 14, corner: 0, background: 0xFFFAF8F4,
              hexOverlay: false, dateStamp: false, isPro: false),
        .init(id: "kumisha", label: "組写", category: .minimal, aspect: 4.0 / 5.0, layout: 2,
              placement: 1, spacing: 4, corner: 4, background: 0xFF0E0E12,
              hexOverlay: false, dateStamp: false, isPro: false),
        .init(id: "seamless", label: "シームレス", category: .minimal, aspect: 9.0 / 16.0, layout: 2,
              placement: 4, spacing: 0, corner: 0, background: 0xFF000000,
              hexOverlay: false, dateStamp: false, isPro: true),
    ]

}

/// The date a film camera's quartz date back burns into the corner of the
/// frame: apostrophe-year, month and day in glowing amber-red LED digits
/// ('26 7 26). Shared by the collage canvas and the camera's live guide, so a
/// shot is framed with the stamp already in place.
struct QuartzDateStamp: View {
    /// Canvas scale (width / 360 design units).
    let scale: CGFloat

    var body: some View {
        Text(Self.formatter.string(from: Date()))
            .font(.system(size: 13 * scale, weight: .semibold, design: .monospaced))
            .tracking(1.2 * scale)
            .foregroundStyle(Color(red: 1.0, green: 0.42, blue: 0.20))
            // Two shadows: a tight core bloom and a wide halo, the way the
            // imprint blooms into surrounding grain on real film.
            .shadow(color: Color(red: 1.0, green: 0.30, blue: 0.10).opacity(0.9), radius: 3 * scale)
            .shadow(color: Color(red: 1.0, green: 0.55, blue: 0.25).opacity(0.55), radius: 8 * scale)
            .padding(14 * scale)
            .allowsHitTesting(false)
    }

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        // '' is an escaped literal apostrophe; no leading zeros, like the back
        // of a date-printing compact.
        f.dateFormat = "''yy M d"
        return f
    }()
}

// MARK: - Template signature decos

/// A preset's exclusive decorative layer — the half-frame film strip, and for
/// the Pro presets a journal sticker, sticker sprinkles, a chrome frame, an
/// instax mat, a magazine masthead. These exist ONLY in this renderer: no
/// slider/swatch combination can produce them, so a hand-built replica of a
/// preset stays visibly "not the preset". Shared by the collage canvas and the
/// camera's live frame guide, so shots are framed inside the finished look.
struct TemplateSignatureDeco: View {
    let templateID: String?
    let width: CGFloat
    let height: CGFloat
    /// Canvas scale (width / 360 design units).
    let scale: CGFloat
    /// チェキ's mat thickness source: the collage passes its live spacing,
    /// the camera passes the template's own spacing.
    let matWidth: CGFloat

    var body: some View {
        switch templateID ?? "" {
        case "half": halfFrame
        case "daylog": daylog
        case "pastel": pastel
        case "y2k": y2k
        case "cheki": cheki
        case "magazine": magazine
        default: EmptyView()
        }
    }

    /// ハーフ: exactly what comes back from the lab — two 3:4 frames sharing
    /// one landscape print, parted by the film rebate. That rebate is notably
    /// wider than the print's own edge, which is the tell that says "half
    /// frame"; no sprockets and no lettering, because a real print has none.
    private var halfFrame: some View {
        let rebate = Color(red: 0.07, green: 0.07, blue: 0.07)
        return ZStack {
            // The gap between the two frames, widened past the layout spacing.
            // Measured off a real print, the rebate runs ~3.5% of the sheet —
            // several times the outer edge, which is what makes the pair read
            // as one negative.
            Rectangle()
                .fill(rebate)
                .frame(width: 13 * scale)
            // The print's own thin edge.
            Rectangle()
                .strokeBorder(rebate, lineWidth: 2 * scale)
        }
    }

    /// デイログ: a tilted journal sticker with today's date — the setlog cue.
    private var daylog: some View {
        VStack(alignment: .leading, spacing: 2 * scale) {
            Text(verbatim: "DAY LOG")
                .font(.system(size: 9 * scale, weight: .heavy, design: .rounded))
                .tracking(2.2 * scale)
                .foregroundStyle(Color(red: 0.45, green: 0.38, blue: 0.28))
            Text(Self.daylogFormatter.string(from: Date()))
                .font(.system(size: 12.5 * scale, weight: .semibold, design: .rounded))
                .foregroundStyle(Color(red: 0.29, green: 0.25, blue: 0.20))
        }
        .padding(.horizontal, 11 * scale).padding(.vertical, 8 * scale)
        .background(Color.white.opacity(0.94), in: RoundedRectangle(cornerRadius: 8 * scale))
        .shadow(color: .black.opacity(0.14), radius: 3 * scale, y: 1.5 * scale)
        .rotationEffect(.degrees(-3))
        .padding(11 * scale)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    /// パステル: generous sticker sprinkles — two hearts, a sparkle, dots —
    /// sized to read instantly (the first pass was too timid).
    private var pastel: some View {
        ZStack {
            Image(systemName: "heart.fill")
                .font(.system(size: 24 * scale))
                .foregroundStyle(Color(red: 1.0, green: 0.58, blue: 0.73))
                .rotationEffect(.degrees(14))
                .shadow(color: .black.opacity(0.10), radius: 2 * scale, y: 1 * scale)
                .position(x: width - 28 * scale, y: 24 * scale)
            Image(systemName: "heart.fill")
                .font(.system(size: 12 * scale))
                .foregroundStyle(.white.opacity(0.95))
                .rotationEffect(.degrees(-16))
                .position(x: width - 52 * scale, y: 40 * scale)
            Image(systemName: "sparkle")
                .font(.system(size: 17 * scale))
                .foregroundStyle(.white)
                .shadow(color: Color(red: 1.0, green: 0.58, blue: 0.73).opacity(0.6),
                        radius: 3 * scale)
                .position(x: 22 * scale, y: height - 58 * scale)
            Circle()
                .fill(Color.white.opacity(0.92))
                .frame(width: 9 * scale, height: 9 * scale)
                .position(x: 30 * scale, y: 18 * scale)
            Circle()
                .fill(Color(red: 1.0, green: 0.73, blue: 0.83))
                .frame(width: 13 * scale, height: 13 * scale)
                .position(x: width - 46 * scale, y: height - 24 * scale)
        }
    }

    /// Y2K: a bolder chrome gradient frame, three starbursts and a chrome-lit
    /// wordmark (white core, cyan/pink split shadows).
    private var y2k: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 9 * scale)
                .strokeBorder(
                    AngularGradient(
                        colors: [.cyan, .white, Color(argb: 0xFFFF2D92), .white, .cyan],
                        center: .center
                    ),
                    lineWidth: 4.5 * scale
                )
                .padding(4 * scale)
            Text(verbatim: "✦")
                .font(.system(size: 22 * scale))
                .foregroundStyle(.white)
                .shadow(color: .cyan.opacity(0.8), radius: 3 * scale)
                .position(x: 30 * scale, y: 34 * scale)
            Text(verbatim: "✦")
                .font(.system(size: 15 * scale))
                .foregroundStyle(.cyan)
                .position(x: width - 34 * scale, y: height - 48 * scale)
            Text(verbatim: "✦")
                .font(.system(size: 11 * scale))
                .foregroundStyle(.white.opacity(0.9))
                .position(x: 26 * scale, y: height - 30 * scale)
            Text(verbatim: "Y2K")
                .font(.system(size: 13 * scale, weight: .black, design: .monospaced))
                .italic()
                .foregroundStyle(.white)
                .shadow(color: .cyan, radius: 0.5, x: 1.4 * scale, y: 1.4 * scale)
                .shadow(color: Color(argb: 0xFFFF2D92), radius: 0.5,
                        x: -1.4 * scale, y: -1.4 * scale)
                .position(x: width - 34 * scale, y: 22 * scale)
        }
    }

    /// チェキ: a real instax structure — the mat IS the margin (edge-to-edge
    /// white border) plus the signature thicker bottom band, and the
    /// handwritten date sits inside that band, never on the photos.
    private var cheki: some View {
        let mat = max(matWidth, 14 * scale)
        // The chin is deep enough to hold the date clear of the photo; the
        // date is an overlay ON the band, so it can never ride up onto the
        // image no matter the canvas size.
        let bottomBand = max(mat * 2.4, 34 * scale)
        return ZStack(alignment: .bottom) {
            RoundedRectangle(cornerRadius: 2 * scale)
                .strokeBorder(Color.white, lineWidth: mat)
            Rectangle()
                .fill(Color.white)
                .frame(height: bottomBand)
                .overlay(alignment: .trailing) {
                    Text(Self.chekiFormatter.string(from: Date()))
                        .font(.system(size: 9.5 * scale, weight: .medium, design: .serif))
                        .italic()
                        .foregroundStyle(Color(red: 0.48, green: 0.45, blue: 0.40))
                        .padding(.trailing, 15 * scale)
                }
        }
    }

    /// マガジン: a cover-style masthead over a cinematic top scrim, so the
    /// serif type stays legible on any photo.
    private var magazine: some View {
        ZStack(alignment: .topTrailing) {
            LinearGradient(
                colors: [.black.opacity(0.48), .black.opacity(0.18), .clear],
                startPoint: .top, endPoint: .bottom
            )
            .frame(height: height * 0.26)
            .frame(maxHeight: .infinity, alignment: .top)
            VStack(alignment: .trailing, spacing: 2.5 * scale) {
                Text(verbatim: "COLOR HUNT")
                    .font(.system(size: 17 * scale, weight: .black, design: .serif))
                    .foregroundStyle(.white)
                Rectangle()
                    .fill(Color.white.opacity(0.9))
                    .frame(width: 78 * scale, height: 1.3 * scale)
                Text(Self.magazineFormatter.string(from: Date()))
                    .font(.system(size: 8 * scale, weight: .semibold, design: .serif))
                    .tracking(1.8 * scale)
                    .textCase(.uppercase)
                    .foregroundStyle(.white.opacity(0.92))
            }
            .padding(13 * scale)
        }
    }

    /// デイログ sticker date (localized weekday reads naturally per region).
    private static let daylogFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "M/d EEE"
        return f
    }()

    /// チェキ's handwritten-style date.
    private static let chekiFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yy.MM.dd"
        return f
    }()

    /// マガジン issue line, kept English for the editorial look.
    private static let magazineFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "MMMM yyyy"
        return f
    }()
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
        // Edit on a downscaled copy: dragging a 12MP original re-renders the full
        // image every frame and stutters; ~1400px is indistinguishable in the
        // editor and the focal maths is resolution-independent.
        self.image = Self.editorScaled(image)
        self.cellRatio = cellRatio
        self.initial = initial
        self.onConfirm = onConfirm
        self.onCancel = onCancel
        _focal = State(initialValue: initial)
    }

    private static func editorScaled(_ image: UIImage, maxSide: CGFloat = 1400) -> UIImage {
        let longest = max(image.size.width, image.size.height)
        guard longest > maxSide, longest > 0 else { return image }
        let s = maxSide / longest
        let size = CGSize(width: image.size.width * s, height: image.size.height * s)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }

    var body: some View {
        VStack(spacing: 18) {
            // Photo-editor style header: cancel / title / gradient confirm.
            HStack {
                Button {
                    onCancel()
                } label: {
                    Image(systemName: "xmark")
                        .font(.callout.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.85))
                        .padding(10)
                        .background(.white.opacity(0.10), in: Circle())
                }
                Spacer()
                VStack(spacing: 2) {
                    Text("トリミング")
                        .font(.system(.headline, design: .rounded).weight(.bold))
                    Text("ドラッグで移動・ピンチで拡大")
                        .font(.caption2).foregroundStyle(.secondary)
                }
                Spacer()
                Button {
                    onConfirm(focal)
                } label: {
                    Text("完了")
                        .font(.system(.callout, design: .rounded).bold())
                        .foregroundStyle(.white)
                        .padding(.horizontal, 18).padding(.vertical, 9)
                        .background(Brand.gradient, in: Capsule())
                        .shadow(color: Brand.purple.opacity(0.45), radius: 8, y: 3)
                }
                .buttonStyle(PopButtonStyle())
            }
            .padding(.horizontal, 16)
            .padding(.top, 18)

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

                    thirdsGrid(boxW: boxW, boxH: boxH)
                        .allowsHitTesting(false)
                }
                .frame(width: boxW, height: boxH)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay {
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(.white.opacity(0.55), lineWidth: 1.5)
                }
                .overlay(alignment: .topTrailing) {
                    // Live zoom badge.
                    Text(String(format: "×%.1f", focal.scale))
                        .font(.system(.caption2, design: .monospaced).bold())
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(.black.opacity(0.55), in: Capsule())
                        .padding(8)
                        .allowsHitTesting(false)
                }
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
            .frame(maxHeight: .infinity)
            .padding(.horizontal, 16)

            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                withAnimation(.spring(response: 0.35, dampingFraction: 0.75)) {
                    focal = CellFocal()
                }
            } label: {
                Label("リセット", systemImage: "arrow.counterclockwise")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.white.opacity(0.85))
                    .padding(.horizontal, 16).padding(.vertical, 9)
                    .background(.white.opacity(0.10), in: Capsule())
            }
            .buttonStyle(PopButtonStyle())
            .padding(.bottom, 20)
        }
        .background(Brand.base)
        // Full-height only: at .medium the fixed editor area clipped off-screen.
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    /// Rule-of-thirds guide over the crop window — the universal photo-editor cue.
    private func thirdsGrid(boxW: CGFloat, boxH: CGFloat) -> some View {
        Path { p in
            for f in [1.0 / 3.0, 2.0 / 3.0] {
                p.move(to: CGPoint(x: boxW * f, y: 0))
                p.addLine(to: CGPoint(x: boxW * f, y: boxH))
                p.move(to: CGPoint(x: 0, y: boxH * f))
                p.addLine(to: CGPoint(x: boxW, y: boxH * f))
            }
        }
        .stroke(.white.opacity(0.28), lineWidth: 0.8)
        .frame(width: boxW, height: boxH)
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
                benefit("Pro限定テンプレート7種（ハーフ・チェキ・シームレス ほか）")
                benefit("2160pxの高画質書き出し")
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
            // Toggles Pro on AND off, so the free-tier paywall flow is
            // testable too. Never ships: DEBUG builds only.
            Button("デバッグ切替") { onDebugUnlock() }
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

/// UIKit share sheet wrapper.
struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
