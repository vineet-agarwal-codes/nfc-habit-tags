import Foundation
import HabitCore

/** Public project URL and publishable key, the same ones the Android app ships. */
enum SupabaseConfig {
    static let url = "https://bfwyytbghnfwnikpcugx.supabase.co"
    static let key = "sb_publishable_eKASnJMTC64q14v01LMnEQ_IgEQVup8"
}

struct RemoteCatalog {
    let recipes: String
    let groups: String
    let stats: String?
}

struct BackendError: Error { let message: String }

/** The same three reads and one RPC as Android's SupabaseBackend; decoding happens in the shared core. */
final class SupabaseBackend {
    private let session = URLSession(configuration: .ephemeral)

    private func get(_ path: String) async throws -> String {
        var request = URLRequest(url: URL(string: SupabaseConfig.url + path)!)
        request.setValue(SupabaseConfig.key, forHTTPHeaderField: "apikey")
        let (data, response) = try await session.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode ?? 0 < 300 else { throw BackendError(message: "GET \(path) failed") }
        return String(decoding: data, as: UTF8.self)
    }

    func fetchCatalog() async throws -> RemoteCatalog {
        let recipes = try await get("/rest/v1/recipes?select=id,doc,published_at,sort_hint&published=is.true")
        let groups = try await get("/rest/v1/app_groups?select=id,doc")
        let stats = try? await get("/rest/v1/recipe_stats?select=recipe_id,weekly_active,installs")
        return RemoteCatalog(recipes: recipes, groups: groups, stats: stats)
    }

    func report(body: String) async throws {
        var request = URLRequest(url: URL(string: SupabaseConfig.url + "/rest/v1/rpc/report_events")!)
        request.httpMethod = "POST"
        request.setValue(SupabaseConfig.key, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data(body.utf8)
        let (_, response) = try await session.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode ?? 0 < 300 else { throw BackendError(message: "report_events failed") }
    }
}

/** Watches Supabase Realtime for catalog changes while the app is in the foreground. */
final class RealtimeWatcher {
    private let onChange: () -> Void
    private var task: URLSessionWebSocketTask?
    private var heartbeat: Foundation.Timer?
    private var ref: Int32 = 1

    init(onChange: @escaping () -> Void) { self.onChange = onChange }

    func start() {
        guard task == nil, let url = URL(string: Realtime.shared.socketUrl(baseUrl: SupabaseConfig.url, apiKey: SupabaseConfig.key)) else { return }
        let socket = URLSession.shared.webSocketTask(with: url)
        task = socket
        socket.resume()
        send(Realtime.shared.joinMessage(ref: nextRef()))
        heartbeat = Foundation.Timer.scheduledTimer(withTimeInterval: TimeInterval(Realtime.shared.HEARTBEAT_MS) / 1000, repeats: true) { [weak self] _ in
            guard let self else { return }
            self.send(Realtime.shared.heartbeat(ref: self.nextRef()))
        }
        receive(on: socket)
    }

    func stop() {
        heartbeat?.invalidate()
        heartbeat = nil
        task?.cancel(with: .normalClosure, reason: nil)
        task = nil
    }

    private func nextRef() -> Int32 {
        ref += 1
        return ref
    }

    private func send(_ text: String) {
        task?.send(.string(text)) { _ in }
    }

    private func receive(on socket: URLSessionWebSocketTask) {
        socket.receive { [weak self] result in
            guard let self, self.task === socket else { return }
            switch result {
            case .success(.string(let text)):
                if Realtime.shared.isCatalogChange(message: text) { self.onChange() }
                self.receive(on: socket)
            case .success:
                self.receive(on: socket)
            case .failure:
                DispatchQueue.main.async { if self.task === socket { self.stop() } }
            }
        }
    }
}
