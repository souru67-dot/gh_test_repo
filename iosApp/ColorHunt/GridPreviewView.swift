import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import SharedColor

/// グリッド — a feed preview: the user's own picked photos laid out 3-up so they can
/// see how a themed feed reads before posting. Tiles are 4:5 with 1px gutters;
/// long-press and drag to reorder; a context menu removes one. A single "+" tile
/// in the grid adds more photos.
///
/// The header reads as a social profile because that is what the preview is for,
/// but deliberately not as any one service's: the counters are ColorHunt's own
/// (photos staged, colours collected) rather than posts/followers/following, and
/// the avatar carries today's hunted colour instead of a brand gradient.
struct GridPreviewView: View {
    @EnvironmentObject private var state: AppState
    @State private var pickerItems: [PhotosPickerItem] = []
    /// The tile the finger lifted, and whether a real drop session ever followed.
    ///
    /// Both are needed because `onDrag`'s closure runs the instant the tile is
    /// lifted — before iOS has decided whether this long-press becomes a drag or
    /// the context menu — so `dragging` alone marked a tile as picked up even when
    /// the gesture went on to open the menu, and no drop delegate ever ran to
    /// clear it. The tile then kept its 0.35 opacity for good. Only a live
    /// session dims a tile, so a lift that goes nowhere leaves no trace.
    @State private var dragging: Int?
    @State private var dragLive = false

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 2), count: 3)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    header
                    tabHint
                    grid
                }
            }
            .background(Color.black)
            // Backstop for a reorder drag released anywhere that is not a tile.
            // It sits on the whole screen rather than on the grid: releasing on
            // the black space below the last row missed a grid-sized target
            // entirely, so nothing ran to end the session.
            .onDrop(of: [.text], delegate: ReorderCancelDelegate(current: $dragging, live: $dragLive))
            .navigationTitle("グリッド")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.black, for: .navigationBar)
            .onChange(of: pickerItems) { items in
                Task { await load(items) }
            }
        }
    }

    // MARK: header

    /// Colours the hunt has actually collected, out of the full set.
    private var collectedColors: (found: Int, total: Int) {
        (state.groupedByBucket.count, ColorBridge.shared.bucketKeys().count)
    }

    private var header: some View {
        let colors = collectedColors
        return VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                Circle()
                    .fill(avatarColor)
                    .frame(width: 64, height: 64)
                    .overlay(Circle().stroke(.white.opacity(0.18), lineWidth: 1))
                    .overlay(Image(systemName: "camera.fill").foregroundStyle(.white.opacity(0.9)))
                VStack(alignment: .leading, spacing: 3) {
                    Text("ColorHunt").font(.subheadline.bold()).foregroundStyle(.white)
                    Text("集めたテーマ色でフィードを組む 🎨")
                        .font(.subheadline).foregroundStyle(.white.opacity(0.85))
                }
            }
            HStack(spacing: 8) {
                stat("\(state.gridPhotos.count)", "枚を配置")
                stat("\(colors.found) / \(colors.total)", "色を収集")
            }
        }
        .padding(16)
    }

    /// Today's hunted colour, so the avatar means something in this app instead of
    /// repeating the brand gradient (which, on a profile-shaped header, read as
    /// another service's).
    private var avatarColor: Color {
        state.todayColor.map { Color(packed: $0) } ?? Color(argb: 0xFF7C4DFF)
    }

    private func stat(_ value: String, _ label: String) -> some View {
        HStack(spacing: 5) {
            Text(value).font(.subheadline.bold()).foregroundStyle(.white)
            Text(LocalizedStringKey(label))
                .font(.caption).foregroundStyle(.white.opacity(0.7))
        }
        .padding(.horizontal, 12).padding(.vertical, 7)
        .background(.white.opacity(0.08), in: Capsule())
        .accessibilityElement(children: .combine)
    }

    private var tabHint: some View {
        HStack(spacing: 6) {
            Image(systemName: "square.grid.3x3.fill").font(.footnote)
            Text("フィードプレビュー").font(.caption.bold())
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .overlay(alignment: .bottom) { Rectangle().fill(.white.opacity(0.12)).frame(height: 1) }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    // MARK: grid

    private var grid: some View {
        LazyVGrid(columns: columns, spacing: 2) {
            ForEach(Array(state.gridPhotos.enumerated()), id: \.offset) { index, image in
                tile(image, index: index)
            }
            addTile
        }
        .padding(.top, 2)
    }

    private func tile(_ image: UIImage, index: Int) -> some View {
        Color.clear
            .aspectRatio(4.0 / 5.0, contentMode: .fit)
            .overlay {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            }
            .clipped()
            // A feed tile has no text of its own; its position in the grid is the
            // one thing worth announcing, and it is what reordering changes.
            .accessibilityElement()
            .accessibilityLabel("写真")
            .accessibilityValue(Text(verbatim: "\(index + 1)"))
            .opacity(dragging == index && dragLive ? 0.35 : 1)
            .onDrag {
                dragging = index
                return NSItemProvider(object: String(index) as NSString)
            }
            .onDrop(
                of: [.text],
                delegate: ReorderDropDelegate(item: index, items: $state.gridPhotos,
                                              current: $dragging, live: $dragLive)
            )
            .contextMenu {
                Button(role: .destructive) {
                    // Reaching the menu proves the long-press was not a drag, and
                    // the removal shifts every index after this one anyway.
                    dragging = nil
                    dragLive = false
                    state.gridPhotos.remove(at: index)
                } label: {
                    Label("削除", systemImage: "trash")
                }
            }
    }

    private var addTile: some View {
        PhotosPicker(selection: $pickerItems, maxSelectionCount: 30, matching: .images) {
            Color.white.opacity(0.06)
                .aspectRatio(4.0 / 5.0, contentMode: .fit)
                .overlay {
                    VStack(spacing: 6) {
                        Image(systemName: "plus").font(.title2)
                        Text("写真を追加").font(.caption2)
                    }
                    .foregroundStyle(.white.opacity(0.7))
                }
                .overlay {
                    RoundedRectangle(cornerRadius: 2)
                        .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                        .foregroundStyle(.white.opacity(0.2))
                }
        }
    }

    private func load(_ items: [PhotosPickerItem]) async {
        for item in items {
            if let data = try? await item.loadTransferable(type: Data.self),
               let image = UIImage(data: data) {
                state.gridPhotos.append(image)
            }
        }
        pickerItems = []
    }
}

/// Live reorder-on-hover for any array, driven through a Binding so it works on
/// iOS 16 (no `MainActor.assumeIsolated`, which is iOS 17+).
///
/// `live` is the proof that a drop session really started: the picked-up index is
/// set optimistically by `onDrag`, and only the events below can confirm it.
struct ReorderDropDelegate<Item>: DropDelegate {
    let item: Int
    let items: Binding<[Item]>
    let current: Binding<Int?>
    let live: Binding<Bool>

    func dropEntered(info: DropInfo) {
        begin()
        guard let from = current.wrappedValue, from != item,
              items.wrappedValue.indices.contains(from) else { return }
        var arr = items.wrappedValue
        let moved = arr.remove(at: from)
        arr.insert(moved, at: min(item, arr.count))
        items.wrappedValue = arr
        current.wrappedValue = item
    }

    func dropUpdated(info: DropInfo) -> DropProposal? {
        begin()
        return DropProposal(operation: .move)
    }

    func performDrop(info: DropInfo) -> Bool {
        current.wrappedValue = nil
        live.wrappedValue = false
        return true
    }

    func dropExited(info: DropInfo) {}

    /// `dropUpdated` fires on every touch move, so only write on a real change —
    /// a @State write invalidates the view whether or not the value differs.
    private func begin() {
        if !live.wrappedValue { live.wrappedValue = true }
    }
}

/// Backstop for a reorder drag released anywhere that is not a tile. Accepts the
/// drop so the item does not fly back, and ends the session.
struct ReorderCancelDelegate: DropDelegate {
    let current: Binding<Int?>
    let live: Binding<Bool>

    func dropUpdated(info: DropInfo) -> DropProposal? { DropProposal(operation: .move) }

    func performDrop(info: DropInfo) -> Bool {
        current.wrappedValue = nil
        live.wrappedValue = false
        return true
    }

    /// Only the highlight is dropped here, not the picked-up index: this target
    /// wraps the tiles, and if it were to fire as a tile takes over the session
    /// clearing `current` would break the reorder mid-drag. A drag that comes
    /// back on to a tile simply lights up again.
    func dropExited(info: DropInfo) { live.wrappedValue = false }
}
