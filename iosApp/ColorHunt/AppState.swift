import SwiftUI
import PhotosUI
import Photos
import CoreLocation
import StoreKit
import SharedColor

// MARK: - Model

/// A hunted photo on iOS — mirrors Android's `HuntPhoto`.
struct HuntPhoto: Identifiable, Equatable {
    let id = UUID()
    let image: UIImage
    /// Packed 0xFFRRGGBB dominant colour, nil while analysing / on failure.
    var dominantColor: Int32?
    /// Stable bucket key from the shared classifier (e.g. "RED"), nil = unanalysed.
    var bucketKey: String?

    static func == (lhs: HuntPhoto, rhs: HuntPhoto) -> Bool { lhs.id == rhs.id }
}

enum AppTab: Hashable {
    case hunt, collage, today, grid, map
}

// MARK: - App state

/// Single source of truth shared by all tabs — mirrors Android's PhotoRepository.
@MainActor
final class AppState: ObservableObject {
    @Published var selectedTab: AppTab = .hunt

    @Published var photos: [HuntPhoto] = []
    @Published var selection: Set<UUID> = []
    /// Active colour filter on the Hunt tab (shared so Today's colour can set it).
    @Published var huntFilter: String?
    /// Today's picked theme colour — the Hunt Camera targets it.
    @Published var todayColor: Int32?
    /// Explicit collage order for the selected photos (drag reorder + hue sort).
    @Published var collageOrder: [UUID] = []

    /// Photos the Grid tab picked independently (parity with Android's Grid).
    @Published var gridPhotos: [UIImage] = []

    /// True while an auto-sort library import is running (drives the spinner).
    @Published var importing = false

    /// Geotagged photos for the colour map (recovered from PHAsset locations).
    @Published var mapPhotos: [MapPin] = []
    @Published var mapLoading = false

    // MARK: Pro (StoreKit 2) — free tier shows the collage watermark.
    @Published var isPro: Bool = UserDefaults.standard.bool(forKey: "isPro")
    @Published var proProduct: Product?
    private let proID = "com.souru.colorhunt.pro"

    init() {
        // Restore entitlements and keep listening for purchases/renewals.
        Task { await startPro() }
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

    func add(images: [UIImage]) {
        // Hash off-main (tiny 16×16 renders), then append only unseen images.
        Task.detached(priority: .userInitiated) { [weak self] in
            var pairs: [(UIImage, Int)] = []
            for image in images {
                if let h = DominantColor.quickHash(image) { pairs.append((image, h)) }
            }
            let result = pairs
            await self?.appendUnique(result)
        }
    }

    private func appendUnique(_ pairs: [(UIImage, Int)]) {
        var fresh: [HuntPhoto] = []
        for (image, hash) in pairs where !photoHashes.contains(hash) {
            photoHashes.insert(hash)
            fresh.append(HuntPhoto(image: image, dominantColor: nil, bucketKey: nil))
        }
        photos.append(contentsOf: fresh)
        for photo in fresh {
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
        photos.removeAll()
        selection.removeAll()
        collageOrder.removeAll()
        photoHashes.removeAll()
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
                Task { @MainActor in
                    if !collected.isEmpty { self?.add(images: collected) }
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

    /// Debug-only manual unlock, so the Pro path is testable without App Store Connect.
    func debugUnlockPro() { setPro(true) }

    private func setPro(_ value: Bool) {
        isPro = value
        UserDefaults.standard.set(value, forKey: "isPro")
    }
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
    /// photos when the picker or auto-sort runs more than once.
    static func quickHash(_ image: UIImage) -> Int? {
        let dim = 16
        guard let buffer = rgbaBuffer(from: image, dim: dim, bytesPerRow: dim * 4) else { return nil }
        var hasher = Hasher()
        buffer.withUnsafeBytes { hasher.combine(bytes: $0) }
        return hasher.finalize()
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
            let valueWeight: Double
            if value < 0.12 { valueWeight = 0.2 }
            else if value < 0.28 { valueWeight = 0.2 + 0.8 * (value - 0.12) / 0.16 }
            else if value > 0.92 { valueWeight = 0.5 }
            else { valueWeight = 1.0 }
            let score = Double(e.count) * (0.35 + sat) * valueWeight
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
