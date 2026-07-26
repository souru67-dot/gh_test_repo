import SwiftUI
// ObservableObject/@Published live in Combine. SwiftUI happens to re-export
// them, but Xcode 26's MemberImportVisibility requires the defining module to
// be imported directly, so this import is load-bearing.
import Combine
import PhotosUI
import Photos
import CoreLocation
import StoreKit
import SharedColor

// MARK: - Model

/// A hunted photo on iOS — mirrors Android's `HuntPhoto`.
struct HuntPhoto: Identifiable, Equatable {
    let id: UUID
    let image: UIImage
    /// Packed 0xFFRRGGBB dominant colour, nil while analysing / on failure.
    var dominantColor: Int32?
    /// Stable bucket key from the shared classifier (e.g. "RED"), nil = unanalysed.
    var bucketKey: String?

    init(id: UUID = UUID(), image: UIImage, dominantColor: Int32?, bucketKey: String?) {
        self.id = id
        self.image = image
        self.dominantColor = dominantColor
        self.bucketKey = bucketKey
    }

    static func == (lhs: HuntPhoto, rhs: HuntPhoto) -> Bool { lhs.id == rhs.id }
}

enum AppTab: Hashable {
    // `camera` is the tab bar's centre placeholder slot — selecting it opens the
    // full-screen Hunt Camera instead of switching content (see RootTabView).
    case hunt, collage, camera, today, grid, map
}

// MARK: - App state

/// Single source of truth shared by all tabs — mirrors Android's PhotoRepository.
@MainActor
final class AppState: ObservableObject {
    @Published var selectedTab: AppTab = .hunt

    // The hunt itself persists across launches (the collection IS the product):
    // every mutation below schedules a debounced save to Documents/HuntStore.
    @Published var photos: [HuntPhoto] = [] { didSet { scheduleSave() } }
    @Published var selection: Set<UUID> = [] { didSet { scheduleSave() } }
    /// Active colour filter on the Hunt tab (shared so Today's colour can set it).
    @Published var huntFilter: String?
    /// Today's picked theme colour — the Hunt Camera targets it.
    @Published var todayColor: Int32? { didSet { scheduleSave() } }
    /// Explicit collage order for the selected photos (drag reorder + hue sort).
    @Published var collageOrder: [UUID] = [] { didSet { scheduleSave() } }
    /// Template queued by the camera's frame mode; CollageView applies it once
    /// when it becomes visible and clears it.
    @Published var pendingCollageTemplateID: String?
    /// Arrangement the shots were framed in, so the collage opens in the same
    /// shape the camera guide showed (nil = keep the template's own layout).
    @Published var pendingCollageLayout: Int32?

    /// Photos the Grid tab picked independently (parity with Android's Grid).
    @Published var gridPhotos: [UIImage] = [] { didSet { scheduleGridSave(); scheduleSave() } }

    /// True while an auto-sort library import is running (drives the spinner).
    @Published var importing = false

    /// Geotagged photos for the colour map (recovered from PHAsset locations).
    @Published var mapPhotos: [MapPin] = []
    @Published var mapLoading = false

    // MARK: Pro (StoreKit 2) — free tier shows the collage watermark.
    @Published var isPro: Bool = UserDefaults.standard.bool(forKey: "isPro")
    @Published var proProduct: Product?
    /// Must match the In-App Purchase created in App Store Connect exactly, and
    /// that identifier can never be renamed once it exists — so it shares the
    /// bundle's prefix (com.yk-dev.ColorHunt) to stay unambiguous.
    private let proID = "com.yk-dev.ColorHunt.pro"

    init() {
        // Restore entitlements and keep listening for purchases/renewals.
        Task { await startPro() }
        // Bring the hunt back from disk (images decode off-main).
        Task { await loadStore() }
    }

    var selectedPhotos: [HuntPhoto] { photos.filter { selection.contains($0.id) } }

    /// Selected photos in the user's chosen collage order.
    var orderedSelectedPhotos: [HuntPhoto] {
        let byId = Dictionary(photos.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        return collageOrder.compactMap { byId[$0] }
    }

    /// Buckets that actually have photos, in the shared display order.
    var groupedByBucket: [(key: String, photos: [HuntPhoto])] {
        let order = ColorBridge.shared.bucketKeys()   // [String] — bridges cleanly
        let groups = Dictionary(grouping: photos.filter { $0.bucketKey != nil }, by: { $0.bucketKey! })
        return order.compactMap { key in groups[key].map { (key, $0) } }
    }

    var unanalysed: [HuntPhoto] { photos.filter { $0.bucketKey == nil } }

    // MARK: Intake & analysis

    /// Content hashes of every photo already in the hunt — re-picking or re-running
    /// auto-sort must not stack duplicates.
    private var photoHashes = Set<Int>()

    /// [unreadable] counts assets the library could not hand over at all (an
    /// iCloud download that failed, say). Passing it — even as 0 — is what
    /// marks this as an auto-sort run and produces the summary toast; the
    /// photo picker and the camera pass nil and stay silent.
    func add(images: [UIImage], unreadable: Int? = nil) {
        // Hash off-main (tiny 16×16 renders), then append only unseen images.
        Task.detached(priority: .userInitiated) { [weak self] in
            var pairs: [(UIImage, Int)] = []
            for image in images {
                if let h = DominantColor.quickHash(image) { pairs.append((image, h)) }
            }
            let result = pairs
            // An image that would not hash cannot be de-duplicated, so it is
            // dropped here and counted with the unreadable ones.
            let lost = unreadable.map { $0 + images.count - pairs.count }
            await self?.appendUnique(result, unreadable: lost)
        }
    }

    private func appendUnique(_ pairs: [(UIImage, Int)], unreadable: Int? = nil) {
        var fresh: [HuntPhoto] = []
        for (image, hash) in pairs where !photoHashes.contains(hash) {
            photoHashes.insert(hash)
            let photo = HuntPhoto(image: image, dominantColor: nil, bucketKey: nil)
            hashByID[photo.id] = hash
            fresh.append(photo)
        }
        photos.append(contentsOf: fresh)
        if let unreadable {
            showImportSummary(ImportSummary(added: fresh.count,
                                            duplicates: pairs.count - fresh.count,
                                            unreadable: unreadable))
        }
        for photo in fresh {
            persistPhotoFile(photo)
            Task.detached(priority: .userInitiated) { [weak self] in
                let color = DominantColor.extract(from: photo.image)
                await self?.finishAnalysis(id: photo.id, color: color)
            }
        }
    }

    /// Why an auto-sort of "the last 200" rarely adds 200: photos already in
    /// the hunt are skipped, and the library cannot always hand every asset
    /// over. Reporting both makes the number make sense instead of looking
    /// like a bug.
    struct ImportSummary: Equatable {
        let added: Int
        let duplicates: Int
        let unreadable: Int

        var message: String {
            var text = String(format: NSLocalizedString("%lld枚を追加しました", comment: ""), added)
            var notes: [String] = []
            if duplicates > 0 {
                notes.append(String(format: NSLocalizedString("重複%lld枚", comment: ""), duplicates))
            }
            if unreadable > 0 {
                notes.append(String(format: NSLocalizedString("読み込めず%lld枚", comment: ""), unreadable))
            }
            guard !notes.isEmpty else { return text }
            // Brackets and the separator are localized too: CJK wants 全角（・）
            // with no spaces, Latin wants " (a, b)".
            let joined = notes.joined(separator: NSLocalizedString("・", comment: "note separator"))
            return String(format: NSLocalizedString("%1$@（%2$@）", comment: "message then notes"),
                          text, joined)
        }
    }

    /// Result of the most recent auto-sort; clears itself so the toast fades.
    @Published var importSummary: ImportSummary?

    private func showImportSummary(_ summary: ImportSummary) {
        importSummary = summary
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            if importSummary == summary { importSummary = nil }
        }
    }

    /// Frame-mode hand-off from the Hunt Camera: add the template shots, select
    /// exactly them in shooting order, queue the template and land on コラージュ.
    /// Fresh captures are unique, so this appends directly (no dedupe race).
    func startCollage(with images: [UIImage], templateID: String, layoutOrdinal: Int32? = nil) {
        var fresh: [HuntPhoto] = []
        for image in images {
            let photo = HuntPhoto(image: image, dominantColor: nil, bucketKey: nil)
            if let h = DominantColor.quickHash(image) {
                photoHashes.insert(h)
                hashByID[photo.id] = h
            }
            fresh.append(photo)
        }
        photos.append(contentsOf: fresh)
        let ids = fresh.map { $0.id }
        selection = Set(ids)
        collageOrder = ids
        pendingCollageTemplateID = templateID
        pendingCollageLayout = layoutOrdinal
        selectedTab = .collage
        for photo in fresh {
            persistPhotoFile(photo)
            Task.detached(priority: .userInitiated) { [weak self] in
                let color = DominantColor.extract(from: photo.image)
                await self?.finishAnalysis(id: photo.id, color: color)
            }
        }
    }

    private func finishAnalysis(id: UUID, color: Int32?) {
        guard let idx = photos.firstIndex(where: { $0.id == id }) else { return }
        photos[idx].dominantColor = color
        if let color {
            // Same classifier as Android (KMP shared module).
            photos[idx].bucketKey = ColorBridge.shared.classifyKey(colorInt: color)
        }
    }

    func toggleSelection(_ id: UUID) {
        if selection.contains(id) {
            selection.remove(id)
            collageOrder.removeAll { $0 == id }
        } else {
            selection.insert(id)
            collageOrder.append(id)
        }
    }

    /// Manual override — the hunter has the final say (parity with Android).
    func rebucket(_ id: UUID, to key: String) {
        guard let idx = photos.firstIndex(where: { $0.id == id }) else { return }
        photos[idx].bucketKey = key
    }

    func clearAll() {
        let ids = photos.map { $0.id }
        photos.removeAll()
        selection.removeAll()
        collageOrder.removeAll()
        photoHashes.removeAll()
        hashByID.removeAll()
        Task.detached(priority: .utility) {
            for id in ids {
                try? FileManager.default.removeItem(at: huntPhotoURL(id))
            }
        }
    }

    // MARK: Persistence (Documents/HuntStore: JPEGs + manifest.json)

    /// id → stable content hash, kept so the manifest can restore the dedupe
    /// set without re-decoding every image at launch.
    private var hashByID: [UUID: Int] = [:]
    /// Suppresses save scheduling while loadStore() writes restored state back.
    private var restoring = false
    private var saveTask: Task<Void, Never>?
    private var gridSaveTask: Task<Void, Never>?

    /// Everything except the pixels; images live as JPEGs next to it.
    private struct StoredPhoto: Codable {
        let id: UUID
        let hash: Int?
        let color: Int32?
        let bucket: String?
    }
    private struct Manifest: Codable {
        var photos: [StoredPhoto]
        var selection: [UUID]
        var order: [UUID]
        var todayColor: Int32?
        var gridCount: Int
    }

    /// Debounced so bursts (200-photo import, per-photo analysis) coalesce
    /// into one small JSON write.
    private func scheduleSave() {
        guard !restoring else { return }
        saveTask?.cancel()
        saveTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 900_000_000)
            guard !Task.isCancelled else { return }
            self?.saveManifest()
        }
    }

    private func saveManifest() {
        let manifest = Manifest(
            photos: photos.map {
                StoredPhoto(id: $0.id, hash: hashByID[$0.id],
                            color: $0.dominantColor, bucket: $0.bucketKey)
            },
            selection: Array(selection),
            order: collageOrder,
            todayColor: todayColor,
            gridCount: gridPhotos.count
        )
        guard let data = try? JSONEncoder().encode(manifest) else { return }
        Task.detached(priority: .utility) {
            let dir = huntStoreDir()
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try? data.write(to: dir.appendingPathComponent("manifest.json"), options: .atomic)
        }
    }

    /// Writes one hunt photo's JPEG (downscaled to ~2400px — plenty for the
    /// 2160px Pro export) off the main thread.
    private func persistPhotoFile(_ photo: HuntPhoto) {
        let image = photo.image
        let url = huntPhotoURL(photo.id)
        Task.detached(priority: .utility) {
            try? FileManager.default.createDirectory(at: huntStoreDir(), withIntermediateDirectories: true)
            writeJPEG(image, to: url)
        }
    }

    /// Grid images are few and unkeyed, so the whole set is rewritten
    /// (debounced) as grid-0.jpg… and stale tail files are removed.
    private func scheduleGridSave() {
        guard !restoring else { return }
        gridSaveTask?.cancel()
        let snapshot = gridPhotos
        gridSaveTask = Task {
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            guard !Task.isCancelled else { return }
            Task.detached(priority: .utility) {
                let dir = huntStoreDir()
                try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
                for (i, image) in snapshot.enumerated() {
                    writeJPEG(image, to: huntGridURL(i), maxSide: 1600)
                }
                var i = snapshot.count
                while FileManager.default.fileExists(atPath: huntGridURL(i).path) {
                    try? FileManager.default.removeItem(at: huntGridURL(i))
                    i += 1
                }
            }
        }
    }

    private func loadStore() async {
        let dir = huntStoreDir()
        guard let data = try? Data(contentsOf: dir.appendingPathComponent("manifest.json")),
              let manifest = try? JSONDecoder().decode(Manifest.self, from: data) else { return }

        let stored = manifest.photos
        let gridCount = manifest.gridCount
        let loadedPairs: [(StoredPhoto, UIImage)] = await Task.detached(priority: .userInitiated) {
            stored.compactMap { sp in
                UIImage(contentsOfFile: huntPhotoURL(sp.id).path).map { (sp, $0) }
            }
        }.value
        let grid: [UIImage] = await Task.detached(priority: .userInitiated) {
            (0..<gridCount).compactMap { UIImage(contentsOfFile: huntGridURL($0).path) }
        }.value

        restoring = true
        photos = loadedPairs.map { sp, image in
            HuntPhoto(id: sp.id, image: image, dominantColor: sp.color, bucketKey: sp.bucket)
        }
        for (sp, _) in loadedPairs {
            if let h = sp.hash {
                photoHashes.insert(h)
                hashByID[sp.id] = h
            }
        }
        let valid = Set(photos.map { $0.id })
        selection = Set(manifest.selection).intersection(valid)
        collageOrder = manifest.order.filter { valid.contains($0) }
        todayColor = manifest.todayColor
        gridPhotos = grid
        restoring = false

        // Finish any analysis that was mid-flight when the app quit.
        for photo in photos where photo.bucketKey == nil {
            Task.detached(priority: .userInitiated) { [weak self] in
                let color = DominantColor.extract(from: photo.image)
                await self?.finishAnalysis(id: photo.id, color: color)
            }
        }
    }

    // MARK: Collage ordering (parity with Android's move / sortByHue)

    /// Swap two collage cells (drag one onto another). A swap — not
    /// remove+insert — so every other cell stays exactly where it was.
    func moveCollage(from: Int, to: Int) {
        guard collageOrder.indices.contains(from), collageOrder.indices.contains(to), from != to else { return }
        collageOrder.swapAt(from, to)
    }

    /// Auto-arrange the collage by hue — a quiet rainbow run, like Android's sortByHue.
    func sortCollageByHue() {
        let byId = Dictionary(photos.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        collageOrder.sort { a, b in
            hueOf(byId[a]?.dominantColor) < hueOf(byId[b]?.dominantColor)
        }
    }

    private func hueOf(_ packed: Int32?) -> Double {
        guard let packed else { return 999 } // unanalysed sinks to the end
        let v = Int(UInt32(bitPattern: packed))
        let r = Double((v >> 16) & 0xFF) / 255, g = Double((v >> 8) & 0xFF) / 255, b = Double(v & 0xFF) / 255
        let maxC = max(r, g, b), minC = min(r, g, b), d = maxC - minC
        if d < 0.0001 { return -1 } // greys first
        let h: Double
        switch maxC {
        case r: h = (g - b) / d + (g < b ? 6 : 0)
        case g: h = (b - r) / d + 2
        default: h = (r - g) / d + 4
        }
        return h * 60
    }

    // MARK: Grid ordering

    func moveGridPhoto(from: Int, to: Int) {
        guard gridPhotos.indices.contains(from), to >= 0, to <= gridPhotos.count, from != to else { return }
        let item = gridPhotos.remove(at: from)
        gridPhotos.insert(item, at: min(to, gridPhotos.count))
    }

    // MARK: Auto-sort from the photo library (parity with Android's importRecent)

    /// Loads the most recent [limit] library photos and auto-sorts them by colour.
    /// Needs `NSPhotoLibraryUsageDescription` in Info.plist.
    func importRecentLibraryPhotos(limit: Int = 200) {
        guard !importing else { return }
        importing = true
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { [weak self] status in
            guard status == .authorized || status == .limited else {
                Task { @MainActor in self?.importing = false }
                return
            }
            DispatchQueue.global(qos: .userInitiated).async {
                let options = PHFetchOptions()
                options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
                options.fetchLimit = limit
                let assets = PHAsset.fetchAssets(with: .image, options: options)

                let manager = PHImageManager.default()
                let req = PHImageRequestOptions()
                req.deliveryMode = .highQualityFormat
                req.isSynchronous = true
                req.isNetworkAccessAllowed = true
                req.resizeMode = .fast

                var images: [UIImage] = []
                assets.enumerateObjects { asset, _, _ in
                    manager.requestImage(
                        for: asset,
                        targetSize: CGSize(width: 1200, height: 1200),
                        contentMode: .aspectFit,
                        options: req
                    ) { image, _ in
                        if let image { images.append(image) }
                    }
                }
                // Snapshot into a let so the concurrent Task doesn't capture a var.
                let collected = images
                // Assets the library never handed back — typically an iCloud
                // original that could not be downloaded.
                let missed = max(assets.count - collected.count, 0)
                Task { @MainActor in
                    self?.add(images: collected, unreadable: missed)
                    self?.importing = false
                }
            }
        }
    }

    // MARK: Colour map (parity with Android's Exif-GPS map)

    /// Fetches recent geotagged library photos, classifies each colour and drops a
    /// colour pin at its location. PhotosPicker strips GPS, so — like Android's
    /// media-store path — we read PHAsset.location directly.
    func loadMapPhotos(limit: Int = 300) {
        guard !mapLoading else { return }
        mapLoading = true
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { [weak self] status in
            guard status == .authorized || status == .limited else {
                Task { @MainActor in self?.mapLoading = false }
                return
            }
            DispatchQueue.global(qos: .userInitiated).async {
                let options = PHFetchOptions()
                options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
                options.fetchLimit = limit
                let assets = PHAsset.fetchAssets(with: .image, options: options)

                let manager = PHImageManager.default()
                let req = PHImageRequestOptions()
                req.deliveryMode = .fastFormat
                req.isSynchronous = true
                req.resizeMode = .fast

                var pins: [MapPin] = []
                assets.enumerateObjects { asset, _, _ in
                    guard let loc = asset.location else { return }
                    manager.requestImage(
                        for: asset,
                        targetSize: CGSize(width: 240, height: 240),
                        contentMode: .aspectFill,
                        options: req
                    ) { image, _ in
                        guard let image, let color = DominantColor.extract(from: image) else { return }
                        pins.append(MapPin(coordinate: loc.coordinate, color: color, thumbnail: image))
                    }
                }
                let collected = pins
                Task { @MainActor in
                    self?.mapPhotos = collected
                    self?.mapLoading = false
                }
            }
        }
    }
}

extension AppState {

    /// Load the product, restore entitlements, and observe future transactions.
    func startPro() async {
        proProduct = try? await Product.products(for: [proID]).first
        await refreshEntitlements()
        for await update in Transaction.updates {
            if case .verified(let txn) = update, txn.productID == proID {
                await txn.finish()
                setPro(true)
            }
        }
    }

    func purchasePro() async {
        guard let product = proProduct else { return }
        guard let result = try? await product.purchase() else { return }
        if case .success(let verification) = result, case .verified(let txn) = verification {
            await txn.finish()
            setPro(true)
        }
    }

    func restorePro() async {
        try? await AppStore.sync()
        await refreshEntitlements()
    }

    private func refreshEntitlements() async {
        for await result in Transaction.currentEntitlements {
            if case .verified(let txn) = result, txn.productID == proID {
                setPro(true)
                return
            }
        }
    }

    /// Debug-only manual toggle, so BOTH tiers are testable without App Store
    /// Connect: flip to Pro to check unlocks, flip back to check the paywall.
    func debugUnlockPro() { setPro(!isPro) }

    private func setPro(_ value: Bool) {
        isPro = value
        UserDefaults.standard.set(value, forKey: "isPro")
    }
}

// MARK: - Store file helpers (file-scope: callable from any isolation)

private func huntStoreDir() -> URL {
    FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("HuntStore", isDirectory: true)
}

private func huntPhotoURL(_ id: UUID) -> URL {
    huntStoreDir().appendingPathComponent("\(id.uuidString).jpg")
}

private func huntGridURL(_ index: Int) -> URL {
    huntStoreDir().appendingPathComponent("grid-\(index).jpg")
}

/// Downscales (if needed) and writes a JPEG. UIGraphicsImageRenderer is
/// thread-safe, so this can run on background queues.
private func writeJPEG(_ image: UIImage, to url: URL, maxSide: CGFloat = 2400) {
    var out = image
    let longest = max(image.size.width, image.size.height)
    if longest > maxSide, longest > 0 {
        let s = maxSide / longest
        let size = CGSize(width: image.size.width * s, height: image.size.height * s)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        out = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }
    guard let data = out.jpegData(compressionQuality: 0.85) else { return }
    try? data.write(to: url, options: .atomic)
}

/// A geotagged photo on the colour map.
struct MapPin: Identifiable {
    let id = UUID()
    let coordinate: CLLocationCoordinate2D
    let color: Int32
    let thumbnail: UIImage
}

// MARK: - Dominant colour extraction

/// Mirrors Android's PaletteExtractor heuristics: downsample, build a coarse
/// histogram, then score population × (0.35 + saturation) × value-weight so the
/// subject's colour beats dark backgrounds and blown highlights.
enum DominantColor {

    /// Cheap content fingerprint (16×16 RGBA bytes hashed) used to skip duplicate
    /// photos when the picker or auto-sort runs more than once. FNV-1a, NOT
    /// Swift's Hasher: Hasher is seeded per-process, and these hashes are
    /// persisted in the HuntStore manifest to survive relaunches.
    static func quickHash(_ image: UIImage) -> Int? {
        let dim = 16
        guard let buffer = rgbaBuffer(from: image, dim: dim, bytesPerRow: dim * 4) else { return nil }
        var h: UInt64 = 0xcbf29ce484222325
        for byte in buffer {
            h = (h ^ UInt64(byte)) &* 0x100000001b3
        }
        return Int(bitPattern: UInt(truncatingIfNeeded: h))
    }

    static func extract(from image: UIImage) -> Int32? {
        let dim = 48
        let bytesPerRow = dim * 4
        guard let buffer = rgbaBuffer(from: image, dim: dim, bytesPerRow: bytesPerRow) else { return nil }

        // Quantise to 4 bits/channel; accumulate population and true colour sums.
        var population = [Int: (count: Int, r: Int, g: Int, b: Int)]()
        for y in 0..<dim {
            let row = y * bytesPerRow
            for x in 0..<dim {
                let p = row + x * 4
                // Buffer is RGBA8 (premultipliedLast); skip only fully transparent
                // padding, keep every real pixel so the histogram is never empty.
                if buffer[p + 3] < 8 { continue }
                let r = Int(buffer[p])
                let g = Int(buffer[p + 1])
                let b = Int(buffer[p + 2])
                let key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4)
                var e = population[key] ?? (0, 0, 0, 0)
                e = (e.count + 1, e.r + r, e.g + g, e.b + b)
                population[key] = e
            }
        }
        guard !population.isEmpty else { return nil }

        var bestScore = -1.0
        var best: (r: Int, g: Int, b: Int)? = nil
        for (_, e) in population {
            let r = Double(e.r) / Double(e.count) / 255.0
            let g = Double(e.g) / Double(e.count) / 255.0
            let b = Double(e.b) / Double(e.count) / 255.0
            let maxC = max(r, g, b), minC = min(r, g, b)
            let value = maxC
            let sat = maxC == 0 ? 0 : (maxC - minC) / maxC
            // Same shape as Android's PaletteExtractor.scoreOf.
            //
            // Pastels used to be penalised twice — once for being bright and
            // again for being low-saturation — so a small dark region beat a
            // photo that was mostly pale yellow or pink. The bright penalty
            // now only applies to near-white (a blown highlight, which is what
            // it was for), and the saturation term has a higher floor and a
            // gentler slope so a soft colour still competes with a vivid one.
            let valueWeight: Double
            if value < 0.12 { valueWeight = 0.2 }
            else if value < 0.28 { valueWeight = 0.2 + 0.8 * (value - 0.12) / 0.16 }
            else if value > 0.92 { valueWeight = sat < 0.10 ? 0.45 : 0.85 }
            else { valueWeight = 1.0 }
            let score = Double(e.count) * (0.45 + 0.75 * sat) * valueWeight
            if score > bestScore {
                bestScore = score
                best = (e.r / e.count, e.g / e.count, e.b / e.count)
            }
        }
        guard let c = best else { return nil }
        let packed = (0xFF << 24) | (c.r << 16) | (c.g << 8) | c.b
        return Int32(truncatingIfNeeded: packed)
    }

    /// Draws [image] (orientation-corrected by UIKit) into an RGBA8 buffer we own,
    /// so extraction never depends on the source's colour space, alpha config or
    /// bit depth — those mismatches were leaving some photos stuck "analysing".
    private static func rgbaBuffer(from image: UIImage, dim: Int, bytesPerRow: Int) -> [UInt8]? {
        // 1. Orientation-correct + downscale via UIKit (handles EXIF orientation).
        let size = CGSize(width: dim, height: dim)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        let small = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
        guard let cgImage = small.cgImage else { return nil }

        // 2. Repaint into a context we control, so any source format → plain RGBA8.
        var buffer = [UInt8](repeating: 0, count: bytesPerRow * dim)
        let ok = buffer.withUnsafeMutableBytes { raw -> Bool in
            guard let ctx = CGContext(
                data: raw.baseAddress,
                width: dim,
                height: dim,
                bitsPerComponent: 8,
                bytesPerRow: bytesPerRow,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            ctx.interpolationQuality = .low
            ctx.draw(cgImage, in: CGRect(x: 0, y: 0, width: dim, height: dim))
            return true
        }
        return ok ? buffer : nil
    }
}

// MARK: - Small helpers

extension Color {
    /// Packed 0xAARRGGBB / 0xFFRRGGBB → SwiftUI Color.
    init(argb: Int64) {
        let r = Double((argb >> 16) & 0xFF) / 255.0
        let g = Double((argb >> 8) & 0xFF) / 255.0
        let b = Double(argb & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b)
    }

    init(packed: Int32) {
        self.init(argb: Int64(UInt32(bitPattern: packed)))
    }
}

func hexString(_ packed: Int32) -> String {
    String(format: "#%06X", Int(UInt32(bitPattern: packed)) & 0xFFFFFF)
}

// MARK: - Collage layout decoding (shared geometry)

struct DecodedCollageLayout {
    var cells: [CGRect]
    var palette: CGRect?
}

/// Decodes the flat FloatArray from `CollageBridge.computeFlat` — see its KDoc
/// for the layout. Uses only primitive access so it's robust to how Kotlin
/// stdlib types are named in the generated framework.
func decodeLayout(_ arr: KotlinFloatArray) -> DecodedCollageLayout {
    func f(_ i: Int) -> CGFloat { CGFloat(arr.get(index: Int32(i))) }
    let n = Int(arr.get(index: 0))
    let hasPalette = arr.get(index: 1) > 0.5
    let palette: CGRect? = hasPalette
        ? CGRect(x: f(2), y: f(3), width: f(4) - f(2), height: f(5) - f(3))
        : nil
    var cells: [CGRect] = []
    cells.reserveCapacity(n)
    for i in 0..<n {
        let b = 6 + i * 4
        cells.append(CGRect(x: f(b), y: f(b + 1), width: f(b + 2) - f(b), height: f(b + 3) - f(b + 1)))
    }
    return DecodedCollageLayout(cells: cells, palette: palette)
}

/// Localised bucket names (v0: ja). Move to Localizable.strings for expansion.
func bucketLabel(_ key: String) -> String {
    let jp: String
    switch key {
    case "RED": jp = "赤"
    case "ORANGE": jp = "橙"
    case "YELLOW": jp = "黄"
    case "YELLOW_GREEN": jp = "黄緑"
    case "GREEN": jp = "緑"
    case "CYAN": jp = "水色"
    case "BLUE": jp = "青"
    case "PURPLE": jp = "紫"
    case "PINK": jp = "ピンク"
    case "WHITE": jp = "白"
    case "BLACK": jp = "黒"
    case "GRAY": jp = "グレー"
    default: return key
    }
    // The Japanese name doubles as the Localizable.strings key.
    return NSLocalizedString(jp, comment: "colour bucket name")
}
