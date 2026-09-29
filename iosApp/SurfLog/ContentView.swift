import SwiftUI
import SharedCore

struct ContentView: View {
    @State private var points: [HourlyForecastPoint] = []
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
                    List(Array(points.prefix(24).enumerated()), id: \.offset) { _, point in
                        ForecastRow(point: point)
                    }
                }
            }
            .navigationTitle("Montalivet")
        }
        .task { await load() }
    }

    @MainActor
    private func load() async {
        do {
            let service = SharedForecastService()
            points = try await service.getForecast(spot: SpotCoordinates(latitude: 45.38, longitude: -1.16))
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
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
