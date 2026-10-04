import Shared
import SwiftUI

struct CompanyProfileView: View {
    let model: StockSearchViewModel
    let stock: StockSearchResult

    var body: some View {
        Section("About the company") {
            if model.isLoadingProfile {
                ProgressView("Loading company profile…")
            } else if let error = model.profileError {
                Text(error)
                Button("Retry profile") { model.loadProfile(for: stock) }.stockStepsGlassButton()
            } else if let profile = model.profile {
                if let name = profile.companyName, !name.isEmpty { Text(name).font(.headline) }
                if let sector = profile.sector, !sector.isEmpty { LabeledContent("Sector", value: sector) }
                if let industry = profile.industry, !industry.isEmpty { LabeledContent("Industry", value: industry) }
                if let country = profile.country, !country.isEmpty { LabeledContent("Country", value: country) }
                if let description = profile.description_, !description.isEmpty {
                    Text(description)
                } else {
                    Text("Company description unavailable.")
                }
            }
        }
    }
}
