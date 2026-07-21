import SwiftUI
import PhotosUI

/// グリッド — Instagram-style 4:5 feed preview with independently picked
/// photos (parity with Android's Grid tab). v0: pick + delete; drag reorder
/// lands with the persistence pass.
struct GridPreviewView: View {
    @EnvironmentObject private var state: AppState
    @State private var pickerItems: [PhotosPickerItem] = []

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 2), count: 3)

    var body: some View {
        NavigationStack {
            ScrollView {
                profileHeader
                if state.gridPhotos.isEmpty {
                    emptyState
                } else {
                    LazyVGrid(columns: columns, spacing: 2) {
                        ForEach(Array(state.gridPhotos.enumerated()), id: \.offset) { index, image in
                            // 4:5 feed tile: clear spacer fixes the cell, image fills
                            // it, then clip — so it can't overflow into the next column.
                            Color.clear
                                .aspectRatio(4.0 / 5.0, contentMode: .fit)
                                .overlay {
                                    Image(uiImage: image)
                                        .resizable()
                                        .scaledToFill()
                                }
                                .clipped()
                                .contextMenu {
                                    Button(role: .destructive) {
                                        state.gridPhotos.remove(at: index)
                                    } label: {
                                        Label("削除", systemImage: "trash")
                                    }
                                }
                        }
                    }
                }
            }
            .background(Color(argb: 0xFF101014))
            .navigationTitle("グリッド")
            .toolbar {
                PhotosPicker(selection: $pickerItems, maxSelectionCount: 30, matching: .images) {
                    Image(systemName: "plus")
                }
            }
            .onChange(of: pickerItems) { _, items in
                Task { await load(items) }
            }
        }
    }

    private var profileHeader: some View {
        HStack(spacing: 20) {
            Circle()
                .fill(LinearGradient(colors: [Color(argb: 0xFF7C4DFF), Color(argb: 0xFFEC407A)],
                                     startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: 72, height: 72)
            ForEach(["投稿", "フォロワー", "フォロー"], id: \.self) { label in
                VStack {
                    Text("1").font(.headline)
                    Text(label).font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
        }
        .padding()
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Text("写真を選んでフィードのレイアウトを組みましょう。")
                .font(.subheadline).foregroundStyle(.secondary)
            PhotosPicker(selection: $pickerItems, maxSelectionCount: 30, matching: .images) {
                Label("写真を追加", systemImage: "plus")
            }
            .buttonStyle(.borderedProminent)
        }
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
