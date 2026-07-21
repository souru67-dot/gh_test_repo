import SwiftUI
import PhotosUI
import SharedColor

/// ハント — pick photos, auto-sort by dominant colour (shared KMP classifier),
/// tap to select for the collage, long-press (context menu) to re-file a colour.
struct HuntView: View {
    @EnvironmentObject private var state: AppState
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var wheelExpanded = false

    private let columns = [GridItem(.adaptive(minimum: 104), spacing: 8)]

    /// Buckets that currently have photos, in shared display order.
    private var availableFilters: [String] { state.groupedByBucket.map { $0.key } }
    /// Groups to show, honouring the active filter.
    private var visibleGroups: [(key: String, photos: [HuntPhoto])] {
        guard let f = state.huntFilter else { return state.groupedByBucket }
        return state.groupedByBucket.filter { $0.key == f }
    }
    /// Every collected dominant colour (for the hue wheel).
    private var collectedColors: [Int32] { state.photos.compactMap { $0.dominantColor } }

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

                    if state.huntFilter == nil, collectedColors.count >= 3 {
                        colorWheelCard
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
            .onChange(of: pickerItems) { _, items in
                Task { await load(items) }
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
                    Text("テーマ色を、集めよう。")
                        .font(.subheadline).foregroundStyle(.white.opacity(0.9))
                }
                Spacer()
                if !state.photos.isEmpty {
                    Button(role: .destructive) {
                        state.clearAll()
                        state.huntFilter = nil
                    } label: {
                        Image(systemName: "trash")
                            .padding(10)
                            .background(.white.opacity(0.18), in: Circle())
                            .foregroundStyle(.white)
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
                PhotosPicker(selection: $pickerItems, maxSelectionCount: 50, matching: .images) {
                    Label("写真を選ぶ", systemImage: "photo.on.rectangle.angled")
                        .font(.callout.bold())
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(.white, in: Capsule())
                        .foregroundStyle(Color(argb: 0xFF7C4DFF))
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
    }

    /// Collapsed-by-default "your colour wheel" card — a mini swatch strip while
    /// closed, the full hue ring when expanded (parity with Android's HueRingCard).
    private var colorWheelCard: some View {
        VStack(spacing: 0) {
            Button {
                withAnimation(.easeInOut(duration: 0.2)) { wheelExpanded.toggle() }
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("あなたのカラーホイール").font(.subheadline.bold()).foregroundStyle(.white)
                        Text("\(availableFilters.count)色を集めました")
                            .font(.caption).foregroundStyle(.white.opacity(0.7))
                    }
                    Spacer()
                    if !wheelExpanded {
                        HStack(spacing: -6) {
                            ForEach(Array(collectedColors.prefix(5).enumerated()), id: \.offset) { _, c in
                                Circle().fill(Color(packed: c))
                                    .frame(width: 20, height: 20)
                                    .overlay(Circle().stroke(Color(argb: 0xFF101014), lineWidth: 1.5))
                            }
                        }
                    }
                    Image(systemName: wheelExpanded ? "chevron.up" : "chevron.down")
                        .font(.caption).foregroundStyle(.white.opacity(0.7))
                }
            }
            .buttonStyle(.plain)

            if wheelExpanded {
                HueRing(colors: collectedColors)
                    .frame(width: 190, height: 190)
                    .padding(.top, 14)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 20))
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
    }

    private func thumb(_ photo: HuntPhoto) -> some View {
        let selected = state.selection.contains(photo.id)
        // Square tile that never overflows its grid cell: a clear 1:1 spacer sets
        // the cell size, the image fills it via overlay, then we clip. (scaledToFill
        // + aspectRatio(.fill) directly on the Image bleeds past the cell.)
        return Color.clear
            .aspectRatio(1, contentMode: .fit)
            .overlay {
                Image(uiImage: photo.image)
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
            .contextMenu {
                ForEach(ColorBridge.shared.bucketKeys(), id: \.self) { key in
                    Button {
                        state.rebucket(photo.id, to: key)
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
                .padding(.bottom, 14)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private func load(_ items: [PhotosPickerItem]) async {
        var images: [UIImage] = []
        for item in items {
            if let data = try? await item.loadTransferable(type: Data.self),
               let image = UIImage(data: data) {
                images.append(image)
            }
        }
        if !images.isEmpty { state.add(images: images) }
        pickerItems = []
    }
}

/// Capsule shape shorthand used by the hero buttons.
enum RoundedCornerStyle {
    static let pill = Capsule()
}

/// A colour ring: the collected swatches placed around a rainbow rim by their
/// hue angle — the iOS echo of Android's HueRing. A snapshot of the palette.
struct HueRing: View {
    let colors: [Int32]

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let radius = size / 2
            let ringWidth = size * 0.10
            let orbit = radius - ringWidth - size * 0.09
            ZStack {
                Circle()
                    .strokeBorder(
                        AngularGradient(
                            colors: (0...12).map { Color(hue: Double($0) / 12, saturation: 0.9, brightness: 1) },
                            center: .center
                        ),
                        lineWidth: ringWidth
                    )
                ForEach(Array(colors.enumerated()), id: \.offset) { _, c in
                    let angle = hueAngle(c) * .pi / 180
                    Circle()
                        .fill(Color(packed: c))
                        .frame(width: size * 0.13, height: size * 0.13)
                        .overlay(Circle().stroke(.white.opacity(0.7), lineWidth: 1))
                        .offset(x: cos(angle) * orbit, y: sin(angle) * orbit)
                }
            }
            .frame(width: size, height: size)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func hueAngle(_ packed: Int32) -> Double {
        let v = Int(UInt32(bitPattern: packed))
        let r = Double((v >> 16) & 0xFF) / 255, g = Double((v >> 8) & 0xFF) / 255, b = Double(v & 0xFF) / 255
        let maxC = max(r, g, b), minC = min(r, g, b), d = maxC - minC
        if d < 0.0001 { return 0 }
        let h: Double
        switch maxC {
        case r: h = (g - b) / d + (g < b ? 6 : 0)
        case g: h = (b - r) / d + 2
        default: h = (r - g) / d + 4
        }
        return h * 60
    }
}
