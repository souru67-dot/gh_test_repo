import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

/// グリッド — an Instagram-authentic profile mock wrapping the user's own picked
/// photos, so they can preview how a themed feed reads. Tiles are 4:5 portrait
/// with 1px gutters; long-press and drag to reorder (parity with Android's
/// reorderable grid); a context menu removes a tile.
struct GridPreviewView: View {
    @EnvironmentObject private var state: AppState
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var dragging: Int?

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 2), count: 3)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    topBar
                    profileHeader
                    bio
                    actionButtons
                    highlights
                    tabStrip
                    if state.gridPhotos.isEmpty {
                        emptyState
                    } else {
                        grid
                    }
                }
            }
            .background(Color.black)
            .toolbar(.hidden, for: .navigationBar)
            .onChange(of: pickerItems) { _, items in
                Task { await load(items) }
            }
        }
    }

    // MARK: chrome

    private var topBar: some View {
        HStack {
            PhotosPicker(selection: $pickerItems, maxSelectionCount: 30, matching: .images) {
                Image(systemName: "plus.app")
            }
            Spacer()
            HStack(spacing: 5) {
                Text("colorhunt").font(.title3.bold())
                Image(systemName: "checkmark.seal.fill").font(.footnote).foregroundStyle(.blue)
                Image(systemName: "chevron.down").font(.caption2)
            }
            Spacer()
            Image(systemName: "line.3.horizontal")
        }
        .font(.title3)
        .foregroundStyle(.white)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private var profileHeader: some View {
        HStack(spacing: 22) {
            avatar
            stat("\(state.gridPhotos.count)", "投稿")
            stat("0", "フォロワー")
            stat("0", "フォロー中")
        }
        .padding(.horizontal, 16)
        .padding(.top, 4)
    }

    private var avatar: some View {
        ZStack(alignment: .bottomTrailing) {
            Circle()
                .fill(LinearGradient(
                    colors: [Color(argb: 0xFF7C4DFF), Color(argb: 0xFFEC407A)],
                    startPoint: .topLeading, endPoint: .bottomTrailing
                ))
                .frame(width: 86, height: 86)
            Circle()
                .fill(.blue)
                .frame(width: 24, height: 24)
                .overlay(Image(systemName: "plus").font(.caption2.bold()).foregroundStyle(.white))
                .overlay(Circle().stroke(.black, lineWidth: 2.5))
        }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.headline.bold()).foregroundStyle(.white)
            Text(label).font(.caption).foregroundStyle(.white.opacity(0.75))
        }
        .frame(maxWidth: .infinity)
    }

    private var bio: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("ColorHunt").font(.subheadline.bold()).foregroundStyle(.white)
            Text("集めたテーマ色でフィードを組む 🎨").font(.subheadline).foregroundStyle(.white.opacity(0.85))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.top, 10)
    }

    private var actionButtons: some View {
        HStack(spacing: 8) {
            igButton("プロフィールを編集")
            igButton("プロフィールをシェア")
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
    }

    private func igButton(_ title: String) -> some View {
        Text(title)
            .font(.subheadline.bold())
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
    }

    private var highlights: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 18) {
                VStack(spacing: 6) {
                    Circle()
                        .stroke(.white.opacity(0.25), lineWidth: 1)
                        .frame(width: 62, height: 62)
                        .overlay(Image(systemName: "plus").foregroundStyle(.white.opacity(0.85)))
                    Text("新規").font(.caption2).foregroundStyle(.white.opacity(0.8))
                }
            }
            .padding(.horizontal, 16)
        }
        .padding(.top, 16)
    }

    private var tabStrip: some View {
        HStack(spacing: 0) {
            tabIcon("squareshape.split.3x3", selected: true)
            tabIcon("play.rectangle", selected: false)
            tabIcon("person.crop.rectangle", selected: false)
        }
        .padding(.top, 14)
        .overlay(alignment: .bottom) {
            Rectangle().fill(.white.opacity(0.12)).frame(height: 1)
        }
    }

    private func tabIcon(_ name: String, selected: Bool) -> some View {
        Image(systemName: name)
            .font(.title3)
            .foregroundStyle(selected ? .white : .white.opacity(0.4))
            .frame(maxWidth: .infinity)
            .padding(.bottom, 10)
            .overlay(alignment: .bottom) {
                if selected {
                    Rectangle().fill(.white).frame(height: 1.5)
                }
            }
    }

    // MARK: grid

    private var grid: some View {
        LazyVGrid(columns: columns, spacing: 2) {
            ForEach(Array(state.gridPhotos.enumerated()), id: \.offset) { index, image in
                tile(image, index: index)
            }
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
                delegate: GridDropDelegate(item: index, current: $dragging) { from, to in
                    state.moveGridPhoto(from: from, to: to)
                }
            )
            .contextMenu {
                Button(role: .destructive) {
                    state.gridPhotos.remove(at: index)
                } label: {
                    Label("削除", systemImage: "trash")
                }
            }
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: "square.grid.3x3")
                .font(.largeTitle)
                .foregroundStyle(.white.opacity(0.5))
            Text("写真を追加してフィードを組みましょう。")
                .font(.subheadline).foregroundStyle(.white.opacity(0.7))
            PhotosPicker(selection: $pickerItems, maxSelectionCount: 30, matching: .images) {
                Label("写真を追加", systemImage: "plus")
            }
            .buttonStyle(.borderedProminent)
            .padding(.top, 6)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
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

/// Live reorder-on-hover for the feed grid. DropDelegate callbacks run on the main
/// thread, so `assumeIsolated` safely reaches the @MainActor move closure.
struct GridDropDelegate: DropDelegate {
    let item: Int
    let current: Binding<Int?>
    let onMove: @MainActor (Int, Int) -> Void

    func dropEntered(info: DropInfo) {
        guard let from = current.wrappedValue, from != item else { return }
        MainActor.assumeIsolated { onMove(from, item) }
        current.wrappedValue = item
    }

    func dropUpdated(info: DropInfo) -> DropProposal? { DropProposal(operation: .move) }

    func performDrop(info: DropInfo) -> Bool {
        current.wrappedValue = nil
        return true
    }

    func dropExited(info: DropInfo) {}
}
