import SwiftUI
import SharedColor

/// 今日の色 — the ring-style colour wheel with a roulette spin, mirroring
/// Android: angle = hue, centre previews the pick, result shows name + HEX
/// only after a colour is chosen.
struct TodayColorView: View {
    @EnvironmentObject private var state: AppState

    // Continuous angle in degrees — can exceed 360 while spinning (several turns).
    // Mirrors Android's Animatable(hueAnim); colour derives from the wrapped value.
    @State private var hue: Double = 210
    @State private var picked = false
    @State private var spinning = false

    private static let spinTurns = 4

    /// Wrapped hue in 0..<360. Colour must use this — SwiftUI `Color(hue:)` clamps
    /// its argument to 0...1, so feeding hue/360 while spinning (>1) froze the hue.
    private var displayHue: Double {
        let m = hue.truncatingRemainder(dividingBy: 360)
        return m < 0 ? m + 360 : m
    }

    private var pickedColor: Color {
        Color(hue: displayHue / 360.0, saturation: 0.92, brightness: 1.0)
    }

    private var packedColor: Int32 {
        let ui = UIColor(hue: CGFloat(displayHue / 360.0), saturation: 0.92, brightness: 1.0, alpha: 1)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        let packed = (0xFF << 24) | (Int(r * 255) << 16) | (Int(g * 255) << 8) | Int(b * 255)
        return Int32(truncatingIfNeeded: packed)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    Text("ホイールで色を選ぶか、今日の色を自動で。")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    wheel
                        .frame(maxWidth: 300)
                        .aspectRatio(1, contentMode: .fit)

                    Button {
                        spin()
                    } label: {
                        Label("今日の色を自動で", systemImage: "sparkles")
                            .font(.callout.bold())
                            .padding(.horizontal, 22).padding(.vertical, 12)
                            .background(Color(argb: 0xFF7C4DFF), in: Capsule())
                            .foregroundStyle(.white)
                    }
                    .disabled(spinning)

                    if picked {
                        resultCard
                    } else {
                        Text("ホイールに触れて色を選びましょう。")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding()
            }
            .background(Color(argb: 0xFF101014))
            .navigationTitle("今日の色")
        }
    }

    private var wheel: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let ringWidth = size * 0.12
            ZStack {
                AngularGradient(
                    colors: (0...12).map { Color(hue: Double($0) / 12.0, saturation: 1, brightness: 1) },
                    center: .center
                )
                .mask(Circle().strokeBorder(style: StrokeStyle(lineWidth: ringWidth)))

                Circle()
                    .fill(pickedColor)
                    .padding(ringWidth + size * 0.06)

                // Marker riding the ring at the current hue.
                Circle()
                    .fill(pickedColor)
                    .stroke(.white, lineWidth: 3)
                    .frame(width: ringWidth * 0.9, height: ringWidth * 0.9)
                    .offset(
                        x: cos(hue * .pi / 180) * (size / 2 - ringWidth / 2),
                        y: sin(hue * .pi / 180) * (size / 2 - ringWidth / 2)
                    )
            }
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0).onChanged { value in
                    guard !spinning else { return }
                    let dx = value.location.x - size / 2
                    let dy = value.location.y - size / 2
                    var deg = atan2(dy, dx) * 180 / .pi
                    if deg < 0 { deg += 360 }
                    hue = deg
                    picked = true
                }
            )
        }
    }

    private var resultCard: some View {
        let key = ColorBridge.shared.classifyKey(colorInt: packedColor)
        return VStack(spacing: 12) {
            HStack(spacing: 14) {
                Circle().fill(pickedColor).frame(width: 48, height: 48)
                    .overlay(Circle().stroke(.white.opacity(0.3)))
                VStack(alignment: .leading) {
                    Text(bucketLabel(key)).font(.title3.bold()).foregroundStyle(pickedColor)
                    Text(hexString(packedColor))
                        .font(.system(.subheadline, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
            }
            Button {
                state.selectedTab = .hunt
            } label: {
                Label("この色をハントする", systemImage: "photo.on.rectangle")
                    .font(.callout)
            }
            .buttonStyle(.bordered)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
        .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 20))
    }

    /// Roulette: several turns easing out onto a random hue — varied every press.
    /// The ring stays put (a full rainbow); the marker races and the centre cycles
    /// through hues, then settles — matching Android's ColorWheel spin.
    private func spin() {
        guard !spinning else { return }
        spinning = true
        let target = Double.random(in: 0..<360)
        // Continue forward from wherever the marker sits, land on target after N turns.
        let base = hue - hue.truncatingRemainder(dividingBy: 360)
        let landing = base + Double(Self.spinTurns) * 360 + target
        withAnimation(.easeOut(duration: 1.8)) {
            hue = landing
        } completion: {
            picked = true
            spinning = false
        }
    }
}
