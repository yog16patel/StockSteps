import SwiftUI
import Shared

struct PortfolioAccountForm: View {
    let existing: PortfolioAccount?
    let onSave: (String, String, String, String, Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme
    @State private var name = ""
    @State private var category = "PERSONAL"
    @State private var currency = "CAD"
    @State private var archived = false
    private let display = PortfolioDisplay.shared
    private let rules = PortfolioFormRules.shared
    private let present = PortfolioPresentation.shared
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        NavigationStack {
            Form {
                // E.2 (D-4): the label stays visible above the input, as in the transaction form (a placeholder-only label vanished once typed).
                VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.xxs)) {
                    Text(display.ACCOUNT_NAME).font(StockStepsTheme.font(StockStepsTheme.typography.label, relativeTo: .footnote))
                        .foregroundStyle(colors.textSecondary).accessibilityHidden(true)
                    TextField("", text: $name, prompt: Text(verbatim: "")).accessibilityLabel(display.ACCOUNT_NAME)
                }
                .padding(.vertical, CGFloat(StockStepsTheme.spacing.xxs))
                // Readable category labels; the tag stays the enum name the bridge expects (A-F2).
                Picker(display.ACCOUNT_CATEGORY, selection: $category) {
                    ForEach(rules.categoryOptions, id: \.name) { Text(present.categoryLabel(category: $0)).tag($0.name) }
                }
                Picker(display.ACCOUNT_CURRENCY, selection: $currency) {
                    ForEach(rules.currencyOptions, id: \.name) { Text(verbatim: $0.name).tag($0.name) }
                }
                if existing != nil { Toggle(display.ACCOUNT_ARCHIVE, isOn: $archived) }
                Text(display.ACCOUNT_NOTE).font(StockStepsTheme.font(StockStepsTheme.typography.caption, relativeTo: .footnote)).foregroundStyle(colors.textMeta)
                Button { onSave(existing?.id ?? UUID().uuidString, name, category, currency, archived) } label: {
                    Text(display.ACCOUNT_SAVE).frame(maxWidth: .infinity)
                }
                .buttonStyle(.stockPrimary)
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || name.count > 40)
                .listRowBackground(Color.clear)
            }.navigationTitle(existing == nil ? display.ACCOUNT_CREATE_TITLE : display.ACCOUNT_SETTINGS_TITLE)
                // E.2: inline like every other Portfolio sheet — a large title never wraps and truncated "Create investment ac…" at Large.
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(display.FORM_CANCEL) { dismiss() } } }
        }.onAppear {
            if let existing { name = existing.name; category = existing.category.name; currency = existing.reportingCurrency.name; archived = existing.archived }
        }
    }
}

/// Portfolio transaction form (Phase D1, mirrors Compose): "Transaction information", "Amounts" (only the fields the type uses, labelled with their
/// currency), then "More options" collapsed. Dates use the native picker on whole UTC days and are stored as YYYY-MM-DD; typed numbers are sent
/// exactly as entered. The shared check (`PortfolioFormRules.check` → `PortfolioEngine.validate`) runs before saving and places each message under
/// its field. Sample scenarios show why entry is unavailable.
struct PortfolioTransactionForm: View {
    let state: PortfolioUiState?
    let existing: PortfolioTransaction?
    let instrument: InstrumentRef?
    var onSearch: (() -> Void)? = nil
    var onUseSavedAccounts: (() -> Void)? = nil
    let onSave: (String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme
    @State private var id = UUID().uuidString
    @State private var account = ""
    @State private var type = "OPENING_POSITION"
    @State private var date = ""
    @State private var currency = "USD"
    @State private var symbol = ""
    @State private var name = ""
    @State private var exchange = ""
    @State private var quantity = ""
    @State private var price = ""
    @State private var amount = ""
    @State private var fees = "0"
    @State private var notes = ""
    @State private var cashCurrency = "USD"
    @State private var fxRate = ""
    @State private var settlementDate = ""
    @State private var securityCurrency = "USD"
    @State private var errors: [PortfolioFormField: String] = [:]
    @State private var formMessage: String?
    @State private var moreOpen = false
    /// The presenter's failure belongs to this form only after this form submitted.
    @State private var attempted = false
    @FocusState private var focused: PortfolioFormField?
    @Environment(\.dynamicTypeSize) private var typeSize
    private let display = PortfolioDisplay.shared
    private let rules = PortfolioFormRules.shared
    private let dates = PortfolioFormDates.shared
    private let present = PortfolioPresentation.shared
    private var transactionType: TransactionType { rules.type(name: type) ?? .openingPosition }
    private var cashDiffers: Bool { cashCurrency != currency }

    private func label(_ field: PortfolioFormField) -> String {
        rules.label(field: field, type: transactionType, currency: currency, cashCurrency: cashCurrency)
    }
    private func fields(_ section: PortfolioFormSection) -> [PortfolioFormField] {
        rules.sectionFields(section: section, type: transactionType, symbol: symbol, cashDiffers: cashDiffers)
    }
    /// Keyboard order: the visible text fields; Next moves on, the last one shows Done.
    private var textFields: [PortfolioFormField] {
        (fields(.details) + fields(.amounts) + (moreOpen ? fields(.more) : [])).filter { $0 == .symbol || $0 == .companyName || $0 == .exchange || rules.decimal(field: $0) }
    }
    private func next(after field: PortfolioFormField) {
        if let index = textFields.firstIndex(of: field), index + 1 < textFields.count { focused = textFields[index + 1] } else { focused = nil }
    }
    private func clear(_ field: PortfolioFormField) { errors[field] = nil }

    /// Whole days since 1970 ↔ a Date at UTC midnight; the picker runs in UTC, so the chosen day never shifts with the device time zone.
    private func day(_ text: Binding<String>, fallback: String) -> Binding<Date> {
        Binding(
            get: { Date(timeIntervalSince1970: TimeInterval((dates.epochDay(date: text.wrappedValue.isEmpty ? fallback : text.wrappedValue)?.int64Value ?? 0) * 86_400)) },
            set: { text.wrappedValue = dates.date(epochDay: Int64(floor($0.timeIntervalSince1970 / 86_400))) }
        )
    }
    private var earliest: Date { Date(timeIntervalSince1970: TimeInterval((dates.epochDay(date: dates.EARLIEST)?.int64Value ?? 0) * 86_400)) }

    /// D1: the label stays visible above the input (a placeholder-only label vanished once a value such as fees "0" was there).
    @ViewBuilder private func textField(_ field: PortfolioFormField, _ text: Binding<String>, keyboard: UIKeyboardType = .default) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.xxs)) {
            Text(label(field)).font(StockStepsTheme.font(StockStepsTheme.typography.label, relativeTo: .footnote))
                .foregroundStyle(StockStepsTheme.colors(scheme).textSecondary).accessibilityHidden(true)
            TextField("", text: text, prompt: Text(verbatim: ""))
                .accessibilityLabel(label(field))
                .keyboardType(keyboard)
                .focused($focused, equals: field)
                .submitLabel(field == textFields.last ? .done : .next)
                .onSubmit { next(after: field) }
                .onChange(of: text.wrappedValue) { _, _ in clear(field) }
        }
        .padding(.vertical, CGFloat(StockStepsTheme.spacing.xxs))
    }

    /// D1: at accessibility sizes a picker's label goes above its value, so neither is squeezed ("Trans-action type …" at AX5).
    @ViewBuilder private func stacked<Control: View>(_ field: PortfolioFormField, @ViewBuilder _ control: () -> Control) -> some View {
        if typeSize.isAccessibilitySize {
            VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.xxs)) {
                Text(label(field)).font(StockStepsTheme.font(StockStepsTheme.typography.label, relativeTo: .footnote))
                    .foregroundStyle(StockStepsTheme.colors(scheme).textSecondary).accessibilityHidden(true)
                control().labelsHidden().accessibilityLabel(label(field))
            }
        } else {
            control()
        }
    }

    /// D1: a picker whose value wraps at accessibility sizes (the system menu picker kept one line: "Ope…sition"); the options are the
    /// same Picker, shown inline inside a Menu. Below accessibility sizes it is the standard row picker.
    @ViewBuilder private func choice<Content: View>(_ field: PortfolioFormField, value: String, selection: Binding<String>, @ViewBuilder options: () -> Content) -> some View {
        if typeSize.isAccessibilitySize {
            let colors = StockStepsTheme.colors(scheme)
            VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.xxs)) {
                Text(label(field)).font(StockStepsTheme.font(StockStepsTheme.typography.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
                    .accessibilityHidden(true)
                Menu {
                    Picker(label(field), selection: selection) { options() }.pickerStyle(.inline)
                } label: {
                    HStack(alignment: .firstTextBaseline) {
                        Text(value).multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading)
                        Image(systemName: "chevron.up.chevron.down").font(.system(size: CGFloat(StockStepsTheme.dimensions.iconSmall) * 0.8)).accessibilityHidden(true)
                    }
                    .foregroundStyle(colors.primaryText)
                    .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
                }
                .accessibilityLabel(label(field)).accessibilityValue(value)
            }
        } else {
            Picker(label(field), selection: selection) { options() }
        }
    }

    /// One row per shared field; pickers show readable labels and keep enum names / account ids as tags (A-F1, A-F2).
    @ViewBuilder private func fieldView(_ field: PortfolioFormField) -> some View {
        let number: UIKeyboardType = .decimalPad
        if field == .account {
            let options = rules.accountOptions(accounts: state?.accounts ?? [], currentId: account)
            choice(field, value: options.first(where: { $0.id == account })?.label ?? display.CHOOSE_ACCOUNT, selection: $account) {
                ForEach(options, id: \.id) { Text($0.label).tag($0.id) }
            }
        } else if field == .transactionType {
            choice(field, value: present.transactionLabel(type: transactionType), selection: $type) {
                ForEach(rules.typeOptions, id: \.name) { Text(present.transactionLabel(type: $0)).tag($0.name) }
            }
        } else if field == .tradeCurrency {
            // E.2 (mirrors Compose): the cash currency follows the trade currency while they were the same, so picking CAD doesn't leave cash in
            // USD and ask for an FX rate nobody needed.
            let trade = Binding(get: { currency }, set: { new in if cashCurrency == currency { cashCurrency = new }; currency = new })
            choice(field, value: currency, selection: trade) { ForEach(rules.currencyOptions, id: \.name) { Text(verbatim: $0.name).tag($0.name) } }
            // E.2: says when the amounts aren't in the account's reporting currency (a typed symbol's currency isn't looked up).
            if let note = rules.currencyNote(tradeCurrency: currency, account: state?.accounts.first(where: { $0.id == account })) {
                Text(note).font(StockStepsTheme.font(StockStepsTheme.typography.caption, relativeTo: .footnote))
                    .foregroundStyle(StockStepsTheme.colors(scheme).textSupporting)
            }
        } else if field == .cashCurrency {
            choice(field, value: cashCurrency, selection: $cashCurrency) { ForEach(rules.currencyOptions, id: \.name) { Text(verbatim: $0.name).tag($0.name) } }
        } else if field == .symbol {
            textField(field, $symbol).textInputAutocapitalization(.characters).autocorrectionDisabled()
            // D1.1: the chosen security, named under the symbol when its details are known (never looked up).
            if let selected = rules.selectedSecurity(symbol: symbol, name: name, exchange: exchange, currency: securityCurrency) {
                Text(verbatim: selected).font(StockStepsTheme.font(StockStepsTheme.typography.caption, relativeTo: .footnote))
                    .foregroundStyle(StockStepsTheme.colors(scheme).textSupporting)
            }
        } else if field == .companyName {
            textField(field, $name)
        } else if field == .exchange {
            textField(field, $exchange)
        } else if field == .shares {
            textField(field, $quantity, keyboard: number)
        } else if field == .price {
            textField(field, $price, keyboard: number)
        } else if field == .amount {
            textField(field, $amount, keyboard: number)
        } else if field == .fees {
            textField(field, $fees, keyboard: number)
        } else if field == .fxRate {
            textField(field, $fxRate, keyboard: number)
        } else if field == .date {
            stacked(field) {
                DatePicker(label(field), selection: day($date, fallback: state?.today ?? ""), in: earliest..., displayedComponents: .date)
                    .environment(\.timeZone, TimeZone(identifier: "UTC")!)
            }
            .onChange(of: date) { _, _ in clear(field) }
            if let note = rules.dateNote(type: transactionType) {
                Text(note).font(StockStepsTheme.font(StockStepsTheme.typography.caption, relativeTo: .footnote)).foregroundStyle(StockStepsTheme.colors(scheme).textSupporting)
            }
        } else if field == .settlement {
            if settlementDate.isEmpty {
                Button(display.FORM_ADD_SETTLEMENT, systemImage: "calendar.badge.plus") { settlementDate = date }
                    .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
            } else {
                stacked(field) {
                    DatePicker(label(field), selection: day($settlementDate, fallback: date), in: earliest..., displayedComponents: .date)
                        .environment(\.timeZone, TimeZone(identifier: "UTC")!)
                }
                .onChange(of: settlementDate) { _, _ in clear(field) }
                Button(display.FORM_REMOVE_SETTLEMENT, role: .destructive) { settlementDate = ""; clear(field) }
                    .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
            }
        } else if field == .notes {
            TextField(label(field), text: $notes, axis: .vertical).focused($focused, equals: field).onChange(of: notes) { _, _ in clear(field) }
        }
        if let error = errors[field] {
            Text(error).font(StockStepsTheme.font(StockStepsTheme.typography.caption, relativeTo: .footnote))
                .foregroundStyle(StockStepsTheme.colors(scheme).negativeText)
                .accessibilityLabel("\(label(field)): \(error)")
        }
        // The search link follows the symbol and its error, so the message sits right under the field it is about.
        if field == .symbol, let onSearch {
            Button(display.SEARCH_SECURITY, systemImage: "magnifyingglass", action: onSearch).frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
        }
    }

    private func submit() {
        guard state?.busy != true, let trade = rules.currencyOptions.first(where: { $0.name == currency }),
              let cash = rules.currencyOptions.first(where: { $0.name == cashCurrency }) else { return }
        let symbolText = symbol.uppercased().trimmingCharacters(in: .whitespaces)
        let tx = rules.visibleTransaction(id: id, accountId: account, type: transactionType, date: date, currency: trade, symbol: symbolText, name: name,
                                          exchange: exchange, instrumentCurrency: securityCurrency, quantity: quantity, price: price, amount: amount, fees: fees,
                                          cashCurrency: cash, fxRate: fxRate, settlement: settlementDate, notes: notes)
        let check = rules.check(transaction: tx, inputs: [.shares: quantity, .price: price, .amount: amount, .fees: fees, .fxRate: fxRate])
        errors = check.errors
        formMessage = check.message ?? (check.errors.isEmpty ? nil : display.FORM_ERRORS_SUMMARY)
        if check.errors.keys.contains(where: { rules.section(field: $0) == .more }) { moreOpen = true }
        guard check.valid else {
            // Bring the first problem into view: focus its input (pickers stay where they are in the Form).
            if let first = rules.redesignOrder.first(where: { check.errors[$0] != nil }), textFields.contains(first) { focused = first }
            return
        }
        focused = nil
        attempted = true
        onSave(id, account, type, tx.tradeDate, currency, symbolText, tx.instrument?.name ?? "", tx.instrument?.exchange ?? "", tx.quantity, tx.unitPrice,
               tx.grossAmount, tx.fees, tx.notes ?? "", cashCurrency, tx.fxRate ?? "", tx.settlementDate ?? "", securityCurrency)
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        NavigationStack {
            Group {
                if rules.readOnly(state: state) {
                    readOnly(colors)
                } else {
                    form(colors)
                }
            }
            .navigationTitle(existing == nil ? display.FORM_ADD_TITLE : display.FORM_EDIT_TITLE)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(display.FORM_CANCEL) { dismiss() } }
                ToolbarItemGroup(placement: .keyboard) { Spacer(); Button(display.FORM_KEYBOARD_DONE) { focused = nil } }
            }
        }.onChange(of: state?.selectedAccountId) { _, value in if account.isEmpty { account = value ?? "" } }
        .onChange(of: formMessage) { _, message in if let message { AccessibilityNotification.Announcement(message).post() } }
        .onChange(of: state?.error) { _, error in
            if attempted, let error { AccessibilityNotification.Announcement("\(display.FORM_SAVE_FAILED_TITLE). \(error)").post() }
        }
        .onChange(of: type) { _, _ in
            // D1.1: errors from the old type's rules no longer apply; format errors stay while their field is still shown.
            errors = rules.retainedErrors(errors: errors, type: transactionType, symbol: symbol, cashDiffers: cashDiffers)
            formMessage = nil; attempted = false
        }
        .onAppear {
            account = existing?.accountId ?? state?.selectedAccountId ?? ""
            date = existing?.tradeDate ?? state?.today ?? String(ISO8601DateFormatter().string(from: Date()).prefix(10))
            let security = existing?.instrument ?? instrument
            symbol = security?.symbol ?? ""; name = security?.name ?? ""; exchange = security?.exchange ?? ""
            currency = existing?.currency.name ?? security?.currency ?? "USD"
            securityCurrency = security?.currency ?? currency
            cashCurrency = existing?.cashCurrency.name ?? currency
            fxRate = existing?.fxRate ?? ""
            settlementDate = existing?.settlementDate ?? ""
            if let existing {
                id = existing.id; type = existing.type.name; quantity = existing.quantity; price = existing.unitPrice
                amount = existing.grossAmount; fees = existing.fees; notes = existing.notes ?? ""
            }
            moreOpen = rules.moreOptionsOpen(values: [.fxRate: fxRate, .settlement: settlementDate, .notes: notes], errors: [], tradeCurrency: currency, cashCurrency: cashCurrency)
        }
    }

    private func form(_ colors: StockColors) -> some View {
        Form {
            Section(rules.sectionTitle(section: .details)) { ForEach(fields(.details), id: \.name) { fieldView($0) } }
            Section(rules.sectionTitle(section: .amounts)) { ForEach(fields(.amounts), id: \.name) { fieldView($0) } }
            Section {
                DisclosureGroup(isExpanded: $moreOpen) {
                    ForEach(fields(.more), id: \.name) { fieldView($0) }
                } label: {
                    VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.xxs)) {
                        Text(display.FORM_SECTION_MORE).font(StockStepsTheme.font(StockStepsTheme.typography.bodySemiBold, relativeTo: .body)).foregroundStyle(colors.textTitle)
                        if !moreOpen { Text(display.FORM_MORE_HINT).font(StockStepsTheme.font(StockStepsTheme.typography.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting) }
                    }
                    .multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading)
                }
                .tint(colors.iconSecondary)
            }
            Section {
                if let formMessage { Text(formMessage).foregroundStyle(colors.negativeText) }
                // Phase E (E-02): announce the form-level message and a rejected save (as Compose's live region does); field errors already move focus.
                if attempted, let error = state?.error {
                    VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.xxs)) {
                        Text(display.FORM_SAVE_FAILED_TITLE).font(StockStepsTheme.font(StockStepsTheme.typography.bodySemiBold, relativeTo: .body))
                        Text(error)
                    }.foregroundStyle(colors.negativeText).accessibilityElement(children: .combine)
                }
                Button(action: submit) {
                    Text(state?.busy == true ? display.FORM_SAVING : display.FORM_SAVE).frame(maxWidth: .infinity)
                }
                .buttonStyle(.stockPrimary)
                .disabled(account.isEmpty || state?.busy == true)
                .listRowBackground(Color.clear)
            }
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func readOnly(_ colors: StockColors) -> some View {
        ScrollView {
            VStack(spacing: CGFloat(StockStepsTheme.spacing.lg)) {
                StockBanner(kind: .sample, message: display.SAMPLE_SHORT)
                Image(systemName: "lock.doc").font(.system(size: CGFloat(StockStepsTheme.dimensions.educationIcon) * 0.7)).foregroundStyle(colors.primaryText).accessibilityHidden(true)
                Text(display.FORM_READ_ONLY_TITLE).font(StockStepsTheme.font(StockStepsTheme.typography.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textTitle)
                    .multilineTextAlignment(.center).accessibilityAddTraits(.isHeader)
                Text(display.FORM_READ_ONLY_MESSAGE).foregroundStyle(colors.textBody).multilineTextAlignment(.center)
                if let onUseSavedAccounts {
                    Button(action: onUseSavedAccounts) { Text(display.FORM_READ_ONLY_ACTION).frame(maxWidth: .infinity) }.buttonStyle(.stockPrimary)
                }
            }.padding(CGFloat(StockStepsTheme.spacing.screen))
        }
    }
}
