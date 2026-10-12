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
                TextField(display.ACCOUNT_NAME, text: $name)
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
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(display.FORM_CANCEL) { dismiss() } } }
        }.onAppear {
            if let existing { name = existing.name; category = existing.category.name; currency = existing.reportingCurrency.name; archived = existing.archived }
        }
    }
}

struct PortfolioTransactionForm: View {
    let state: PortfolioUiState?
    let existing: PortfolioTransaction?
    let instrument: InstrumentRef?
    let onSave: (String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String) -> Void
    @Environment(\.dismiss) private var dismiss
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
    @Environment(\.colorScheme) private var scheme
    private let display = PortfolioDisplay.shared
    private let rules = PortfolioFormRules.shared
    private let present = PortfolioPresentation.shared
    private var transactionType: TransactionType { rules.type(name: type) ?? .openingPosition }

    private func label(_ field: PortfolioFormField) -> String {
        rules.label(field: field, type: transactionType, currency: currency, cashCurrency: cashCurrency)
    }

    /// One form row per shared field; pickers show readable labels and keep enum names / account ids as tags (A-F1, A-F2).
    @ViewBuilder private func fieldView(_ field: PortfolioFormField) -> some View {
        let keyboard: UIKeyboardType = rules.decimal(field: field) ? .decimalPad : .default
        if field == .account {
            Picker(label(field), selection: $account) {
                ForEach(rules.accountOptions(accounts: state?.accounts ?? [], currentId: account), id: \.id) { Text($0.label).tag($0.id) }
            }
        } else if field == .transactionType {
            Picker(label(field), selection: $type) {
                ForEach(rules.typeOptions, id: \.name) { Text(present.transactionLabel(type: $0)).tag($0.name) }
            }
        } else if field == .tradeCurrency {
            Picker(label(field), selection: $currency) { ForEach(rules.currencyOptions, id: \.name) { Text(verbatim: $0.name).tag($0.name) } }
        } else if field == .cashCurrency {
            Picker(label(field), selection: $cashCurrency) { ForEach(rules.currencyOptions, id: \.name) { Text(verbatim: $0.name).tag($0.name) } }
        } else if field == .symbol {
            TextField(label(field), text: $symbol).textInputAutocapitalization(.characters).autocorrectionDisabled()
        } else if field == .companyName {
            TextField(label(field), text: $name)
        } else if field == .exchange {
            TextField(label(field), text: $exchange)
        } else if field == .shares {
            TextField(label(field), text: $quantity).keyboardType(keyboard)
        } else if field == .price {
            TextField(label(field), text: $price).keyboardType(keyboard)
        } else if field == .amount {
            TextField(label(field), text: $amount).keyboardType(keyboard)
        } else if field == .fees {
            TextField(label(field), text: $fees).keyboardType(keyboard)
        } else if field == .fxRate {
            TextField(label(field), text: $fxRate).keyboardType(keyboard)
        } else if field == .date {
            TextField(label(field), text: $date)
        } else if field == .settlement {
            TextField(label(field), text: $settlementDate)
        } else if field == .notes {
            TextField(label(field), text: $notes, axis: .vertical)
        }
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        NavigationStack {
            Form {
                Section {
                    Text(display.FORM_INTRO)
                    // Same fields, order and labels as Compose (PortfolioFormRules); the typed number text is sent as entered.
                    ForEach(rules.visibleFields(type: transactionType, symbol: symbol, cashDiffers: cashCurrency != currency), id: \.name) { field in
                        fieldView(field)
                    }
                }
                if let error = state?.error { Text(error).foregroundStyle(colors.negativeText) }
                Button {
                    onSave(id, account, type, date, currency, symbol.uppercased().trimmingCharacters(in: .whitespaces), name, exchange, quantity, price, amount, fees, notes, cashCurrency, fxRate, settlementDate, securityCurrency)
                } label: {
                    Text(state?.busy == true ? display.FORM_SAVING : display.FORM_SAVE).frame(maxWidth: .infinity)
                }
                .buttonStyle(.stockPrimary)
                .disabled(account.isEmpty || state?.busy == true)
                .listRowBackground(Color.clear)
            }.navigationTitle(existing == nil ? display.FORM_ADD_TITLE : display.FORM_EDIT_TITLE)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(display.FORM_CANCEL) { dismiss() } } }
        }.onChange(of: state?.selectedAccountId) { _, value in if account.isEmpty { account = value ?? "" } }
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
        }
    }
}
