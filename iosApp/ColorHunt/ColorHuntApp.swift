import SwiftUI

/// Brand palette — a single source so every screen reads as one system.
enum Brand {
    static let purple = Color(red: 124 / 255, green: 77 / 255, blue: 255 / 255)   // 0xFF7C4DFF
    static let pink = Color(red: 236 / 255, green: 64 / 255, blue: 122 / 255)     // 0xFFEC407A
    static let amber = Color(red: 255 / 255, green: 167 / 255, blue: 38 / 255)    // 0xFFFFA726
    static let accent = Color(red: 158 / 255, green: 124 / 255, blue: 255 / 255)  // 0xFF9E7CFF
    static let base = Color(red: 0x10 / 255.0, green: 0x10 / 255.0, blue: 0x14 / 255.0)

    /// The signature diagonal brand gradient.
    static let gradient = LinearGradient(
        colors: [purple, pink, amber],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
}

/// Portrait-only, like every photo-SNS camera app: the whole UI (camera
/// chrome, tab bar, collage editor) is designed vertical, and rotating the
/// capture screen scattered its controls. Runtime lock so no project-setting
/// step can be forgotten.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        .portrait
    }
}

// ColorHunt iOS — feature parity with the Android app, sharing the KMP colour
// and collage-geometry core (framework `SharedColor`). Build from Xcode on
// macOS; see iosApp/README.md for the one-time framework wiring.
@main
struct ColorHuntApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var state = AppState()

    init() {
        // One branded tab bar everywhere: deep app-base colour with soft
        // unselected items, instead of the default washed system blur.
        let ap = UITabBarAppearance()
        ap.configureWithOpaqueBackground()
        ap.backgroundColor = UIColor(red: 0x10 / 255, green: 0x10 / 255, blue: 0x14 / 255, alpha: 1)
        let normal = UIColor.white.withAlphaComponent(0.45)
        for item in [ap.stackedLayoutAppearance, ap.inlineLayoutAppearance, ap.compactInlineLayoutAppearance] {
            item.normal.iconColor = normal
            item.normal.titleTextAttributes = [.foregroundColor: normal]
        }
        UITabBar.appearance().standardAppearance = ap
        UITabBar.appearance().scrollEdgeAppearance = ap
    }

    var body: some Scene {
        WindowGroup {
            RootTabView()
                .environmentObject(state)
                .tint(Brand.accent)          // brand-coloured controls + tab selection
                .preferredColorScheme(.dark) // 映え-first dark base, like Android
        }
    }
}

struct RootTabView: View {
    @EnvironmentObject private var state: AppState
    @AppStorage("onboarded_v1") private var onboarded = false
    @State private var showCamera = false

    /// Tab selection with the centre slot intercepted: picking the camera
    /// placeholder opens the full-screen camera and keeps the current tab.
    private var tabSelection: Binding<AppTab> {
        Binding(
            get: { state.selectedTab },
            set: { tab in
                if tab == .camera {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    showCamera = true
                } else {
                    state.selectedTab = tab
                }
            }
        )
    }

    var body: some View {
        ZStack {
            // Five slots — 2 tabs / camera / 2 tabs — so the bar splits evenly
            // around the raised centre button instead of the four tabs crowding
            // it. The middle slot is an empty placeholder the button sits over;
            // tapping anywhere in that slot also opens the camera.
            // Grid (the most secondary feature) opens from the Hunt hero.
            TabView(selection: tabSelection) {
                HuntView()
                    .tabItem { Label("ハント", systemImage: "photo.on.rectangle.angled") }
                    .tag(AppTab.hunt)
                CollageView()
                    .tabItem { Label("コラージュ", systemImage: "square.grid.2x2") }
                    .tag(AppTab.collage)
                Color.clear
                    .tabItem { Text(verbatim: "") }
                    .tag(AppTab.camera)
                TodayColorView()
                    .tabItem { Label("今日の色", systemImage: "sparkles") }
                    .tag(AppTab.today)
                HuntMapView()
                    .tabItem { Label("マップ", systemImage: "map") }
                    .tag(AppTab.map)
            }
            .overlay(alignment: .bottom) { cameraButton }

            if !onboarded {
                OnboardingView {
                    withAnimation(.easeOut(duration: 0.45)) { onboarded = true }
                }
                .transition(.opacity)
                .zIndex(1)
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            HuntCameraView().environmentObject(state)
        }
    }

    /// The star of the tab bar: a raised gradient camera button sitting in the
    /// empty centre gap of the four-tab bar.
    private var cameraButton: some View {
        Button {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            showCamera = true
        } label: {
            ZStack {
                Circle()
                    .fill(Brand.gradient)
                    .frame(width: 62, height: 62)
                    .overlay(Circle().stroke(Brand.base, lineWidth: 5))
                    .shadow(color: Brand.purple.opacity(0.6), radius: 12, y: 4)
                Image(systemName: "camera.viewfinder")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(.white)
            }
        }
        .buttonStyle(PopButtonStyle())
        .offset(y: 6)
    }
}

/// A springy press-scale for primary CTAs — a small delight that reads as modern.
struct PopButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

/// The success toast used by collage/map saves — checkmark pill sliding in from
/// the top with a glassy backing.
struct SaveToast: View {
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(.green)
            Text("保存しました")
                .font(.system(.subheadline, design: .rounded).bold())
        }
        .padding(.horizontal, 18).padding(.vertical, 12)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(Capsule().stroke(.white.opacity(0.15), lineWidth: 1))
        .shadow(color: .black.opacity(0.35), radius: 12, y: 4)
        .transition(.move(edge: .top).combined(with: .opacity))
    }
}

// MARK: - Onboarding

/// First-launch hero: floating colour orbs, a slowly turning hue ring, the brand
/// wordmark and three feature lines — sets the "collect the world's colours"
/// mood in one screen. Shown once (onboarded_v1).
struct OnboardingView: View {
    let onDone: () -> Void

    @State private var drift = false
    @State private var ringAngle = 0.0

    var body: some View {
        ZStack {
            Brand.base.ignoresSafeArea()

            // Ambient colour orbs drifting behind the content.
            Circle().fill(Brand.purple.opacity(0.45))
                .frame(width: 220, height: 220)
                .blur(radius: 60)
                .offset(x: drift ? -90 : -50, y: drift ? -230 : -180)
            Circle().fill(Brand.pink.opacity(0.4))
                .frame(width: 190, height: 190)
                .blur(radius: 60)
                .offset(x: drift ? 110 : 70, y: drift ? -60 : -110)
            Circle().fill(Brand.amber.opacity(0.35))
                .frame(width: 180, height: 180)
                .blur(radius: 60)
                .offset(x: drift ? -60 : -20, y: drift ? 200 : 160)

            VStack(spacing: 0) {
                Spacer()

                ZStack {
                    Circle()
                        .strokeBorder(
                            AngularGradient(
                                colors: (0...12).map { Color(hue: Double($0) / 12, saturation: 0.9, brightness: 1) },
                                center: .center
                            ),
                            lineWidth: 14
                        )
                        .frame(width: 150, height: 150)
                        .rotationEffect(.degrees(ringAngle))
                    Image(systemName: "camera.viewfinder")
                        .font(.system(size: 44, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .padding(.bottom, 28)

                Text("ColorHunt")
                    .font(.system(size: 44, design: .rounded).weight(.heavy))
                    .foregroundStyle(Brand.gradient)
                Text("世界を、色で集めよう。")
                    .font(.system(.headline, design: .rounded))
                    .foregroundStyle(.white.opacity(0.9))
                    .padding(.top, 6)

                VStack(alignment: .leading, spacing: 14) {
                    onboardRow("wand.and.stars", "カメラロールを色で自動仕分け")
                    onboardRow("square.grid.2x2", "組写コラージュをワンタップで")
                    onboardRow("sparkles", "今日の色をハントしてシェア")
                }
                .padding(.top, 34)

                Spacer()

                Button {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    onDone()
                } label: {
                    Text("はじめる")
                        .font(.system(.headline, design: .rounded).bold())
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Brand.gradient, in: Capsule())
                        .shadow(color: Brand.purple.opacity(0.55), radius: 16, y: 6)
                }
                .buttonStyle(PopButtonStyle())
                .padding(.horizontal, 32)
                .padding(.bottom, 40)
            }
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 5).repeatForever(autoreverses: true)) {
                drift = true
            }
            withAnimation(.linear(duration: 14).repeatForever(autoreverses: false)) {
                ringAngle = 360
            }
        }
    }

    private func onboardRow(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.callout.weight(.semibold))
                .foregroundStyle(Brand.gradient)
                .frame(width: 26)
            Text(text)
                .font(.system(.subheadline, design: .rounded))
                .foregroundStyle(.white.opacity(0.9))
        }
    }
}
