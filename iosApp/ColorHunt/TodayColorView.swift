import SwiftUI
import UserNotifications
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

    // Daily reminder (parity with Android's ThemeReminderScheduler).
    @AppStorage("reminderEnabled") private var reminderEnabled = false
    @AppStorage("reminderHour") private var reminderHour = 8
    @AppStorage("reminderMinute") private var reminderMinute = 0
    @State private var showTimePicker = false

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

                    reminderCard
                }
                .padding()
            }
            .background(Color(argb: 0xFF101014))
            .navigationTitle("今日の色")
        }
        .sheet(isPresented: $showTimePicker) {
            ReminderTimeSheet(hour: $reminderHour, minute: $reminderMinute) {
                ThemeReminder.schedule(hour: reminderHour, minute: reminderMinute)
                showTimePicker = false
            }
        }
    }

    // MARK: reminder

    private var reminderCard: some View {
        VStack(spacing: 0) {
            Toggle(isOn: reminderBinding) {
                Label("毎日リマインド", systemImage: "bell.badge")
                    .font(.subheadline)
            }
            .tint(Color(argb: 0xFF7C4DFF))
            .padding(.vertical, 6)

            if reminderEnabled {
                Divider().overlay(Color.white.opacity(0.08))
                Button {
                    showTimePicker = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "clock").foregroundStyle(.secondary)
                        Text("通知の時刻").font(.subheadline)
                        Spacer()
                        Text(String(format: "%02d:%02d", reminderHour, reminderMinute))
                            .font(.title3.bold())
                            .foregroundStyle(Color(argb: 0xFF9E7CFF))
                    }
                    .foregroundStyle(.primary)
                    .padding(.vertical, 10)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
        .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 16))
    }

    private var reminderBinding: Binding<Bool> {
        Binding(
            get: { reminderEnabled },
            set: { on in
                if on {
                    ThemeReminder.request { granted in
                        reminderEnabled = granted
                        if granted { ThemeReminder.schedule(hour: reminderHour, minute: reminderMinute) }
                    }
                } else {
                    reminderEnabled = false
                    ThemeReminder.cancel()
                }
            }
        )
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
                    .font(.callout.weight(.medium))
                    .padding(.horizontal, 20)
                    .padding(.vertical, 11)
                    .foregroundStyle(Color(argb: 0xFF9E7CFF))
                    .overlay(
                        Capsule().stroke(Color(argb: 0xFF9E7CFF).opacity(0.7), lineWidth: 1.5)
                    )
            }
            .buttonStyle(.plain)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
        .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 20))
    }

    /// Roulette: several turns easing out onto a random hue — varied every press.
    /// The ring stays put (a full rainbow); the marker races and the centre cycles
    /// through hues, then settles — matching Android's ColorWheel spin.
    ///
    /// Driven per-frame rather than via `withAnimation`: SwiftUI would interpolate
    /// the marker's offset along a straight chord and blend the centre colour
    /// directly (no spin, no rainbow sweep). Updating `hue` each frame instead
    /// makes the derived marker angle and colour recompute continuously, exactly
    /// like Android's Animatable driving a Canvas redraw.
    private func spin() {
        guard !spinning else { return }
        spinning = true
        let start = hue
        let target = Double.random(in: 0..<360)
        let base = start - start.truncatingRemainder(dividingBy: 360)
        let landing = base + Double(Self.spinTurns) * 360 + target
        let duration = 1.8

        Task { @MainActor in
            let startTime = Date()
            while true {
                let t = min(Date().timeIntervalSince(startTime) / duration, 1.0)
                let eased = 1 - pow(1 - t, 3) // easeOut cubic, like FastOutSlowIn's tail
                hue = start + (landing - start) * eased
                if t >= 1.0 { break }
                try? await Task.sleep(nanoseconds: 16_000_000) // ~60 fps
            }
            hue = landing
            picked = true
            spinning = false
        }
    }
}

/// Daily theme-colour reminder — the iOS counterpart of Android's
/// ThemeReminderScheduler (a repeating local notification at a chosen time).
enum ThemeReminder {
    private static let id = "daily_theme_reminder"

    static func request(_ completion: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                DispatchQueue.main.async { completion(granted) }
            }
    }

    static func schedule(hour: Int, minute: Int) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [id])

        let content = UNMutableNotificationContent()
        content.title = "今日の色をハントしよう"
        content.body = "今日のテーマ色を決めて、街で見つけよう 🎨"
        content.sound = .default

        var comps = DateComponents()
        comps.hour = hour
        comps.minute = minute
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }

    static func cancel() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [id])
    }
}

/// Wheel time picker for the daily reminder.
struct ReminderTimeSheet: View {
    @Binding var hour: Int
    @Binding var minute: Int
    let onDone: () -> Void

    @State private var date: Date

    init(hour: Binding<Int>, minute: Binding<Int>, onDone: @escaping () -> Void) {
        _hour = hour
        _minute = minute
        self.onDone = onDone
        var c = DateComponents()
        c.hour = hour.wrappedValue
        c.minute = minute.wrappedValue
        _date = State(initialValue: Calendar.current.date(from: c) ?? Date())
    }

    var body: some View {
        VStack(spacing: 20) {
            Text("通知の時刻").font(.headline).padding(.top, 20)
            DatePicker("", selection: $date, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
            Button {
                let c = Calendar.current.dateComponents([.hour, .minute], from: date)
                hour = c.hour ?? 8
                minute = c.minute ?? 0
                onDone()
            } label: {
                Text("完了").frame(maxWidth: .infinity).padding(.vertical, 4)
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
        .presentationDetents([.medium])
    }
}
