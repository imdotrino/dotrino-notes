import DotrinoNativeUI
import Foundation

/// The app's texts: the SAME as the PWA (src/i18n.js), flattened by `scripts/native-i18n.mjs`
/// (`list.new`). A missing key or variable is an error, not a hole on screen.
enum I18n {
    static let dict: [String: [String: String]] = {
        guard let url = Bundle.main.url(forResource: "i18n", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let d = try? JSONDecoder().decode([String: [String: String]].self, from: data)
        else { preconditionFailure("i18n.json is missing or broken in the app bundle") }
        return d
    }()

    static var lang: String { DotrinoLang.shared.code }
    static func has(_ key: String) -> Bool { dict[lang]?[key] != nil }

    private static var locale: Locale { Locale(identifier: lang == "es" ? "es_EC" : "en_US") }

    /// The time if it is from today, the day if not (`shortWhen` in the PWA).
    static func shortWhen(_ ts: Int64) -> String {
        let d = Date(timeIntervalSince1970: Double(ts) / 1000)
        let f = DateFormatter(); f.locale = locale
        if Calendar.current.isDateInToday(d) { f.timeStyle = .short; f.dateStyle = .none } else { f.dateStyle = .medium; f.timeStyle = .none }
        return f.string(from: d)
    }

    static func dateTime(_ ts: Int64) -> String {
        let f = DateFormatter(); f.locale = locale; f.dateStyle = .medium; f.timeStyle = .short
        return f.string(from: Date(timeIntervalSince1970: Double(ts) / 1000))
    }
}

func t(_ key: String, _ vars: [String: Any] = [:]) -> String {
    let lang = I18n.lang
    guard let s = I18n.dict[lang]?[key] else { preconditionFailure("missing i18n key: \(key) (\(lang))") }
    if vars.isEmpty { return s }
    var out = "", rest = Substring(s)
    while let open = rest.firstIndex(of: "{"), let close = rest[open...].firstIndex(of: "}") {
        out += rest[..<open]
        let name = String(rest[rest.index(after: open)..<close])
        guard let v = vars[name] else { preconditionFailure("missing i18n var \"\(name)\" for \(key)") }
        out += "\(v)"
        rest = rest[rest.index(after: close)...]
    }
    return out + rest
}
