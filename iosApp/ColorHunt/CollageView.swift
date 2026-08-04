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
    /// Full-resolution photos for the cells currently on screen.
    ///
    /// `HuntPhoto.thumb` is 440px — right for a Hunt tile, far too small for a
    /// canvas that gets rendered at up to 2160px on export. Only the handful of
    /// photos actually laid out as cells are read back, and the thumbnail stands
    /// in until each arrives so the preview never blanks.
    @State private var fulls: [UUID: UIImage] = [:]
    @State private var editTarget: EditTarget?
    // Long-press drag reorder state: picked-up cell and current drop target.
    @State private var dragCell: Int?
    @State private var dragTarget: Int?
    /// Which settings group the panel is showing. Only one at a time — see
    /// CollageTool.
    @State private var tool: CollageTool = .template

    /// Wrapping the image in an Identifiable and presenting with sheet(item:)
    /// rather than sheet(isPresented:): the latter reads its content while the
    /// image is still nil on the first tap — both were set in the same runloop —
    /// so the first share opened an empty sheet and only later taps worked.
    @State private var sharePayload: SharePayload?
    @State private var showPaywall = false
    @State private var saving = false
    @State private var saveDone = false

    /// @State does not survive the process, so every setting above used to come
    /// back at its default after the app was killed — the collage the user had
    /// built was gone. Seed them from the last saved snapshot here, in init, so
    /// the restored values are already in place for the first frame (restoring
    /// in onAppear would render one frame of defaults first).
    init() {
        let saved = CollageSettingsStore.load()
        _layoutOrdinal = State(initialValue: saved.layout)
        _placementOrdinal = State(initialValue: saved.placement)
        _spacing = State(initialValue: saved.spacing)
        _cornerRadius = State(initialValue: saved.cornerRadius)
        _borderWidth = State(initialValue: saved.borderWidth)
        _borderColor = State(initialValue: saved.borderColor)
        _aspect = State(initialValue: saved.aspect)
        _background = State(initialValue: saved.background)
        _bgFollowsTheme = State(initialValue: saved.bgFollowsTheme)
        _hexOverlay = State(initialValue: saved.hexOverlay)
        _dateStamp = State(initialValue: saved.dateStamp)
        _overlayPosFrac = State(initialValue: saved.overlayPosFrac)
        _overlayWidthFrac = State(initialValue: saved.overlayWidthFrac)
        _appliedTemplateID = State(initialValue: saved.validTemplateID)
        _focals = State(initialValue: saved.focals)
    }

    /// The photos actually drawn as cells, in order — what `canvas(width:)`
    /// takes a prefix of. Used as the task id so a reorder reloads nothing that
    /// is already in hand.
    private var cellPhotoIDs: [UUID] {
        state.orderedSelectedPhotos.prefix(templateCellCap).map { $0.id }
    }

    /// Pulls in every cell photo that is not already loaded, and drops the ones
    /// that have left the collage so a long session cannot accumulate them.
    ///
    /// Returns what it loaded as well as storing it: the export path renders
    /// immediately afterwards and must use these exact images, not whatever a
    /// re-read of `@State` happens to give back on this pass.
    @discardableResult
    @MainActor
    private func loadFulls() async -> [UUID: UIImage] {
        let wanted = cellPhotoIDs
        var next = fulls.filter { wanted.contains($0.key) }
        for id in wanted where next[id] == nil {
            if let image = await state.fullImage(for: id) { next[id] = image }
        }
        fulls = next
        return next
    }

    /// Everything the panel can change, gathered into one Equatable value so a
    /// single onChange in `body` covers every control.
    private var settingsSnapshot: CollageSettings {
        CollageSettings(
            layout: layoutOrdinal,
            placement: placementOrdinal,
            spacing: spacing,
            cornerRadius: cornerRadius,
            borderWidth: borderWidth,
            borderColor: borderColor,
            aspect: aspect,
            background: background,
            bgFollowsTheme: bgFollowsTheme,
            hexOverlay: hexOverlay,
            dateStamp: dateStamp,
            overlayPosFrac: overlayPosFrac,
            overlayWidthFrac: overlayWidthFrac,
            templateID: appliedTemplateID,
            focals: focals
        )
    }

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
            // Read the cells' full-resolution photos whenever the set changes —
            // a different selection, a reorder, or a preset that caps the count.
            .task(id: cellPhotoIDs) { await loadFulls() }
            .onChange(of: settingsSnapshot) { snapshot in
                var toSave = snapshot
                // Crops are keyed by photo, and deleting a photo leaves its entry
                // behind; drop the orphans so the blob cannot grow without bound.
                let live = Set(state.photos.map(\.id))
                toSave.focals = toSave.focals.filter { live.contains($0.key) }
                CollageSettingsStore.save(toSave)
            }
        }
        .sheet(item: $sharePayload) { payload in
            ActivityView(items: [payload.image])
        }
        .sheet(item: $editTarget) { target in
            if let photo = state.photos.first(where: { $0.id == target.photoID }) {
                CropEditorView(
                    image: fulls[photo.id] ?? photo.thumb,
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
                    // Left alone the preview is dozens of unlabelled images, HEX
                    // chips and palette blocks — VoiceOver reads it as noise. As
                    // one element it says what it is and how many photos are in
                    // it; the gestures it carries (tap to crop, long-press to
                    // reorder) are out of reach for VoiceOver either way.
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("コラージュのプレビュー")
                    .accessibilityValue(Text("\(min(state.orderedSelectedPhotos.count, templateCellCap))枚"))

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

    /// One group of settings. Stacking all of them made the panel taller than the
    /// screen, so the preview scrolled away exactly when you needed to watch it —
    /// the controls at the bottom were being used blind. Only one group is on
    /// screen at a time now, and the preview stays put.
    private enum CollageTool: Hashable, CaseIterable {
        case template, layout, palette, finish, size

        var label: LocalizedStringKey {
            switch self {
            case .template: return "テンプレ"
            case .layout:   return "レイアウト"
            case .palette:  return "パレット"
            case .finish:   return "仕上げ"
            case .size:     return "サイズ"
            }
        }

        var icon: String {
            switch self {
            case .template: return "wand.and.stars"
            case .layout:   return "rectangle.3.group"
            case .palette:  return "paintpalette"
            case .finish:   return "slider.horizontal.3"
            case .size:     return "aspectratio"
            }
        }
    }

    /// ハーフ and インスタント reproduce a sheet, so arrangement and margins are
    /// not theirs to change — those tools drop out rather than offering ways to
    /// break the format.
    private func tools(formatLocked: Bool) -> [CollageTool] {
        formatLocked ? [.template, .palette, .size] : CollageTool.allCases
    }

    private var controls: some View {
        VStack(alignment: .leading, spacing: 14) {
            toolRail
            Rectangle().fill(.white.opacity(0.08)).frame(height: 1)
            Group {
                switch tool {
                case .template: templateSection
                case .layout:   layoutSection
                case .palette:  paletteSection
                case .finish:   finishSection
                case .size:     sizeSection
                }
            }
            // A floor so switching to a one-control group does not collapse the
            // panel and jerk the preview down the screen.
            .frame(maxWidth: .infinity, minHeight: 104, alignment: .topLeading)
        }
        .padding(16)
        .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 20))
        // Applying a format preset can retire the tool that is open.
        .onChange(of: formatLocked) { locked in
            if !tools(formatLocked: locked).contains(tool) { tool = .template }
        }
    }

    private var toolRail: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(tools(formatLocked: formatLocked), id: \.self) { item in
                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        withAnimation(.easeInOut(duration: 0.18)) { tool = item }
                    } label: {
                        VStack(spacing: 5) {
                            Image(systemName: item.icon)
                                .font(.system(size: 16, weight: .semibold))
                            Text(item.label)
                                .font(.caption2.weight(.medium))
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                        }
                        .frame(width: 66, height: 54)
                        .foregroundStyle(tool == item ? .white : .white.opacity(0.55))
                        .background {
                            ZStack {
                                RoundedRectangle(cornerRadius: 14).fill(.white.opacity(0.07))
                                if tool == item {
                                    RoundedRectangle(cornerRadius: 14).fill(Brand.gradient)
                                }
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 1)
        }
    }

    private var templateSection: some View {
        VStack(alignment: .leading, spacing: 10) {
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
                            .accessibilityLabel("Pro")
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
        // インスタント is one photo on a print, so the generic 2×2 grid below
        // advertised the wrong format entirely. Show the single frame and the
        // signature chin instead.
        if tpl.id == "cheki" {
            return AnyView(
                VStack(spacing: 0) {
                    RoundedRectangle(cornerRadius: 1.5).fill(ink)
                    Spacer(minLength: 0).frame(height: 9)
                }
                .padding(.horizontal, 7)
                .padding(.top, 7)
                .padding(.bottom, 4)
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
            // ハーフ and インスタント print their palette into the sheet's own
            // margin, so there is no placement to choose — only whether the lab
            // printed it. Offering the rail here was what let the format break.
            if formatLocked {
                Toggle(isOn: Binding(get: { placementOrdinal != 0 },
                                     set: { placementOrdinal = $0 ? 1 : 0 })) {
                    Text("カラーパレットを印字").font(.callout)
                }
                .tint(Color(argb: 0xFF7C4DFF))
            } else {
                Picker("パレット", selection: $placementOrdinal) {
                    Text("なし").tag(Int32(0))
                    Text("下帯").tag(Int32(5))
                    Text("中央").tag(Int32(1))
                    Text("重ねる").tag(Int32(4))
                    Text("左").tag(Int32(3))
                    Text("右").tag(Int32(2))
                }
                .pickerStyle(.segmented)
            }

            // OVERLAY (重ねる) fine-tuning: position and band width. The band is
            // always vertical — a horizontal one cut straight through the
            // photos and never produced a usable composition.
            if placementOrdinal == 4 && !formatLocked {
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

    /// The sheet ratio. A format preset has none to choose — its sheet size IS
    /// the preset — so it explains itself instead.
    private var sizeSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            if formatLocked {
                Text(formatSizeNote)
                    .font(.callout)
                    .foregroundStyle(.white.opacity(0.7))
            } else {
                Picker("サイズ", selection: $aspect) {
                    Text("1:1").tag(CGFloat(1))
                    Text("4:5").tag(CGFloat(4.0 / 5.0))
                    Text("9:16").tag(CGFloat(9.0 / 16.0))
                    Text("16:9").tag(CGFloat(16.0 / 9.0))
                }
                .pickerStyle(.segmented)
            }
        }
    }

    /// Margins, corners and colour. A film print and an instax card have fixed
    /// margins, square corners and no drawn border, and their sheet colour is
    /// part of the format — ハーフ's rebate is film-black, インスタント's mat covers
    /// the sheet entirely — so this whole tool retires for them.
    private var finishSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sliderRow("余白", value: $spacing, range: 0...24)
            sliderRow("角丸", value: $cornerRadius, range: 0...48)
            sliderRow("枠線の太さ", value: $borderWidth, range: 0...8)

            sectionLabel("枠線の色")
            swatchRow(selected: borderColor) { borderColor = $0 }

            HStack {
                sectionLabel("背景色")
                Spacer()
                Text("テーマ色を使う").font(.caption).foregroundStyle(.white.opacity(0.8))
                    .accessibilityHidden(true)
                Toggle("テーマ色を使う", isOn: $bgFollowsTheme)
                    .labelsHidden().tint(Color(argb: 0xFF7C4DFF))
            }
            if !bgFollowsTheme {
                swatchRow(selected: background) { background = $0 }
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
            // The read-out above is a separate Text, so the Slider itself has no
            // name of its own — VoiceOver announced a bare percentage.
            Slider(value: value, in: range)
                .tint(Color(argb: 0xFF7C4DFF))
                .accessibilityLabel(LocalizedStringKey(label))
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
    /// [images] overrides the loaded full-resolution set. The export passes the
    /// dictionary it just awaited so a render can never quietly fall back to
    /// 440px thumbnails.
    private func canvas(width: CGFloat, images: [UUID: UIImage]? = nil) -> some View {
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
        let footerH = (placementOrdinal == footerPlacement && !formatLocked)
            ? height * Self.footerRatio : 0
        let flat = CollageBridge.shared.computeFlat(
            cellCount: Int32(photos.count),
            layoutOrdinal: layoutOrdinal,
            // The band is drawn here, so the shared geometry is asked for a
            // plain grid with no rail eating horizontal space. ハーフ and
            // インスタント never reserve one either: a rail carved out of the
            // canvas shifts the cells while the deco keeps drawing the rebate
            // and the mat against the full sheet, so the frames and their
            // furniture come apart. Their palette is printed into the sheet's
            // own margin instead — see formatPalette.
            placementOrdinal: (placementOrdinal == footerPlacement || formatLocked)
                ? 0 : placementOrdinal,
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
                    cellView((images ?? fulls)[photo.id] ?? photo.thumb, cell: r, focal: focal,
                             corner: cornerRadius * scale, border: borderWidth * scale)
                        .overlay(alignment: .bottomLeading) {
                            if hexOverlay, let c = photo.dominantColor {
                                hexChip(c, cellWidth: r.width, scale: scale)
                                    // インスタント's mat and chin are drawn over the
                                    // cell, so a chip on the cell's own bottom edge
                                    // sat underneath them and the toggle looked
                                    // dead. Lift it into the visible photo.
                                    .padding(.bottom, hexChipBottomInset(scale: scale))
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
        // Above the deco, because the margin it prints into is drawn by the deco.
        .overlay(alignment: .bottom) {
            if formatPaletteActive {
                formatPalette(scale: scale, photos: photos)
                    .allowsHitTesting(false)
            }
        }
        .overlay(alignment: .bottomTrailing) {
            // Retro film-camera date stamp (the setlog/dazz nostalgia cue) —
            // bottom-right, where every quartz camera printed it. The
            // watermark lives bottom-left so the two never collide. 下帯 owns
            // the bottom edge, so both lift clear of the band rather than
            // printing on top of a swatch.
            if dateStamp {
                QuartzDateStamp(scale: scale)
                    .padding(.bottom, footerInset(height, scale: scale))
            }
        }
        .overlay(alignment: .bottomLeading) {
            if !state.isPro {
                watermarkPill(scale: scale)
                    .padding(10 * scale)
                    .padding(.bottom, footerInset(height, scale: scale))
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
                    // max() on both sides. The height guard stops an infinite
                    // ratio, which collapsed the crop editor to zero height; the
                    // width guard stops a ratio of zero, which is worse — the
                    // editor computes boxW / ratio, and 0/0 is a NaN frame, and
                    // SwiftUI traps on a non-finite frame rather than drawing
                    // something wrong.
                    ratio: max(cells[i].width, 1) / max(cells[i].height, 1)
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
    private func hexChip(_ packed: Int32, cellWidth: CGFloat, scale: CGFloat) -> some View {
        // The clamps have to travel with the render scale. Fixed 6...11 points
        // were sized for the 340pt preview, so the same chip came out 11px tall
        // in a 1080px export — present, but far too small to read, which looked
        // exactly like the toggle doing nothing on save.
        let fs = min(max(cellWidth * 0.075, 6 * scale), 11 * scale)
        return HStack(spacing: fs * 0.4) {
            Circle().fill(Color(packed: packed))
                .overlay(Circle().stroke(.white.opacity(0.85), lineWidth: max(0.5 * scale, 0.5)))
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

    /// How much of a cell's bottom edge the format deco covers, so a HEX chip
    /// placed there still lands on the photo. インスタント's mat runs all round
    /// with a deep chin at the foot; ハーフ has only its thin print edge plus,
    /// when the palette is printed, the caption strip.
    private func hexChipBottomInset(scale: CGFloat) -> CGFloat {
        switch appliedTemplateID {
        case "cheki":
            let mat = max(spacing * scale, 14 * scale)
            return max(mat * 2.4, 34 * scale)
        case "half":
            return footerInset(0, scale: scale) + 2 * scale
        default:
            return 0
        }
    }

    /// Placement ordinal for 下帯. The shared enum stops at 4 (OVERLAY); this one
    /// is drawn on the iOS side, so the number continues the same sequence
    /// without the Kotlin enum needing a new case.
    private var footerPlacement: Int32 { 5 }
    /// Height of the 下帯 band as a fraction of the canvas.
    private static let footerRatio: CGFloat = 0.15

    /// Space something already owns along the bottom edge, so corner-anchored
    /// marks (date stamp, watermark) clear it: the 下帯 band, or ハーフ's caption
    /// strip. インスタント needs none — its date is drawn by the deco inside the
    /// chin, and the palette sits at the opposite end of the same chin.
    private func footerInset(_ height: CGFloat, scale: CGFloat) -> CGFloat {
        if placementOrdinal == footerPlacement && !formatLocked {
            return height * Self.footerRatio
        }
        if formatPaletteActive && appliedTemplateID == "half" {
            return 23 * scale
        }
        return 0
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

    /// True when a format preset should print its palette into its own margin.
    private var formatPaletteActive: Bool { formatLocked && placementOrdinal != 0 }

    /// ハーフ and インスタント reproduce a physical sheet, so they get their own
    /// palette rather than the shared rail. A lab prints the reference in the
    /// margin it already has — the chin of a print, the foot of a film sheet —
    /// and that margin is drawn by the deco on top of the photos, so nothing
    /// about the frames' geometry has to move to make room.
    @ViewBuilder
    private func formatPalette(scale: CGFloat, photos: [HuntPhoto]) -> some View {
        let entries = photos.compactMap { p in
            p.dominantColor.map { PaletteEntry(color: $0, bucket: p.bucketKey) }
        }
        if !entries.isEmpty {
            if appliedTemplateID == "cheki" {
                chekiPalette(entries, scale: scale)
            } else if appliedTemplateID == "half" {
                halfPalette(entries, scale: scale)
            }
        }
    }

    /// インスタント: the chin is already there and already holds the date, so the
    /// swatch and its reading sit at the other end of it. Nothing moves.
    private func chekiPalette(_ entries: [PaletteEntry], scale: CGFloat) -> some View {
        let mat = max(spacing * scale, 14 * scale)
        let chin = max(mat * 2.4, 34 * scale)
        let chip = chin * 0.30
        return HStack(spacing: chip * 0.46) {
            RoundedRectangle(cornerRadius: 1 * scale)
                .fill(Color(packed: entries[0].color))
                .frame(width: chip, height: chip)
            VStack(alignment: .leading, spacing: 0) {
                if let bucket = entries[0].bucket {
                    Text(bucket.replacingOccurrences(of: "_", with: " "))
                        .font(.system(size: chip * 0.34, weight: .semibold))
                        .tracking(chip * 0.05)
                        .foregroundStyle(Color(red: 0.60, green: 0.57, blue: 0.52))
                }
                Text(hexString(entries[0].color))
                    .font(.system(size: chip * 0.50, weight: .regular, design: .serif))
                    .foregroundStyle(Color(red: 0.38, green: 0.36, blue: 0.32))
            }
            .lineLimit(1)
            .minimumScaleFactor(0.5)
        }
        .frame(height: chin)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.leading, 15 * scale)
    }

    /// ハーフ: a strip along the foot of the sheet, in the rebate's own black, so
    /// it reads as the lab's caption margin rather than a rail bolted on. One
    /// reading per frame, each aligned under the frame it came from.
    private func halfPalette(_ entries: [PaletteEntry], scale: CGFloat) -> some View {
        let bar = 21 * scale
        let chip = bar * 0.38
        return HStack(spacing: 0) {
            ForEach(Array(entries.enumerated()), id: \.offset) { idx, entry in
                HStack(spacing: chip * 0.5) {
                    RoundedRectangle(cornerRadius: 1 * scale)
                        .fill(Color(packed: entry.color))
                        .frame(width: chip, height: chip)
                    Text(hexString(entry.color))
                        .font(.system(size: bar * 0.36, weight: .regular, design: .serif))
                        .foregroundStyle(.white.opacity(0.88))
                    if let bucket = entry.bucket {
                        Text(bucket.replacingOccurrences(of: "_", with: " "))
                            .font(.system(size: bar * 0.25, weight: .semibold))
                            .tracking(bar * 0.04)
                            .foregroundStyle(.white.opacity(0.5))
                    }
                }
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .frame(maxWidth: .infinity, alignment: idx == 0 ? .leading : .trailing)
                .padding(.horizontal, 10 * scale)
            }
        }
        .frame(height: bar)
        .frame(maxWidth: .infinity)
        .background(Color(red: 0.07, green: 0.07, blue: 0.07))
        // Sit inside the print's own edge, which the deco strokes at 2pt.
        .padding(2 * scale)
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
    private func render(using images: [UUID: UIImage]) -> UIImage? {
        // Pro exports at 2160px (crisper on retina feeds); free at 1080px.
        let width: CGFloat = state.isPro ? 2160 : 1080
        let renderer = ImageRenderer(content: canvas(width: width, images: images)
            .environmentObject(state))
        renderer.scale = 1
        return renderer.uiImage
    }

    @MainActor private func share() {
        if proTemplateLock {
            showPaywall = true
            return
        }
        // Share assist parity: hashtag caption on the pasteboard, paste-and-go.
        // Localised, not a fixed string: the tags are the growth engine, and a
        // Japanese one reaches nobody on an English or Korean feed. Each locale
        // carries the tags that are actually searched there.
        UIPasteboard.general.string = NSLocalizedString(
            "ColorHuntで色あつめ 🎨📸 #カラーハント #色集め #組写 #colorhunt",
            comment: "share caption copied to the pasteboard")
        Task {
            // The preview may still be standing in with thumbnails; an export
            // never may.
            let images = await loadFulls()
            if let image = render(using: images) { sharePayload = SharePayload(image: image) }
        }
    }

    /// Save with real feedback: spinner while writing, success haptic + toast when
    /// the photo actually lands in the library (silent fire-and-forget felt broken).
    @MainActor private func save() {
        if proTemplateLock {
            showPaywall = true
            return
        }
        guard !saving else { return }
        saving = true
        Task {
            let images = await loadFulls()
            guard let image = render(using: images) else {
                saving = false
                return
            }
            writeToLibrary(image)
        }
    }

    /// The library write itself, split out so `save()` can await the photos first
    /// while the spinner is already up.
    @MainActor private func writeToLibrary(_ image: UIImage) {
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
struct CellFocal: Equatable, Codable {
    var x: CGFloat = 0.5
    var y: CGFloat = 0.5
    var scale: CGFloat = 1
    static let maxScale: CGFloat = 4
}

/// Every user-tunable collage setting in one value. The panel holds these as
/// @State — fine while the app is running, gone the moment it is killed — so the
/// whole set is snapshotted here and written to UserDefaults on every change.
private struct CollageSettings: Equatable, Codable {
    var layout: Int32 = 0
    var placement: Int32 = 1
    var spacing: CGFloat = 4
    var cornerRadius: CGFloat = 6
    var borderWidth: CGFloat = 0
    var borderColor: Int64 = 0xFFFFFFFF
    var aspect: CGFloat = 4.0 / 5.0
    var background: Int64 = 0xFF0E0E12
    var bgFollowsTheme: Bool = true
    var hexOverlay: Bool = false
    var dateStamp: Bool = false
    var overlayPosFrac: CGFloat = 0.5
    var overlayWidthFrac: CGFloat = 0.16
    var templateID: String?
    var focals: [UUID: CellFocal] = [:]

    /// A preset that has since been renamed or dropped would leave the panel
    /// pointing at nothing (and `formatLocked` guessing), so only a template the
    /// current build still ships is restored.
    var validTemplateID: String? {
        guard let templateID,
              CollageTemplateVM.all.contains(where: { $0.id == templateID }) else { return nil }
        return templateID
    }

    /// What comes back from UserDefaults is data, not constants — a blob written
    /// by a different build, or a truncated one, can hold anything the decoder
    /// accepts. `aspect` is the dangerous one: `canvas(width:)` computes
    /// `width / aspect`, so a stored 0 hands SwiftUI a non-finite frame, and that
    /// is a hard crash in layout rather than a preview that merely looks wrong.
    /// Clamp every value to the range its own control offers.
    func sanitised() -> CollageSettings {
        func clamp(_ v: CGFloat, _ lo: CGFloat, _ hi: CGFloat, or fallback: CGFloat) -> CGFloat {
            v.isFinite ? min(max(v, lo), hi) : fallback
        }
        var s = self
        // 0.5625 (9:16) … 1.778 (16:9) are the extremes any preset or size uses.
        s.aspect = clamp(s.aspect, 0.4, 2.0, or: 4.0 / 5.0)
        s.spacing = clamp(s.spacing, 0, 24, or: 4)
        s.cornerRadius = clamp(s.cornerRadius, 0, 48, or: 6)
        s.borderWidth = clamp(s.borderWidth, 0, 8, or: 0)
        s.overlayPosFrac = clamp(s.overlayPosFrac, 0, 1, or: 0.5)
        s.overlayWidthFrac = clamp(s.overlayWidthFrac, 0.08, 0.5, or: 0.16)
        s.layout = (0...2).contains(s.layout) ? s.layout : 0
        // 0 NONE … 4 OVERLAY from the shared enum, plus 5 (下帯) laid out here.
        s.placement = (0...5).contains(s.placement) ? s.placement : 1
        s.focals = s.focals.mapValues { focal in
            var f = focal
            f.x = clamp(f.x, 0, 1, or: 0.5)
            f.y = clamp(f.y, 0, 1, or: 0.5)
            f.scale = clamp(f.scale, 1, CellFocal.maxScale, or: 1)
            return f
        }
        return s
    }
}

/// UserDefaults-backed store for the collage panel.
///
/// Deliberately stateless: SwiftUI re-creates `CollageView` on every parent body
/// pass, so `load()` runs often — but it is a small keyed read plus a ~300-byte
/// decode, and holding no cache keeps it free of shared mutable state.
///
/// The key is versioned because the synthesised decoder requires every field: if
/// this struct ever gains or loses one, bump `key` instead of shipping a blob the
/// next build cannot read (settings fall back to defaults once, rather than the
/// decode failing silently on every launch).
private enum CollageSettingsStore {
    private static let key = "collage_settings_v1"

    static func load() -> CollageSettings {
        guard let data = UserDefaults.standard.data(forKey: key),
              let decoded = try? JSONDecoder().decode(CollageSettings.self, from: data)
        else { return CollageSettings() }
        return decoded.sanitised()
    }

    static func save(_ settings: CollageSettings) {
        guard let data = try? JSONEncoder().encode(settings) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }
}

/// Carries the rendered collage into the share sheet, so the sheet can never be
/// presented without one.
struct SharePayload: Identifiable {
    let id = UUID()
    let image: UIImage
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
        // 9:16 rather than a photo booth's true 1:2.8 strip. That ratio is the
        // format, but a feed shrinks anything taller than 4:5 to a sliver, and
        // this preset exists to be posted. 9:16 is the story/reel frame, so the
        // strip fills the screen it actually gets shared on. Spacing comes down
        // with it so the four frames keep some height. The camera's frame guide
        // reads the same aspect, so it follows automatically.
        .init(id: "fourcut", label: "4カット", category: .trend, aspect: 9.0 / 16.0, layout: 1,
              placement: 0, spacing: 7, corner: 0, background: 0xFFFFFFFF,
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
    ///
    /// Built from a template rather than a fixed pattern. `"M/d EEE"` looked
    /// right in English ("8/2 Sun") but Japanese renders the short weekday as a
    /// bare 日/月/火, so a Sunday came out "8/2 日" — which reads as a mistyped
    /// 8月2日, not as a date with its weekday. The template lets each locale
    /// place and punctuate it its own way: 8/2(日) in Japanese, "Sun, 8/2" in
    /// English, 8. 2. (일) in Korean.
    private static let daylogFormatter: DateFormatter = {
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate("MdEEE")
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
                .accessibilityLabel("キャンセル")
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
            .accessibilityLabel("閉じる")
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
