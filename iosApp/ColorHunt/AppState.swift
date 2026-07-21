import SwiftUI
import PhotosUI
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

    /// Photos the Grid tab picked independently (parity with Android's Grid).
    @Published var gridPhotos: [UIImage] = []

    var selectedPhotos: [HuntPhoto] { photos.filter { selection.contains($0.id) } }

    /// Buckets that actually have photos, in the shared display order.
    var groupedByBucket: [(key: String, photos: [HuntPhoto])] {
        let order = ColorBridge.shared.bucketKeys()   // [String] — bridges cleanly
        let groups = Dictionary(grouping: photos.filter { $0.bucketKey != nil }, by: { $0.bucketKey! })
        return order.compactMap { key in groups[key].map { (key, $0) } }
    }

    var unanalysed: [HuntPhoto] { photos.filter { $0.bucketKey == nil } }

    // MARK: Intake & analysis

    func add(images: [UIImage]) {
        let fresh = images.map { HuntPhoto(image: $0, dominantColor: nil, bucketKey: nil) }
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
        if selection.contains(id) { selection.remove(id) } else { selection.insert(id) }
    }

    /// Manual override — the hunter has the final say (parity with Android).
    func rebucket(_ id: UUID, to key: String) {
        guard let idx = photos.firstIndex(where: { $0.id == id }) else { return }
        photos[idx].bucketKey = key
    }

    func clearAll() {
        photos.removeAll()
        selection.removeAll()
    }
}

// MARK: - Dominant colour extraction

/// Mirrors Android's PaletteExtractor heuristics: downsample, build a coarse
/// histogram, then score population × (0.35 + saturation) × value-weight so the
/// subject's colour beats dark backgrounds and blown highlights.
enum DominantColor {

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
    switch key {
    case "RED": return "赤"
    case "ORANGE": return "橙"
    case "YELLOW": return "黄"
    case "YELLOW_GREEN": return "黄緑"
    case "GREEN": return "緑"
    case "CYAN": return "水色"
    case "BLUE": return "青"
    case "PURPLE": return "紫"
    case "PINK": return "ピンク"
    case "WHITE": return "白"
    case "BLACK": return "黒"
    case "GRAY": return "グレー"
    default: return key
    }
}
