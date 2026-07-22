import SwiftUI
import AVFoundation

// MARK: - Camera engine

/// Drives the capture session: a live video feed for real-time centre-colour
/// read-out, plus a photo output for the actual hunt shot. Kept off the main
/// actor because AVFoundation delivers frames on its own queues; UI-facing
/// values hop back to main.
final class CameraController: NSObject, ObservableObject {
    @Published var liveColor: Int32?          // colour under the reticle, ~10 fps
    @Published var authorized = true
    @Published var running = false

    let session = AVCaptureSession()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let photoOutput = AVCapturePhotoOutput()
    private let sessionQueue = DispatchQueue(label: "colorhunt.camera.session")
    private let sampleQueue = DispatchQueue(label: "colorhunt.camera.sample")
    private var lastSample = Date.distantPast
    private var onCapture: ((UIImage) -> Void)?

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

                if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                   let input = try? AVCaptureDeviceInput(device: device),
                   self.session.canAddInput(input) {
                    self.session.addInput(input)
                }

                self.videoOutput.videoSettings = [
                    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
                ]
                self.videoOutput.alwaysDiscardsLateVideoFrames = true
                self.videoOutput.setSampleBufferDelegate(self, queue: self.sampleQueue)
                if self.session.canAddOutput(self.videoOutput) { self.session.addOutput(self.videoOutput) }
                if self.session.canAddOutput(self.photoOutput) { self.session.addOutput(self.photoOutput) }

                self.session.commitConfiguration()
                self.configured = true
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

    func capture(_ completion: @escaping (UIImage) -> Void) {
        sessionQueue.async {
            self.onCapture = completion
            let settings = AVCapturePhotoSettings()
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

    func makeUIView(context: Context) -> PreviewView {
        let v = PreviewView()
        v.videoPreviewLayer.session = session
        v.videoPreviewLayer.videoGravity = .resizeAspectFill
        if let conn = v.videoPreviewLayer.connection, conn.isVideoOrientationSupported {
            conn.videoOrientation = .portrait
        }
        return v
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var videoPreviewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}

// MARK: - Hunt Camera screen

/// ハントカメラ — point at the world, read the colour under the reticle live, and
/// when it matches "today's colour" the ring fills and celebrates. Shooting adds
/// the photo straight into the hunt (→ auto-sorted → colour collection).
struct HuntCameraView: View {
    @EnvironmentObject private var state: AppState
    @Environment(\.dismiss) private var dismiss
    @StateObject private var cam = CameraController()

    @State private var matched = false
    @State private var flash = false
    @State private var huntedCount = 0
    @State private var lastShot: UIImage?

    private var target: Int32? { state.todayColor }

    /// 0…1 closeness of the live colour to the target (1 = identical).
    private var closeness: Double {
        guard let t = target, let l = cam.liveColor else { return 0 }
        return 1 - min(colorDistance(t, l) / 120.0, 1)   // ~120 = generous match radius
    }

    var body: some View {
        ZStack {
            if cam.authorized {
                CameraPreviewView(session: cam.session)
                    .ignoresSafeArea()
            } else {
                permissionState
            }

            // Darkened vignette so the reticle pops.
            RadialGradient(colors: [.clear, .black.opacity(0.45)], center: .center, startRadius: 120, endRadius: 460)
                .ignoresSafeArea()
                .allowsHitTesting(false)

            content

            if flash {
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
    }

    // MARK: content overlay

    private var content: some View {
        VStack(spacing: 0) {
            topBar
            Spacer()
            reticle
            Spacer()
            bottomBar
        }
    }

    private var topBar: some View {
        HStack {
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(12)
                    .background(.black.opacity(0.35), in: Circle())
            }
            Spacer()
            if let t = target {
                HStack(spacing: 8) {
                    Circle().fill(Color(packed: t)).frame(width: 16, height: 16)
                        .overlay(Circle().stroke(.white.opacity(0.7), lineWidth: 1))
                    Text("今日の色を探す")
                        .font(.system(.subheadline, design: .rounded).bold())
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 14).padding(.vertical, 9)
                .background(.ultraThinMaterial, in: Capsule())
            } else {
                Text("色をスキャン")
                    .font(.system(.subheadline, design: .rounded).bold())
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14).padding(.vertical, 9)
                    .background(.ultraThinMaterial, in: Capsule())
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private var reticle: some View {
        let live = cam.liveColor
        return VStack(spacing: 16) {
            ZStack {
                // Progress ring toward the target (only when a target is set).
                if target != nil {
                    Circle()
                        .stroke(.white.opacity(0.18), lineWidth: 6)
                        .frame(width: 188, height: 188)
                    Circle()
                        .trim(from: 0, to: closeness)
                        .stroke(
                            AngularGradient(
                                colors: [Brand.purple, Brand.pink, Brand.amber, Brand.purple],
                                center: .center
                            ),
                            style: StrokeStyle(lineWidth: 6, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                        .frame(width: 188, height: 188)
                }

                // Viewfinder frame + live colour core.
                RoundedRectangle(cornerRadius: 30, style: .continuous)
                    .stroke(.white.opacity(matched ? 0.0 : 0.85), lineWidth: 2)
                    .frame(width: 150, height: 150)

                Circle()
                    .fill(live.map { Color(packed: $0) } ?? .gray)
                    .frame(width: matched ? 132 : 60, height: matched ? 132 : 60)
                    .overlay(Circle().stroke(.white, lineWidth: matched ? 4 : 2))
                    .shadow(color: (live.map { Color(packed: $0) } ?? .clear).opacity(matched ? 0.9 : 0), radius: 20)

                if matched {
                    Text("見つけた！")
                        .font(.system(.headline, design: .rounded).bold())
                        .foregroundStyle(.white)
                        .shadow(radius: 4)
                }
            }
            .scaleEffect(matched ? 1.06 : 1)

            if let live {
                Text(hexString(live))
                    .font(.system(.title3, design: .monospaced).bold())
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14).padding(.vertical, 6)
                    .background(.black.opacity(0.4), in: Capsule())
            }
        }
    }

    private var bottomBar: some View {
        HStack {
            // Recently hunted thumbnail (tap-free memento).
            Group {
                if let lastShot {
                    Image(uiImage: lastShot).resizable().scaledToFill()
                        .frame(width: 52, height: 52)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(.white.opacity(0.6), lineWidth: 1))
                } else {
                    RoundedRectangle(cornerRadius: 12).fill(.white.opacity(0.12))
                        .frame(width: 52, height: 52)
                }
            }
            .frame(width: 60)

            Spacer()

            // Shutter.
            Button {
                shoot()
            } label: {
                ZStack {
                    Circle().fill(matched ? AnyShapeStyle(Brand.gradient) : AnyShapeStyle(Color.white))
                        .frame(width: 74, height: 74)
                    Circle().stroke(.white, lineWidth: 4).frame(width: 86, height: 86)
                    Image(systemName: "camera.fill")
                        .font(.title3)
                        .foregroundStyle(matched ? .white : Brand.purple)
                }
            }
            .buttonStyle(PopButtonStyle())
            .disabled(!cam.authorized)

            Spacer()

            // Hunted counter.
            VStack(spacing: 2) {
                Text("\(huntedCount)")
                    .font(.system(.title3, design: .rounded).weight(.heavy))
                    .foregroundStyle(.white)
                Text("ハント")
                    .font(.caption2).foregroundStyle(.white.opacity(0.8))
            }
            .frame(width: 60)
        }
        .padding(.horizontal, 22)
        .padding(.bottom, 28)
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

    private func shoot() {
        cam.capture { image in
            state.add(images: [image])
            lastShot = image
            huntedCount += 1
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            withAnimation(.easeOut(duration: 0.08)) { flash = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                withAnimation(.easeIn(duration: 0.25)) { flash = false }
            }
        }
    }

    private func colorDistance(_ a: Int32, _ b: Int32) -> Double {
        let av = UInt32(bitPattern: a), bv = UInt32(bitPattern: b)
        let dr = Double(Int((av >> 16) & 0xFF) - Int((bv >> 16) & 0xFF))
        let dg = Double(Int((av >> 8) & 0xFF) - Int((bv >> 8) & 0xFF))
        let db = Double(Int(av & 0xFF) - Int(bv & 0xFF))
        return (dr * dr + dg * dg + db * db).squareRoot()
    }
}
