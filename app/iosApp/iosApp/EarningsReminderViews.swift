import Observation
import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography

/// The account-wide earnings reminder state (shared presenter; the server schedules and sends).
@MainActor @Observable
final class EarningsRemindersModel {
    private(set) var state: EarningsRemindersState?
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    init(client: IosEarningsClient) {
        self.client = client
        subscription = client.observeReminders { [weak self] in self?.state = $0 }
    }
    deinit { subscription?.cancel() }

    var signedIn: Bool { state?.signedIn == true }
    func control(_ eventId: String) -> String { state.map { client.reminderControl(state: $0, eventId: eventId) } ?? "OFF" }
    func note(_ eventId: String) -> String? { state.flatMap { client.reminderNote(state: $0, eventId: eventId) } }
    /// Refreshes the platform permission (never stored) into the shared state.
    func refreshPermission() async { client.setNotificationPermission(granted: await PushCoordinator.shared.enabled) }
}

/// What a reminder sheet is about: one upcoming event.
struct ReminderTargetInfo: Identifiable {
    let eventId: String, symbol: String, name: String, dateText: String
    var timingText: String? = nil
    var dateNote: String? = nil
    var id: String { eventId }
}

/// The reminder control; words carry the state, not only the icon.
struct ReminderControlButton: View {
    let control: String
    var compact = false
    let action: () -> Void
    var body: some View {
        let label = switch control {
        case "ON": "Reminder On"
        case "SAVING": "Saving…"
        case "FAILED": "Couldn't save · Retry"
        default: compact ? "Remind" : "Remind Me"
        }
        Button(action: action) { Label(label, systemImage: control == "ON" ? "bell.fill" : "bell").frame(minHeight: 44) }
            .buttonStyle(.bordered)
            .disabled(control == "SAVING")
            .accessibilityValue(label)
    }
}

/// "Remind Me About Earnings": event date vs notification date, 1/3/7 days, results option, permission state.
struct EarningsReminderSheet: View {
    let target: ReminderTargetInfo
    let model: EarningsRemindersModel
    var onSignIn: () -> Void = {}
    let onDone: () -> Void
    @State private var offset: Int32 = 1
    @State private var results = true
    @State private var permission: Bool?

    var body: some View {
        NavigationStack {
            Form {
                if !model.signedIn {
                    Section { Text("Sign in to save earnings reminders. They're free."); Button("Sign in") { onDone(); onSignIn() } }
                } else {
                    let prefs = model.state?.preferences
                    Section {
                        Text("\(target.name) (\(target.symbol))").font(.headline)
                        Text("Earnings date: \(target.dateText)" + (target.dateNote.map { " · \($0)" } ?? ""))
                        Text("Reporting time: \(target.timingText ?? "Not confirmed")")
                    }
                    Section {
                        Picker("Notify me", selection: $offset) {
                            ForEach(model.client.reminderOffsets.map { $0.int32Value }, id: \.self) { d in Text(d == 1 ? "1 day before" : "\(d) days before").tag(d) }
                        }
                        .pickerStyle(.segmented)
                        Toggle("Also tell me when results are published", isOn: $results)
                    } footer: {
                        Text("Delivered around \(prefs?.deliveryTime ?? "09:00") your time (\(prefs?.timeZone ?? "")) on that day. Push services can delay delivery, so it isn't exact. A reminder is a heads-up, not a signal to buy or sell.")
                    }
                    if let warning = model.state?.deliveryWarning {
                        Section {
                            Text(warning).font(.footnote)
                            if permission == false { Button("Allow notifications") { Task { permission = await PushCoordinator.shared.requestAuthorization(); await model.refreshPermission() } } }
                        }
                    }
                }
            }
            .navigationTitle("Remind Me About Earnings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: onDone) }
                if model.signedIn {
                    ToolbarItem(placement: .confirmationAction) {
                        Button(model.control(target.eventId) == "ON" ? "Save" : "Remind Me") { model.client.remind(eventId: target.eventId, offsetDays: offset, results: results); onDone() }
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                if model.control(target.eventId) == "ON" {
                    Button("Turn off reminder", role: .destructive) { model.client.turnOffReminder(eventId: target.eventId); onDone() }.padding()
                }
            }
            .task {
                if let state = model.state { offset = model.client.reminderOffset(state: state, eventId: target.eventId) }
                await model.refreshPermission()
                permission = await PushCoordinator.shared.enabled
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/// Earnings Reminders settings: preferences, permission, reminders with status, scheduled notifications.
struct EarningsRemindersSettingsScene: View {
    let model: EarningsRemindersModel
    var onSignIn: () -> Void = {}
    var onOpenEvent: (String) -> Void = { _ in }
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        Form {
            Section { Text("Get a heads-up before companies report, and when results are out. Free.").foregroundStyle(colors.textSecondary) }
            if let state = model.state, state.signedIn {
                let p = state.preferences
                if state.response?.sampleData == true { Section { Text("Sample mode: notifications are simulated, nothing is sent through Firebase.").font(.footnote) } }
                if let message = state.message { Section { Text(message).foregroundStyle(colors.cautionText); Button("Try again") { model.client.refreshReminders() } } }
                Section("Device notifications") {
                    Text(state.permissionGranted?.boolValue == true ? "Allowed for StockSteps on this device."
                         : state.permissionGranted?.boolValue == false ? "Off for StockSteps on this device. Reminders are saved, but nothing will appear until you allow notifications."
                         : "Couldn't check this device's notification setting.")
                    if state.permissionGranted?.boolValue != true {
                        Button("Allow notifications") { Task { _ = await PushCoordinator.shared.requestAuthorization(); await model.refreshPermission() } }
                    }
                }
                Section {
                    toggle("Earnings notifications", p.enabled, p) { q, v in (v, q.watchlistAuto, q.resultsAvailable, q.dateChanges, q.cancellations) }
                    toggle("Automatically remind me about earnings for companies in my watchlist.", p.watchlistAuto, p) { q, v in (q.enabled, v, q.resultsAvailable, q.dateChanges, q.cancellations) }
                    Picker("Default timing", selection: Binding(get: { p.defaultOffsetDays }, set: { save(p, offset: $0) })) {
                        ForEach(model.client.reminderOffsets.map { $0.int32Value }, id: \.self) { d in Text(d == 1 ? "1 day before" : "\(d) days before").tag(d) }
                    }
                    toggle("Results available", p.resultsAvailable, p) { q, v in (q.enabled, q.watchlistAuto, v, q.dateChanges, q.cancellations) }
                    toggle("Date changes", p.dateChanges, p) { q, v in (q.enabled, q.watchlistAuto, q.resultsAvailable, v, q.cancellations) }
                    toggle("Canceled reports", p.cancellations, p) { q, v in (q.enabled, q.watchlistAuto, q.resultsAvailable, q.dateChanges, v) }
                    Picker("Delivery time", selection: Binding(get: { p.deliveryTime }, set: { save(p, time: $0) })) {
                        ForEach(["07:00", "08:00", "09:00", "12:00", "18:00"], id: \.self) { Text($0).tag($0) }
                    }
                    Toggle("Quiet hours (10 PM – 7 AM)", isOn: Binding(get: { p.quietStart != nil }, set: { save(p, quiet: $0) }))
                } footer: {
                    Text("Automatic reminders are off unless you turn them on; each company is reminded once even if it's on several watchlists. Times use your device's time zone (\(p.timeZone)) and follow you when you travel. Earnings dates are the exchange's local date.")
                }
                Section("Your reminders") {
                    let reminders = state.response?.reminders ?? []
                    if reminders.isEmpty { Text("No reminders yet. Tap \"Remind Me\" on an upcoming earnings event.").foregroundStyle(colors.textSecondary) }
                    ForEach(reminders, id: \.reminderId) { r in
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(r.companyName ?? r.instrumentId) (\(r.instrumentId))").font(.headline)
                            Text([r.source.name == "WATCHLIST_AUTO" ? "From your watchlist" : (r.earningsEventId == nil ? "Every upcoming report" : nil),
                                  r.offsetDays == 1 ? "1 day before" : "\(r.offsetDays) days before"].compactMap { $0 }.joined(separator: " · "))
                                .font(.caption).foregroundStyle(colors.textSecondary)
                            Text(r.statusMessage ?? r.nextDeliveryText.map { "Notification: \($0)" } ?? r.scheduleStatus.label).font(.subheadline)
                        }
                        .accessibilityElement(children: .combine)
                        .swipeActions { if r.source.name == "MANUAL" { Button("Remove", role: .destructive) { model.client.deleteReminder(reminderId: r.reminderId) } } }
                        .onTapGesture { if let id = r.earningsEventId { onOpenEvent(id) } }
                    }
                }
                let notes = state.response?.notifications ?? []
                if !notes.isEmpty {
                    Section {
                        ForEach(notes, id: \.idempotencyKey) { n in
                            VStack(alignment: .leading) {
                                Text("\(n.type.label) · \(n.instrumentId) · \(n.status.name.capitalized)").font(.subheadline)
                                Text(n.scheduledFor + (n.failureReason.map { " · \($0)" } ?? "")).font(.caption).foregroundStyle(colors.textSecondary)
                            }
                            .accessibilityElement(children: .combine)
                        }
                    } header: { Text("Scheduled and sent") } footer: { Text("\"Submitted\" means the push service accepted it; it doesn't confirm the device showed it.") }
                }
            } else {
                Section { Text("Sign in to set up earnings reminders."); Button("Sign in", action: onSignIn) }
            }
        }
        .navigationTitle("Earnings Reminders")
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.refreshPermission(); model.client.refreshReminders() }
        .refreshable { model.client.refreshReminders() }
    }

    private func toggle(_ title: String, _ value: Bool, _ p: EarningsReminderPreferences,
                        _ make: @escaping (EarningsReminderPreferences, Bool) -> (Bool, Bool, Bool, Bool, Bool)) -> some View {
        Toggle(title, isOn: Binding(get: { value }, set: { v in
            let (enabled, auto, results, dates, cancels) = make(p, v)
            model.client.updateReminderPreferences(enabled: enabled, watchlistAuto: auto, defaultOffsetDays: p.defaultOffsetDays, resultsAvailable: results,
                                                   dateChanges: dates, cancellations: cancels, deliveryTime: p.deliveryTime, quietHours: p.quietStart != nil)
        }))
    }

    private func save(_ p: EarningsReminderPreferences, offset: Int32? = nil, time: String? = nil, quiet: Bool? = nil) {
        model.client.updateReminderPreferences(enabled: p.enabled, watchlistAuto: p.watchlistAuto, defaultOffsetDays: offset ?? p.defaultOffsetDays,
                                               resultsAvailable: p.resultsAvailable, dateChanges: p.dateChanges, cancellations: p.cancellations,
                                               deliveryTime: time ?? p.deliveryTime, quietHours: quiet ?? (p.quietStart != nil))
    }
}
