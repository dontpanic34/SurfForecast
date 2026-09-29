import SwiftUI
import SharedCore

struct ContentView: View {
    @State private var points: [HourlyForecastPoint] = []
    @State private var todayTide: DailyTide?
    @State private var errorMessage: String?
    @State private var isLoading = true

    var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    ProgressView("Chargement…")
                } else if let errorMessage {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                        .padding()
                } else {
                    List {
                        if let todayTide {
                            Section("Marée du jour") {
                                TideRow(tide: todayTide)
                            }
                        }
                        Section("Prochaines heures") {
                            ForEach(Array(points.prefix(24).enumerated()), id: \.offset) { _, point in
                                ForecastRow(point: point)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Montalivet")
        }
        .task { await load() }
    }

    @MainActor
    private func load() async {
        let spot = SpotCoordinates(latitude: 45.38, longitude: -1.16)
        do {
            points = try await SharedForecastService().getForecast(spot: spot)
        } catch {
            errorMessage = error.localizedDescription
        }
        // Les marées ne bloquent pas l'affichage : le service renvoie une liste vide en cas d'erreur.
        let today = Self.isoDay.string(from: Date())
        let tides = try? await SharedTideService().getTides(spot: spot, fromDateIso: today, toDateIso: today)
        todayTide = tides?.first
        isLoading = false
    }

    private static let isoDay: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Europe/Paris")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}

private struct TideRow: View {
    let tide: DailyTide

    var body: some View {
        HStack {
            Label(tide.highTideTime ?? "--", systemImage: "arrow.up")
            Spacer()
            Label(tide.lowTideTime ?? "--", systemImage: "arrow.down")
            Spacer()
            if let coef = tide.coefficient {
                Text("Coef \(coef.intValue)")
                    .font(.subheadline.bold())
            }
        }
    }
}

private struct ForecastRow: View {
    let point: HourlyForecastPoint

    var body: some View {
        HStack {
            Text(point.timeLabel)
                .font(.subheadline.monospacedDigit())
                .frame(width: 80, alignment: .leading)
            VStack(alignment: .leading, spacing: 2) {
                Text(String(format: "%.1fm · %.0fs", point.waveHeight, point.wavePeriod))
                    .font(.headline)
                Text(String(format: "Vent %.0f km/h", point.windSpeedKmh))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(String(format: "%.0f°C", point.temperature))
        }
    }
}
