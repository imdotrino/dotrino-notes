import XCTest

/// The App Store screenshots (`store/graphics/ios/`). It writes sample notes in each language,
/// pins one and saves three screens: the list, a note and a search. Only runs when asked:
///
///   TEST_RUNNER_SHOT_DIR=/tmp/shots xcodebuild test -project Notes.xcodeproj -scheme Notes \
///     -destination 'platform=iOS Simulator,name=iPhone 16 Pro Max' -only-testing:NotesUITests
///
/// It DELETES every note of the simulator's active profile first: never point it at a real one.
final class StoreScreenshotsUITests: XCTestCase {
    private struct Lang {
        let code: String
        let names: [String]          // list, note, search
        let notes: [String]          // oldest first
        let search: String
    }

    private let langs = [
        Lang(code: "es", names: ["1-lista", "2-nota", "3-buscar"], notes: [
            "Libros para leer\nRayuela, Ficciones y Pedro Páramo",
            "Compras de la semana\nPan, leche, huevos, café y fruta",
            "Llamar al dentista\nPedir cita para el martes por la tarde",
            "Ideas para el viaje\n- Llevar la cámara y el cargador\n- Reservar el hotel antes del viernes\n- Bajar los mapas para usarlos sin conexión\n- Cambiar dinero en el banco",
        ], search: "hotel"),
        Lang(code: "en", names: ["1-list", "2-note", "3-search"], notes: [
            "Books to read\nDune, Circe and The Hobbit",
            "Weekly groceries\nBread, milk, eggs, coffee and fruit",
            "Call the dentist\nBook an appointment for Tuesday afternoon",
            "Trip ideas\n- Pack the camera and the charger\n- Book the hotel before Friday\n- Download maps to use offline\n- Exchange money at the bank",
        ], search: "hotel"),
    ]

    func testStoreScreenshots() throws {
        guard let dir = ProcessInfo.processInfo.environment["SHOT_DIR"] else {
            throw XCTSkip("store screenshots: set TEST_RUNNER_SHOT_DIR to take them")
        }
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["new-note"].waitForExistence(timeout: 30), "notes did not open")

        for lang in langs {
            app.buttons[lang.code.uppercased()].firstMatch.tap()
            removeAll(app)
            for body in lang.notes { write(app, body) }
            // Newest first: the third one written («dentist») is second; pin it to the top.
            app.buttons.matching(identifier: "note-pin").element(boundBy: 1).tap()
            sleep(1)
            try shot(dir, lang.code, lang.names[0])

            app.buttons.matching(identifier: "note-item").element(boundBy: 1).tap()
            XCTAssertTrue(app.textViews["editor-body"].waitForExistence(timeout: 5))
            sleep(1)
            try shot(dir, lang.code, lang.names[1])
            app.buttons["editor-back"].tap()

            let search = app.textFields["search"]
            search.tap()
            search.typeText(lang.search + "\n")   // Return closes the keyboard
            sleep(1)
            try shot(dir, lang.code, lang.names[2])
            search.tap()
            search.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: lang.search.count) + "\n")
        }
    }

    private func removeAll(_ app: XCUIApplication) {
        while app.buttons["note-remove"].firstMatch.exists {
            app.buttons["note-remove"].firstMatch.tap()
            let confirm = app.buttons["remove-confirm"]
            XCTAssertTrue(confirm.waitForExistence(timeout: 5))
            confirm.tap()
            sleep(1)
        }
    }

    private func write(_ app: XCUIApplication, _ body: String) {
        app.buttons["new-note"].tap()
        let editor = app.textViews["editor-body"]
        XCTAssertTrue(editor.waitForExistence(timeout: 5))
        editor.tap()
        editor.typeText(body)
        app.buttons["editor-back"].tap()
        XCTAssertTrue(app.buttons["new-note"].waitForExistence(timeout: 5))
    }

    private func shot(_ dir: String, _ lang: String, _ name: String) throws {
        let url = URL(fileURLWithPath: dir).appendingPathComponent("iphone-\(lang)-\(name).png")
        try XCUIScreen.main.screenshot().pngRepresentation.write(to: url)
    }
}
