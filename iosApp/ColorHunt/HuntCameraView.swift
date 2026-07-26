import SwiftUI
// Required for ObservableObject/@Published under MemberImportVisibility
// (see AppState.swift).
import Combine
import AVFoundation
import SharedColor

// MARK: - Camera engine

/// Drives the capture session: a live video feed for real-time centre-colour
/// read-out, plus a photo output for the actual hunt shot. Handles lens
/// switching (0.5×/1×/2×), front camera, flash, tap-to-focus and exposure
/// bias. Kept off the main actor because AVFoundation delivers frames on its
/// own queues; UI-facing values hop back to main.
final class CameraController: NSObject, ObservableObject {
    @Published var liveColor: Int32?          // colour under the reticle, ~10 fps
    @Published var authorized = true
    @Published var running = false
    @Published var lensOptions: [LensOption] = []
    @Published var selectedLensID = "1"
    @Published var position: AVCaptureDevice.Position = .back
    @Published var hasFlash = false

    /// One entry per zoom chip. 0.5× is the ultra-wide module when present;
    /// 2× is a digital zoom on the wide module so every device gets it.
    struct LensOption: Identifiable {
        let id: String
        let label: String
        let device: AVCaptureDevice
        let zoom: CGFloat
    }

    let session = AVCaptureSession()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let photoOutput = AVCapturePhotoOutput()
    private let sessionQueue = DispatchQueue(label: "colorhunt.camera.session")
    private let sampleQueue = DispatchQueue(label: "colorhunt.camera.sample")
    private var lastSample = Date.distantPast
    private var onCapture: ((UIImage) -> Void)?
    private var currentInput: AVCaptureDeviceInput?
    private var currentDevice: AVCaptureDevice?

    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureIfNeeded()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async { self?.authorized = granted }
                if granted { self?.configureIfNeeded() }
            }
        default:
            DispatchQueue.main.async { self.authorized = false }
        }
    }

    private var configured = false
    private func configureIfNeeded() {
        sessionQueue.async {
            if !self.configured {
                self.session.beginConfiguration()
                self.session.sessionPreset = .photo

                let wide = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)
                let ultra = AVCaptureDevice.default(.builtInUltraWideCamera, for: .video, position: .back)
                let tele = AVCaptureDevice.default(.builtInTelephotoCamera, for: .video, position: .back)
                var options: [LensOption] = []
                if let ultra {
                    options.append(LensOption(id: "0.5", label: "0.5×", device: ultra, zoom: 1))
                }
                // Pro phones carry a real telephoto (2×/3×/4×/5× depending on
                // generation) — label it from its actual focal ratio, and skip
                // the digital 2× chip when the tele IS 2× (12 Pro).
                var teleLabel: String?
                if let wide, let tele {
                    teleLabel = Self.opticalZoomLabel(wide: wide, tele: tele)
                }
                if let wide {
                    options.append(LensOption(id: "1", label: "1×", device: wide, zoom: 1))
                    if teleLabel != "2×" {
                        options.append(LensOption(id: "2", label: "2×", device: wide, zoom: 2))
                    }
                }
                if let tele, let teleLabel {
                    options.append(LensOption(id: "tele", label: teleLabel, device: tele, zoom: 1))
                }

                if let device = wide,
                   let input = try? AVCaptureDeviceInput(device: device),
                   self.session.canAddInput(input) {
                    self.session.addInput(input)
                    self.currentInput = input
                    self.currentDevice = device
                }

                self.videoOutput.videoSettings = [
                    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
                ]
                self.videoOutput.alwaysDiscardsLateVideoFrames = true
                self.videoOutput.setSampleBufferDelegate(self, queue: self.sampleQueue)
                if self.session.canAddOutput(self.videoOutput) { self.session.addOutput(self.videoOutput) }
                if self.session.canAddOutput(self.photoOutput) { self.session.addOutput(self.photoOutput) }
                self.photoOutput.maxPhotoQualityPrioritization = .quality

                self.session.commitConfiguration()
                self.configured = true

                let flash = self.currentDevice?.hasFlash ?? false
                DispatchQueue.main.async {
                    self.lensOptions = options
                    self.hasFlash = flash
                }
            }
            if !self.session.isRunning { self.session.startRunning() }
            DispatchQueue.main.async { self.running = true }
        }
    }

    func stop() {
        sessionQueue.async {
            if self.session.isRunning { self.session.stopRunning() }
            DispatchQueue.main.async { self.running = false }
        }
    }

    /// The telephoto's optical zoom relative to the wide, read from the
    /// virtual multi-camera's own switch-over factors — the exact numbers iOS
    /// uses, so the chip matches the system camera on every generation
    /// (2× on 12 Pro, 3× on 13–14 Pro, 4× on 17 Pro, 5× on 15–16 Pro Max).
    /// Deriving it from fields of view only approximated these (17 Pro landed
    /// on 4.5×), so that is now just the fallback.
    private static func opticalZoomLabel(wide: AVCaptureDevice, tele: AVCaptureDevice) -> String {
        if let ratio = switchOverRatio() {
            return format(ratio)
        }
        let wf = Double(wide.activeFormat.videoFieldOfView)
        let tf = Double(tele.activeFormat.videoFieldOfView)
        guard wf > 0, tf > 0, tf < wf else { return "2×" }
        return format(tan(wf * .pi / 360) / tan(tf * .pi / 360))
    }

    /// Telephoto-to-wide ratio from the virtual device. Factors are expressed
    /// against the virtual device's base lens: on a triple camera that base is
    /// the ultra-wide, so [wide, tele] must be divided out; on a wide+tele dual
    /// the single factor is already relative to the wide.
    private static func switchOverRatio() -> Double? {
        if let triple = AVCaptureDevice.default(.builtInTripleCamera, for: .video, position: .back) {
            let f = triple.virtualDeviceSwitchOverVideoZoomFactors.map { Double(truncating: $0) }
            if f.count >= 2, f[0] > 0 { return f[1] / f[0] }
        }
        if let dual = AVCaptureDevice.default(.builtInDualCamera, for: .video, position: .back) {
            let f = dual.virtualDeviceSwitchOverVideoZoomFactors.map { Double(truncating: $0) }
            if let first = f.first, first > 0 { return first }
        }
        return nil
    }

    /// "4×" for whole numbers, "2.5×" otherwise — never a stray ".0".
    private static func format(_ zoom: Double) -> String {
        let rounded = (zoom * 10).rounded() / 10
        return abs(rounded.rounded() - rounded) < 0.05
            ? String(format: "%.0f×", rounded)
            : String(format: "%.1f×", rounded)
    }

    // MARK: lens / position / flash / focus / exposure

    func selectLens(_ option: LensOption) {
        selectedLensID = option.id
        setDevice(option.device, zoom: option.zoom)
    }

    func flip() {
        let newPosition: AVCaptureDevice.Position = position == .back ? .front : .back
        position = newPosition
        if newPosition == .front {
            if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front) {
                setDevice(device, zoom: 1)
            }
        } else if let option = lensOptions.first(where: { $0.id == selectedLensID }) ?? lensOptions.first {
            setDevice(option.device, zoom: option.zoom)
        }
    }

    private func setDevice(_ device: AVCaptureDevice, zoom: CGFloat) {
        sessionQueue.async {
            self.session.beginConfiguration()
            if let current = self.currentInput {
                self.session.removeInput(current)
            }
            if let input = try? AVCaptureDeviceInput(device: device),
               self.session.canAddInput(input) {
                self.session.addInput(input)
                self.currentInput = input
                self.currentDevice = device
            } else if let current = self.currentInput, self.session.canAddInput(current) {
                // Fall back to the previous camera rather than going dark.
                self.session.addInput(current)
            }
            self.session.commitConfiguration()

            if let d = self.currentDevice {
                do {
                    try d.lockForConfiguration()
                    let z = min(max(zoom, d.minAvailableVideoZoomFactor), d.maxAvailableVideoZoomFactor)
                    d.videoZoomFactor = z
                    d.unlockForConfiguration()
                } catch {}
            }
            let flash = self.currentDevice?.hasFlash ?? false
            DispatchQueue.main.async { self.hasFlash = flash }
        }
    }

    /// Focus + expose on a tap point (normalised device coordinates from the
    /// preview layer).
    func focus(at devicePoint: CGPoint) {
        sessionQueue.async {
            guard let d = self.currentDevice else { return }
            do {
                try d.lockForConfiguration()
                if d.isFocusPointOfInterestSupported {
                    d.focusPointOfInterest = devicePoint
                    d.focusMode = .autoFocus
                }
                if d.isExposurePointOfInterestSupported {
                    d.exposurePointOfInterest = devicePoint
                    d.exposureMode = .autoExpose
                }
                d.unlockForConfiguration()
            } catch {}
        }
    }

    /// EV compensation, clamped to what the current camera supports.
    func setExposureBias(_ ev: Float) {
        sessionQueue.async {
            guard let d = self.currentDevice else { return }
            do {
                try d.lockForConfiguration()
                let clamped = min(max(ev, d.minExposureTargetBias), d.maxExposureTargetBias)
                d.setExposureTargetBias(clamped, completionHandler: nil)
                d.unlockForConfiguration()
            } catch {}
        }
    }

    func capture(flash: AVCaptureDevice.FlashMode, _ completion: @escaping (UIImage) -> Void) {
        sessionQueue.async {
            self.onCapture = completion
            let settings = AVCapturePhotoSettings()
            if self.photoOutput.supportedFlashModes.contains(flash) {
                settings.flashMode = flash
            }
            settings.photoQualityPrioritization = .quality
            if let conn = self.photoOutput.connection(with: .video) {
                if conn.isVideoOrientationSupported {
                    conn.videoOrientation = .portrait
                }
                // Selfies: the preview is mirrored (that is what feels natural),
                // so mirror the capture too — otherwise the saved shot comes out
                // flipped relative to what was framed.
                if conn.isVideoMirroringSupported {
                    conn.automaticallyAdjustsVideoMirroring = false
                    conn.isVideoMirrored = self.currentDevice?.position == .front
                }
            }
            self.photoOutput.capturePhoto(with: settings, delegate: self)
        }
    }
}

extension CameraController: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer,
                       from connection: AVCaptureConnection) {
        // Throttle the read-out to ~10 fps — plenty for a colour meter, easy on power.
        let now = Date()
        guard now.timeIntervalSince(lastSample) > 0.1 else { return }
        lastSample = now
        guard let pb = CMSampleBufferGetImageBuffer(sampleBuffer),
              let color = Self.centreColor(pb) else { return }
        DispatchQueue.main.async { self.liveColor = color }
    }

    /// Average colour of the centre square of a BGRA pixel buffer.
    private static func centreColor(_ pb: CVPixelBuffer) -> Int32? {
        CVPixelBufferLockBaseAddress(pb, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pb, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(pb) else { return nil }
        let w = CVPixelBufferGetWidth(pb)
        let h = CVPixelBufferGetHeight(pb)
        let bpr = CVPixelBufferGetBytesPerRow(pb)
        let ptr = base.assumingMemoryBound(to: UInt8.self)

        let side = max(min(w, h) / 6, 8)
        let x0 = w / 2 - side / 2
        let y0 = h / 2 - side / 2
        var r = 0, g = 0, b = 0, n = 0
        var y = y0
        while y < y0 + side {
            let row = ptr + y * bpr
            var x = x0
            while x < x0 + side {
                let p = row + x * 4
                b += Int(p[0]); g += Int(p[1]); r += Int(p[2])   // BGRA
                n += 1
                x += 3
            }
            y += 3
        }
        guard n > 0 else { return nil }
        let packed = (0xFF << 24) | ((r / n) << 16) | ((g / n) << 8) | (b / n)
        return Int32(truncatingIfNeeded: packed)
    }
}

extension CameraController: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto,
                     error: Error?) {
        guard let data = photo.fileDataRepresentation(), let image = UIImage(data: data) else { return }
        DispatchQueue.main.async { self.onCapture?(image) }
    }
}

// MARK: - Preview layer

struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession
    /// Tap callback with (layer point, normalised device point) for
    /// focus-on-tap plus the on-screen focus ring.
    var onTap: ((CGPoint, CGPoint) -> Void)? = nil

    func makeCoordinator() -> Coordinator { Coordinator(onTap: onTap) }

    func makeUIView(context: Context) -> PreviewView {
        let v = PreviewView()
        v.videoPreviewLayer.session = session
        v.videoPreviewLayer.videoGravity = .resizeAspectFill
        if let conn = v.videoPreviewLayer.connection, conn.isVideoOrientationSupported {
            conn.videoOrientation = .portrait
        }
        let tap = UITapGestureRecognizer(target: context.coordinator,
                                         action: #selector(Coordinator.tapped(_:)))
        v.addGestureRecognizer(tap)
        return v
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        context.coordinator.onTap = onTap
    }

    final class Coordinator: NSObject {
        var onTap: ((CGPoint, CGPoint) -> Void)?
        init(onTap: ((CGPoint, CGPoint) -> Void)?) { self.onTap = onTap }

        @objc func tapped(_ gesture: UITapGestureRecognizer) {
            guard let v = gesture.view as? PreviewView else { return }
            let p = gesture.location(in: v)
            let devicePoint = v.videoPreviewLayer.captureDevicePointConverted(fromLayerPoint: p)
            onTap?(p, devicePoint)
        }
    }

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var videoPreviewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}

// MARK: - Hunt Camera screen

/// ハントカメラ — point at the world and hunt colours. The viewfinder centre
/// stays clear (a minimal crosshair + a thin match arc); the live HEX and the
/// target-vs-live match meter live in the top pill. Real camera controls:
/// lens chips (0.5×/1×/2×), selfie flip, flash, timer, aspect ratio, exposure
/// bias and tap-to-focus. Shooting drops the photo straight into the hunt.
struct HuntCameraView: View {
    @EnvironmentObject private var state: AppState
    @Environment(\.dismiss) private var dismiss
    @StateObject private var cam = CameraController()

    @State private var matched = false
    @State private var flashOverlay = false
    /// Lifetime hunt counter — persists so the number keeps growing across
    /// sessions (collection psychology, like the colour collection card).
    @AppStorage("huntedTotal") private var huntedCount = 0
    @State private var lastShot: UIImage?

    // Capture settings.
    @State private var flashMode: CamFlash = .off
    @State private var timerSeconds = 0            // 0 / 3 / 10
    @State private var aspect: CamAspect = .classic
    @State private var ev: Double = 0
    @State private var showExposure = false
    @State private var countdown: Int?
    @State private var countdownTask: Task<Void, Never>?

    // Tap-to-focus ring.
    @State private var focusPoint: CGPoint?
    @State private var focusStamp = 0

    // Frame mode (dazz-style): shoot cell by cell into a collage template's
    // on-screen guide, then jump straight into the collage with it applied.
    /// The chosen shooting style; nil = フリー (no guide).
    @State private var frameStyle: FrameStyle?
    @State private var frameShots: [UIImage] = []
    @State private var showFramePicker = false
    /// Drives the breathing gradient ring on the next guide cell.
    @State private var guidePulse = false

    private var frameTemplate: CollageTemplateVM? { frameStyle?.template }
    private var frameCellCount: Int { max(frameStyle?.cells ?? 0, 1) }

    private var target: Int32? { state.todayColor }

    /// 0…1 closeness of the live colour to the target (1 = identical).
    private var closeness: Double {
        guard let t = target, let l = cam.liveColor else { return 0 }
        return 1 - min(colorDistance(t, l) / 120.0, 1)   // ~120 = generous match radius
    }

    var body: some View {
        ZStack {
            if cam.authorized {
                cameraStage

                if frameTemplate == nil {
                    aspectMaskView

                    // Gentle vignette — just enough to seat the UI on bright
                    // scenes (frame mode shows true colours, no vignette).
                    RadialGradient(colors: [.clear, .black.opacity(0.32)],
                                   center: .center, startRadius: 180, endRadius: 520)
                        .ignoresSafeArea()
                        .allowsHitTesting(false)

                    // The crosshair and side rail step aside while a guide is
                    // up — frame mode is a dedicated, immersive capture screen.
                    reticleLayer
                    railLayer
                }
            } else {
                permissionState
            }

            content

            if let countdown {
                Text("\(countdown)")
                    .font(.system(size: 118, weight: .heavy, design: .rounded))
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.55), radius: 16, y: 4)
                    .transition(.scale(scale: 1.6).combined(with: .opacity))
                    .id(countdown)
                    .allowsHitTesting(false)
            }

            if flashOverlay {
                Color.white.ignoresSafeArea().transition(.opacity)
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { cam.start() }
        .onDisappear { cam.stop() }
        .onChange(of: closeness >= 0.72) { isMatch in
            if isMatch && !matched {
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
            withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) { matched = isMatch }
        }
        .onChange(of: ev) { value in
            cam.setExposureBias(Float(value))
        }
    }

    // MARK: chrome (top bar + bottom cluster)

    private var content: some View {
        VStack(spacing: 0) {
            topBar
            if cam.authorized && frameTemplate != nil {
                frameProgressPill
                    .padding(.top, 10)
            }
            Spacer()
            if cam.authorized {
                bottomCluster
            }
        }
    }

    /// "2/4" shot progress while a template guide is up.
    private var frameProgressPill: some View {
        HStack(spacing: 6) {
            Image(systemName: "square.grid.2x2")
                .font(.caption2.weight(.bold))
            Text(verbatim: "\(min(frameShots.count, frameCellCount))/\(frameCellCount)")
                .font(.system(.caption, design: .rounded).bold())
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 12).padding(.vertical, 6)
        .background(.black.opacity(0.35), in: Capsule())
    }

    private var topBar: some View {
        HStack(alignment: .top) {
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(12)
                    .background(.black.opacity(0.35), in: Circle())
            }

            Spacer()

            if cam.authorized { matchPill }

            Spacer()

            if cam.authorized {
                Button {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    ev = 0
                    showExposure = false
                    cam.flip()
                } label: {
                    Image(systemName: "arrow.triangle.2.circlepath.camera")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(11)
                        .background(.black.opacity(0.35), in: Circle())
                }
                .buttonStyle(PopButtonStyle())
            } else {
                // Keep the pill centred even before permission is granted.
                Color.clear.frame(width: 44, height: 44)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    /// Target → live colour read-out with a thin match meter underneath —
    /// everything that used to crowd the viewfinder centre now lives here.
    private var matchPill: some View {
        VStack(spacing: 6) {
            HStack(spacing: 8) {
                if let t = target {
                    colorDot(t)
                    Image(systemName: "arrow.right")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white.opacity(0.55))
                }
                if let live = cam.liveColor {
                    colorDot(live)
                    Text(hexString(live))
                        .font(.system(.caption, design: .monospaced).weight(.bold))
                        .foregroundStyle(.white)
                } else {
                    Text(target == nil ? "色をスキャン" : "今日の色を探す")
                        .font(.system(.caption, design: .rounded).bold())
                        .foregroundStyle(.white)
                }
            }
            .padding(.horizontal, 13).padding(.vertical, 8)
            .background(.black.opacity(0.35), in: Capsule())

            if target != nil {
                Capsule()
                    .fill(.white.opacity(0.25))
                    .frame(width: 112, height: 3)
                    .overlay(alignment: .leading) {
                        Capsule()
                            .fill(Brand.gradient)
                            .frame(width: 112 * closeness, height: 3)
                    }
                    .animation(.easeOut(duration: 0.2), value: closeness)
            }
        }
    }

    private func colorDot(_ packed: Int32) -> some View {
        Circle()
            .fill(Color(packed: packed))
            .frame(width: 14, height: 14)
            .overlay(Circle().stroke(.white.opacity(0.7), lineWidth: 1))
    }

    private var bottomCluster: some View {
        VStack(spacing: 14) {
            if showExposure && frameTemplate == nil {
                exposureSlider
            }

            if showFramePicker {
                framePicker
            }

            if let tpl = frameTemplate, frameShots.count >= frameCellCount {
                frameCompleteRow(tpl)
            } else {
                HStack(spacing: 8) {
                    frameToggle
                    if frameTemplate != nil && !frameShots.isEmpty {
                        frameUndoButton
                    }
                    if cam.position == .back && cam.lensOptions.count > 1 {
                        lensChips
                    }
                }
            }

            HStack {
                galleryButton.frame(width: 64)
                Spacer()
                shutter
                Spacer()
                counter.frame(width: 64)
            }
            .padding(.horizontal, 24)
        }
        .padding(.bottom, 26)
    }

    // MARK: frame mode (dazz-style template shooting)

    /// Opens/closes the template shelf; lights up while a template is active.
    private var frameToggle: some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                showFramePicker.toggle()
            }
        } label: {
            Image(systemName: "rectangle.grid.2x2")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(frameTemplate != nil ? Brand.accent : .white)
                .frame(width: 42, height: 28)
                .background(.black.opacity(0.35), in: Capsule())
        }
        .buttonStyle(PopButtonStyle())
    }

    private var frameUndoButton: some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                if !frameShots.isEmpty { frameShots.removeLast() }
            }
        } label: {
            Image(systemName: "arrow.uturn.backward")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 42, height: 28)
                .background(.black.opacity(0.35), in: Capsule())
        }
        .buttonStyle(PopButtonStyle())
    }

    /// The dazz-style shelf, now a single row of finished styles: one tap
    /// picks the look AND the shape. Pro styles join the row once Pro is
    /// owned, so those users shoot straight into the finished Pro look.
    private var frameShelf: [FrameStyle] {
        state.isPro ? FrameStyle.free + FrameStyle.pro : FrameStyle.free
    }

    private var framePicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(frameShelf) { style in
                    frameCard(style)
                }
            }
            .padding(.horizontal, 16)
        }
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    /// A style card: a literal miniature of the frame you will get, with the
    /// name underneath — no abstract icons, no second choice to make.
    private func frameCard(_ style: FrameStyle) -> some View {
        let isFree = style.templateID == nil
        let selected = isFree ? frameStyle == nil : frameStyle?.id == style.id
        return Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                frameStyle = isFree ? nil : style
                frameShots = []
            }
            // Reset so the guide's onAppear restarts the pulse next time.
            if isFree { guidePulse = false }
        } label: {
            VStack(spacing: 4) {
                Group {
                    if isFree {
                        Image(systemName: "viewfinder")
                            .font(.system(size: 19, weight: .semibold))
                    } else {
                        frameStyleGlyph(style, selected: selected)
                    }
                }
                .frame(width: 26, height: 30)
                Text(LocalizedStringKey(style.label))
                    .font(.system(size: 9.5, weight: .bold, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .foregroundStyle(selected ? Color.black : .white)
            .frame(width: 58, height: 58)
            .background(
                selected ? AnyShapeStyle(Color.white) : AnyShapeStyle(Color.black.opacity(0.35)),
                in: RoundedRectangle(cornerRadius: 12)
            )
        }
        .buttonStyle(PopButtonStyle())
    }

    /// The miniature cell pattern inside a style card.
    private func frameStyleGlyph(_ style: FrameStyle, selected: Bool) -> some View {
        let ink = selected ? Color.black.opacity(0.6) : Color.white.opacity(0.85)
        return VStack(spacing: 2) {
            ForEach(0..<max(style.rows, 1), id: \.self) { row in
                HStack(spacing: 2) {
                    ForEach(0..<max(style.columns, 1), id: \.self) { col in
                        // A trailing slot stays faint when the shot count is
                        // not a multiple of the column count.
                        let index = row * style.columns + col
                        RoundedRectangle(cornerRadius: 1.5)
                            .fill(ink.opacity(index < style.cells ? 1 : 0.15))
                    }
                }
            }
        }
    }

    /// All cells shot → one tap drops the set into the collage with the
    /// template already applied.
    private func frameCompleteRow(_ tpl: CollageTemplateVM) -> some View {
        HStack(spacing: 8) {
            frameUndoButton
            Button {
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                let shots = frameShots
                state.startCollage(with: shots, templateID: tpl.id,
                                   layoutOrdinal: frameStyle?.layout)
                frameShots = []
                frameStyle = nil
                guidePulse = false
                dismiss()
            } label: {
                Label("コラージュを作る", systemImage: "wand.and.stars")
                    .font(.system(.callout, design: .rounded).bold())
                    .foregroundStyle(.white)
                    .padding(.horizontal, 22).padding(.vertical, 11)
                    .background(Brand.gradient, in: Capsule())
                    .shadow(color: Brand.purple.opacity(0.55), radius: 12, y: 4)
            }
            .buttonStyle(PopButtonStyle())
        }
        .transition(.scale.combined(with: .opacity))
    }

    // MARK: camera stage (one preview — full-screen, or living inside a cell)

    /// The single live preview. Free mode: it fills the screen. Frame mode:
    /// the WHOLE camera view shrinks into the active cell (aspect-filled), the
    /// canvas takes over the screen, and after each shot the preview hops to
    /// the next cell. One AVCaptureVideoPreviewLayer throughout — it just
    /// moves, so mode changes animate instead of re-attaching the session.
    private var cameraStage: some View {
        GeometryReader { geo in
            let s = geo.size
            let rect = previewRect(in: s)
            let inCell = frameTemplate != nil
            let complete = inCell && frameShots.count >= frameCellCount
            ZStack(alignment: .topLeading) {
                Brand.base

                if let tpl = frameTemplate {
                    frameGuideContent(tpl, in: s)
                }

                CameraPreviewView(session: cam.session, onTap: { layerPoint, devicePoint in
                    cam.focus(at: devicePoint)
                    focusStamp += 1
                    focusPoint = layerPoint
                    let stamp = focusStamp
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) {
                        if stamp == focusStamp { focusPoint = nil }
                    }
                })
                .frame(width: rect.width, height: rect.height)
                .clipShape(RoundedRectangle(cornerRadius: inCell ? 4 : 0))
                .overlay {
                    if let p = focusPoint {
                        FocusRing(point: p).id(focusStamp)
                    }
                }
                .overlay {
                    // The breathing "you are here" ring in frame mode.
                    if inCell && !complete {
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(
                                AngularGradient(
                                    colors: [Brand.purple, Brand.pink, Brand.amber, Brand.purple],
                                    center: .center
                                ),
                                lineWidth: 3
                            )
                            .opacity(guidePulse ? 1 : 0.55)
                    }
                }
                .offset(x: rect.minX, y: rect.minY)
                // All four shot: the preview bows out and the finished collage
                // stands alone on the plate, waiting for the CTA.
                .opacity(complete ? 0 : 1)
                .animation(.spring(response: 0.45, dampingFraction: 0.85), value: rect)
            }
        }
        .ignoresSafeArea()
    }

    /// Where the live preview lives right now: the whole screen, or the
    /// active cell of the guide.
    private func previewRect(in s: CGSize) -> CGRect {
        // Falling back to full-screen keeps the camera usable even if the
        // shared geometry ever hands back no cells (an empty array would make
        // `count - 1` a negative index).
        guard let tpl = frameTemplate else {
            return CGRect(origin: .zero, size: s)
        }
        let g = guideGeometry(in: s, tpl: tpl)
        guard !g.cells.isEmpty else { return CGRect(origin: .zero, size: s) }
        return g.cells[min(frameShots.count, g.cells.count - 1)]
    }

    /// The template canvas laid out in SCREEN coordinates — one source of
    /// truth for the guide drawing, the preview's cell position and the
    /// capture crop. Deterministic (no geometry state), so shoot() can
    /// recompute it from UIScreen bounds.
    private func guideGeometry(in s: CGSize, tpl: CollageTemplateVM)
        -> (canvas: CGRect, cells: [CGRect]) {
        let maxW = s.width * 0.92
        let maxH = s.height * 0.54
        let w = min(maxW, maxH * tpl.aspect)
        let h = w / tpl.aspect
        let flat = CollageBridge.shared.computeFlat(
            cellCount: Int32(frameCellCount),
            layoutOrdinal: frameStyle?.layout ?? tpl.layout,
            placementOrdinal: 0,
            spacingFrac: Float(tpl.spacing / 360.0),
            width: Float(w),
            height: Float(h),
            overlayHorizontal: false,
            overlayPosFrac: 0.5,
            overlayWidthFrac: 0.16
        )
        let layout = decodeLayout(flat)
        let origin = CGPoint(x: (s.width - w) / 2, y: s.height * 0.42 - h / 2)
        let cells = layout.cells.map { $0.offsetBy(dx: origin.x, dy: origin.y) }
        return (CGRect(origin: origin, size: CGSize(width: w, height: h)), cells)
    }

    /// The near-full-screen collage canvas: an opaque plate in the template's
    /// background, shot cells filled in, upcoming cells as numbered slots and
    /// the Pro signature deco on top. The ACTIVE cell stays open — the live
    /// preview view sits over it (see cameraStage), showing the whole camera
    /// view aspect-filled to the cell.
    private func frameGuideContent(_ tpl: CollageTemplateVM, in s: CGSize) -> some View {
        let g = guideGeometry(in: s, tpl: tpl)
        let gScale = g.canvas.width / 360
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(argb: tpl.background).opacity(0.96))
                .frame(width: g.canvas.width, height: g.canvas.height)
                .offset(x: g.canvas.minX, y: g.canvas.minY)
                .shadow(color: .black.opacity(0.45), radius: 18, y: 6)
            ForEach(Array(g.cells.enumerated()), id: \.offset) { i, r in
                liveGuideCell(index: i, rect: r)
            }
            // Every preset with a deco renders it live (the view is empty for
            // the plain ones), so ハーフ's film strip frames the shot just like
            // the Pro decos do.
            TemplateSignatureDeco(templateID: tpl.id,
                                  width: g.canvas.width, height: g.canvas.height,
                                  scale: gScale, matWidth: tpl.spacing * gScale)
                .frame(width: g.canvas.width, height: g.canvas.height)
                .offset(x: g.canvas.minX, y: g.canvas.minY)
        }
        .allowsHitTesting(false)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                guidePulse = true
            }
        }
    }

    /// One guide cell (rects are absolute screen coords): shot cells show
    /// their photo; the active cell is a dark well the live preview covers;
    /// upcoming cells are numbered dashed slots on the plate.
    @ViewBuilder
    private func liveGuideCell(index i: Int, rect r: CGRect) -> some View {
        ZStack {
            if i < frameShots.count {
                Image(uiImage: frameShots[i])
                    .resizable()
                    .scaledToFill()
                    .frame(width: r.width, height: r.height)
                    .clipShape(RoundedRectangle(cornerRadius: 3))
                RoundedRectangle(cornerRadius: 3)
                    .stroke(.white.opacity(0.9), lineWidth: 1.5)
            } else if i == frameShots.count {
                // The live preview sits here; this well only shows for the
                // instant the preview is hopping between cells.
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.black.opacity(0.25))
            } else {
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color.black.opacity(0.12))
                RoundedRectangle(cornerRadius: 3)
                    .stroke(style: StrokeStyle(lineWidth: 1.2, dash: [5, 4]))
                    .foregroundStyle(.white.opacity(0.55))
                Text(verbatim: "\(i + 1)")
                    .font(.system(size: min(r.height * 0.18, 15), weight: .bold, design: .rounded))
                    .foregroundStyle(.white.opacity(0.7))
            }
        }
        .frame(width: r.width, height: r.height)
        .offset(x: r.minX, y: r.minY)
    }

    /// Bottom-left thumbnail — tap to leave the camera and land on the Hunt
    /// tab where the shots just went.
    private var galleryButton: some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            dismiss()
            state.selectedTab = .hunt
        } label: {
            ZStack(alignment: .bottomTrailing) {
                if let thumb = lastShot ?? state.photos.last?.image {
                    Image(uiImage: thumb).resizable().scaledToFill()
                        .frame(width: 52, height: 52)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(.white.opacity(0.6), lineWidth: 1))
                } else {
                    RoundedRectangle(cornerRadius: 12).fill(.white.opacity(0.12))
                        .frame(width: 52, height: 52)
                        .overlay {
                            Image(systemName: "photo.on.rectangle.angled")
                                .font(.callout)
                                .foregroundStyle(.white.opacity(0.7))
                        }
                }
                Image(systemName: "arrow.up.right.circle.fill")
                    .font(.footnote)
                    .foregroundStyle(.white, Brand.purple)
                    .offset(x: 5, y: 5)
            }
        }
        .buttonStyle(PopButtonStyle())
    }

    private var shutter: some View {
        Button {
            triggerShutter()
        } label: {
            ZStack {
                Circle().stroke(.white, lineWidth: 4).frame(width: 84, height: 84)
                Circle()
                    .fill(matched ? AnyShapeStyle(Brand.gradient) : AnyShapeStyle(Color.white))
                    .frame(width: 70, height: 70)
                    .shadow(color: matched ? Brand.pink.opacity(0.85) : .clear, radius: 16)
                if countdown != nil {
                    Image(systemName: "xmark")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(Brand.purple)
                }
            }
            .scaleEffect(matched ? 1.06 : 1)
        }
        .buttonStyle(PopButtonStyle())
        .disabled(!cam.authorized || (frameTemplate != nil && frameShots.count >= frameCellCount))
    }

    private var counter: some View {
        VStack(spacing: 2) {
            Text("\(huntedCount)")
                .font(.system(.title3, design: .rounded).weight(.heavy))
                .foregroundStyle(.white)
            Text("ハント")
                .font(.caption2).foregroundStyle(.white.opacity(0.8))
        }
    }

    private var lensChips: some View {
        HStack(spacing: 6) {
            ForEach(cam.lensOptions) { option in
                Button {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    cam.selectLens(option)
                } label: {
                    Text(option.label)
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(cam.selectedLensID == option.id ? Color.black : .white)
                        .frame(width: 42, height: 28)
                        .background(
                            cam.selectedLensID == option.id
                                ? AnyShapeStyle(Color.white)
                                : AnyShapeStyle(Color.black.opacity(0.35)),
                            in: Capsule()
                        )
                }
                .buttonStyle(PopButtonStyle())
            }
        }
        .padding(4)
        .background(.black.opacity(0.25), in: Capsule())
    }

    // MARK: side rail (flash / timer / aspect / exposure)

    private var railLayer: some View {
        VStack(spacing: 10) {
            if cam.hasFlash {
                railButton(icon: flashMode.icon, label: nil, active: flashMode != .off) {
                    flashMode = flashMode.next
                }
            }
            railButton(icon: "timer",
                       label: timerSeconds > 0 ? "\(timerSeconds)s" : nil,
                       active: timerSeconds > 0) {
                timerSeconds = timerSeconds == 0 ? 3 : (timerSeconds == 3 ? 10 : 0)
            }
            // Aspect is owned by the template while a frame guide is up.
            if frameTemplate == nil {
                railButton(icon: "aspectratio", label: aspect.label, active: aspect != .classic) {
                    aspect = aspect.next
                }
            }
            railButton(icon: "plusminus.circle",
                       label: ev != 0 ? String(format: "%+.1f", ev) : nil,
                       active: showExposure || ev != 0) {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                    showExposure.toggle()
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
        .padding(.trailing, 12)
    }

    private func railButton(icon: String, label: String?, active: Bool,
                            action: @escaping () -> Void) -> some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            action()
        } label: {
            VStack(spacing: 2) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .semibold))
                if let label {
                    Text(label)
                        .font(.system(size: 9, weight: .bold, design: .rounded))
                }
            }
            .foregroundStyle(active ? Brand.accent : .white)
            .frame(width: 42, height: 44)
            .background(.black.opacity(0.35), in: RoundedRectangle(cornerRadius: 13))
        }
        .buttonStyle(PopButtonStyle())
    }

    private var exposureSlider: some View {
        HStack(spacing: 10) {
            Image(systemName: "sun.min")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.8))
            Slider(value: $ev, in: -2...2)
                .frame(width: 190)
                .tint(.white)
            Image(systemName: "sun.max.fill")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.8))
        }
        .padding(.horizontal, 14).padding(.vertical, 8)
        .background(.black.opacity(0.35), in: Capsule())
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    // MARK: viewfinder centre

    /// Minimal crosshair + a thin closeness arc — the centre of the frame stays
    /// visible, unlike the old full-size colour disc.
    private var reticleLayer: some View {
        VStack(spacing: 16) {
            ZStack {
                if target != nil {
                    Circle()
                        .stroke(.white.opacity(0.22), lineWidth: 3)
                        .frame(width: 76, height: 76)
                    Circle()
                        .trim(from: 0, to: closeness)
                        .stroke(
                            AngularGradient(
                                colors: [Brand.purple, Brand.pink, Brand.amber, Brand.purple],
                                center: .center
                            ),
                            style: StrokeStyle(lineWidth: 3, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                        .frame(width: 76, height: 76)
                        .animation(.easeOut(duration: 0.2), value: closeness)
                }

                Circle()
                    .stroke(.white.opacity(0.9), lineWidth: 1.5)
                    .frame(width: 30, height: 30)
                ForEach(0..<4, id: \.self) { i in
                    Capsule()
                        .fill(.white.opacity(0.9))
                        .frame(width: 1.5, height: 7)
                        .offset(y: -22)
                        .rotationEffect(.degrees(Double(i) * 90))
                }
                Circle()
                    .fill(cam.liveColor.map { Color(packed: $0) } ?? .white.opacity(0.4))
                    .frame(width: 9, height: 9)

                if matched {
                    MatchBurst()
                }
            }
            .frame(width: 96, height: 96)
            .scaleEffect(matched ? 1.12 : 1)

            Text("見つけた！")
                .font(.system(.subheadline, design: .rounded).bold())
                .foregroundStyle(.white)
                .padding(.horizontal, 14).padding(.vertical, 7)
                .background(.black.opacity(0.35), in: Capsule())
                .overlay(Capsule().stroke(Brand.gradient, lineWidth: 1.5))
                .opacity(matched ? 1 : 0)
                .scaleEffect(matched ? 1 : 0.7)
        }
        .allowsHitTesting(false)
    }

    /// WYSIWYG capture window: the opening in this mask and the saved photo
    /// are exactly the same region (see windowCrop), whatever the aspect —
    /// so 1:1 really looks square on screen. Full-width, centred.
    static func captureWindow(in screen: CGSize, ratio: CGFloat) -> CGSize {
        var w = screen.width
        var h = w / ratio
        let maxH = screen.height * 0.92
        if h > maxH {
            h = maxH
            w = h * ratio
        }
        return CGSize(width: w, height: h)
    }

    private var aspectMaskView: some View {
        GeometryReader { geo in
            let s = geo.size
            let win = Self.captureWindow(in: s, ratio: aspect.ratio)
            let vBar = max((s.height - win.height) / 2, 0)
            let hBar = max((s.width - win.width) / 2, 0)
            ZStack {
                VStack(spacing: 0) {
                    Color.black.opacity(0.5).frame(height: vBar)
                    Spacer(minLength: 0)
                    Color.black.opacity(0.5).frame(height: vBar)
                }
                HStack(spacing: 0) {
                    Color.black.opacity(0.5).frame(width: hBar)
                    Spacer(minLength: 0)
                    Color.black.opacity(0.5).frame(width: hBar)
                }
                RoundedRectangle(cornerRadius: 4)
                    .stroke(.white.opacity(0.4), lineWidth: 1)
                    .frame(width: win.width, height: win.height)
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .animation(.easeInOut(duration: 0.25), value: aspect)
    }

    private var permissionState: some View {
        VStack(spacing: 14) {
            Image(systemName: "camera.metering.center.weighted")
                .font(.system(size: 44))
                .foregroundStyle(Brand.gradient)
            Text("カメラで色をハント")
                .font(.system(.title3, design: .rounded).bold())
            Text("設定 › ColorHunt でカメラを許可すると、\n街の色をリアルタイムでハントできます。")
                .font(.subheadline).foregroundStyle(.white.opacity(0.7))
                .multilineTextAlignment(.center)
            Button("設定を開く") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(32)
    }

    // MARK: shooting

    /// Fires immediately, or runs the countdown when a timer is set. Tapping
    /// the shutter mid-countdown cancels.
    private func triggerShutter() {
        if let task = countdownTask {
            task.cancel()
            countdownTask = nil
            withAnimation(.easeOut(duration: 0.2)) { countdown = nil }
            return
        }
        guard timerSeconds > 0 else {
            shoot()
            return
        }
        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) { countdown = timerSeconds }
        countdownTask = Task { @MainActor in
            var remain = timerSeconds
            while remain > 0 {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                do {
                    try await Task.sleep(nanoseconds: 1_000_000_000)
                } catch { return }
                remain -= 1
                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                    countdown = remain > 0 ? remain : nil
                }
            }
            countdownTask = nil
            shoot()
        }
    }

    private func shoot() {
        cam.capture(flash: flashMode.av) { image in
            if let tpl = frameTemplate {
                // The cell preview aspect-fills the WHOLE camera view, so the
                // maximal centred crop at the cell's aspect is exactly what
                // was on screen (the crop editor can still refine).
                guard frameShots.count < frameCellCount else { return }
                let g = guideGeometry(in: UIScreen.main.bounds.size, tpl: tpl)
                guard !g.cells.isEmpty else { return }
                let cell = g.cells[min(frameShots.count, g.cells.count - 1)]
                let final = Self.aspectCrop(image, ratio: cell.width / max(cell.height, 1))
                withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                    frameShots.append(final)
                }
                if frameShots.count >= frameCellCount {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                } else {
                    UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                }
            } else {
                let final = Self.windowCrop(image, screen: UIScreen.main.bounds.size,
                                            ratio: aspect.ratio)
                state.add(images: [final])
                withAnimation(.spring(response: 0.35, dampingFraction: 0.6)) { lastShot = final }
                huntedCount += 1
                UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            }
            withAnimation(.easeOut(duration: 0.08)) { flashOverlay = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                withAnimation(.easeIn(duration: 0.25)) { flashOverlay = false }
            }
        }
    }

    /// Crops the captured photo to exactly the centred on-screen capture
    /// window — what you framed is what you get.
    private static func windowCrop(_ image: UIImage, screen: CGSize, ratio: CGFloat) -> UIImage {
        let win = captureWindow(in: screen, ratio: ratio)
        let region = CGRect(x: (screen.width - win.width) / 2,
                            y: (screen.height - win.height) / 2,
                            width: win.width, height: win.height)
        return regionCrop(image, screen: screen, region: region)
    }

    /// Maximal centred crop at [ratio] (w/h) — matches what an aspect-filled
    /// preview of the whole camera view showed. Used by frame mode's cells.
    /// The photo arrives orientation-tagged, so it is normalised first.
    private static func aspectCrop(_ image: UIImage, ratio: CGFloat) -> UIImage {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let normalised = UIGraphicsImageRenderer(size: image.size, format: format).image { _ in
            image.draw(at: .zero)
        }
        guard let cg = normalised.cgImage, ratio > 0 else { return image }
        let w = CGFloat(cg.width)
        let h = CGFloat(cg.height)
        let current = w / max(h, 1)
        var cropW = w
        var cropH = h
        if ratio > current {
            cropH = w / ratio
        } else {
            cropW = h * ratio
        }
        let rect = CGRect(x: (w - cropW) / 2, y: (h - cropH) / 2,
                          width: cropW, height: cropH).integral
        guard let croppedCG = cg.cropping(to: rect) else { return normalised }
        return UIImage(cgImage: croppedCG)
    }

    /// Maps an arbitrary SCREEN rect to the sensor and crops the photo to it.
    /// The preview aspect-fills the screen, so screen point ↔ sensor pixel is
    /// one uniform scale plus the centring offset. Used by the aspect window
    /// AND the frame mode's live cells (the hole you framed IS the shot). The
    /// photo arrives orientation-tagged, so it is normalised first.
    private static func regionCrop(_ image: UIImage, screen: CGSize, region: CGRect) -> UIImage {
        guard screen.width > 0, screen.height > 0 else { return image }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let normalised = UIGraphicsImageRenderer(size: image.size, format: format).image { _ in
            image.draw(at: .zero)
        }
        guard let cg = normalised.cgImage else { return image }
        let iw = CGFloat(cg.width)
        let ih = CGFloat(cg.height)
        let scale = max(screen.width / iw, screen.height / ih)   // aspect-fill
        let offX = (iw * scale - screen.width) / 2               // cropped-off margins
        let offY = (ih * scale - screen.height) / 2
        let rect = CGRect(x: (region.minX + offX) / scale,
                          y: (region.minY + offY) / scale,
                          width: region.width / scale,
                          height: region.height / scale)
            .intersection(CGRect(x: 0, y: 0, width: iw, height: ih))
            .integral
        guard !rect.isEmpty, let croppedCG = cg.cropping(to: rect) else { return normalised }
        return UIImage(cgImage: croppedCG)
    }

    private func colorDistance(_ a: Int32, _ b: Int32) -> Double {
        let av = UInt32(bitPattern: a), bv = UInt32(bitPattern: b)
        let dr = Double(Int((av >> 16) & 0xFF) - Int((bv >> 16) & 0xFF))
        let dg = Double(Int((av >> 8) & 0xFF) - Int((bv >> 8) & 0xFF))
        let db = Double(Int(av & 0xFF) - Int(bv & 0xFF))
        return (dr * dr + dg * dg + db * db).squareRoot()
    }
}

// MARK: - Small camera types & effects

/// One tap = one complete shooting style: the look (template) and the shape
/// (shot count + arrangement) together. Choosing those separately meant two
/// stacked rows of controls and combinations that made no sense (a 6-shot
/// チェキ), so the shelf now offers finished styles whose card shows exactly
/// the frame you will get.
struct FrameStyle: Identifiable, Equatable {
    let id: String
    let label: String
    /// nil = フリー: no guide, plain full-screen camera.
    let templateID: String?
    let cells: Int
    /// 0 GRID / 1 VERTICAL / 2 TWO_COLUMN — the collage's ordinals.
    let layout: Int32
    /// Rows × columns of the mini diagram drawn on the card.
    let rows: Int
    let columns: Int
    let isPro: Bool

    static let free: [FrameStyle] = [
        .init(id: "free", label: "フリー", templateID: nil, cells: 0, layout: 0,
              rows: 0, columns: 0, isPro: false),
        // Half-frame: two upright shots in one landscape negative — always a
        // pair, which is what makes it read as a half-frame.
        .init(id: "half", label: "ハーフ", templateID: "half", cells: 2, layout: 2,
              rows: 1, columns: 2, isPro: false),
        .init(id: "fourcut", label: "4カット", templateID: "fourcut", cells: 4, layout: 1,
              rows: 4, columns: 1, isPro: false),
        .init(id: "grid4", label: "2×2", templateID: "dump", cells: 4, layout: 0,
              rows: 2, columns: 2, isPro: false),
        .init(id: "v3", label: "縦3", templateID: "white", cells: 3, layout: 1,
              rows: 3, columns: 1, isPro: false),
        .init(id: "col6", label: "2列6", templateID: "kumisha", cells: 6, layout: 2,
              rows: 3, columns: 2, isPro: false),
    ]

    /// Pro looks, appended to the shelf once Pro is owned. チェキ is a single
    /// shot because a real instax print holds exactly one photo.
    static let pro: [FrameStyle] = [
        .init(id: "cheki", label: "チェキ", templateID: "cheki", cells: 1, layout: 0,
              rows: 1, columns: 1, isPro: true),
        .init(id: "daylog", label: "デイログ", templateID: "daylog", cells: 4, layout: 0,
              rows: 2, columns: 2, isPro: true),
        .init(id: "pastel", label: "パステル", templateID: "pastel", cells: 4, layout: 0,
              rows: 2, columns: 2, isPro: true),
        .init(id: "y2k", label: "Y2K", templateID: "y2k", cells: 4, layout: 2,
              rows: 2, columns: 2, isPro: true),
        .init(id: "magazine", label: "マガジン", templateID: "magazine", cells: 4, layout: 0,
              rows: 2, columns: 2, isPro: true),
    ]

    var template: CollageTemplateVM? {
        templateID.flatMap { id in CollageTemplateVM.all.first { $0.id == id } }
    }
}

/// Flash cycle: off → auto → on.
enum CamFlash {
    case off, auto, on

    var next: CamFlash {
        switch self {
        case .off: return .auto
        case .auto: return .on
        case .on: return .off
        }
    }

    var icon: String {
        switch self {
        case .off: return "bolt.slash.fill"
        case .auto: return "bolt.badge.a.fill"
        case .on: return "bolt.fill"
        }
    }

    var av: AVCaptureDevice.FlashMode {
        switch self {
        case .off: return .off
        case .auto: return .auto
        case .on: return .on
        }
    }
}

/// Capture aspect cycle: 4:3 (full sensor) → 1:1 → 9:16.
enum CamAspect: Equatable {
    case classic, square, tall

    var next: CamAspect {
        switch self {
        case .classic: return .square
        case .square: return .tall
        case .tall: return .classic
        }
    }

    /// Width / height of the (portrait) capture.
    var ratio: CGFloat {
        switch self {
        case .classic: return 3.0 / 4.0
        case .square: return 1
        case .tall: return 9.0 / 16.0
        }
    }

    var label: String {
        switch self {
        case .classic: return "4:3"
        case .square: return "1:1"
        case .tall: return "9:16"
        }
    }
}

/// One-shot celebration: twelve hue dots radiating out of the reticle.
struct MatchBurst: View {
    @State private var fly = false

    var body: some View {
        ZStack {
            ForEach(0..<12, id: \.self) { i in
                Circle()
                    .fill(Color(hue: Double(i) / 12, saturation: 0.85, brightness: 1))
                    .frame(width: 8, height: 8)
                    .offset(x: fly ? CGFloat(cos(Double(i) / 12 * 2 * .pi)) * 92 : 0,
                            y: fly ? CGFloat(sin(Double(i) / 12 * 2 * .pi)) * 92 : 0)
                    .opacity(fly ? 0 : 1)
            }
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.7)) { fly = true }
        }
        .allowsHitTesting(false)
    }
}

/// The shrinking ring shown where the user tapped to focus.
struct FocusRing: View {
    let point: CGPoint
    @State private var shrink = false

    var body: some View {
        Circle()
            .stroke(.white, lineWidth: 1.5)
            .background(Circle().fill(.white.opacity(0.08)))
            .frame(width: shrink ? 54 : 84, height: shrink ? 54 : 84)
            .opacity(shrink ? 0.95 : 0.4)
            .position(point)
            .onAppear {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) { shrink = true }
            }
            .allowsHitTesting(false)
    }
}
