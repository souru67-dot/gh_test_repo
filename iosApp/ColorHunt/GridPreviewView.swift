import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

/// グリッド — a light feed-preview (Instagram-flavoured, not a clone) that wraps the
/// user's own picked photos so they can see how a themed 3-up feed reads. Tiles are
/// 4:5 with 1px gutters; long-press and drag to reorder; a context menu removes one.
/// A single "+" tile in the grid adds more photos.
struct GridPreviewView: View {
    @EnvironmentObject private var state: AppState
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var dragging: Int?

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
            .navigationTitle("グリッド")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.black, for: .navigationBar)
            .onChange(of: pickerItems) { items in
                Task { await load(items) }
            }
        }
    }

    // MARK: header

    private var header: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 22) {
                Circle()
                    .fill(LinearGradient(
                        colors: [Color(argb: 0xFF7C4DFF), Color(argb: 0xFFEC407A), Color(argb: 0xFFFFA726)],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    ))
                    .frame(width: 76, height: 76)
                    .overlay(Image(systemName: "camera.fill").foregroundStyle(.white.opacity(0.9)))
                stat("\(state.gridPhotos.count)", "投稿")
                stat("—", "フォロワー")
                stat("—", "フォロー中")
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("ColorHunt").font(.subheadline.bold()).foregroundStyle(.white)
                Text("集めたテーマ色でフィードを組む 🎨")
                    .font(.subheadline).foregroundStyle(.white.opacity(0.85))
            }
        }
        .padding(16)
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.headline.bold()).foregroundStyle(.white)
            Text(LocalizedStringKey(label)).font(.caption).foregroundStyle(.white.opacity(0.7))
        }
        .frame(maxWidth: .infinity)
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
            .opacity(dragging == index ? 0.35 : 1)
            .onDrag {
                dragging = index
                return NSItemProvider(object: String(index) as NSString)
            }
            .onDrop(
                of: [.text],
                delegate: ReorderDropDelegate(item: index, items: $state.gridPhotos, current: $dragging)
            )
            .contextMenu {
                Button(role: .destructive) {
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
/// iOS 16 (no `MainActor.assumeIsolated`, which is iOS 17+). Reused by the Grid
/// tiles and the collage cells.
struct ReorderDropDelegate<Item>: DropDelegate {
    let item: Int
    let items: Binding<[Item]>
    let current: Binding<Int?>

    func dropEntered(info: DropInfo) {
        guard let from = current.wrappedValue, from != item,
              items.wrappedValue.indices.contains(from) else { return }
        var arr = items.wrappedValue
        let moved = arr.remove(at: from)
        arr.insert(moved, at: min(item, arr.count))
        items.wrappedValue = arr
        current.wrappedValue = item
    }

    func dropUpdated(info: DropInfo) -> DropProposal? { DropProposal(operation: .move) }

    func performDrop(info: DropInfo) -> Bool {
        current.wrappedValue = nil
        return true
    }

    func dropExited(info: DropInfo) {}
}
