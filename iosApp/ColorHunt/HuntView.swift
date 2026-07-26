import SwiftUI
import PhotosUI
import SharedColor

/// ハント — pick photos, auto-sort by dominant colour (shared KMP classifier),
/// tap to select for the collage, long-press (context menu) to re-file a colour.
struct HuntView: View {
    @EnvironmentObject private var state: AppState
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var showGrid = false

    private let columns = [GridItem(.adaptive(minimum: 104), spacing: 8)]

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
            .onChange(of: pickerItems) { items in
                Task { await load(items) }
            }
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
                    Text("テーマ色を、集めよう。")
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
                // Clear of the raised camera button, which pokes above the tab
                // bar right where this CTA sits.
                .padding(.bottom, 34)
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

