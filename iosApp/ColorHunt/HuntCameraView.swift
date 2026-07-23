import SwiftUI
import AVFoundation

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
                var options: [LensOption] = []
                if let ultra {
                    options.append(LensOption(id: "0.5", label: "0.5×", device: ultra, zoom: 1))
                }
                if let wide {
                    options.append(LensOption(id: "1", label: "1×", device: wide, zoom: 1))
                    options.append(LensOption(id: "2", label: "2×", device: wide, zoom: 2))
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
            if let conn = self.photoOutput.connection(with: .video), conn.isVideoOrientationSupported {
                conn.videoOrientation = .portrait
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
    @State private var huntedCount = 0
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

    private var target: Int32? { state.todayColor }

    /// 0…1 closeness of the live colour to the target (1 = identical).
    private var closeness: Double {
        guard let t = target, let l = cam.liveColor else { return 0 }
        return 1 - min(colorDistance(t, l) / 120.0, 1)   // ~120 = generous match radius
    }

    var body: some View {
        ZStack {
            if cam.authorized {
                CameraPreviewView(session: cam.session, onTap: { layerPoint, devicePoint in
                    cam.focus(at: devicePoint)
                    focusStamp += 1
                    focusPoint = layerPoint
                    let stamp = focusStamp
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) {
                        if stamp == focusStamp { focusPoint = nil }
                    }
                })
                .overlay {
                    if let p = focusPoint {
                        FocusRing(point: p).id(focusStamp)
                    }
                }
                .ignoresSafeArea()

                aspectMaskView

                // Gentle vignette — just enough to seat the UI on bright scenes.
                RadialGradient(colors: [.clear, .black.opacity(0.32)],
                               center: .center, startRadius: 180, endRadius: 520)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)

                reticleLayer
                railLayer
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
            Spacer()
            if cam.authorized {
                bottomCluster
            }
        }
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
            if showExposure {
                exposureSlider
            }

            if cam.position == .back && cam.lensOptions.count > 1 {
                lensChips
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
        .disabled(!cam.authorized)
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
            railButton(icon: "aspectratio", label: aspect.label, active: aspect != .classic) {
                aspect = aspect.next
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

    /// Dim bars marking the capture area for the selected aspect ratio. The
    /// preview fills the screen (aspect-fill of the 3:4 sensor), so bar sizes
    /// are derived from the sensor's on-screen extent.
    private var aspectMaskView: some View {
        GeometryReader { geo in
            let s = geo.size
            let r = aspect.ratio
            let sensorW = s.height * 3 / 4          // sensor width on screen (aspect-fill)
            let regionH = min(sensorW / r, s.height)
            let regionW = min(s.height * r, sensorW)
            let vBar = max((s.height - regionH) / 2, 0)
            let hBar = max((s.width - regionW) / 2, 0)
            ZStack {
                VStack(spacing: 0) {
                    Color.black.opacity(0.55).frame(height: vBar)
                    Spacer(minLength: 0)
                    Color.black.opacity(0.55).frame(height: vBar)
                }
                HStack(spacing: 0) {
                    Color.black.opacity(0.55).frame(width: hBar)
                    Spacer(minLength: 0)
                    Color.black.opacity(0.55).frame(width: hBar)
                }
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
            let final = Self.cropped(image, to: aspect.ratio)
            state.add(images: [final])
            withAnimation(.spring(response: 0.35, dampingFraction: 0.6)) { lastShot = final }
            huntedCount += 1
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            withAnimation(.easeOut(duration: 0.08)) { flashOverlay = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                withAnimation(.easeIn(duration: 0.25)) { flashOverlay = false }
            }
        }
    }

    /// Centre-crops the captured photo to the selected aspect (w/h). The photo
    /// arrives orientation-tagged, so it is normalised first.
    private static func cropped(_ image: UIImage, to ratio: CGFloat) -> UIImage {
        let current = image.size.width / max(image.size.height, 1)
        guard abs(current - ratio) > 0.01 else { return image }
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let normalised = UIGraphicsImageRenderer(size: image.size, format: format).image { _ in
            image.draw(at: .zero)
        }
        guard let cg = normalised.cgImage else { return image }
        let w = CGFloat(cg.width)
        let h = CGFloat(cg.height)
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

    private func colorDistance(_ a: Int32, _ b: Int32) -> Double {
        let av = UInt32(bitPattern: a), bv = UInt32(bitPattern: b)
        let dr = Double(Int((av >> 16) & 0xFF) - Int((bv >> 16) & 0xFF))
        let dg = Double(Int((av >> 8) & 0xFF) - Int((bv >> 8) & 0xFF))
        let db = Double(Int(av & 0xFF) - Int(bv & 0xFF))
        return (dr * dr + dg * dg + db * db).squareRoot()
    }
}

// MARK: - Small camera types & effects

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
