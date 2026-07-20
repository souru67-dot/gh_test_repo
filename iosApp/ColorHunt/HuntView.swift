import SwiftUI
import PhotosUI
import SharedColor

/// ハント — pick photos, auto-sort by dominant colour (shared KMP classifier),
/// tap to select for the collage, long-press (context menu) to re-file a colour.
struct HuntView: View {
    @EnvironmentObject private var state: AppState
    @State private var pickerItems: [PhotosPickerItem] = []

    private let columns = [GridItem(.adaptive(minimum: 104), spacing: 8)]

    var body: some View {
        NavigationStack {
            ScrollView {
                heroHeader
                    .padding(.horizontal)

                LazyVGrid(columns: columns, spacing: 8) {
                    ForEach(state.groupedByBucket, id: \.key) { group in
                        Section {
                            ForEach(group.photos) { photo in
                                thumb(photo)
                            }
                        } header: {
                            bucketHeader(group.key, count: group.photos.count)
                        }
                    }
                    if !state.unanalysed.isEmpty {
                        Section {
                            ForEach(state.unanalysed) { photo in thumb(photo) }
                        } header: {
                            Text("解析中…")
                                .font(.subheadline.bold())
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.top, 8)
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 96)
            }
            .background(Color(argb: 0xFF101014))
            .navigationTitle("ColorHunt")
            .overlay(alignment: .bottom) { makeCollageBar }
            .onChange(of: pickerItems) { _, items in
                Task { await load(items) }
            }
        }
    }

    // MARK: pieces

    private var heroHeader: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("テーマ色を、集めよう。")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.85))
            HStack(spacing: 10) {
                PhotosPicker(selection: $pickerItems, maxSelectionCount: 50, matching: .images) {
                    Label("写真を選ぶ", systemImage: "photo.on.rectangle.angled")
                        .font(.callout.bold())
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(.white, in: RoundedCornerStyle.pill)
                        .foregroundStyle(Color(argb: 0xFF7C4DFF))
                }
                if !state.photos.isEmpty {
                    Button(role: .destructive) {
                        state.clearAll()
                    } label: {
                        Image(systemName: "trash")
                            .padding(12)
                            .background(.white.opacity(0.18), in: Circle())
                            .foregroundStyle(.white)
                    }
                }
            }
            Text("色は自動判定です。イメージと違うときは写真を長押しで変更できます。")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.75))
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(
                colors: [Color(argb: 0xFF7C4DFF), Color(argb: 0xFFEC407A), Color(argb: 0xFFFFA726)],
                startPoint: .topLeading, endPoint: .bottomTrailing,
            ),
            in: RoundedRectangle(cornerRadius: 22),
        )
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
        return Image(uiImage: photo.image)
            .resizable()
            .scaledToFill()
            .frame(minWidth: 0, maxWidth: .infinity)
            .aspectRatio(1, contentMode: .fill)
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
                    state.selectedTab = .collage
                } label: {
                    Label("コラージュを作成 (\(state.selection.count))", systemImage: "checkmark.circle.fill")
                        .font(.callout.bold())
                        .padding(.horizontal, 20).padding(.vertical, 14)
                        .background(Color(argb: 0xFF7C4DFF), in: Capsule())
                        .foregroundStyle(.white)
                }
                .padding(.bottom, 12)
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
