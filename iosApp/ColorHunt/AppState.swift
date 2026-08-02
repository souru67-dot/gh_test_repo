import SwiftUI
// ObservableObject/@Published live in Combine. SwiftUI happens to re-export
// them, but Xcode 26's MemberImportVisibility requires the defining module to
// be imported directly, so this import is load-bearing.
import Combine
import PhotosUI
import Photos
import CoreLocation
import StoreKit
// Decoding straight to a target size (see downsampled) instead of decoding full
// frames and shrinking them afterwards.
import ImageIO
import SharedColor

// MARK: - Model

/// A hunted photo on iOS — mirrors Android's `HuntPhoto`.
struct HuntPhoto: Identifiable, Equatable {
    /// Long edge of the resident copy. Sized for the largest Hunt tile a phone
    /// can produce: the grid is `.adaptive(minimum: 104)`, so ~127pt on a Pro
    /// Max, which is 381px at @3x.
    static let thumbSide: CGFloat = 440

    let id: UUID
    /// The **only** pixels that stay in memory — a ~440px copy for the Hunt
    /// grid, the map and the camera's gallery button.
    ///
    /// Full resolution lives in `Documents/HuntStore/<id>.jpg` and is read back
    /// on demand by `AppState.fullImage(for:)`. Keeping every photo decoded at
    /// full size cost 3.6MB each — 718MB for 200 photos, measured — and photos
    /// added through the picker were kept at their original size, which is four
    /// times worse again once they come back from disk. At 440px the same 200
    /// photos cost about 110MB.
    let thumb: UIImage
    /// Packed 0xFFRRGGBB dominant colour, nil while analysing / on failure.
    var dominantColor: Int32?
    /// Stable bucket key from the shared classifier (e.g. "RED"), nil = unanalysed.
    var bucketKey: String?

    init(id: UUID = UUID(), thumb: UIImage, dominantColor: Int32?, bucketKey: String?) {
        self.id = id
        self.thumb = thumb
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

    /// Long edge for the Grid tab's own photos. They are only ever shown 3-up
    /// (about 130pt, so 390px at @3x) and never exported, so there is nothing to
    /// gain from holding them any larger.
    static let gridSide: CGFloat = 600

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
    /// that identifier can never be renamed or reused once it exists.
    ///
    /// Deliberately **without a hyphen**, even though the bundle id has one
    /// (com.yk-dev.ColorHunt). App Store Connect's own help allows hyphens here,
    /// but Xcode's StoreKit Configuration editor rejects them outright — and the
    /// developer forums carry the same complaint about App Store Connect itself.
    /// A product id does not have to match the bundle id, and this one cannot be
    /// corrected after it is created, so it avoids the character entirely.
    private let proID = "com.ykdev.ColorHunt.pro"

    /// Full-resolution photos read back from the store, kept only as long as
    /// there is room. NSCache is the right container precisely because it drops
    /// its contents under memory pressure — the JPEG on disk is the real copy,
    /// and re-reading one is far cheaper than keeping 200 of them decoded.
    private let fullCache = NSCache<NSUUID, UIImage>()

    init() {
        // ~12 photos at 2400px. A collage rarely shows more, and going over
        // simply evicts the least recently used one.
        fullCache.totalCostLimit = 200 * 1024 * 1024
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

    /// The full-resolution photo, for the collage canvas and the crop editor —
    /// the two places that must not work from the 440px resident copy, because
    /// the collage renders the very same view at up to 2160px for export.
    ///
    /// Reads off the main thread; hits the cache on the way back.
    func fullImage(for id: UUID) async -> UIImage? {
        let key = id as NSUUID
        if let hit = fullCache.object(forKey: key) { return hit }
        let url = huntPhotoURL(id)
        // Hoisted out of the guard on purpose: Swift cannot parse a trailing
        // closure inside a guard/if condition — the brace is ambiguous with the
        // statement's own body.
        let loaded = await Task.detached(priority: .userInitiated) {
            UIImage(contentsOfFile: url.path)
        }.value
        guard let image = loaded else { return nil }
        let pixels = image.size.width * image.size.height * image.scale * image.scale
        fullCache.setObject(image, forKey: key, cost: Int(pixels) * 4)
        return image
    }

    // MARK: Intake & analysis

    /// Content hashes of every photo already in the hunt — re-picking or re-running
    /// auto-sort must not stack duplicates.
    private var photoHashes = Set<Int>()
    /// PHAsset local identifiers of every library photo in the hunt. Survives the
    /// two intake paths handing back different pixels for the same photo.
    private var assetIDs = Set<String>()
    private var assetIDByPhoto: [UUID: String] = [:]

    /// [assetID] is the PHAsset local identifier when the photo came from the
    /// library. It is what makes de-duplication work across the two intake
    /// paths: auto-sort gets a downscaled render from PHImageManager while the
    /// picker hands over the original file, and no pixel hash survives that —
    /// resampling shifts enough channel values that the two never agree. The
    /// identifier is the same string either way. The camera has no asset, so it
    /// falls back to the pixel hash, which is fine: a fresh capture is never a
    /// duplicate.
    ///
    /// Takes one photo all the way in — hash, thumbnail, JPEG — and only then
    /// returns, so a caller looping over a picker selection never holds more
    /// than one full frame at a time.
    ///
    /// The picker allows 50 images at once and hands back originals; collecting
    /// those first and adding them in one go is hundreds of megabytes that exist
    /// for no reason. Auto-sort has its own bounded path (see collectRecent).
    func addOne(image: UIImage, assetID: String?) async {
        if let assetID, assetIDs.contains(assetID) { return }
        let full = image
        let prepared = await Task.detached(priority: .userInitiated) { () -> (UIImage, Int)? in
            guard let hash = DominantColor.quickHash(full) else { return nil }
            return (thumbnail(of: full), hash)
        }.value
        guard let prepared else { return }
        let (thumb, hash) = prepared
        if assetID == nil, photoHashes.contains(hash) { return }

        let photo = HuntPhoto(thumb: thumb, dominantColor: nil, bucketKey: nil)
        photoHashes.insert(hash)
        hashByID[photo.id] = hash
        if let assetID {
            assetIDs.insert(assetID)
            assetIDByPhoto[photo.id] = assetID
        }
        photos.append(photo)

        // Awaited, so the full frame is released before the caller reads the
        // next one off the picker.
        let url = huntPhotoURL(photo.id)
        await Task.detached(priority: .userInitiated) {
            try? FileManager.default.createDirectory(at: huntStoreDir(), withIntermediateDirectories: true)
            writeJPEG(full, to: url)
        }.value

        let id = photo.id
        Task.detached(priority: .userInitiated) { [weak self] in
            let color = DominantColor.extract(from: thumb)
            await self?.finishAnalysis(id: id, color: color)
        }
    }

    /// Bulk intake for the camera, which hands over the shots it just took.
    /// The library paths do not come through here — auto-sort has
    /// `collectRecent` and the picker has `addOne`, both of which keep only one
    /// full frame alive at a time.
    func add(images: [UIImage], assetIDs: [String?]? = nil) {
        // Hash off-main (tiny 16×16 renders), then append only unseen images.
        Task.detached(priority: .userInitiated) { [weak self] in
            var pairs: [(image: UIImage, hash: Int, assetID: String?)] = []
            for (i, image) in images.enumerated() {
                guard let h = DominantColor.quickHash(image) else { continue }
                pairs.append((image, h, assetIDs.flatMap { i < $0.count ? $0[i] : nil }))
            }
            let result = pairs
            await self?.appendUnique(result)
        }
    }

    private func appendUnique(_ pairs: [(image: UIImage, hash: Int, assetID: String?)]) {
        var fresh: [(photo: HuntPhoto, full: UIImage)] = []
        for (image, hash, assetID) in pairs {
            // An identifier is authoritative when we have one; the pixel hash is
            // only consulted for photos with no asset behind them.
            if let assetID {
                if assetIDs.contains(assetID) { continue }
            } else if photoHashes.contains(hash) {
                continue
            }
            photoHashes.insert(hash)
            // Only the thumbnail is kept; the full frame goes to disk below.
            let photo = HuntPhoto(thumb: thumbnail(of: image), dominantColor: nil, bucketKey: nil)
            hashByID[photo.id] = hash
            if let assetID {
                assetIDs.insert(assetID)
                assetIDByPhoto[photo.id] = assetID
            }
            fresh.append((photo, image))
        }
        photos.append(contentsOf: fresh.map { $0.photo })
        for (photo, full) in fresh {
            persistPhotoFile(id: photo.id, full: full)
            Task.detached(priority: .userInitiated) { [weak self] in
                // The thumbnail is enough: extract works from a 48×48 render, so
                // the full frame would only be decoded to be thrown away.
                let color = DominantColor.extract(from: photo.thumb)
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
        var fresh: [(photo: HuntPhoto, full: UIImage)] = []
        for image in images {
            let photo = HuntPhoto(thumb: thumbnail(of: image), dominantColor: nil, bucketKey: nil)
            if let h = DominantColor.quickHash(image) {
                photoHashes.insert(h)
                hashByID[photo.id] = h
            }
            fresh.append((photo, image))
        }
        photos.append(contentsOf: fresh.map { $0.photo })
        let ids = fresh.map { $0.photo.id }
        selection = Set(ids)
        collageOrder = ids
        pendingCollageTemplateID = templateID
        pendingCollageLayout = layoutOrdinal
        selectedTab = .collage
        for (photo, full) in fresh {
            persistPhotoFile(id: photo.id, full: full)
            Task.detached(priority: .userInitiated) { [weak self] in
                let color = DominantColor.extract(from: photo.thumb)
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
        assetIDs.removeAll()
        assetIDByPhoto.removeAll()
        fullCache.removeAllObjects()
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
        /// Absent in manifests written before asset-identifier de-duplication.
        var asset: String?
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
                            color: $0.dominantColor, bucket: $0.bucketKey,
                            asset: assetIDByPhoto[$0.id])
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
    private func persistPhotoFile(id: UUID, full: UIImage) {
        let url = huntPhotoURL(id)
        Task.detached(priority: .utility) {
            try? FileManager.default.createDirectory(at: huntStoreDir(), withIntermediateDirectories: true)
            writeJPEG(full, to: url)
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
        // Thumbnails only, decoded straight to size. Reading the stored JPEGs at
        // full resolution here was the other half of the memory problem: every
        // photo came back as a bitmap of up to 2400px whether or not it was ever
        // shown larger than a grid tile.
        let loadedPairs: [(StoredPhoto, UIImage)] = await Task.detached(priority: .userInitiated) {
            stored.compactMap { sp in
                downsampled(contentsOf: huntPhotoURL(sp.id), maxSide: HuntPhoto.thumbSide)
                    .map { (sp, $0) }
            }
        }.value
        let grid: [UIImage] = await Task.detached(priority: .userInitiated) {
            // max(_, 0): the count comes off disk, and a truncated or foreign
            // manifest decoding to a negative here would make 0..<n trap.
            (0..<max(gridCount, 0)).compactMap {
                downsampled(contentsOf: huntGridURL($0), maxSide: AppState.gridSide)
            }
        }.value

        restoring = true
        photos = loadedPairs.map { sp, image in
            HuntPhoto(id: sp.id, thumb: image, dominantColor: sp.color, bucketKey: sp.bucket)
        }
        for (sp, _) in loadedPairs {
            if let h = sp.hash {
                photoHashes.insert(h)
                hashByID[sp.id] = h
            }
            if let a = sp.asset {
                assetIDs.insert(a)
                assetIDByPhoto[sp.id] = a
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
                let color = DominantColor.extract(from: photo.thumb)
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
    /// Everything the main actor needs from one imported asset. The full frame is
    /// already written to disk and released by the time this crosses over.
    private struct Intake {
        let id: UUID
        let thumb: UIImage
        let hash: Int
        let assetID: String
    }

    func importRecentLibraryPhotos(limit: Int = 200) {
        guard !importing else { return }
        importing = true
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { [weak self] status in
            guard status == .authorized || status == .limited else {
                Task { @MainActor in self?.importing = false }
                return
            }
            Task { @MainActor in
                guard let self else { return }
                let known = self.assetIDs
                let result = await Task.detached(priority: .userInitiated) {
                    AppState.collectRecent(limit: limit, known: known)
                }.value
                self.accept(result.intakes,
                            duplicates: result.duplicates,
                            unreadable: result.unreadable)
                self.importing = false
            }
        }
    }

    /// Reads the library one photo at a time: request, hash, thumbnail, JPEG,
    /// release. **One** full frame exists at any moment.
    ///
    /// This used to collect all 200 frames into an array before doing anything
    /// with them — about 820MB at 1200px, which was the app's entire measured
    /// memory peak, and it happened on every auto-sort.
    private nonisolated static func collectRecent(limit: Int, known: Set<String>)
        -> (intakes: [Intake], duplicates: Int, unreadable: Int) {
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

        try? FileManager.default.createDirectory(at: huntStoreDir(), withIntermediateDirectories: true)

        var intakes: [Intake] = []
        var duplicates = 0
        var handedBack = 0
        assets.enumerateObjects { asset, _, _ in
            // Skipped before the pixels are ever requested, which is also why a
            // repeated auto-sort is now nearly instant.
            if known.contains(asset.localIdentifier) {
                duplicates += 1
                handedBack += 1
                return
            }
            // The pool is what actually returns the frame's bytes each turn;
            // without it they would pile up until the enumeration finished.
            autoreleasepool {
                var full: UIImage?
                manager.requestImage(
                    for: asset,
                    targetSize: CGSize(width: 1200, height: 1200),
                    contentMode: .aspectFit,
                    options: req
                ) { image, _ in full = image }
                guard let full else { return }
                handedBack += 1
                guard let hash = DominantColor.quickHash(full) else { return }
                let id = UUID()
                writeJPEG(full, to: huntPhotoURL(id))
                intakes.append(Intake(id: id,
                                      thumb: thumbnail(of: full),
                                      hash: hash,
                                      assetID: asset.localIdentifier))
            }
        }
        // Assets the library never handed back — typically an iCloud original
        // that could not be downloaded.
        return (intakes, duplicates, max(assets.count - handedBack, 0))
    }

    /// Files and thumbnails are already done; this only publishes them.
    private func accept(_ intakes: [Intake], duplicates: Int, unreadable: Int) {
        var fresh: [HuntPhoto] = []
        for intake in intakes {
            guard !assetIDs.contains(intake.assetID) else { continue }
            let photo = HuntPhoto(id: intake.id, thumb: intake.thumb,
                                  dominantColor: nil, bucketKey: nil)
            photoHashes.insert(intake.hash)
            hashByID[photo.id] = intake.hash
            assetIDs.insert(intake.assetID)
            assetIDByPhoto[photo.id] = intake.assetID
            fresh.append(photo)
        }
        photos.append(contentsOf: fresh)
        showImportSummary(ImportSummary(added: fresh.count,
                                        duplicates: duplicates,
                                        unreadable: unreadable))
        for photo in fresh {
            Task.detached(priority: .userInitiated) { [weak self] in
                let color = DominantColor.extract(from: photo.thumb)
                await self?.finishAnalysis(id: photo.id, color: color)
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

    /// Coarse RGB histogram (4 bits per channel) plus true colour sums per bin.
    ///
    /// Shared by the photo path and the camera's live read-out. The camera used
    /// to take a plain mean of the centre square, which mixes the subject with
    /// whatever is behind it and so always drifts darker and greyer than what
    /// the user is pointing at — the same object then sorted one way as a photo
    /// and read another way through the viewfinder. One histogram, one scoring
    /// rule, one answer.
    struct Histogram {
        private var bins: [Int: (count: Int, r: Int, g: Int, b: Int)] = [:]

        /// Spelled out because the synthesised memberwise init would inherit
        /// `bins`'s private access and be unreachable from the camera.
        init() {}

        var isEmpty: Bool { bins.isEmpty }

        mutating func add(r: Int, g: Int, b: Int) {
            let key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4)
            var e = bins[key] ?? (0, 0, 0, 0)
            e = (e.count + 1, e.r + r, e.g + g, e.b + b)
            bins[key] = e
        }

        /// The bin that wins on population × saturation × value-weight — the
        /// subject's colour rather than the largest dark area.
        var dominant: Int32? {
            var bestScore = -1.0
            var best: (r: Int, g: Int, b: Int)?
            for (_, e) in bins {
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
            return Int32(truncatingIfNeeded: (0xFF << 24) | (c.r << 16) | (c.g << 8) | c.b)
        }
    }

    static func extract(from image: UIImage) -> Int32? {
        let dim = 48
        let bytesPerRow = dim * 4
        guard let buffer = rgbaBuffer(from: image, dim: dim, bytesPerRow: bytesPerRow) else { return nil }

        var histogram = Histogram()
        for y in 0..<dim {
            let row = y * bytesPerRow
            for x in 0..<dim {
                let p = row + x * 4
                // Buffer is RGBA8 (premultipliedLast); skip only fully transparent
                // padding, keep every real pixel so the histogram is never empty.
                if buffer[p + 3] < 8 { continue }
                histogram.add(r: Int(buffer[p]), g: Int(buffer[p + 1]), b: Int(buffer[p + 2]))
            }
        }
        guard !histogram.isEmpty else { return nil }
        return histogram.dominant
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

// MARK: - Downsampling

/// Decodes an image **straight to the size we need**.
///
/// `UIImage(contentsOfFile:)` followed by a redraw decodes the whole frame
/// first, so a 2400px photo briefly becomes a 17MB bitmap even when it is only
/// ever shown 100pt wide. ImageIO reads the file's own scaled representation
/// instead, so the big bitmap never exists. This is what makes 200 photos cost
/// ~110MB rather than ~800MB.
func downsampled(contentsOf url: URL, maxSide: CGFloat) -> UIImage? {
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
    return downsampled(source: source, maxSide: maxSide)
}

func downsampled(data: Data, maxSide: CGFloat) -> UIImage? {
    guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
    return downsampled(source: source, maxSide: maxSide)
}

private func downsampled(source: CGImageSource, maxSide: CGFloat) -> UIImage? {
    let options: [CFString: Any] = [
        kCGImageSourceCreateThumbnailFromImageAlways: true,
        // Honours EXIF orientation, so a portrait photo does not come back on
        // its side the way a raw CGImage would.
        kCGImageSourceCreateThumbnailWithTransform: true,
        kCGImageSourceShouldCacheImmediately: true,
        kCGImageSourceThumbnailMaxPixelSize: maxSide,
    ]
    guard let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    else { return nil }
    return UIImage(cgImage: cg)
}

/// Shrinks an in-memory image to the resident thumbnail size. Used for photos
/// that arrive as `UIImage` (the camera) rather than as a file.
func thumbnail(of image: UIImage, maxSide: CGFloat = HuntPhoto.thumbSide) -> UIImage {
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
    // Element 0 is a count that arrived as a Float across the bridge. It is our
    // own Kotlin writing it, but a negative or absurd value here would trap in
    // reserveCapacity and read past the array, so bound it by what the array can
    // actually hold (6 header floats + 4 per cell).
    let capacity = max((Int(arr.size) - 6) / 4, 0)
    let n = min(max(Int(arr.get(index: 0)), 0), capacity)
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
