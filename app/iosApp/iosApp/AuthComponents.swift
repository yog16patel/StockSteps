import Shared
import SwiftUI

/// Sign-in / create-account building blocks (mirrors Android's `AuthComponents.kt`). Colours come from the
/// StockSteps theme for the current colour scheme (Light, Dark or System); sizes from the shared `AuthTokens`.
enum AuthTheme {
    static let tokens = AuthTokens.shared
    static func font(_ value: ThemeTextStyle, _ role: UIFont.TextStyle = .body) -> Font {
        StockStepsTheme.font(value, relativeTo: role)
    }
    static var radius: CGFloat { CGFloat(tokens.controlRadius) }
    static var height: CGFloat { CGFloat(tokens.controlHeight) }
}

/// Rounded-square "S" mark, wordmark and tagline.
struct StockStepsAuthHeader: View {
    let colors: StockColors
    private let t = AuthTheme.tokens
    var body: some View {
        HStack(spacing: 12) {
            Text("S")
                .font(AuthTheme.font(t.logo, .title2))
                .foregroundStyle(.white)
                .frame(width: CGFloat(t.logoSize), height: CGFloat(t.logoSize))
                .background(LinearGradient(colors: [Color(red: 0.16, green: 0.59, blue: 1), colors.primary, colors.primaryDark], startPoint: .topLeading, endPoint: .bottomTrailing),
                            in: RoundedRectangle(cornerRadius: CGFloat(t.logoRadius)))
            VStack(alignment: .leading, spacing: 1) {
                Text("StockSteps").font(AuthTheme.font(t.brand, .title2)).foregroundStyle(colors.textPrimary)
                Text("Learn  •  Practice  •  Invest Smarter").font(AuthTheme.font(t.tagline, .footnote)).foregroundStyle(colors.textSecondary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

/// "Build your investing skills" with a decorative chart (no values, tickers or performance claims).
struct StockStepsAuthHero: View {
    let signup: Bool
    let compact: Bool
    let colors: StockColors
    let dark: Bool
    private let t = AuthTheme.tokens
    var body: some View {
        let height = CGFloat(compact ? t.heroHeightCompact : t.heroHeight)
        ZStack(alignment: .leading) {
            AuthChartIllustration(primary: colors.primary, dark: dark)
                .frame(maxWidth: .infinity, alignment: .trailing)
                .frame(height: height)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 10) {
                (Text(signup ? "Start your\n" : "Build your\n").foregroundColor(colors.textPrimary)
                    + Text(signup ? "investing journey" : "investing skills").foregroundColor(colors.primary))
                    .font(AuthTheme.font(t.heroTitle, .largeTitle).leading(.tight))
                    .accessibilityAddTraits(.isHeader)
                Text(signup ? "Save your watchlists, practice\nand progress on every device." : "Simple insights. Real data.\nA smarter you.")
                    .font(AuthTheme.font(t.heroBody, .body)).foregroundStyle(colors.textBody)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(minHeight: height)
    }
}

private struct AuthChartIllustration: View {
    let primary: Color
    let dark: Bool
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width * 0.5, h = geo.size.height, x0 = geo.size.width - w
            ZStack {
                Circle().fill(RadialGradient(colors: [primary.opacity(dark ? 0.28 : 0.16), .clear], center: .center, startRadius: 0, endRadius: w * 0.55))
                    .frame(width: w * 1.1, height: w * 1.1).position(x: x0 + w * 0.72, y: h * 0.45)
                ForEach(Array([0.22, 0.34, 0.46, 0.60, 0.76].enumerated()), id: \.offset) { i, f in
                    let gap = w * 0.035, barWidth = (w * 0.80 - gap * 4) / 5
                    let left = x0 + w * 0.18 + CGFloat(i) * (barWidth + gap), top = h * (1 - f)
                    RoundedRectangle(cornerRadius: 6)
                        .fill(LinearGradient(colors: [primary.opacity(dark ? 0.38 : 0.24), primary.opacity(0.02)], startPoint: .top, endPoint: .bottom))
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(primary.opacity(dark ? 0.30 : 0.22), lineWidth: 1))
                        .frame(width: barWidth, height: h - top).position(x: left + barWidth / 2, y: top + (h - top) / 2)
                }
                let curve = Path { p in
                    p.move(to: CGPoint(x: x0 + w * 0.12, y: h * 0.88))
                    p.addCurve(to: CGPoint(x: x0 + w * 0.70, y: h * 0.38), control1: CGPoint(x: x0 + w * 0.35, y: h * 0.90), control2: CGPoint(x: x0 + w * 0.55, y: h * 0.62))
                    p.addCurve(to: CGPoint(x: x0 + w * 0.96, y: h * 0.06), control1: CGPoint(x: x0 + w * 0.80, y: h * 0.22), control2: CGPoint(x: x0 + w * 0.88, y: h * 0.13))
                }
                curve.stroke(primary.opacity(0.25), style: StrokeStyle(lineWidth: 14, lineCap: .round))
                curve.stroke(LinearGradient(colors: [primary.opacity(0.4), primary, Color(red: 0.42, green: 0.72, blue: 1)], startPoint: .bottomLeading, endPoint: .topTrailing),
                             style: StrokeStyle(lineWidth: 4, lineCap: .round))
                Path { p in
                    let e = CGPoint(x: x0 + w * 0.96, y: h * 0.06), head: CGFloat = 11
                    p.move(to: CGPoint(x: e.x + head * 0.35, y: e.y - head * 0.35))
                    p.addLine(to: CGPoint(x: e.x - head, y: e.y - head * 0.05))
                    p.addLine(to: CGPoint(x: e.x - head * 0.05, y: e.y + head))
                    p.closeSubpath()
                }.fill(Color(red: 0.42, green: 0.72, blue: 1))
            }
        }
    }
}

/// The rounded surface grouping every sign-in option.
struct StockStepsAuthCard<Content: View>: View {
    let colors: StockColors
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(AuthTheme.tokens.groupGap)) { content }
            .padding(CGFloat(AuthTheme.tokens.cardPadding))
            .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(AuthTheme.tokens.cardRadius)))
            .overlay(RoundedRectangle(cornerRadius: CGFloat(AuthTheme.tokens.cardRadius)).stroke(colors.border, lineWidth: 1))
    }
}

/// White "Continue with Google" button with Google's own multi-colour "G".
struct StockStepsGoogleButton: View {
    let colors: StockColors
    let dark: Bool
    let enabled: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                GoogleLogo().frame(width: CGFloat(AuthTheme.tokens.googleSize), height: CGFloat(AuthTheme.tokens.googleSize))
                Text("Continue with Google").font(AuthTheme.font(AuthTheme.tokens.button, .headline)).foregroundStyle(Color(white: 0.12))
                Spacer()
                Image(systemName: "chevron.right").font(.subheadline.weight(.semibold)).foregroundStyle(Color(white: 0.38))
            }
            .padding(.horizontal, 18)
            .frame(maxWidth: .infinity, minHeight: AuthTheme.height)
            .background(Color.white, in: RoundedRectangle(cornerRadius: AuthTheme.radius))
            .overlay(RoundedRectangle(cornerRadius: AuthTheme.radius).stroke(dark ? Color.white : colors.border, lineWidth: 1))
            .opacity(enabled ? 1 : 0.55)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Google's "G" mark (Sign in with Google branding), drawn from its published 48×48 path data.
private struct GoogleLogo: View {
    private static let parts: [(Color, String)] = [
        (Color(red: 0.918, green: 0.263, blue: 0.208), "M24,9.5c3.54,0 6.71,1.22 9.21,3.6l6.85,-6.85C35.9,2.38 30.47,0 24,0 14.62,0 6.51,5.38 2.56,13.22l7.98,6.19C12.43,13.72 17.74,9.5 24,9.5z"),
        (Color(red: 0.259, green: 0.522, blue: 0.957), "M46.98,24.55c0,-1.57 -0.15,-3.09 -0.38,-4.55H24v9.02h12.94c-0.58,2.96 -2.26,5.48 -4.78,7.18l7.73,6c4.51,-4.18 7.09,-10.36 7.09,-17.65z"),
        (Color(red: 0.984, green: 0.737, blue: 0.020), "M10.53,28.59c-0.48,-1.45 -0.76,-2.99 -0.76,-4.59s0.27,-3.14 0.76,-4.59l-7.98,-6.19C0.92,16.46 0,20.12 0,24c0,3.88 0.92,7.54 2.56,10.78l7.97,-6.19z"),
        (Color(red: 0.204, green: 0.659, blue: 0.325), "M24,48c6.48,0 11.93,-2.13 15.89,-5.81l-7.73,-6c-2.15,1.45 -4.92,2.3 -8.16,2.3 -6.26,0 -11.57,-4.22 -13.47,-9.91l-7.98,6.19C6.51,42.62 14.62,48 24,48z")
    ]
    var body: some View {
        Canvas { context, size in
            let scale = min(size.width, size.height) / 48
            for (color, data) in Self.parts {
                context.fill(SVGPath.parse(data).applying(CGAffineTransform(scaleX: scale, y: scale)), with: .color(color))
            }
        }
        .accessibilityHidden(true)
    }
}

/// Minimal SVG path-data parser (M, L, H, V, C, S, Z in absolute and relative forms) for the logo above.
private enum SVGPath {
    static func parse(_ d: String) -> Path {
        var path = Path()
        var tokens: [String] = []
        var number = ""
        func flush() { if !number.isEmpty { tokens.append(number); number = "" } }
        for ch in d {
            if ch.isLetter { flush(); tokens.append(String(ch)) }
            else if ch == "," || ch == " " { flush() }
            else if ch == "-" { flush(); number = "-" }
            else { number.append(ch) }
        }
        flush()
        var i = 0, command = "M"
        var current = CGPoint.zero, start = CGPoint.zero, lastControl: CGPoint?
        func next() -> CGFloat { defer { i += 1 }; return CGFloat(Double(tokens[i]) ?? 0) }
        while i < tokens.count {
            if tokens[i].first?.isLetter == true { command = tokens[i]; i += 1; if command == "z" || command == "Z" { path.closeSubpath(); current = start; continue } }
            let rel = command.first?.isLowercase == true
            func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { rel ? CGPoint(x: current.x + x, y: current.y + y) : CGPoint(x: x, y: y) }
            switch command.uppercased() {
            case "M": current = pt(next(), next()); start = current; path.move(to: current); command = rel ? "l" : "L"; lastControl = nil
            case "L": current = pt(next(), next()); path.addLine(to: current); lastControl = nil
            case "H": let x = next(); current = CGPoint(x: rel ? current.x + x : x, y: current.y); path.addLine(to: current); lastControl = nil
            case "V": let y = next(); current = CGPoint(x: current.x, y: rel ? current.y + y : y); path.addLine(to: current); lastControl = nil
            case "C":
                let c1 = pt(next(), next()), c2 = pt(next(), next()), end = pt(next(), next())
                path.addCurve(to: end, control1: c1, control2: c2); lastControl = c2; current = end
            case "S":
                let c1 = lastControl.map { CGPoint(x: 2 * current.x - $0.x, y: 2 * current.y - $0.y) } ?? current
                let c2 = pt(next(), next()), end = pt(next(), next())
                path.addCurve(to: end, control1: c1, control2: c2); lastControl = c2; current = end
            default: i += 1
            }
        }
        return path
    }
}

struct StockStepsAuthDivider: View {
    let text: String
    let colors: StockColors
    var body: some View {
        HStack(spacing: 12) {
            Rectangle().fill(colors.border).frame(height: 1)
            Text(text).font(.footnote).foregroundStyle(colors.textSecondary).fixedSize()
            Rectangle().fill(colors.border).frame(height: 1)
        }
        .accessibilityHidden(true)
    }
}

/// Label above a rounded, theme-tinted input with a leading icon (and the password reveal button).
struct StockStepsAuthTextField<Content: View>: View {
    let label: String
    let icon: String
    let colors: StockColors
    let dark: Bool
    var focused = false
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(AuthTheme.tokens.fieldGap)) {
            Text(label).font(AuthTheme.font(AuthTheme.tokens.label, .subheadline)).foregroundStyle(colors.textSecondary)
            HStack(spacing: 14) {
                Image(systemName: icon).font(.body).foregroundStyle(colors.textSecondary).frame(width: 22).accessibilityHidden(true)
                content.font(AuthTheme.font(AuthTheme.tokens.body)).foregroundStyle(colors.textPrimary).tint(colors.primary)
            }
            .padding(.leading, 16).padding(.trailing, 6)
            .frame(maxWidth: .infinity, minHeight: AuthTheme.height)
            .background(dark ? colors.appBackground.opacity(0.55) : colors.appBackground, in: RoundedRectangle(cornerRadius: AuthTheme.radius))
            .overlay(RoundedRectangle(cornerRadius: AuthTheme.radius).stroke(focused ? colors.primary : colors.border, lineWidth: focused ? 1.5 : 1))
        }
    }
}

/// Full-width primary action with a trailing arrow (spinner while working).
struct StockStepsAuthButton: View {
    let title: String
    let enabled: Bool
    let loading: Bool
    let colors: StockColors
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            ZStack {
                Text(title).font(AuthTheme.font(AuthTheme.tokens.button, .headline).weight(.semibold))
                HStack {
                    Spacer()
                    if loading { ProgressView().tint(.white) } else { Image(systemName: "arrow.right").font(.headline) }
                }
                .padding(.trailing, 20)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: AuthTheme.height)
            .background(LinearGradient(colors: [colors.primary, Color(red: 0.18, green: 0.56, blue: 1)], startPoint: .leading, endPoint: .trailing),
                        in: RoundedRectangle(cornerRadius: AuthTheme.radius))
            .opacity(enabled || loading ? 1 : 0.6)
        }
        .buttonStyle(.plain)
        .disabled(!enabled || loading)
        .accessibilityValue(loading ? "Working" : "")
    }
}

/// Outlined "Continue as guest" with its explanation.
struct StockStepsGuestAction: View {
    let colors: StockColors
    let enabled: Bool
    let action: () -> Void
    var body: some View {
        VStack(spacing: 10) {
            Button(action: action) {
                HStack(spacing: 14) {
                    Image(systemName: "person.fill").font(.body).accessibilityHidden(true)
                    Text("Continue as guest").font(AuthTheme.font(AuthTheme.tokens.button, .headline))
                    Spacer()
                    Image(systemName: "chevron.right").font(.subheadline.weight(.semibold)).foregroundStyle(colors.textSecondary)
                }
                .foregroundStyle(colors.textPrimary)
                .padding(.horizontal, 18)
                .frame(maxWidth: .infinity, minHeight: AuthTheme.height)
                .overlay(RoundedRectangle(cornerRadius: AuthTheme.radius).stroke(colors.textSecondary.opacity(0.5), lineWidth: 1))
                .contentShape(Rectangle())
                .opacity(enabled ? 1 : 0.55)
            }
            .buttonStyle(.plain)
            .disabled(!enabled)
            Text("Explore stocks, try features, and learn — no account needed.")
                .font(.footnote).foregroundStyle(colors.textSecondary).multilineTextAlignment(.center).frame(maxWidth: .infinity)
        }
    }
}

/// "New to StockSteps? Create account" (or the reverse) on a raised strip.
struct StockStepsAuthSwitch: View {
    let prompt: String
    let action: String
    let colors: StockColors
    let enabled: Bool
    let onTap: () -> Void
    var body: some View {
        Button(action: onTap) {
            HStack {
                Text(prompt).font(.subheadline.weight(.medium)).foregroundStyle(colors.textPrimary)
                Spacer()
                Text(action).font(.subheadline.weight(.semibold)).foregroundStyle(colors.primary)
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(colors.primary)
            }
            .padding(.horizontal, 18)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(colors.surfaceSecondary, in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel("\(prompt) \(action)")
    }
}

/// Inline error: icon + text, never colour alone.
struct StockStepsAuthError: View {
    let message: String
    let colors: StockColors
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(colors.negativeText).accessibilityLabel("Error")
            Text(message).font(.subheadline).foregroundStyle(colors.negativeText)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.negativeContainer, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(colors.negativeBorder, lineWidth: 1))
    }
}
