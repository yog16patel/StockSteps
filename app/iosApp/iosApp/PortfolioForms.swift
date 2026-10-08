import SwiftUI
import Shared

struct PortfolioAccountForm: View {
    let existing: PortfolioAccount?
    let onSave: (String, String, String, String, Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var category = "PERSONAL"
    @State private var currency = "CAD"
    @State private var archived = false
    var body: some View {
        NavigationStack {
            Form {
                TextField("Account name", text: $name)
                Picker("Category", selection: $category) {
                    ForEach(["TFSA", "RRSP", "FHSA", "NON_REGISTERED", "PERSONAL", "OTHER"], id: \.self) { Text($0.replacingOccurrences(of: "_", with: " ")).tag($0) }
                }
                Picker("Reporting currency", selection: $currency) { Text("CAD").tag("CAD"); Text("USD").tag("USD") }
                if existing != nil { Toggle("Archive account", isOn: $archived) }
                Text("Categories organize investments. Moving-average cost is not a legal tax-basis calculation.").font(.caption)
                Button("Save account") { onSave(existing?.id ?? UUID().uuidString, name, category, currency, archived) }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || name.count > 40)
            }.navigationTitle(existing == nil ? "Create account" : "Account settings")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
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
    private var shareType: Bool { ["BUY", "SELL", "OPENING_POSITION", "DIVIDEND_REINVESTMENT", "STOCK_SPLIT"].contains(type) }
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Existing investments begin on the effective date you enter. No earlier returns are invented.")
                    Picker("Account", selection: $account) {
                        ForEach(state?.accounts.filter { !$0.archived } ?? [], id: \.id) { Text($0.name).tag($0.id) }
                    }
                    Picker("Transaction", selection: $type) {
                        ForEach(["OPENING_POSITION", "BUY", "SELL", "CASH_DEPOSIT", "CASH_WITHDRAWAL", "DIVIDEND", "DIVIDEND_REINVESTMENT", "FEE", "CASH_ADJUSTMENT", "TRANSFER_IN", "TRANSFER_OUT", "STOCK_SPLIT"], id: \.self) { Text($0.replacingOccurrences(of: "_", with: " ")).tag($0) }
                    }
                    Picker("Trade / cash currency", selection: $currency) { Text("CAD").tag("CAD"); Text("USD").tag("USD") }
                    TextField("Security symbol (optional for cash)", text: $symbol).textInputAutocapitalization(.characters).autocorrectionDisabled()
                    if shareType || !symbol.isEmpty {
                        TextField("Company name", text: $name)
                        TextField("Exchange", text: $exchange)
                        TextField(type == "STOCK_SPLIT" ? "New / old split ratio" : "Shares", text: $quantity).keyboardType(.decimalPad)
                        TextField(type == "OPENING_POSITION" ? "Average price per share" : "Price per share", text: $price).keyboardType(.decimalPad)
                    }
                    if !shareType { TextField("Amount", text: $amount).keyboardType(.decimalPad) }
                    TextField("Fees", text: $fees).keyboardType(.decimalPad)
                    TextField("Effective / trade date · YYYY-MM-DD", text: $date)
                    Picker("Cash currency", selection: $cashCurrency) { Text("CAD").tag("CAD"); Text("USD").tag("USD") }
                    if cashCurrency != currency { TextField("Trade-date FX: one \(currency) in \(cashCurrency)", text: $fxRate).keyboardType(.decimalPad) }
                    TextField("Settlement date (optional) · YYYY-MM-DD", text: $settlementDate)
                    TextField("Notes", text: $notes, axis: .vertical)
                }
                if let error = state?.error { Text(error).foregroundStyle(.red) }
                Button(state?.busy == true ? "Saving…" : "Save transaction") {
                    onSave(id, account, type, date, currency, symbol.uppercased().trimmingCharacters(in: .whitespaces), name, exchange, quantity, price, amount, fees, notes, cashCurrency, fxRate, settlementDate, securityCurrency)
                }.disabled(account.isEmpty || state?.busy == true)
            }.navigationTitle(existing == nil ? "Add investment" : "Edit transaction")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
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
